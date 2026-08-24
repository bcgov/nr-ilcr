package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CodeRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.CostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository.RoadRecordRow;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest.CheckEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CodeLists;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6SaveRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 6 (Road Management Costs) read document from the stored {@code
 * ROAD_MAINTENANCE_REPORT} records and their item-69 cost details, computing every derived value
 * server-side (AD-5, AD-6): the Resource Management Grouping (RMG, BR-04), the $/m&sup3;
 * cost-per-volume (BR-04/BR-07), and the running totals (BR-07). The mill/year context is validated
 * by {@code MillContextService} in the controller before this runs (AD-4).
 *
 * <p>A valid, active mill/year with NO road records is NOT a 404 — it is the legitimate no-records
 * state and yields a 200 {@code roadRecords: []} with zero totals (mirrors the legacy {@code
 * Schedule6DAO.getSchedule}, which returned an empty document, never null, for an empty result; the
 * 404 is reserved for the missing mill/year context, Story 8.1 Task 1). A record whose
 * classification (TSA/TSB/TFL) is entirely blank is a general-comment placeholder (S18): it is
 * excluded from {@code roadRecords} but its {@code COMMENTS} supplies the schedule-level {@code
 * generalComments}.
 *
 * <p>Story 8.2 adds the write side (add/edit a road record, save the general comment) and Check
 * Status. Every write is one transaction gated on the Schedules 1–10 track being Draft (AD-9;
 * recorded hardening deviation (a) — legacy gates in the UI only) and returns the refreshed read
 * document with the Draft status just proven (Schedule 11 idiom). Costs/volumes/comments are NEVER
 * logged (AD-11).
 */
@Service
@Slf4j
public class Schedule6Service {

  private static final String STATUS_DRAFT = "D";
  private static final String AREA_TYPE_TFL = "TFL";

  // TSA_NUMBER VARCHAR2(2) (V31 DDL, delivery-verified) — the TSA-branch width guard in classify().
  private static final int TSA_NUMBER_MAX_LENGTH = 2;

  private static final String OUTCOME_MET = "MET";
  private static final String OUTCOME_ISSUES = "ISSUES";

  // Check-status message keys (the service emits keys with null text; the controller resolves the
  // verbatim composed lines — Schedule 4 idiom, AD-8).
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_ROAD_MET = "roadRequirementsMetMsg";
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";

  // The FieldIssue.field names the controller composes labels from (§ PINNED WRITE CONTRACT).
  static final String FIELD_AREA_TYPE = "areaType";
  static final String FIELD_TFL_NUMBER = "tflNumber";
  static final String FIELD_SUPPLY_BLOCK = "supplyBlock";
  static final String FIELD_COST = "cost";

  private final Schedule6Repository repository;

  public Schedule6Service(Schedule6Repository repository) {
    this.repository = repository;
  }

