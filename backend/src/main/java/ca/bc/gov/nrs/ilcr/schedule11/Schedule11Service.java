package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule11.dto.BiogeoclimaticOption;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocationRequest;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 11 (Basic Silviculture) locations document and computes all BR-08
 * derivations server-side (AD-5, AD-6). The mill/year context is already validated by {@code
 * MillContextService} (AD-4) — zero locations is a valid state, never re-checked here. The track
 * status is read via millcontext (AD-9 single owner) and is the SILVICULTURE track's code — the
 * 1–10 track never touches this document (AR7).
 *
 * <p>Legacy arithmetic is transcribed verbatim from {@code CoreUtil} ({@code
 * bigDecimalAddition}/{@code bigDecimalDivision}/{@code sumBigDecimalAreas}/ {@code
 * sumBigDecimalCosts}): null — never zero — signals "no data" at every level. Row and footer
 * figures are computed directly from their two operands, NOT via the legacy getter-side- effect
 * ordering quirk (recorded in the story; correct only by JSF render order).
 */
@Service
@Slf4j
public class Schedule11Service {

  private static final String STATUS_DRAFT = "D";

  // The delivery unique key on (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
  // BECBIOGEOCLIMATIC_CATALOGUE_ID, LOCATION) — legacy SILVICULTURE_UNIQUE_BIOGEOCODE. Only THIS
  // constraint may map to the biogeo 409; any other integrity failure (PK collision from a lagging
  // sequence, a NOT NULL on a cost child) is a server fault -> 500 ERR-004, never a false conflict.
  private static final String BIOGEO_UNIQUE_CONSTRAINT = "BSRPT_BSRPT_UK_UK";

  // Legacy Constant.REPORT_COST_ITEMS.Schedule11_1_* ids (delivery-verified: 23='Planned',
  // 24='Actual', category '11').
  private static final int CODE_PLANNED = 23;
  private static final int CODE_ACTUAL = 24;

  // Legacy Constant.POSITIVE_IND.
  private static final String POSITIVE_IND = "Y";

  // Check-status message keys (SUC-004 always, SUC-003 when met; FLD-004 per missing cost reuses
  // the shared "Value Required" key). Composed verbatim in legacy Schedule11MB.checkStatus() order.
  private static final String MSG_STATUS_CHECKED = "checkStatusMessage";
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";

  private final Schedule11Repository repository;
  private final MillContextService millContextService;
  private final MessageSource messageSource;

  /**
   * Constructs the Schedule 11 service.
   *
   * @param repository the repository
   * @param millContextService the mill context service
   * @param messageSource the message source
   */
  public Schedule11Service(
      Schedule11Repository repository,
      MillContextService millContextService,
      MessageSource messageSource) {
    this.repository = repository;
    this.millContextService = millContextService;
    this.messageSource = messageSource;
  }

  /**
   * The Schedule 11 aggregate document for a mill/year (S01 serve half). Context is already
   * validated by {@code MillContextService} in the controller (AD-4).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (from {@code
   *     SchedulePermissions} — never inlined, AC7)
   * @return the document with server-computed BR-08 figures and track-independent editability
   */
  @Transactional(readOnly = true)
  public Schedule11Response getSchedule11(long millId, int year, boolean callerMayEdit) {
    String trackStatus =
        millContextService.findSchedule11TrackStatusCode(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit);
  }

  /**
   * Type-ahead search of the global BEC catalogue for the forced-selection field (BR-09, S16). A
   * blank/whitespace term returns an empty list WITHOUT touching the database (legacy {@code
   * minQueryLength=1}); otherwise the trimmed term — with Oracle {@code LIKE} metacharacters
   * escaped so the match is a LITERAL prefix (legacy {@code String.startsWith} semantics) — drives
   * a case-insensitive prefix match on the concatenated label, and each catalogue row is mapped to
   * its {@code becLabel} — the same concat the served location rows use, so a picked option reads
   * identically to a saved row. The catalogue is global: no mill/year context, no Draft gate
   * (VIEW-gated lookup).
   *
   * @param term the raw search term from the request (may be null/blank)
   * @return the label-ordered options (empty when the term is blank; capped by the repository)
   */
  @Transactional(readOnly = true)
  public List<BiogeoclimaticOption> searchBiogeoCatalogue(String term) {
    if (term == null || term.isBlank()) {
      return List.of();
    }
    return repository.searchBiogeoCatalogue(escapeLike(term.trim())).stream()
        .map(
            row ->
                new BiogeoclimaticOption(
                    row.id(),
                    becLabel(row.becZoneCode(), row.subzone(), row.variant(), row.phase())))
        .toList();
  }

