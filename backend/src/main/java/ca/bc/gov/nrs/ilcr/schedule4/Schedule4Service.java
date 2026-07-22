package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.SubPageRowRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryInput;
import ca.bc.gov.nrs.ilcr.schedule4.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.LocationCheckResult;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4LocationRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4SubPageRowRequest;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRowType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 4 read document from the stored {@code TRANSPORTATION_REPORT} locations and
 * their in-scope category detail rows. Every derived value ({@code perUnit}, {@code kind},
 * {@code editable}, per-category {@code distance}) is computed here (AD-5/AD-6) — never read from
 * storage as a client-supplied figure.
 *
 * <p>The mill/year context is validated by {@code MillContextService} in the controller before this
 * runs (AD-4). A valid, active mill/year with NO category-{@code "4"} locations is NOT a 404 — it is
 * the legitimate no-locations state and yields a 200 empty {@code locations: []}, editable per track
 * (mirrors the legacy JPA {@code getResultList()}-empty → empty-doc behaviour; the DAO returned null
 * only on a thrown exception).
 *
 * <p>Storage shape (delivery-DB confirmed, see the Story 4.1/4.2 Completion Notes): a location is a
 * FAMILY of {@code TRANSPORTATION_REPORT} rows sharing a {@code LOCATION_DESCRIPTION} — one primary
 * report (distance null) holds the 9 fixed categories, and each distance-based category (47/48/52)
 * lives on its OWN report with its OWN {@code DISTANCE} (two distance categories on one location can
 * differ). Category amounts are {@code ILCR_COST_REPORT_DETAIL} rows joined by
 * {@code TRANSPORTATION_REPORT_ID}. The Story 4.2 write path (create/edit/copy/delete a location)
 * lives in this same service.
 */
@Service
@Slf4j
public class Schedule4Service {

  private static final String STATUS_DRAFT = "D";

  private static final String KIND_FIXED = "FIXED";
  private static final String KIND_DISTANCE = "DISTANCE";

  /** The 3 distance-based cost-item codes (47 Truck Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul). */
  private static final Set<Integer> DISTANCE_CODES = Set.of(47, 48, 52);

  /** The 9 fixed no-distance cost-item codes (written as detail rows on the primary report). */
  private static final Set<Integer> FIXED_CODES = Set.of(40, 41, 42, 44, 45, 49, 50, 51, 53);

  private static final String OUTCOME_MET = "MET";
  private static final String OUTCOME_ISSUES = "ISSUES";
  private static final String MSG_SCHEDULE_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_LOCATION_MET = "locationRequirementsMetMsg";
  private static final String MSG_MISSING_REQUIRED = "missingRequiredFieldMsg";

  private final Schedule4Repository repository;

  public Schedule4Service(Schedule4Repository repository) {
    this.repository = repository;
  }

