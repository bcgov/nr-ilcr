package ca.bc.gov.nrs.ilcr.schedule1;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsSummary;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 1 aggregate document from stored detail rows and computes all derived
 * values server-side (AD-5, AD-6). The mill/year context is already validated by
 * {@code MillContextService} before this runs (AD-4) — the summary is expected to exist.
 */
@Service
@Slf4j
public class Schedule1Service {

  private static final String SCHEDULE_1_CATEGORY = "1";
  private static final String STATUS_DRAFT = "D";

  // Legacy Constant.REPORT_COST_ITEMS ids (BR-02).
  private static final List<Integer> LINE_ITEM_CODES = List.of(12, 13, 14, 15, 16, 17, 18, 143, 144);
  private static final int CODE_SILV_ACTUAL = 1;
  private static final int CODE_SILV_ACCRUED = 2;
  private static final int CODE_SILV_LESS_ADMIN = 139;
  private static final int CODE_SILV_TOTAL = 140;
  private static final int CODE_FOREST_MGMT_ADMIN = 143;
  private static final int CODE_SUBTOTAL_COMPANY_LOGGING = 144;
  private static final int CODE_OTHER = 19;

  // Schedule 3 (category "3") source cost-item codes for the Story 2.3 reads (legacy Constant).
  private static final int CODE_SCH3_CROWN_TIMBER = 119;           // BR-03 pre-fill source (volume)
  private static final int CODE_SCH3_SILV_ADMIN = 37;             // BR-04 Less Silv Admin (PO&P = 0)
  private static final int CODE_SCH3_SUBTOTAL_ACTUAL_HARVEST = 115; // BR-04 Forest Mgmt Admin (harvest)
  private static final int CODE_SCH3_SUBTOTAL_ACTUAL_POP = 135;     // BR-04 Forest Mgmt Admin (PO&P)

  // WRN-001: legacy verbatim bundle key for the crown-timber pre-fill warning (BR-03, S02).
  private static final String WARN_CROWN_PREFILL = "crownVolumeSetForSchedule1";

  // Fixed line-item codes writable with BOTH volume and cost from the PUT request (12–18).
  private static final Set<Integer> WRITABLE_LINE_ITEM_CODES = Set.of(12, 13, 14, 15, 16, 17, 18);

  // Codes whose VOLUME is user-entered but whose COST is pulled/derived, not client-written
  // (D2 reversed per the use cases — BR-04: only their cost comes from Sch 3 / is derived): Forest
  // Mgmt Admin (143), Subtotal Company Logging (144), Less Silv Admin (139), Total Silviculture (140).
  // The BR-03 pre-fill copies the crown volume into the full legacy 13-field set: the 12–18 line
  // items, these four volumes, and silviculture 1 & 2 (never the Other-Costs shared volume).

  private final Schedule1Repository repository;
  private final MessageSource messageSource;

  public Schedule1Service(Schedule1Repository repository, MessageSource messageSource) {
    this.repository = repository;
    this.messageSource = messageSource;
  }