  /**
   * Assemble the served document for a KNOWN track status. The write methods reuse this with the
   * {@code D} their Draft gate just proved (same transaction) instead of re-running the
   * track-status query on every mutation.
   */
  private Schedule11Response buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit) {
    // Editable = EDIT_SCHEDULE ∧ silviculture track Draft (legacy disableUserInputSchedule11
    // D+Licensee row; a null code cannot be Draft). The 1–10 track plays no part (S10).
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<SilvicultureLocationEntity> locationRows = repository.findLocations(year, millId);
    Map<Long, CostPair> costs = unpackCosts(repository.findCostDetails(year, millId));

    List<SilvicultureLocation> locations =
        locationRows.stream()
            .map(row -> toLocation(row, costs.getOrDefault(row.locationId(), CostPair.EMPTY)))
            .toList();

    // Document revisionCount is ALWAYS null: no ILCR_REPORT_SUMMARY row exists for this list
    // schedule (recorded AR11 keying delta — 25.2 keys per-row). message is null on the GET
    // (Jackson non_null omits it) — the write echoes attach it via withMessage in the controller.
    return new Schedule11Response(
        millId, year, trackStatus, editable, null, locations, totalsOf(locations), null);
  }

  // ===============================================================================================
  // Write path (Story 25.2) — add/edit/delete a location. Each method is one transaction: a
  // persistence failure rolls back and surfaces as 500/ERR-004. The Draft gate keys on the
  // SILVICULTURE track (AD-9) — never the 1–10 track (AR7). Costs/comments/location values are
  // NEVER logged (AD-11).
  // ===============================================================================================

  /**
   * Create one Schedule 11 location and return the recomputed document (S01/S02/S09). The location
   * persists immediately (legacy {@code addLocation()} → {@code save(true)}). Costs are optional; a
   * present cost writes its item-23/24 child, an absent cost writes no row (delivery-faithful —
   * real silviculture locations carry no cost rows, AC9). Draft-gated (AD-9); a duplicate
   * biogeo/location key → 409, an unresolvable biogeo id → 400.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered location fields
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document (the new row included; footer totals refreshed)
   */
  @Transactional
  public Schedule11Response addLocation(
      long millId,
      int year,
      SilvicultureLocationRequest request,
      boolean callerMayEdit,
      String user) {
    requireSilvicultureDraft(millId, year);
    requireValidBiogeo(request.biogeoclimaticCatalogueId());
    try {
      long locationId = repository.nextLocationId();
      repository.insertLocation(
          locationId,
          millId,
          year,
          request.location(),
          request.biogeoclimaticCatalogueId(),
          request.netArea(),
          enhancedInd(request.enhancedIndicator()),
          request.comments(),
          user);
      writeCosts(
          locationId,
          wholeDollars(request.actualCost()),
          wholeDollars(request.plannedCost()),
          user);
    } catch (DataIntegrityViolationException ex) {
      throwConflictOrNotSaved(ex, "add", millId, year);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 11 add failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Edit one existing Schedule 11 location and return the recomputed document (S03).
   * Optimistic-lock on the row's {@code REVISION_COUNT} (AR11): a stale token → 409, an unknown id
   * → 404. Cost edits upsert their child row, or remove it when a cost is cleared to null (clear
   * semantics).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param locationId the location id to edit
   * @param request the entered fields + the required {@code revisionCount} token
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document
   */
  @Transactional
  public Schedule11Response updateLocation(
      long millId,
      int year,
      long locationId,
      SilvicultureLocationRequest request,
      boolean callerMayEdit,
      String user) {
    requireSilvicultureDraft(millId, year);
    requireValidBiogeo(request.biogeoclimaticCatalogueId());
    try {
      int updated =
          repository.updateLocation(
              locationId,
              millId,
              year,
              request.revisionCount(),
              request.location(),
              request.biogeoclimaticCatalogueId(),
              request.netArea(),
              enhancedInd(request.enhancedIndicator()),
              request.comments(),
              user);
      if (updated == 0) {
        // 0 rows = the id is absent (404) OR the revision is stale (409) — disambiguate (AC7).
        if (repository.countLocation(locationId, millId, year) == 0) {
          throw new SilvicultureLocationNotFoundException();
        }
        throw new StaleRevisionException();
      }
      writeCosts(
          locationId,
          wholeDollars(request.actualCost()),
          wholeDollars(request.plannedCost()),
          user);
    } catch (DataIntegrityViolationException ex) {
      throwConflictOrNotSaved(ex, "update", millId, year);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 11 update failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Delete one Schedule 11 location and ALL its cost children (S07 — legacy whole-row removal; a
   * 23/24-only cascade would orphan other attached items). Draft-gated; an unknown id → 404.
   * Carries NO revision token — the systemic AR11 DELETE deviation (Story 2.1; legacy delete
   * re-fetches by PK and removes, no lock).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param locationId the location id to delete
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @return the recomputed aggregate document (the row and its costs gone; footer refreshed)
   */
  @Transactional
  public Schedule11Response deleteLocation(
      long millId, int year, long locationId, boolean callerMayEdit) {
    requireSilvicultureDraft(millId, year);
    try {
      // The mill/year-scoped location delete runs FIRST: its 0-rows result is the ownership check,
      // so the id-scoped cost cascade below can never touch another mill's rows.
      int deleted = repository.deleteLocation(locationId, millId, year);
      if (deleted == 0) {
        throw new SilvicultureLocationNotFoundException();
      }
      repository.deleteCostsForLocation(locationId);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 11 delete failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Write the two cost children of a location per the clear semantics: a present cost upserts its
   * item row (audit-preserving), a null cost removes any existing row (delivery-faithful — absent
   * cost rows are the dominant real case, AC9).
   */
  private void writeCosts(long locationId, Integer actualCost, Integer plannedCost, String user) {
    writeCost(locationId, CODE_ACTUAL, actualCost, user);
    writeCost(locationId, CODE_PLANNED, plannedCost, user);
  }

  private void writeCost(long locationId, int costItemId, Integer cost, String user) {
    if (cost == null) {
      repository.deleteCost(locationId, costItemId);
    } else {
      repository.upsertCost(locationId, costItemId, cost, user);
    }
  }

  /**
   * Map an integrity failure to its verbatim client error: the {@code BSRPT_BSRPT_UK_UK} unique key
   * (legacy SILVICULTURE_UNIQUE_BIOGEOCODE) → 409 biogeo conflict; ANY other violated constraint
   * (PK, NOT NULL, a cost-child failure) is a server fault → 500 ERR-004 — a blanket 409 would tell
   * the user to change a biogeo that is not the problem.
   */
  private void throwConflictOrNotSaved(
      DataIntegrityViolationException ex, String action, long millId, int year) {
    String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
    if (cause.contains(BIOGEO_UNIQUE_CONSTRAINT)) {
      throw new SilvicultureBiogeoConflictException();
    }
    log.warn(
        "Schedule 11 {} failed for mill {} year {} [{}]",
        action,
        millId,
        year,
        ex.getClass().getSimpleName());
    throw new ScheduleNotSavedException();
  }

  /**
   * The Draft-gate for every write: the SILVICULTURE track must be {@code D} (else 409). Keys on
   * {@code MILL_SILVICULTUR_STATUS_CODE} via millcontext (AD-9 single owner) — a Submitted/Verified
   * 1–10 track leaves Schedule 11 writable (AR7 track independence). Context (400/404/409-mill) is
   * already validated by the controller before this runs (AD-4).
   */
  private void requireSilvicultureDraft(long millId, int year) {
    String trackStatus =
        millContextService.findSchedule11TrackStatusCode(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /** Reject a biogeo id that resolves to no catalogue row (force-selection enforcement, S16). */
  private void requireValidBiogeo(long biogeoclimaticCatalogueId) {
    if (repository.countBiogeo(biogeoclimaticCatalogueId) == 0) {
      throw new InvalidBiogeoCodeException();
    }
  }

  private static String enhancedInd(Boolean enhancedIndicator) {
    return Boolean.TRUE.equals(enhancedIndicator) ? POSITIVE_IND : "N";
  }

  // ===============================================================================================
  // Check Status — BR-07 readiness validation (Story 25.2, S04/S05/S06). Read-only; no persistence,
  // no transition. A location passes iff BOTH costs are non-null (missing = null; 0 is present).
  // Legacy Schedule11CheckStatus.checkStatus() + Schedule11MB.checkStatus() message composition.
  // ===============================================================================================

  /**
   * BR-07 Check Status: validate whether every stored Schedule 11 location has both costs.
   * Read-only — mutates nothing (VIEW-gated, not Draft-gated; runs on any status). Zero locations →
   * vacuously met. SUC-004 "Status has been checked" is returned on every call; SUC-003 only when
   * met.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the check-status result with verbatim, legacy-ordered messages
   */
  @Transactional(readOnly = true)
  public Schedule11CheckStatusResponse checkStatus(long millId, int year) {
    List<SilvicultureLocationEntity> locationRows = repository.findLocations(year, millId);
    Map<Long, CostPair> costs = unpackCosts(repository.findCostDetails(year, millId));

    List<MessageInfo> errors = new ArrayList<>();
    for (SilvicultureLocationEntity row : locationRows) {
      CostPair pair = costs.getOrDefault(row.locationId(), CostPair.EMPTY);
      // Legacy Schedule11MB.checkStatus() order: Actual missing before Planned missing, per row.
      if (pair.actual() == null) {
        errors.add(missingCost(row.location(), "Actual cost"));
      }
      if (pair.planned() == null) {
        errors.add(missingCost(row.location(), "Planned cost"));
      }
    }

    boolean requirementsMet = errors.isEmpty();
    MessageInfo requirementsMetMessage =
        requirementsMet
            ? new MessageInfo(MSG_REQUIREMENTS_MET, resolveText(MSG_REQUIREMENTS_MET))
            : null;
    // SUC-004 is ALWAYS emitted (legacy adds "Status has been checked" on every invocation).
    MessageInfo statusChecked =
        new MessageInfo(MSG_STATUS_CHECKED, resolveText(MSG_STATUS_CHECKED));
    return new Schedule11CheckStatusResponse(
        requirementsMet, errors, requirementsMetMessage, statusChecked);
  }

  /**
   * A FLD-004 missing-cost message composed VERBATIM in legacy form: {@code "location : <location>
   * - <Actual|Planned> cost: Value Required"} — note the DOUBLE space after {@code location}
   * (legacy {@code Schedule11MB} literal) and the shared {@code missingRequiredFieldMsg} = {@code
   * "Value Required"} suffix.
   */
  private MessageInfo missingCost(String location, String costLabel) {
    String text =
        "location  : " + location + " - " + costLabel + ": " + resolveText(MSG_VALUE_REQUIRED);
    return new MessageInfo(MSG_VALUE_REQUIRED, text);
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8). */
  private String resolveText(String key) {
    return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
  }

  /**
   * Unpack the 23/24 cost rows per location (legacy {@code Schedule11DAO.getSilvicultureReport}
   * loop). Re-checks the item ids even though the SQL already filters — an out-of-scope item must
   * reach no figure. Duplicate items keep the last row read, in detail-id order (duplicates are
   * legal; never a single-row expectation).
   */
  private Map<Long, CostPair> unpackCosts(List<SilvicultureCostEntity> rows) {
    Map<Long, CostPair> byLocation = new HashMap<>();
    for (SilvicultureCostEntity row : rows) {
      CostPair pair = byLocation.getOrDefault(row.basicSilvicultureReportId(), CostPair.EMPTY);
      if (row.costItemId() == CODE_ACTUAL) {
        pair = new CostPair(row.cost(), pair.planned());
      } else if (row.costItemId() == CODE_PLANNED) {
        pair = new CostPair(pair.actual(), row.cost());
      } else {
        continue;
      }
      byLocation.put(row.basicSilvicultureReportId(), pair);
    }
    return byLocation;
  }

  /** Map one location row + its cost pair to the wire shape, computing the BR-08 row figures. */
  private SilvicultureLocation toLocation(SilvicultureLocationEntity row, CostPair costs) {
    Integer totalCost = addNullTolerant(costs.actual(), costs.planned());
    return new SilvicultureLocation(
        row.locationId(),
        row.location(),
        POSITIVE_IND.equals(row.enhancedInd()),
        row.biogeoclimaticCatalogueId(),
        becLabel(row),
        row.netArea(),
        costs.actual(),
        costs.planned(),
        totalCost,
        perNetArea(totalCost == null ? null : totalCost.longValue(), row.netArea()),
        row.comments(),
        row.revisionCount());
  }

  /**
   * The BR-08 footer, computed from the served rows ({@code Schedule11DO} getters): null-not-zero
   * sums, area total rounded to scale 1, and the per-area figure divided by the ROUNDED footer area
   * (the value legacy's getter chain used).
   */
  private SilvicultureTotals totalsOf(List<SilvicultureLocation> locations) {
    BigDecimal netArea = sumAreas(locations);
    Long actual = sumCosts(locations, SilvicultureLocation::actualCost);
    Long planned = sumCosts(locations, SilvicultureLocation::plannedCost);
    Long totalCost = addNullTolerant(actual, planned);
    return new SilvicultureTotals(
        netArea, actual, planned, totalCost, perNetArea(totalCost, netArea));
  }

  /**
   * {@code CoreUtil.sumBigDecimalAreas}: sum of non-null areas, scale 1 HALF_UP; null when none.
   */
  private static BigDecimal sumAreas(List<SilvicultureLocation> locations) {
    List<BigDecimal> areas =
        locations.stream().map(SilvicultureLocation::netArea).filter(Objects::nonNull).toList();
    if (areas.isEmpty()) {
      return null;
    }
    return areas.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(1, RoundingMode.HALF_UP);
  }

  /**
   * {@code CoreUtil.sumBigDecimalCosts}: sum of non-null whole-dollar costs; null when none (the
   * legacy per-item scale-0 rounding is a no-op on integer storage). Accumulates in {@code long} —
   * each cost is {@code NUMBER(8,0)} and a footer sum can exceed {@code Integer.MAX_VALUE}; an
   * {@code int} sum would wrap where legacy's {@code BigDecimal} did not.
   */
  private static Long sumCosts(
      List<SilvicultureLocation> locations,
      java.util.function.Function<SilvicultureLocation, Integer> cost) {
    List<Integer> values = locations.stream().map(cost).filter(Objects::nonNull).toList();
    if (values.isEmpty()) {
      return null;
    }
    return values.stream().mapToLong(Integer::intValue).sum();
  }

  /**
   * {@code CoreUtil.bigDecimalAddition} on whole-dollar row costs: null+null=null, null+x=x. Row
   * operands each fit {@code int} and their sum (≤ 199,999,998) stays in {@code int} range.
   */
  private static Integer addNullTolerant(Integer a, Integer b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * {@code CoreUtil.bigDecimalAddition} on the {@code long} footer cost totals: same null-tolerant
   * semantics, but in {@code long} so the footer {@code totalCost = actual + planned} of two
   * already-large sums cannot overflow (legacy {@code BigDecimal} parity).
   */
  private static Long addNullTolerant(Long a, Long b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * {@code CoreUtil.bigDecimalDivision} with the recorded project-wide per-unit deviation
   * (deferred-work.md): null when the total OR the area is null OR the area is zero; otherwise
   * scale 4 HALF_UP, {@code stripTrailingZeros}, min scale 1 (so {@code 140.0}, not {@code 140}).
   * Legacy display masks (scale 2) are 25.3's formatting duty. Numerator is {@code Long} so both
   * the row total (widened) and the footer total (already {@code long}) divide overflow-free.
   */
  private static BigDecimal perNetArea(Long totalCost, BigDecimal netArea) {
    if (totalCost == null || netArea == null || netArea.signum() == 0) {
      return null;
    }
    BigDecimal result =
        BigDecimal.valueOf(totalCost).divide(netArea, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }

  private static String becLabel(SilvicultureLocationEntity row) {
    return becLabel(row.becZoneCode(), row.subzone(), row.variant(), row.phase());
  }

  /**
   * Legacy {@code BiogeoclimaticCatalogue.getBiogeoSubZoneVariantPase()}: zone+subzone+variant+
   * phase with null variant/phase as {@code ""}. A missing catalogue row (null zone — no FK in
   * delivery, AC9) yields null rather than a partial label. Shared by the served location rows and
   * the BEC catalogue lookup so both render the identical label (BR-09 forced selection).
   */
  private static String becLabel(String zoneCode, String subzone, String variant, String phase) {
    if (zoneCode == null) {
      return null;
    }
    return zoneCode + subzone + (variant != null ? variant : "") + (phase != null ? phase : "");
  }

  /**
   * Escape Oracle {@code LIKE} metacharacters ({@code \}, {@code %}, {@code _}) in the user's
   * search term so the repository match is a LITERAL prefix — legacy {@code
   * Schedule11MB.completeBiogeoSubzoneVariant} used plain {@code String.startsWith}, where these
   * characters match nothing special. Pairs with the {@code ESCAPE '\'} clause on {@link
   * Schedule11Repository#searchBiogeoCatalogue}.
   */
  private static String escapeLike(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /**
   * Narrow a validated whole-dollar cost to the {@code COST NUMBER(15)} column type. Safe by
   * construction: {@code @Digits(fraction = 0)} on the request has already rejected any fractional
   * value, so {@code intValueExact} cannot throw here.
   */
  private static Integer wholeDollars(BigDecimal cost) {
    return cost == null ? null : cost.intValueExact();
  }

  /** The 24/23 whole-dollar cost pair of one location; either side may be null. */
  private record CostPair(Integer actual, Integer planned) {
    static final CostPair EMPTY = new CostPair(null, null);
  }
}