  /**
   * Assemble the Schedule 4 read document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the read document (never null; {@code locations: []} when the mill/year has none)
   */
  @Transactional(readOnly = true)
  public Schedule4Response getSchedule4(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // A location spans MULTIPLE TRANSPORTATION_REPORT rows sharing LOCATION_DESCRIPTION (delivery-DB
    // confirmed): one primary report carries the 9 fixed categories (distance null); each distance
    // category 47/48/52 is its OWN report with its OWN distance. So group by location NAME and take a
    // distance-category's distance from the report that detail belongs to — never a single
    // per-location value. Report order (legacy findTransportationReportDetails) is by report id;
    // the LinkedHashMap keeps first-seen (lowest report id) location order.
    List<LocationRow> locationRows = repository.findLocations(millId, year);
    Map<Integer, BigDecimal> distanceByReport = new HashMap<>();
    Map<Integer, String> nameByReport = new HashMap<>();
    Map<String, List<CategoryAmount>> categoriesByName = new LinkedHashMap<>();
    // The location's stable, rename-safe id (§Decision 2): the distance-null primary report if the
    // family has one, else its lowest report id.
    Map<String, Integer> primaryIdByName = new HashMap<>();
    Map<String, Integer> minIdByName = new HashMap<>();
    for (LocationRow loc : locationRows) {
      distanceByReport.put(loc.transportationReportId(), loc.distance());
      nameByReport.put(loc.transportationReportId(), loc.locationDescription());
      categoriesByName.computeIfAbsent(loc.locationDescription(), k -> new ArrayList<>());
      minIdByName.merge(loc.locationDescription(), loc.transportationReportId(), Math::min);
      if (loc.distance() == null) {
        primaryIdByName.putIfAbsent(loc.locationDescription(), loc.transportationReportId());
      }
    }

    for (DetailRow d : repository.findInScopeDetails(millId, year)) {
      if (d.costItemCode() == null) {
        continue;
      }
      String name = nameByReport.get(d.transportationReportId());
      if (name == null) {
        continue; // detail's report is not a category-"4" location row (defensive)
      }
      boolean isDistance = DISTANCE_CODES.contains(d.costItemCode());
      BigDecimal categoryDistance =
          isDistance ? normalize(distanceByReport.get(d.transportationReportId())) : null;
      categoriesByName.get(name).add(new CategoryAmount(
          d.costItemCode(),
          isDistance ? KIND_DISTANCE : KIND_FIXED,
          normalize(d.volume()),
          d.cost(),
          categoryDistance,
          perUnit(bd(d.cost()), d.volume())));
    }

    // Sub-page list rows (Story 4.3): each its own report sharing the location name; grouped under
    // the location, kept separate from the fixed/distance category grid.
    Map<String, List<SubPageRow>> subPageByName = new HashMap<>();
    for (SubPageRowRow r : repository.findSubPageRows(millId, year)) {
      if (r.costItemCode() == null) {
        continue;
      }
      subPageByName.computeIfAbsent(r.locationDescription(), k -> new ArrayList<>())
          .add(new SubPageRow(
              r.transportationReportId(),
              r.costItemCode(),
              r.description(),
              normalize(r.distance()),
              normalize(r.volume()),
              r.cost(),
              r.cycle(),
              perUnit(bd(r.cost()), r.volume())));
    }

    List<Location> locations = new ArrayList<>(categoriesByName.size());
    categoriesByName.forEach((name, categories) -> locations.add(new Location(
        primaryIdByName.getOrDefault(name, minIdByName.get(name)), name, categories,
        subPageByName.getOrDefault(name, List.of()))));

    return new Schedule4Response(millId, year, trackStatus, editable, locations, null);
  }

  /**
   * Evaluate the Schedule 4 completion requirement (BR-07, Check Status) for a mill/year — read-only
   * (AD-5), mutates nothing. Reuses the assembled read model ({@link #getSchedule4}) and, per
   * location, flags every in-scope category / sub-page row whose Cost is null as a missing field (0
   * counts as present; Distance is NOT enforced — §Decision 2, legacy parity; Comments are soft —
   * §Decision 3). The schedule {@code outcome} is {@code MET} only when EVERY location passes
   * (all-or-nothing, S31). Emits bundle KEYS; the controller resolves the verbatim text (AD-8),
   * substituting the location name into the per-location met message. A mill/year with no locations
   * is vacuously MET (legacy {@code isSchedule4Valid} AND-over-locations).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the MET/ISSUES outcome + per-location breakdown (message text resolved by the controller)
   */
  @Transactional(readOnly = true)
  public Schedule4CheckStatusResponse checkStatus(long millId, int year) {
    // callerMayEdit is irrelevant to the requirement check (only stored Costs matter); pass false.
    Schedule4Response document = getSchedule4(millId, year, false);
    List<LocationCheckResult> results = new ArrayList<>(document.locations().size());
    boolean scheduleMet = true;
    for (Location location : document.locations()) {
      List<FieldIssue> issues = new ArrayList<>();
      // Every stored category (fixed + distance-based) with a null Cost is a missing field.
      for (CategoryAmount category : location.categories()) {
        if (category.cost() == null) {
          issues.add(new FieldIssue(category.code(), new MessageInfo(MSG_MISSING_REQUIRED, null)));
        }
      }
      // Sub-page list rows enforce Cost only, same rule (Story 4.3 rows; distance/cycle not checked).
      for (SubPageRow row : location.subPageRows()) {
        if (row.cost() == null) {
          issues.add(new FieldIssue(row.code(), new MessageInfo(MSG_MISSING_REQUIRED, null)));
        }
      }
      boolean met = issues.isEmpty();
      scheduleMet &= met;
      List<MessageInfo> messages =
          met ? List.of(new MessageInfo(MSG_LOCATION_MET, null)) : List.of();
      results.add(new LocationCheckResult(location.id(), location.name(), met, messages, issues));
    }
    String outcome = scheduleMet ? OUTCOME_MET : OUTCOME_ISSUES;
    List<MessageInfo> scheduleMessages =
        scheduleMet ? List.of(new MessageInfo(MSG_SCHEDULE_MET, null)) : List.of();
    return new Schedule4CheckStatusResponse(outcome, scheduleMessages, results);
  }