  /**
   * Persist the entered Schedule 1 fields for a mill/year and return the recomputed document (S01).
   * The mill/year context is already validated by {@code MillContextService} in the controller
   * (AD-4). Enforces the server-side Draft gate (AD-9) and optimistic-lock concurrency (AR11), and
   * writes only the writable codes — never the itemized Other-Costs rows (AC2). The whole method is
   * one transaction: a persistence failure rolls back completely (S23) and surfaces as 500/ERR-004.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered fields + optimistic-lock token
   * @param callerMayEdit whether the caller holds EDIT_SCHEDULE (for the echoed {@code editable} flag)
   * @param user the acting user id (audit columns)
   * @return the recomputed aggregate document (incremented {@code revisionCount})
   */
  @Transactional
  public Schedule1Response saveSchedule1(
      long millId, int year, Schedule1Request request, boolean callerMayEdit, String user) {
    int summaryId = requireEditableSummary(millId, year);
    int expectedRevision = request.revisionCount() == null ? -1 : request.revisionCount();
    try {
      int bumped = repository.bumpRevision(summaryId, expectedRevision, request.comments(), user);
      if (bumped == 0) {
        throw new StaleRevisionException();
      }
      writeWritableDetails(summaryId, request, user);
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Never log cost/volume values (AD-11) — action + status + exception type only.
      log.warn("Schedule 1 save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return getSchedule1(millId, year, callerMayEdit);
  }

  /**
   * Delete the whole Schedule 1 (summary + all detail rows) for a mill/year (BR-08, S13). Enforces
   * the same Draft gate as save. Context is already validated in the controller (AD-4).
   *
   * @param millId the mill id
   * @param year the reporting year
   */
  @Transactional
  public void deleteSchedule1(long millId, int year) {
    int summaryId = requireEditableSummary(millId, year);
    try {
      repository.deleteSchedule(summaryId);
    } catch (DataAccessException ex) {
      log.warn("Schedule 1 delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Subtotal Other Costs sub-resource (Story 2.4) — sole writer of the itemized item-19 rows.
  // ---------------------------------------------------------------------------------------------

  /**
   * The Other-Costs document (itemized rows + shared volume + server-computed totals) for a mill/year
   * (S09 read). Does not gate on Draft — a non-Draft schedule is still viewable, just {@code editable
   * = false}.
   */
  public OtherCostsDocument getOtherCostsDocument(long millId, int year, boolean callerMayEdit) {
    int summaryId = repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new)
        .summaryId();
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);
    return buildOtherCostsDocument(summaryId, editable);
  }

  /**
   * Add one itemized Other-Costs row inheriting the shared volume (BR-06, S09). Enforces the Draft
   * gate (AD-9) and returns the recomputed document. One transaction (S23).
   */
  @Transactional
  public OtherCostsDocument addOtherCost(
      long millId, int year, OtherCostRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year);
    try {
      BigDecimal sharedVolume = repository.findSharedOtherCostsVolume(summaryId).orElse(null);
      repository.insertOtherCost(summaryId, request.description(), request.cost(), sharedVolume, user);
    } catch (DataAccessException ex) {
      log.warn("Other-Costs add failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherCostsDocument(summaryId, true);
  }

  /**
   * Update one itemized Other-Costs row's description/cost (S11). Draft-gated; 404 when {@code id} is
   * not an itemized item-19 row under this schedule. Last-write-wins (legacy has no per-row lock).
   */
  @Transactional
  public OtherCostsDocument updateOtherCost(
      long millId, int year, int id, OtherCostRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year);
    try {
      int updated =
          repository.updateOtherCost(id, summaryId, request.description(), request.cost(), user);
      if (updated == 0) {
        throw new OtherCostNotFoundException();
      }
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Other-Costs update failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherCostsDocument(summaryId, true);
  }

  /**
   * Delete one itemized Other-Costs row by id (S12). Draft-gated; 404 when {@code id} is not an
   * itemized item-19 row under this schedule.
   */
  @Transactional
  public OtherCostsDocument deleteOtherCost(long millId, int year, int id) {
    int summaryId = requireEditableSummary(millId, year);
    try {
      int deleted = repository.deleteOtherCost(id, summaryId);
      if (deleted == 0) {
        throw new OtherCostNotFoundException();
      }
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Other-Costs delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherCostsDocument(summaryId, true);
  }

  /** Assemble the Other-Costs document from stored rows; all derived values computed here (AD-6). */
  private OtherCostsDocument buildOtherCostsDocument(int summaryId, boolean editable) {
    BigDecimal sharedVolume = repository.findSharedOtherCostsVolume(summaryId).orElse(null);
    List<OtherCostDetailRow> rows = repository.findOtherCostRows(summaryId);

    // Sum as long to avoid silent int overflow across many/large itemized costs.
    long costSubtotal = rows.stream()
        .map(OtherCostDetailRow::cost)
        .filter(Objects::nonNull)
        .mapToLong(Integer::longValue)
        .sum();

    List<OtherCostRow> dtoRows = rows.stream()
        .map(r -> new OtherCostRow(
            r.id(),
            r.description(),
            r.cost(),
            // Per-row $/m³ uses the shared volume (BR-06), matching legacy otherCostItemCal.
            perUnit(sharedVolume, r.cost() == null ? null : BigDecimal.valueOf(r.cost()))))
        .toList();

    return new OtherCostsDocument(
        normalizeVolume(sharedVolume),
        costSubtotal,
        perUnit(sharedVolume, BigDecimal.valueOf(costSubtotal)),
        rows.size(),
        dtoRows,
        editable,
        null); // success message is attached by the controller on the mutation echo (AD-8)
  }

  /**
   * The Draft-gate guard shared by save and delete: the track must be Draft (else 409) and a
   * Schedule 1 summary must exist. Returns the summary id.
   */
  private int requireEditableSummary(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
    return repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new)
        .summaryId();
  }

  /** Upsert the writable codes; itemized Other-Costs rows untouched (their sole writer is Story 2.4). */
  private void writeWritableDetails(int summaryId, Schedule1Request request, String user) {
    // 12–18: volume + cost.
    writeLineItems(summaryId, request.lineItems(), user);
    // 143 / 144: VOLUME only — their cost is pulled from Sch 3 (143) or derived (144), never client-set.
    // Guarded by != null (like otherCostsVolume) so a request that omits the field leaves the stored
    // volume untouched rather than null-clobbering it.
    if (request.forestMgmtAdminVolume() != null) {
      repository.upsertFixedDetail(
          summaryId, CODE_FOREST_MGMT_ADMIN, request.forestMgmtAdminVolume(), null, user);
    }
    if (request.subtotalCompanyLoggingVolume() != null) {
      repository.upsertFixedDetail(
          summaryId, CODE_SUBTOTAL_COMPANY_LOGGING, request.subtotalCompanyLoggingVolume(), null, user);
    }
    // Silviculture: 1 & 2 are volume + cost; 139 (pulled cost) and 140 (derived cost) are VOLUME only.
    writeSilviculture(summaryId, request.silviculture(), user);
    if (request.otherCostsVolume() != null) {
      // Shared item-19 volume row (null description) only — never the itemized rows (AC2).
      repository.upsertFixedDetail(summaryId, CODE_OTHER, request.otherCostsVolume(), null, user);
    }
  }

  /** Write the writable 12–18 line items (volume + cost); null or non-writable codes are ignored. */
  private void writeLineItems(
      int summaryId, List<Schedule1Request.LineItemInput> lineItems, String user) {
    if (lineItems == null) {
      return;
    }
    for (Schedule1Request.LineItemInput li : lineItems) {
      if (li.costItemCode() != null && WRITABLE_LINE_ITEM_CODES.contains(li.costItemCode())) {
        repository.upsertFixedDetail(summaryId, li.costItemCode(), li.volume(), li.cost(), user);
      }
    }
  }

  /** Write the silviculture codes: 1 & 2 are volume + cost; 139/140 are VOLUME only. */
  private void writeSilviculture(
      int summaryId, Schedule1Request.SilvicultureInput silv, String user) {
    if (silv == null) {
      return;
    }
    if (silv.actualSpent() != null) {
      repository.upsertFixedDetail(
          summaryId, CODE_SILV_ACTUAL, silv.actualSpent().volume(), silv.actualSpent().cost(), user);
    }
    if (silv.accruedLessActual() != null) {
      repository.upsertFixedDetail(
          summaryId, CODE_SILV_ACCRUED,
          silv.accruedLessActual().volume(), silv.accruedLessActual().cost(), user);
    }
    if (silv.lessAdminVolume() != null) {
      repository.upsertFixedDetail(summaryId, CODE_SILV_LESS_ADMIN, silv.lessAdminVolume(), null, user);
    }
    if (silv.totalVolume() != null) {
      repository.upsertFixedDetail(summaryId, CODE_SILV_TOTAL, silv.totalVolume(), null, user);
    }
  }

  /**
   * Assemble the Schedule 1 document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the aggregate document
   */
  public Schedule1Response getSchedule1(long millId, int year, boolean callerMayEdit) {
    SummaryRow summary = repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new);
    List<DetailRow> details = repository.findDetails(summary.summaryId());
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);

    // Schedule 3 source data (BR-03 crown pre-fill + BR-04 admin-cost pulls). The Sch 3 context is not
    // guaranteed to exist (MillContextService validates category "1" only), so this is tolerant of a
    // missing Schedule 3 — an empty map yields a null crown (no pre-fill) and null pulled costs.
    // First row per code wins (rows come back ordered by detail id) so a duplicate/corrupt row can't
    // make the crown volume or pulled costs depend on driver row order (legacy takes the first row).
    Map<Integer, DetailRow> sch3ByCode = new HashMap<>();
    for (DetailRow row : repository.findSchedule3Details(millId, year)) {
      if (row.costItemCode() != null) {
        sch3ByCode.putIfAbsent(row.costItemCode(), row);
      }
    }
    DetailRow crownRow = sch3ByCode.get(CODE_SCH3_CROWN_TIMBER);
    BigDecimal sch3CrownVolume = crownRow == null ? null : crownRow.volume();

    Map<Integer, DetailRow> byCode = new HashMap<>();
    List<DetailRow> otherCostRows = new ArrayList<>();
    for (DetailRow row : details) {
      if (row.costItemCode() != null && row.costItemCode() == CODE_OTHER) {
        otherCostRows.add(row);
      } else if (row.costItemCode() != null) {
        byCode.put(row.costItemCode(), row);
      }
    }

    // BR-03 pre-fill (S02): first entry (every stored volume empty) + a Schedule 3 Crown Timber
    // volume present ⇒ copy that volume into the full legacy 13-field volume set (all line items 12–18,
    // 143, 144 + silviculture 1, 2, 139, 140 — D2 reversed per the use cases; never the Other-Costs
    // shared volume) in the SERVED document only. Nothing is persisted; the user must Save (hence
    // WRN-001 "Please check and save schedule.").
    boolean prefill = sch3CrownVolume != null && allVolumesEmpty(details);

    List<LineItem> lineItems = new ArrayList<>();
    for (Integer code : LINE_ITEM_CODES) {
      DetailRow row = byCode.get(code);
      if (prefill) {
        lineItems.add(prefilledLineItem(code, row, sch3CrownVolume));
      } else if (row != null) {
        lineItems.add(toLineItem(row));
      }
    }

    SilvicultureBlock silviculture = new SilvicultureBlock(
        prefilledSilv(CODE_SILV_ACTUAL, byCode.get(CODE_SILV_ACTUAL), prefill, sch3CrownVolume),
        prefilledSilv(CODE_SILV_ACCRUED, byCode.get(CODE_SILV_ACCRUED), prefill, sch3CrownVolume),
        prefilledSilv(CODE_SILV_LESS_ADMIN, byCode.get(CODE_SILV_LESS_ADMIN), prefill, sch3CrownVolume),
        prefilledSilv(CODE_SILV_TOTAL, byCode.get(CODE_SILV_TOTAL), prefill, sch3CrownVolume));

    // BR-04: the two admin costs are PULLED from Schedule 3 (read-only), never from Schedule 1's own
    // 143/139 rows. Forest Mgmt Admin = crownCost of Sch 3 Subtotal Actual Costs (harvest 115 −
    // PO&P 135); Less Silv Admin = Sch 3 Silviculture Admin (item 37; PO&P forced 0 ⇒ = its cost).
    Integer forestMgmtAdminCost = crownCost(
        sch3ByCode.get(CODE_SCH3_SUBTOTAL_ACTUAL_HARVEST),
        sch3ByCode.get(CODE_SCH3_SUBTOTAL_ACTUAL_POP));
    Integer lessSilvAdminCost = costOf(sch3ByCode.get(CODE_SCH3_SILV_ADMIN));

    OtherCostsSummary otherCosts = toOtherCosts(otherCostRows);

    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<MessageInfo> warnings = prefill ? List.of(warning(WARN_CROWN_PREFILL)) : List.of();

    return new Schedule1Response(
        millId,
        year,
        trackStatus,
        editable,
        summary.crownVolume(),
        normalizeVolume(sch3CrownVolume),
        summary.revisionCount(),
        summary.comments(),
        lineItems,
        silviculture,
        forestMgmtAdminCost,
        lessSilvAdminCost,
        otherCosts,
        warnings,
        null); // success message is set by the controller on the PUT echo (AD-8)
  }

  // ---------------------------------------------------------------------------------------------
  // Check Status — BR-07 readiness validation (Story 2.6). Read-only; no persistence, no transition.
  // ---------------------------------------------------------------------------------------------

  /** A mandatory Schedule 1 field checked by BR-07: its legacy label + which of volume/cost apply. */
  private record CheckField(int code, String label, boolean checkVolume, boolean checkCost) {
  }

  // Legacy Schedule1MB.checkStatus() field order + verbatim labels. 143/144/139/140 are volume-only
  // (their cost is pulled/derived). Message: "{label} - Volume|Cost: Value Required".
  private static final List<CheckField> CHECK_FIELDS = List.of(
      new CheckField(12, "Standing Tree to Loaded Truck", true, true),
      new CheckField(13, "Log Transportation", true, true),
      new CheckField(14, "Road Management", true, true),
      new CheckField(15, "Road Construction Costs", true, true),
      new CheckField(16, "Post Logging Treatment", true, true),
      new CheckField(CODE_FOREST_MGMT_ADMIN, "Forest Management Administration", true, false),
      new CheckField(17, "Stumpage and Royalty", true, true),
      new CheckField(18, "Depletion and Amortization", true, true),
      new CheckField(CODE_SUBTOTAL_COMPANY_LOGGING, "Subtotal Company Logging", true, false),
      new CheckField(CODE_SILV_ACTUAL, "Actual $ Spent", true, true),
      new CheckField(CODE_SILV_LESS_ADMIN, "Less Silviculture Admin Costs", true, false),
      new CheckField(CODE_SILV_ACCRUED, "Accrued less Actual $ Spent", true, true),
      new CheckField(CODE_SILV_TOTAL, "Total Silviculture", true, false));

  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_OTHER_COST_GT_ZERO = "sch1.subtotal.other.costs.costs.grearter.than.zero";
  private static final String MSG_OTHER_VOLUME_GT_ZERO = "sch1.subtotal.other.costs.volume.grearter.than.zero";
  private static final String WARN_OTHER_COST_EMPTY = "warning.schedule1.checkstatus.subtotalother.costEmpty";

  /**
   * BR-07 Check Status (S14–S18): validate whether the stored Schedule 1 meets all requirements. A
   * field is missing when its stored value is null (0 is present). Read-only — mutates nothing. Errors
   * (missing mandatory fields + Other-Costs volume/cost consistency) block; the empty-cost row check is
   * a non-blocking warning. Verbatim messages composed server-side (AD-8), in legacy field order.
   */
  public CheckStatusResponse checkSchedule1Status(long millId, int year) {
    SummaryRow summary = repository.findSummary(millId, year, SCHEDULE_1_CATEGORY)
        .orElseThrow(ScheduleNotFoundException::new);
    List<DetailRow> details = repository.findDetails(summary.summaryId());
    // First row per code wins (rows come back ordered by detail id), matching the GET/save reads so a
    // corrupt duplicate can't make check-status disagree with the served document.
    Map<Integer, DetailRow> byCode = indexFirstByCode(details);

    List<MessageInfo> errors = new ArrayList<>(collectRequiredFieldErrors(byCode));
    List<MessageInfo> warnings = new ArrayList<>();

    // Subtotal Other Costs (N = itemized-row count).
    BigDecimal sharedVolume = repository.findSharedOtherCostsVolume(summary.summaryId()).orElse(null);
    List<OtherCostDetailRow> otherRows = repository.findOtherCostRows(summary.summaryId());
    int count = otherRows.size();
    long subtotalCost = otherRows.stream()
        .map(OtherCostDetailRow::cost).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
    String otherLabel = "Subtotal Other Costs (" + count + ")";

    MessageInfo otherCostError = otherCostsError(sharedVolume, subtotalCost, otherLabel);
    if (otherCostError != null) {
      errors.add(otherCostError);
    }

    // WRN-002 (non-blocking): any itemized row with a description but a null cost.
    if (anyOtherCostEmpty(otherRows)) {
      warnings.add(new MessageInfo(WARN_OTHER_COST_EMPTY,
          resolveText(WARN_OTHER_COST_EMPTY, count)));
    }

    boolean requirementsMet = errors.isEmpty();
    MessageInfo message = requirementsMet
        ? new MessageInfo(MSG_REQUIREMENTS_MET, resolveText(MSG_REQUIREMENTS_MET))
        : null;
    return new CheckStatusResponse(requirementsMet, errors, warnings, message);
  }

  /** First stored detail row per (non-Other) cost-item code; later duplicates are ignored. */
  private static Map<Integer, DetailRow> indexFirstByCode(List<DetailRow> details) {
    Map<Integer, DetailRow> byCode = new HashMap<>();
    for (DetailRow row : details) {
      if (row.costItemCode() != null && row.costItemCode() != CODE_OTHER) {
        byCode.putIfAbsent(row.costItemCode(), row);
      }
    }
    return byCode;
  }

  /** "Value Required" errors for each required volume/cost field that is missing (FLD-007/010). */
  private List<MessageInfo> collectRequiredFieldErrors(Map<Integer, DetailRow> byCode) {
    List<MessageInfo> errors = new ArrayList<>();
    for (CheckField f : CHECK_FIELDS) {
      DetailRow row = byCode.get(f.code());
      if (f.checkVolume() && (row == null || row.volume() == null)) {
        errors.add(valueRequired(f.label() + " - Volume"));
      }
      if (f.checkCost() && (row == null || row.cost() == null)) {
        errors.add(valueRequired(f.label() + " - Cost"));
      }
    }
    return errors;
  }

  /** The single Subtotal-Other-Costs error (volume required / cost-vs-volume mismatch), or null. */
  private MessageInfo otherCostsError(
      BigDecimal sharedVolume, long subtotalCost, String otherLabel) {
    if (sharedVolume == null) {
      return valueRequired(otherLabel + " - Volume");
    }
    if (sharedVolume.signum() > 0 && subtotalCost == 0L) {
      return new MessageInfo(MSG_OTHER_COST_GT_ZERO,
          otherLabel + ": " + resolveText(MSG_OTHER_COST_GT_ZERO));
    }
    if (sharedVolume.signum() == 0 && subtotalCost > 0L) {
      return new MessageInfo(MSG_OTHER_VOLUME_GT_ZERO,
          otherLabel + ": " + resolveText(MSG_OTHER_VOLUME_GT_ZERO));
    }
    return null;
  }

  /** WRN-002: true when any itemized Other-Costs row has a description but a null cost. */
  private static boolean anyOtherCostEmpty(List<OtherCostDetailRow> otherRows) {
    return otherRows.stream()
        .anyMatch(r -> r.cost() == null && StringUtils.isNotBlank(r.description()));
  }

  /** A "{label}: Value Required" error (FLD-007/010), verbatim text resolved from the bundle. */
  private MessageInfo valueRequired(String label) {
    return new MessageInfo(MSG_VALUE_REQUIRED, label + ": " + resolveText(MSG_VALUE_REQUIRED));
  }

  /** Resolve a bundle key (with optional MessageFormat args) to verbatim text (AD-8). */
  private String resolveText(String key, Object... args) {
    return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
  }

  /** True when no stored detail row carries a volume — the legacy "all volume fields empty" test. */
  private static boolean allVolumesEmpty(List<DetailRow> details) {
    return details.stream().allMatch(r -> r.volume() == null);
  }

  /** A line item pre-filled with the crown volume: keeps any stored cost, recomputes {@code perUnit}. */
  private static LineItem prefilledLineItem(int code, DetailRow row, BigDecimal crownVolume) {
    Integer cost = row == null ? null : row.cost();
    return new LineItem(
        code,
        normalizeVolume(crownVolume),
        cost,
        perUnit(crownVolume, cost == null ? null : BigDecimal.valueOf(cost)));
  }

  /** A silviculture entry, pre-filled with the crown volume when pre-fill is active (else mapped). */
  private static LineItem prefilledSilv(int code, DetailRow row, boolean prefill, BigDecimal crown) {
    return prefill ? prefilledLineItem(code, row, crown) : toLineItem(row);
  }

  /**
   * Legacy {@code CostType.getCrownCost} = harvest cost − PO&amp;P cost, returning null when EITHER
   * side is absent ({@code bigDecimalNotNullCostSubtraction}). Costs are whole dollars.
   */
  private static Integer crownCost(DetailRow harvest, DetailRow pop) {
    if (harvest == null || harvest.cost() == null || pop == null || pop.cost() == null) {
      return null;
    }
    return harvest.cost() - pop.cost();
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8) for an advisory warning message. */
  private MessageInfo warning(String key) {
    return new MessageInfo(
        key, messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale()));
  }

  private static LineItem toLineItem(DetailRow row) {
    if (row == null) {
      return null;
    }
    return new LineItem(
        row.costItemCode(),
        normalizeVolume(row.volume()),
        row.cost(),
        perUnit(row.volume(), row.cost() == null ? null : BigDecimal.valueOf(row.cost())));
  }

  private static Integer costOf(DetailRow row) {
    return row == null ? null : row.cost();
  }

  private OtherCostsSummary toOtherCosts(List<DetailRow> otherCostRows) {
    // Always present (AD-5/AD-12): a schedule with no Other Costs still carries count 0 / subtotal 0,
    // so the client can distinguish "zero" from "missing".
    // The shared volume is carried by the item-19 row with a null/empty description (legacy).
    BigDecimal sharedVolume = otherCostRows.stream()
        .filter(r -> StringUtils.isBlank(r.itemDescription()))
        .map(DetailRow::volume)
        .findFirst()
        .orElse(null);

    List<DetailRow> itemized = otherCostRows.stream()
        .filter(r -> StringUtils.isNotBlank(r.itemDescription()))
        .toList();

    // Sum as long to avoid silent int overflow across many/large itemized costs.
    long costSubtotal = itemized.stream()
        .map(DetailRow::cost)
        .filter(c -> c != null)
        .mapToLong(Integer::longValue)
        .sum();

    return new OtherCostsSummary(
        normalizeVolume(sharedVolume),
        costSubtotal,
        perUnit(sharedVolume, BigDecimal.valueOf(costSubtotal)),
        itemized.size());
  }

  /**
   * $/m³ = cost / volume, computed server-side. Null when volume is null/zero or cost is null
   * (legacy divide-by-zero returns null). Kept at scale &ge; 1 so it serializes as a decimal
   * (e.g. {@code 50.0}, not {@code 50}).
   */
  private static BigDecimal perUnit(BigDecimal volume, BigDecimal cost) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    BigDecimal result = cost.divide(volume, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }

  /**
   * Normalize a volume so a whole value serializes as an integer ({@code 8000}, not {@code 8000.0000}
   * or {@code 8E+3}) while a fractional value keeps its decimals.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }
}