  /**
   * Assemble the Schedule 6 read document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (from the controller)
   * @return the read document (never null; {@code roadRecords: []} when the mill/year has none)
   */
  @Transactional(readOnly = true)
  public Schedule6Response getSchedule6(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit);
  }

  /**
   * Assemble the served document for a KNOWN track status. The write methods reuse this with the
   * {@code D} their Draft gate just proved (same transaction) instead of re-running the
   * track-status query on every mutation.
   */
  private Schedule6Response buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit) {
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<RoadRecordRow> rows = repository.findRoadRecords(millId, year);
    Map<Integer, CostDetailRow> costByRecord = costDetailsByRecord(millId, year);

    List<RoadRecord> roadRecords = new ArrayList<>();
    long totalCost = 0L;
    BigDecimal totalVolume = BigDecimal.ZERO;
    // The general comment is stored replicated on every road-record row (legacy data-model quirk);
    // legacy reads the LAST row's COMMENTS, so track it across all rows (placeholders included).
    String generalComments = null;

    for (RoadRecordRow row : rows) {
      // Comments are served raw, exactly as saved — legacy Schedule6DAO.getReport appends COMMENTS
      // untrimmed (read-side normalization rejected at code review 2026-08-04, legacy-faithful).
      generalComments = row.generalComment();
      // Classification codes ARE normalized (trimToNull), once, so the TSA-vs-TFL split below and
      // RoadGroupLookup.rmgFor decide from identical values; rmgFor's TFL-first "!= null" routing
      // is legacy-verbatim (RoadMaintenanceReportType.getRmg).
      String tsaNumber = StringUtils.trimToNull(row.tsaNumber());
      String tsbNumberCode = StringUtils.trimToNull(row.tsbNumberCode());
      String tflNumberCode = StringUtils.trimToNull(row.tflNumberCode());
      if (tsaNumber == null && tsbNumberCode == null && tflNumberCode == null) {
        // General-comment placeholder (S18, legacy Schedule6MB onlyGeneralCommentExists): no
        // classification at all — contributes the comment, not a road record. A cost detail on a
        // placeholder is a data anomaly whose money would silently vanish from totals; say so.
        if (costByRecord.containsKey(row.recordId())) {
          log.warn(
              "Schedule 6 mill {}/{}: placeholder row {} carries an item-69 cost detail; "
                  + "excluded from records and totals",
              millId,
              year,
              row.recordId());
        }
        continue;
      }
      CostDetailRow detail = costByRecord.get(row.recordId());
      BigDecimal volume = detail == null ? null : detail.volume();
      Integer cost = detail == null ? null : detail.cost();
      String comments = detail == null ? null : detail.comments();

      boolean tfl = tsaNumber == null && tflNumberCode != null;
      roadRecords.add(
          new RoadRecord(
              row.recordId(),
              row.revisionCount(),
              tfl ? AREA_TYPE_TFL : tsaNumber,
              tfl ? tflNumberCode : null,
              tfl ? null : tsbNumberCode,
              RoadGroupLookup.rmgFor(tsaNumber, tsbNumberCode, tflNumberCode),
              normalizeVolume(volume),
              cost,
              perUnit(cost == null ? null : (long) cost, volume),
              comments));

      if (cost != null) {
        totalCost += cost;
      }
      if (volume != null) {
        totalVolume = totalVolume.add(volume);
      }
    }

    // generalComments now holds the LAST row's COMMENTS (legacy reads the general comment off the
    // last road-record row; the data model replicates it on every row, so any row would do).
    return new Schedule6Response(
        millId,
        year,
        trackStatus,
        editable,
        generalComments,
        roadRecords,
        normalizeVolume(totalVolume),
        totalCost,
        perUnit(totalCost, totalVolume),
        codeLists(millId, year),
        null);
  }

  /**
   * The two dropdown lists for the entry controls (deviation (A) retired). Read on every document
   * build rather than cached: legacy read them from a process-wide {@code LookUpCaches} cache,
   * which has no counterpart here, and the two queries are small indexed code-table scans.
   */
  private Schedule6CodeLists codeLists(long millId, int year) {
    return new Schedule6CodeLists(
        toCodeDescriptions(repository.findTsaNumbers(millId, year)),
        toCodeDescriptions(repository.findSupplyBlocks(millId, year)));
  }

  private static List<CodeDescriptionDto> toCodeDescriptions(List<CodeRow> rows) {
    return rows.stream().map(r -> new CodeDescriptionDto(r.code(), r.description())).toList();
  }

  /**
   * The item-69 cost detail per road record. One detail per record is the invariant; if a duplicate
   * ever exists, first-by-id wins (rows ordered by detail id) so a derived total can't depend on
   * row order.
   */
  private Map<Integer, CostDetailRow> costDetailsByRecord(long millId, int year) {
    Map<Integer, CostDetailRow> costByRecord = new HashMap<>();
    for (CostDetailRow detail : repository.findCostDetails(millId, year)) {
      if (costByRecord.putIfAbsent(detail.roadMaintenanceReportId(), detail) != null) {
        log.warn(
            "Schedule 6 mill {}/{}: duplicate item-69 cost detail for road record {}; "
                + "keeping first-by-id",
            millId,
            year,
            detail.roadMaintenanceReportId());
      }
    }
    return costByRecord;
  }

  // ===============================================================================================
  // Write path (Story 8.2) — add/edit a road record, save the general comment. Each method is one
  // transaction: a persistence failure rolls back and surfaces as 500/ERR-004. The Draft gate keys
  // on the Schedules 1-10 track (AD-9) via the existing findTrackStatus — never the silviculture
  // track. Costs/volumes/comments are NEVER logged (AD-11).
  // ===============================================================================================

  /**
   * Create one Schedule 6 road record and return the recomputed document (S01/S03). The BR-02
   * counterpart-clear and BR-03 TFL validation run before anything persists; the insert carries the
   * CURRENT general comment (BR-09 replication invariant), and when the only existing row is the
   * general-comment placeholder the record is written ONTO that row — its id and {@code ENTRY_*}
   * survive, mirroring {@code Schedule6DAO.java:268–278} — with its item-69 detail created by the
   * upsert. Draft-gated (deviation (a)).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered record fields
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable})
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document (the new record included; totals refreshed)
   */
  @Transactional
  public Schedule6Response addRecord(
      long millId, int year, RoadRecordRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    Classification classification =
        classify(request.areaType(), request.tflNumber(), request.supplyBlock());
    try {
      List<RoadRecordRow> rows = repository.findRoadRecords(millId, year);
      Integer placeholderId = lonePlaceholderId(rows);
      int recordId;
      if (placeholderId != null
          && repository.claimPlaceholder(
                  placeholderId,
                  millId,
                  year,
                  classification.tsaNumber(),
                  classification.tsbNumberCode(),
                  classification.tflNumberCode(),
                  user)
              == 1) {
        recordId = placeholderId;
      } else {
        // The BR-09 replication invariant (the new row carries the current general comment) is
        // satisfied inside insertRoadReport's SQL, not from a value read here — see its javadoc:
        // reading it in Java lost a concurrent general-comments save (code review 2026-08-04).
        recordId = repository.nextRoadReportId();
        repository.insertRoadReport(
            recordId,
            millId,
            year,
            classification.tsaNumber(),
            classification.tsbNumberCode(),
            classification.tflNumberCode(),
            user);
      }
      repository.upsertCostDetail(
          recordId, request.volume(), request.cost(), request.comments(), user);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 6 add failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Save the whole Schedule 6 document in one transaction — every road record plus the general
   * comment (legacy {@code Schedule6DAO.saveSchedule} :236-346).
   *
   * <p>Retires deviation (C). The per-record edit / independent-comment writes this replaced could
   * only ever save the one row an Edit button had opened; with Task 7 making every row editable at
   * once, legacy's atomic whole-document save is the only shape that cannot half-land across
   * records and the comment they replicate.
   *
   * <p>Order matters: rows first, comment second. {@link Schedule6Repository#updateRoadReport}
   * deliberately never touches {@code COMMENTS} (S04 independence), so the single {@link
   * Schedule6Repository#updateAllComments} that follows is what enforces the BR-09 replication
   * invariant across every row — including any row a concurrent {@link #addRecord} inserted, which
   * is exactly what legacy's blanket write did.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request every served record plus the general comment
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document
   */
  @Transactional
  public Schedule6Response saveDocument(
      long millId, int year, Schedule6SaveRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    String comments =
        StringUtils.isBlank(request.generalComments()) ? null : request.generalComments();
    try {
      List<RoadRecordRow> stored = repository.findRoadRecords(millId, year);
      requireEveryServedRow(stored, request.records());
      // Pre-fetched once, keyed by id: the per-entry loop below used to re-query findRoadRecords
      // via isPlaceholderId on every iteration (N+1 over a list already in hand). A recordId absent
      // from this map is unknown/foreign to this mill/year, not a placeholder — updateRoadReport's
      // 0-rows-affected path below still disambiguates that to 404 exactly as before.
      Map<Integer, RoadRecordRow> storedById =
          stored.stream().collect(Collectors.toMap(RoadRecordRow::recordId, row -> row));

      for (RoadRecordEntry entry : request.records()) {
        int recordId = entry.recordId();
        // Defence in depth for the AR11 token: Bean Validation blocks a null revisionCount today,
        // but updateRoadReport takes a primitive int, so unboxing it here would NPE -> 500 for any
        // future caller that bypasses validation. Never a coerced 409 (Story 2.1 lesson).
        if (entry.revisionCount() == null) {
          throw new RevisionCountRequiredException();
        }
        // A placeholder is not a served record, so a client can never legitimately address one —
        // 404 before the update could convert it into a real record.
        RoadRecordRow existing = storedById.get(recordId);
        if (existing != null && isPlaceholder(existing)) {
          throw new RoadRecordNotFoundException();
        }
        Classification classification =
            classify(entry.areaType(), entry.tflNumber(), entry.supplyBlock());
        int updated =
            repository.updateRoadReport(
                recordId,
                millId,
                year,
                entry.revisionCount(),
                classification.tsaNumber(),
                classification.tsbNumberCode(),
                classification.tflNumberCode(),
                user);
        if (updated == 0) {
          // Zero rows means either absent (404) or stale (409) — disambiguate, never guess (the
          // Story 2.1 lesson).
          throw repository.countRoadRecord(recordId, millId, year) == 0
              ? new RoadRecordNotFoundException()
              : new StaleRevisionException();
        }
        repository.upsertCostDetail(recordId, entry.volume(), entry.cost(), entry.comments(), user);
      }

      // The BR-09 comment branches — branching on the STORED rows, not the submitted list. Those
      // two agree on every OTHER row by requireEveryServedRow above, but they can disagree
      // here: a lone existing PLACEHOLDER means `stored` is non-empty while `records` is
      // legitimately empty (nothing served). Branching on `request.records().isEmpty()` collapsed
      // that case into the zero-rows branch and INSERTED A SECOND placeholder next to the
      // original instead of updating it in place — legacy's `onlyGeneralCommentsExist` guard
      // (`Schedule6DAO.java:263,286`) exists specifically to keep writing onto the same row
      // (code review 2026-08-21, C1).
      if (stored.isEmpty()) {
        if (comments != null) {
          repository.insertPlaceholder(repository.nextRoadReportId(), millId, year, comments, user);
        }
        // blank + no rows = nothing stored, nothing to clear (a no-op success, like legacy).
      } else if (comments == null && stored.stream().allMatch(Schedule6Service::isPlaceholder)) {
        // Clearing the comment when it was the only thing stored removes the placeholder row(s)
        // (legacy generalCommentRemovedLastRecord). The DELETE re-checks the placeholder shape in
        // SQL and can legitimately match nothing — whitespace rather than NULL classification, or
        // a concurrent addRecord having claimed it since the read above. A silent no-op would
        // answer 200 "Data saved successfully" while the comment survived, so fall back to
        // clearing COMMENTS in place.
        int deleted = 0;
        for (RoadRecordRow row : stored) {
          deleted += repository.deletePlaceholder(row.recordId(), millId, year);
        }
        if (deleted < stored.size()) {
          repository.updateAllComments(millId, year, null, user);
        }
      } else {
        repository.updateAllComments(millId, year, comments, user);
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 6 document save failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Every served (non-placeholder) row must appear in the payload. Legacy posted its whole
   * in-memory list, so a partial payload could not arise there; silently skipping an absent row
   * would discard the user's data behind a success message (the same house 400 Schedules 5 and 7.4
   * pinned for their own whole-document saves). A placeholder is excluded from this check — it is
   * not served (excluded from {@code roadRecords[]}), so its omission is expected, not a defect;
   * otherwise the lone-comment state would become unsavable with an empty {@code records} list.
   */
  private static void requireEveryServedRow(
      List<RoadRecordRow> stored, List<RoadRecordEntry> submitted) {
    Set<Integer> submittedIds =
        submitted.stream().map(RoadRecordEntry::recordId).collect(Collectors.toSet());
    boolean anyOmitted =
        stored.stream()
            .filter(row -> !isPlaceholder(row))
            .anyMatch(row -> !submittedIds.contains(row.recordId()));
    if (anyOmitted) {
      throw new OmittedRoadRecordsException();
    }
  }

  /**
   * Delete one Schedule 6 road record and return the recomputed document.
   *
   * <p>Ported from legacy {@code Schedule6MB.remove} :208-218 → {@code Schedule6DAO.saveSchedule}
   * :288-310. Carries NO revision token: legacy's row Delete had none, matching the
   * general-comments precedent (deviation (c2)). Draft-gated (deviation (a)).
   *
   * <p><strong>The BR-09 delete-side re-insert is load-bearing.</strong> The schedule-level general
   * comment is stored replicated on every cat-6 row, so deleting the LAST road record would take
   * the comment with it. Legacy guards exactly this case at {@code Schedule6DAO.java:297-309} by
   * inserting a bare placeholder carrying the deleted row's comment. The row count and the comment
   * are both read before the DELETE, because afterwards neither is recoverable.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param recordId the road record id to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (for the echoed {@code
   *     editable})
   * @param user the acting user id (audit columns on the re-inserted placeholder)
   * @return the recomputed aggregate document
   */
  @Transactional
  public Schedule6Response deleteRecord(
      long millId, int year, int recordId, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    try {
      // One read answers all three questions below (the storedById refactor already applied to
      // saveDocument, :328-329, back-applied here): this used to call findRoadRecords twice (once
      // inside isPlaceholderId, once again for wasOnlyRow) plus a third targeted findRoadRecord.
      List<RoadRecordRow> stored = repository.findRoadRecords(millId, year);
      RoadRecordRow target =
          stored.stream()
              .filter(row -> row.recordId() == recordId)
              .findFirst()
              .orElseThrow(RoadRecordNotFoundException::new);
      // A placeholder is excluded from roadRecords[], so a client can never legitimately address
      // one — 404 before the delete could remove the row holding the general comment. isPlaceholder
      // stays trim-aware exactly as before (whitespace, not just NULL, counts) — a stricter null
      // check here would misclassify a whitespace row as a real record.
      if (isPlaceholder(target)) {
        throw new RoadRecordNotFoundException();
      }
      // Read before deleting: legacy decides the re-insert from the pre-delete list size
      // (Schedule6DAO.java:297 evaluates getRoadMaintenanceReports().size() == 1).
      boolean wasOnlyRow = stored.size() == 1;
      String survivingComment = target.generalComment();

      repository.deleteCostDetailsFor(recordId);
      if (repository.deleteRoadReport(recordId, millId, year) == 0) {
        // Raced by a concurrent delete between the read above and here.
        throw new RoadRecordNotFoundException();
      }
      // Empty-aware, NOT blank-aware, matching legacy's CoreUtil.isNullOrEmptyString
      // (CoreUtil.java:166-172): a whitespace-only comment re-inserts a placeholder in legacy, so
      // it
      // re-inserts one here. This was previously isNotBlank — a recorded deviation justified by
      // saveDocument normalizing blank to NULL on the save side, so a whitespace-only stored
      // comment
      // "should not exist in practice". It can still arrive on a pre-existing delivery row, and
      // there the deviation silently destroyed the comment, so the deviation is retired in favour
      // of
      // legacy's own predicate (code review 2026-08-24).
      //
      // The trim-aware isPlaceholder convention this service uses elsewhere is a separate rule
      // about
      // the CLASSIFICATION columns, not the comment: a re-inserted row is a placeholder because its
      // TSA/TSB/TFL are all NULL, whatever its COMMENTS holds.
      if (wasOnlyRow && StringUtils.isNotEmpty(survivingComment)) {
        repository.insertPlaceholder(
            repository.nextRoadReportId(), millId, year, survivingComment, user);
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 6 delete failed for mill {} year {} record {} [{}]",
          millId,
          year,
          recordId,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * The Draft gate for every write: the Schedules 1–10 track must be {@code D} (else 409). Keys on
   * {@code ILCR_MILL_REPORT_STATUS_CODE} via the existing {@code findTrackStatus} — never the
   * silviculture track (AD-9). Recorded hardening deviation (a): legacy gates in the UI only
   * ({@code Schedule6MB.java:62} TODO). Context (400/404/409-mill) is already validated by the
   * controller before this runs (AD-4).
   */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /**
   * BR-02 counterpart-clear + BR-03 TFL validation, before anything persists. {@code "TFL"} (the
   * literal, case-sensitive — legacy {@code Constant.TFL.equals}) stores the validated TFL number
   * and NULLs both TSA columns; any other area type stores TSA+TSB and NULLs the TFL. Legacy's ajax
   * listener always clears the counterpart ({@code RoadMaintenanceReportType.onTsaNumberItemChange}
   * :293–309) and the DAO nulls TSA when the type is TFL ({@code Schedule6DAO.java:221–224}) — the
   * server-side clear reproduces the only UI-reachable net effect and closes the crafted-post hole
   * (deviation (b)).
   *
   * <p>Takes the three classification fields directly, not a {@code RoadRecordRequest}: both {@code
   * addRecord} (unpacking a {@link RoadRecordRequest}) and {@code saveDocument} (unpacking a {@link
   * RoadRecordEntry}) route through this ONE helper, so the classification rule can never drift
   * between the per-row and whole-document write paths (Task 5).
   */
  private static Classification classify(String areaType, String tflNumber, String supplyBlock) {
    // Defence in depth for FLD-001, mirroring the revisionCount guard in saveDocument (:337): both
    // write DTOs mark areaType @NotBlank and both endpoints bind them @Valid, but the TSA branch
    // below dereferences the value (areaType.length()), so a direct service caller that bypasses
    // Bean Validation would get an NPE -> 500 instead of the house 400 (code review 2026-08-24).
    if (StringUtils.isBlank(areaType)) {
      throw new AreaTypeRequiredException();
    }
    if (AREA_TYPE_TFL.equals(areaType)) {
      return new Classification(null, null, requireValidTfl(tflNumber));
    }
    // The DTOs cap areaType at 3 for the "TFL" literal, so a 3-char NON-TFL code clears Bean
    // Validation and would hit TSA_NUMBER VARCHAR2(2) as ORA-12899 -> 500. Reject it as the house
    // 400 instead (code review 2026-08-04). Width only — deviation (f) still stores unknown codes.
    if (areaType.length() > TSA_NUMBER_MAX_LENGTH) {
      throw new InvalidClassificationCodeException();
    }
    return new Classification(areaType, supplyBlock, null);
  }

  /**
   * BR-03 normalize-then-validate: apply the complete legacy leading-zero alias table onto the
   * STORED value (deviation (h) — legacy normalizes only in the ajax listener; its validator-only
   * path stores an un-padded alias the FK cache silently NULLs, a defect not ported), then valid
   * iff the RMG lookup resolves it. Case-sensitive; max length 2 ({@code TFL_NUMBER_CODE
   * VARCHAR2(2)}, legacy inputs {@code maxlength="2"}) — one rule stated in three agreeing places:
   * the {@code RoadRecordRequest} {@code @Size(max = 2)}, the width guard below, and the lookup,
   * out of which legacy's unstorable {@code "52B"} entry is commented out (code review 2026-08-05).
   */
  private static String requireValidTfl(String tflNumber) {
    String normalized = normalizeTflAlias(tflNumber);
    // The width guard is redundant with the lookup now that no ported entry is 3 chars wide, and is
    // kept deliberately: it holds the column width for direct service callers (which bypass Bean
    // Validation) independently of what the verbatim table happens to contain.
    if (normalized == null
        || normalized.length() > 2
        || RoadGroupLookup.rmgFor(null, null, normalized) == null) {
      throw new InvalidTflNumberException();
    }
    return normalized;
  }

  /**
   * The complete legacy alias table ({@code RoadGroupUtil.translateNoLeadingZeroButNumberMatch}
   * :202–215): a no-leading-zero TFL number that numerically matches an accepted code becomes its
   * leading-zero form; anything else passes through unchanged for the lookup to judge.
   */
  private static String normalizeTflAlias(String tflNumber) {
    if (tflNumber == null) {
      return null;
    }
    return switch (tflNumber) {
      case "1" -> "01";
      case "3" -> "03";
      case "5" -> "05";
      case "8" -> "08";
      default -> tflNumber;
    };
  }

  /** The placeholder id when the mill/year's ONLY row is one (BR-09 reuse branch), else null. */
  private static Integer lonePlaceholderId(List<RoadRecordRow> rows) {
    if (rows.size() == 1 && isPlaceholder(rows.get(0))) {
      return rows.get(0).recordId();
    }
    return null;
  }

  /** A general-comment placeholder: classification entirely blank (the read-side S18 rule). */
  private static boolean isPlaceholder(RoadRecordRow row) {
    return StringUtils.trimToNull(row.tsaNumber()) == null
        && StringUtils.trimToNull(row.tsbNumberCode()) == null
        && StringUtils.trimToNull(row.tflNumberCode()) == null;
  }

  /** The pre-cleared classification a write persists (BR-02: exactly one side populated). */
  private record Classification(String tsaNumber, String tsbNumberCode, String tflNumberCode) {}

  // ===============================================================================================
  // Check Status (Story 8.2) — read-only readiness validation, ported VERBATIM from
  // Schedule6CheckStatus + Schedule6MB.checkStatus() :139-180 including the pinned quirks: the
  // missing-cost line is mislabelled "TSA or TFL (Cost $)" (:172), cost==0 is MET (null-only check,
  // D2 precedent), volume is never checked (commented out in legacy :19), and the schedule-level
  // pass ignores the area-type flag (isScheduleValid :26-55). VIEW-gated, not Draft-gated (2.6
  // precedent); mutates nothing; no status transition (transitions are Epics 15-18).
  // ===============================================================================================

  /**
   * Check Status for Schedule 6 (S09–S11, S20, S21). A passing schedule returns the single MET
   * banner and NO per-record results at all (the legacy pass branch never enters the loop); a
   * failing one returns each record's issues plus the per-record met banner for clean records. The
   * service emits bundle keys with null text; the controller composes/resolves (AD-8).
   *
   * <p>{@code request} is the on-screen values (Task 6, {@code Schedule6MB.checkStatus} :139-140 —
   * legacy's {@code ajax="false"} postback applied the screen to the model before evaluating, so
   * the verdict always described the screen, never the database). The candidates below come from
   * {@code request.records()} in payload order and evaluate exactly that, nothing else.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the on-screen values to evaluate
   * @return the check-status result with key-only messages for the controller to resolve
   */
  @Transactional(readOnly = true)
  public Schedule6CheckStatusResponse checkStatus(
      long millId, int year, Schedule6CheckRequest request) {
    List<CheckCandidate> candidates = payloadCandidates(request);

    List<RoadRecordCheckResult> records = new ArrayList<>();
    boolean schedulePasses = true;
    for (CheckCandidate candidate : candidates) {
      List<FieldIssue> issues =
          evaluateRecord(
              candidate.areaType(),
              candidate.tflNumber(),
              candidate.supplyBlock(),
              candidate.cost());
      // The schedule-level pass ignores the area-type flag — the legacy isScheduleValid quirk,
      // ported verbatim (unreachable in practice: FLD-001 blocks area-type-less writes).
      schedulePasses =
          schedulePasses
              && recordPasses(
                  candidate.areaType(),
                  candidate.tflNumber(),
                  candidate.supplyBlock(),
                  candidate.cost());
      boolean met = issues.isEmpty();
      records.add(
          new RoadRecordCheckResult(
              candidate.recordId(),
              candidate.rowCounter(),
              met,
              met ? new MessageInfo(MSG_ROAD_MET, null) : null,
              issues));
    }

    if (schedulePasses) {
      // Zero records (and lone-comment, via the placeholder exclusion) is a vacuous pass — the
      // legacy loop never runs. The pass branch emits ONLY the schedule banner.
      return new Schedule6CheckStatusResponse(
          OUTCOME_MET, List.of(new MessageInfo(MSG_REQUIREMENTS_MET, null)), List.of());
    }
    return new Schedule6CheckStatusResponse(OUTCOME_ISSUES, List.of(), records);
  }

  /**
   * The payload source (Task 6): one candidate per submitted row, in payload order. A payload row
   * addresses no stored record, so it has no {@code recordId} — the 1-based ordinal is served in
   * BOTH {@code recordId} and {@code rowCounter} (see the DTO javadoc + the Task 6 report's
   * recordId decision: the frontend only ever uses {@code recordId} as a React list key, never to
   * correlate a check result back to a stored row, so the ordinal is a legitimate, always-unique
   * stand-in and keeps {@link RoadRecordCheckResult#recordId()} a non-null primitive).
   */
  private List<CheckCandidate> payloadCandidates(Schedule6CheckRequest request) {
    List<CheckCandidate> candidates = new ArrayList<>();
    int rowCounter = 0;
    for (CheckEntry entry : request.records()) {
      rowCounter++;
      candidates.add(
          new CheckCandidate(
              rowCounter,
              rowCounter,
              entry.areaType(),
              entry.tflNumber(),
              entry.supplyBlock(),
              entry.cost()));
    }
    return candidates;
  }

  /**
   * One record's check-status inputs, source-agnostic (stored row or payload row) — the whole point
   * of Task 6 is that {@link #evaluateRecord} and {@link #recordPasses} never need to know which.
   */
  private record CheckCandidate(
      int recordId,
      int rowCounter,
      String areaType,
      String tflNumber,
      String supplyBlock,
      Integer cost) {}

  /**
   * One record's missing-field findings in the verbatim legacy order — type, TFL/Supply Block, cost
   * ({@code Schedule6MB.checkStatus()} :153–173). The TFL-missing branch is ported verbatim though
   * it is unreachable from persisted rows (legacy view-state-only — recorded in Completion Notes);
   * the cost check is null-only, so {@code 0} is MET (D2 precedent) and volume is never checked.
   */
  static List<FieldIssue> evaluateRecord(
      String areaType, String tflNumber, String supplyBlock, Integer cost) {
    List<FieldIssue> issues = new ArrayList<>();
    if (StringUtils.isBlank(areaType)) {
      issues.add(valueRequired(FIELD_AREA_TYPE));
    }
    if (AREA_TYPE_TFL.equals(areaType)) {
      if (StringUtils.isBlank(tflNumber)) {
        issues.add(valueRequired(FIELD_TFL_NUMBER));
      }
    } else if (StringUtils.isBlank(supplyBlock)) {
      issues.add(valueRequired(FIELD_SUPPLY_BLOCK));
    }
    if (cost == null) {
      issues.add(valueRequired(FIELD_COST));
    }
    return issues;
  }

  /**
   * The schedule-gate half of the legacy split ({@code Schedule6CheckStatus.isScheduleValid}
   * :26–55): TFL records need a TFL number, everything else needs a Supply Block, and both need a
   * cost — the area-type flag is emitted by {@link #evaluateRecord} but never gates this.
   */
  static boolean recordPasses(String areaType, String tflNumber, String supplyBlock, Integer cost) {
    if (cost == null) {
      return false;
    }
    if (AREA_TYPE_TFL.equals(areaType)) {
      return StringUtils.isNotBlank(tflNumber);
    }
    return StringUtils.isNotBlank(supplyBlock);
  }

  /** A key-only "Value Required" finding for a field; the controller composes the verbatim line. */
  private static FieldIssue valueRequired(String field) {
    return new FieldIssue(field, new MessageInfo(MSG_VALUE_REQUIRED, null));
  }

  /**
   * $/m&sup3; = cost / volume, computed server-side to match legacy {@code CostVolumeCommentsType
   * .getCostVolume} ({@code CoreUtil.bigDecimalDivision}: divide at scale 10 HALF_UP, then round to
   * scale 2 HALF_UP). Null when cost is null or volume is null/zero.
   */
  private static BigDecimal perUnit(Long cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    return BigDecimal.valueOf(cost)
        .divide(volume, 10, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Normalize a volume so a whole value serializes as an integer ({@code 1000}, not {@code
   * 1000.0000} or {@code 1.0E+3}) while a fractional value keeps its decimals. Null-safe.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