  /**
   * Save (create-or-edit) one Schedule 4 location and return the recomputed document (Story 4.2,
   * S01/S02/S07). The mill/year context is already validated in the controller (AD-4). Enforces the
   * Draft gate (AD-9), server-side name uniqueness (BR-02), and per-location optimistic locking
   * (§Decision 3).
   *
   * <p>A location is a FAMILY of {@code TRANSPORTATION_REPORT} rows: {@code request.id()} null →
   * CREATE (insert the primary report, revision 0→1); present → EDIT (bump the primary revision,
   * re-stamp the family name on rename). The 9 fixed categories are detail rows on the primary; each
   * distance category (47/48/52) lives on its own child report with its own distance — inserted when
   * first entered, updated in place otherwise, and DELETED when cleared to fully-empty (the write
   * mirror of the per-category-distance read; §Decision 1). Copy (S07) is a client-driven prefill
   * that arrives here as an ordinary create (BR-09) — no dedicated server endpoint.
   *
   * <p>The whole method is one transaction: a persistence fault rolls back and surfaces as 500
   * ({@code scheduleNotSavedErrorMsg}), logging type-only (AD-11).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the location name, optimistic-lock token, and entered categories (validated)
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed Schedule 4 document
   */
  @Transactional
  public Schedule4Response saveLocation(
      long millId, int year, Schedule4LocationRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    String name = request.name().trim();
    // The edited family's current name (null on create) — excluded from the uniqueness comparison so
    // a no-op or case-only self-rename never self-collides.
    String oldName = request.id() == null
        ? null
        : repository.findLocationName(request.id()).orElse(null);
    if (repository.nameExists(millId, year, name, oldName)) {
      throw new LocationNameConflictException();
    }
    try {
      int primaryId;
      if (request.id() == null) {
        primaryId = repository.insertReport(millId, year, name, null, user); // primary: distance null
        repository.bumpRevision(primaryId, 0, user); // 0 -> 1 (monotonic, mirrors Schedule 2)
      } else {
        primaryId = request.id();
        int expectedRevision = request.revisionCount() == null ? 0 : request.revisionCount();
        if (repository.bumpRevision(primaryId, expectedRevision, user) == 0) {
          throw new StaleRevisionException();
        }
        if (oldName != null && !oldName.equals(name)) {
          repository.renameFamily(millId, year, oldName, name, user);
        }
      }
      for (CategoryInput category : request.categoriesOrEmpty()) {
        writeCategory(millId, year, name, primaryId, category, user);
      }
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Never log cost/volume/distance values (AD-11) — action + status + exception type only.
      log.warn("Schedule 4 location save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule4(millId, year, callerMayEdit);
  }

  /**
   * Delete a whole Schedule 4 location family (primary + distance children + cascaded details) for a
   * mill/year, targeted by the primary report {@code id} (BR-08, S10). Enforces the same Draft gate
   * as save. Idempotent: an absent/unknown id is a no-op that still returns success (never 404),
   * mirroring Schedule 2's delete. Context is already validated in the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param id the primary report id of the location to delete
   */
  @Transactional
  public void deleteLocation(long millId, int year, int id) {
    requireDraft(millId, year);
    String name = repository.findLocationName(id).orElse(null);
    if (name == null) {
      return; // idempotent — nothing to remove
    }
    try {
      repository.deleteFamily(millId, year, name);
    } catch (DataAccessException ex) {
      log.warn("Schedule 4 location delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * Add one sub-page list row (Towing/Truck Rehaul/Other) to a location and return the recomputed
   * document (Story 4.3, S03–S06). A row is its OWN {@code TRANSPORTATION_REPORT} sharing the
   * location's name + a single detail (item 43/46/55 with {@code ITEM_DESCRIPTION}); {@code cycle} is
   * written for Truck Rehaul only. Draft gate (AD-9); unknown {@code locationId} → 404; persistence
   * fault → 500 (type-only log). The mill/year context is validated in the controller (AD-4).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param locationId the parent location's primary report id (its {@link Location#id()})
   * @param request the row type, description, and amounts (validated)
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed Schedule 4 document
   */
  @Transactional
  public Schedule4Response addSubPageRow(long millId, int year, int locationId,
      Schedule4SubPageRowRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    String name = repository.findLocationName(locationId).orElse(null);
    if (name == null) {
      throw new ScheduleNotFoundException(); // 404 — no such location to attach the row to
    }
    try {
      Integer cycle = request.type() == SubPageRowType.TRUCK_REHAUL ? request.cycle() : null;
      int rowId = repository.insertSubPageReport(millId, year, name, request.distance(), cycle, user);
      repository.insertDetailWithDescription(rowId, request.type().code(),
          request.volume(), request.cost(), request.description().trim(), user);
    } catch (DataAccessException ex) {
      log.warn("Schedule 4 sub-page add failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule4(millId, year, callerMayEdit);
  }

  /**
   * Delete one sub-page list row (its whole {@code TRANSPORTATION_REPORT} + cascaded detail) and
   * return the recomputed document (Story 4.3, S11 / BR-08). Draft gate (AD-9). Idempotent: an
   * unknown id — or an id that is NOT a sub-page row (e.g. a location's primary/category report) —
   * is a no-op success, so this endpoint can never delete a location. Fixes the legacy Other-sub-page
   * bug by returning the "deleted" semantics for all three types (§Decision 4). Context validated in
   * the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   * @param rowId the sub-page row's own report id
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @return the recomputed Schedule 4 document
   */
  @Transactional
  public Schedule4Response deleteSubPageRow(long millId, int year, int rowId, boolean callerMayEdit) {
    requireDraft(millId, year);
    if (repository.isSubPageRow(rowId, millId, year)) {
      try {
        repository.deleteReport(rowId);
      } catch (DataAccessException ex) {
        log.warn("Schedule 4 sub-page delete failed for mill {} year {} [{}]",
            millId, year, ex.getClass().getSimpleName());
        throw new ScheduleNotSavedException();
      }
    }
    return getSchedule4(millId, year, callerMayEdit);
  }

  /**
   * Persist one entered category. Fixed codes become detail rows on the primary report; distance
   * codes (47/48/52) live on their own child report (insert/update, or delete when fully emptied).
   * Out-of-scope codes (deferred sub-page lists 43/46/55, dead 54, unknown) are ignored — never
   * written by this story.
   */
  private void writeCategory(long millId, int year, String name, int primaryId,
      CategoryInput category, String user) {
    Integer code = category.code();
    if (code == null) {
      return;
    }
    if (DISTANCE_CODES.contains(code)) {
      writeDistanceCategory(millId, year, name, code, category, user);
    } else if (FIXED_CODES.contains(code)) {
      repository.upsertDetail(primaryId, code, category.volume(), category.cost(), user);
    }
  }

  /**
   * A distance category is its OWN {@code TRANSPORTATION_REPORT} child (its own distance). Fully-empty
   * (distance + volume + cost all null) clears it: delete the child if one exists. Otherwise
   * update-in-place when present, else insert a new child report; then upsert its single detail row.
   */
  private void writeDistanceCategory(long millId, int year, String name, int code,
      CategoryInput category, String user) {
    Optional<Integer> existing = repository.findDistanceReportId(millId, year, name, code);
    boolean empty =
        category.distance() == null && category.volume() == null && category.cost() == null;
    if (empty) {
      existing.ifPresent(repository::deleteReport);
      return;
    }
    int reportId;
    if (existing.isPresent()) {
      reportId = existing.get();
      repository.updateReportDistance(reportId, category.distance(), user);
    } else {
      reportId = repository.insertReport(millId, year, name, category.distance(), user);
    }
    repository.upsertDetail(reportId, code, category.volume(), category.cost(), user);
  }

  /** The Draft gate shared by save and delete: the Schedules 1–10 track must be Draft (else 409). */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  private static BigDecimal bd(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  /**
   * Strip an Oracle {@code NUMBER(18,4)} volume/distance to its natural form so a whole value
   * serializes as an integer ({@code 2000}, not {@code 2000.0000}) — Schedule 1/2 wire-contract
   * parity. Null-safe.
   */
  private static BigDecimal normalize(BigDecimal value) {
    if (value == null) {
      return null;
    }
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * $/m³ = cost / volume, computed server-side (AD-6, legacy {@code costVolumeConverter} display
   * figure). Null when either operand is null or volume is zero. Scale 4 HALF_UP
   * {@code stripTrailingZeros}, kept at scale &ge; 1 so it serializes as a decimal (e.g. {@code 50.0},
   * not {@code 50}) — Schedule 1/2 parity.
   */
  private static BigDecimal perUnit(BigDecimal cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    BigDecimal result = cost.divide(volume, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }
}
