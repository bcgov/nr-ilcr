package ca.bc.gov.nrs.ilcr.schedule3;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SubPageRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRequest;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * Assembles the Schedule 3 aggregate document from stored detail rows and computes every derived
 * value server-side (AD-5, AD-6), reproducing the legacy {@code Schedule3DO}/{@code CostType} getter
 * cascade exactly. The mill/year context is already validated by {@code MillContextService} before
 * this runs (AD-4) — the category-"3" summary is expected to exist.
 *
 * <p>Read-only for Story 4.1 (GET). The write path (PUT/DELETE/check-status + the BR-09 Crown Timber
 * push into Schedule 1) arrives with Story 4.2.
 */
@Service
@Slf4j
public class Schedule3Service {

  private static final String STATUS_DRAFT = "D";
  private static final String OVERRIDE_DEFAULT = "N";

  // Fixed admin-cost lines (legacy Constant.REPORT_COST_ITEMS). Each line's Harvest item id is the
  // line's identity; popCode is its PO&P item id (null when the line has no PO&P item). Annual Rents
  // (29) and Silviculture Admin (37) are Harvest-only: the legacy Schedule3DAO forces their PO&P to
  // ZERO on load (so crown = harvest). Scaling (33) has no stored PO&P — its PO&P is derived from the
  // timber-volume ratio (getScalingExpense).
  private static final int CODE_ANNUAL_RENTS = 29;
  private static final int CODE_SCALING = 33;
  private static final int CODE_SILV_ADMIN = 37;

  private record LineSpec(int code, Integer popCode, boolean harvestOnly) {
  }

  private static final List<LineSpec> LINES = List.of(
      new LineSpec(27, 125, false),   // Licenses, Fees, Insurance
      new LineSpec(28, 126, false),   // Taxes, Leases, Rentals
      new LineSpec(CODE_ANNUAL_RENTS, null, true),   // Annual Rents (Harvest-only; PO&P forced 0)
      new LineSpec(30, 128, false),   // Wages/Salaries incl. Benefits
      new LineSpec(31, 129, false),   // Vehicle Expense
      new LineSpec(32, 130, false),   // Office Expense
      new LineSpec(CODE_SCALING, null, false),       // Scaling Expense (PO&P derived)
      new LineSpec(34, 132, false),   // Cruising & Layout Expense
      new LineSpec(35, 133, false),   // Residue & Waste Expense
      new LineSpec(36, 134, false),   // Depreciation Expense
      new LineSpec(CODE_SILV_ADMIN, null, true));    // Silviculture Admin Costs (Harvest-only; PO&P 0)

  private static final int CODE_POP_TIMBER = 118;      // PO&P Timber volume
  private static final int CODE_CROWN_TIMBER = 119;    // Crown Timber volume (BR-09 push source, 4.2)
  private static final int CODE_OTHER_ACCEPTABLE = 124; // Other Acceptable Costs sub-page rows
  private static final int CODE_UNACCEPTABLE = 38;      // Included Unacceptable Costs sub-page rows

  // Legacy other-acceptable grouping (Constant.SCH3_OTHERACCEPT_*). The item-124 COMMENTS encode
  // "SCH3_2_<TYPE>_<GRP>" — chars 7..10 are the cost type ("TOT" carries the group's harvest total,
  // otherwise PO&P), and the trailing group key ties a TOT row to its PO&P row.
  private static final String OTHERACCEPT_TYPE_TOTAL = "TOT";
  // Legacy Constant.SCH3_OTHERACCEPT_GROUPKEY_* — the item-124 COMMENTS prefixes; a group's TOT and
  // PO&P rows share the trailing group-number suffix (Story 4.4 sub-page writers).
  private static final String GROUPKEY_TOT = "SCH3_2_TOT_GRP";
  private static final String GROUPKEY_POP = "SCH3_2_POP_GRP";

  /** Fixed lines indexed by Harvest cost-item code, for the write path. */
  private static final Map<Integer, LineSpec> LINE_BY_CODE = new HashMap<>();

  static {
    for (LineSpec spec : LINES) {
      LINE_BY_CODE.put(spec.code(), spec);
    }
  }

  // Check-status field config (Story 4.2). Verbatim legacy labels + order (Schedule3MB.java:166-340).
  // hasPop lines require BOTH Harvest and PO&P and get the BR-03 Harvest≥PO&P check; the three
  // Harvest-only lines (29/33/37) require only Harvest and skip BR-03.
  private record CheckLine(int code, Integer popCode, String name, boolean hasPop) {
  }

  private static final List<CheckLine> CHECK_LINES = List.of(
      new CheckLine(27, 125, "Licence, Fees, Insurance", true),
      new CheckLine(28, 126, "Taxes, Leases, Rentals", true),
      new CheckLine(CODE_ANNUAL_RENTS, null, "Annual Rents", false),
      new CheckLine(30, 128, "Wages/Salaries, incl benefits", true),
      new CheckLine(31, 129, "Vehicle Expense", true),
      new CheckLine(32, 130, "Office Expense", true),
      new CheckLine(CODE_SCALING, null, "Scaling Expense", false),
      new CheckLine(34, 132, "Cruising & Layout Expense", true),
      new CheckLine(35, 133, "Residue & Waste Expense", true),
      new CheckLine(36, 134, "Depreciation Expense", true),
      new CheckLine(CODE_SILV_ADMIN, null, "Silviculture Admin Costs", false));

  private static final String LABEL_POP_TIMBER =
      "Privately Owned & Purchased (PO&P) Timber (Harvest Volume)";
  private static final String LABEL_CROWN_TIMBER = "Crown Timber (Harvest Volume)";
  // Sub-page check-status labels (verbatim legacy Schedule3MB.java:303-330, Story 4.4).
  private static final String LABEL_OA_DESCRIPTION = "Subtotal Other Costs (Description)";
  private static final String LABEL_OA_TOTAL = "Subtotal Other Costs (Harvest Total $)";
  private static final String LABEL_OA_POP = "Subtotal Other Costs (PO&P $)";
  private static final String LABEL_UNACCEPT_DESCRIPTION = "Included Unacceptable Costs (Description)";
  private static final String LABEL_UNACCEPT_TOTAL = "Included Unacceptable Costs (Total $)";

  // Check-status message keys (verbatim legacy bundle) — BR-11 missing value + BR-03 Harvest≥PO&P.
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";
  private static final String MSG_HARVEST_NOT_GT_POP = "harvestNotGreaterThanPopErrorMsg";
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  // BR-09 Crown Timber push outcome (Story 4.2 Save).
  private static final String WARN_CROWN_APPLIED = "crownVolumeChangeSchedule1";       // WRN-001
  private static final String WARN_CROWN_NOT_OPENED = "crownVolumeNotSetSchedule1";     // WRN-002
  private static final String OVERRIDE_YES = "Y";

  private final Schedule3Repository repository;
  private final Schedule1Service schedule1Service;
  private final MessageSource messageSource;

  public Schedule3Service(
      Schedule3Repository repository,
      Schedule1Service schedule1Service,
      MessageSource messageSource) {
    this.repository = repository;
    this.schedule1Service = schedule1Service;
    this.messageSource = messageSource;
  }

  /**
   * Assemble the Schedule 3 document for a mill/year (S03 round-trip on reopen).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the {@code EDIT_SCHEDULE} action (from the controller)
   * @return the aggregate document with all derived values computed server-side
   */
  public Schedule3Response getSchedule3(long millId, int year, boolean callerMayEdit) {
    SummaryRow summary = repository.findSummary(millId, year)
        .orElseThrow(ScheduleNotFoundException::new);
    List<DetailRow> details = repository.findDetails(summary.summaryId());
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);

    // One row per (summary, cost-item) is the invariant; if a duplicate ever exists, first-by-detail-id
    // wins (rows are ordered by detail id) so a derived value can't depend on driver row order. (Legacy
    // set each field unconditionally while iterating, i.e. last-wins in Hibernate's unspecified order —
    // this is deterministic instead; the two agree for the expected unique-row data.)
    Map<Integer, DetailRow> byCode = new HashMap<>();
    List<DetailRow> acceptableRows = new ArrayList<>();
    List<DetailRow> unacceptableRows = new ArrayList<>();
    for (DetailRow row : details) {
      Integer code = row.costItemCode();
      if (code == null) {
        continue;
      }
      if (code == CODE_OTHER_ACCEPTABLE) {
        acceptableRows.add(row);
      } else if (code == CODE_UNACCEPTABLE) {
        unacceptableRows.add(row);
      } else {
        byCode.putIfAbsent(code, row);
      }
    }

    // --- Base entered values -------------------------------------------------------------------
    BigDecimal popTimberVolume = volumeOf(byCode.get(CODE_POP_TIMBER));
    BigDecimal crownTimberVolume = volumeOf(byCode.get(CODE_CROWN_TIMBER));
    // Total Overhead volume = PO&P Timber + Crown Timber (legacy bigDecimalCostAddition, null-tolerant).
    BigDecimal overheadVolume = add(popTimberVolume, crownTimberVolume);

    // --- Fixed lines (harvest/pop/crown) -------------------------------------------------------
    Map<Integer, Integer> harvestByCode = new HashMap<>();
    Map<Integer, Integer> popByCode = new HashMap<>();
    List<CostLine> lineItems = new ArrayList<>();
    for (LineSpec spec : LINES) {
      Integer harvest = costOf(byCode.get(spec.code()));
      Integer pop = resolvePop(spec, harvest, byCode, popTimberVolume, overheadVolume);
      Integer crown = crownCost(harvest, pop);
      harvestByCode.put(spec.code(), harvest);
      popByCode.put(spec.code(), pop);
      if (harvest != null || pop != null || crown != null) {
        lineItems.add(new CostLine(spec.code(), harvest, pop, crown));
      }
    }

    // --- Subtotal Other Costs (from item-124 groups) -------------------------------------------
    ThreeColumnTotal subtotalOtherCosts = subtotalOtherCosts(acceptableRows);
    int otherAcceptableCount = countAcceptableGroups(acceptableRows);

    // --- Subtotal Actual Costs = Σ(11 lines) + Subtotal Other Costs (sumCostType seeds at 0) -----
    long subtotalActualHarvest = subtotalOtherCosts.harvest();
    long subtotalActualPop = subtotalOtherCosts.pop();
    for (LineSpec spec : LINES) {
      subtotalActualHarvest += nz(harvestByCode.get(spec.code()));
      subtotalActualPop += nz(popByCode.get(spec.code()));
    }
    ThreeColumnTotal subtotalActualCosts = total(subtotalActualHarvest, subtotalActualPop);

    // --- Included Unacceptable Costs = Σ(item-38) + Annual Rents harvest; PO&P forced 0 ---------
    long unacceptableHarvest = 0L;
    for (DetailRow row : unacceptableRows) {
      unacceptableHarvest += nz(row.cost());
    }
    Integer annualRentsHarvest = harvestByCode.get(CODE_ANNUAL_RENTS);
    unacceptableHarvest += nz(annualRentsHarvest);
    ThreeColumnTotal includedUnacceptableCosts = new ThreeColumnTotal(
        unacceptableHarvest, 0L, unacceptableHarvest); // pop 0 ⇒ crown = harvest

    // --- Total Costs = Subtotal Actual − Included Unacceptable ----------------------------------
    long totalHarvest = subtotalActualCosts.harvest() - includedUnacceptableCosts.harvest();
    long totalPop = subtotalActualCosts.pop() - includedUnacceptableCosts.pop();
    ThreeColumnTotal totalCosts = total(totalHarvest, totalPop);

    // --- Timber blocks: costs pushed from Total Costs; overhead sums the two ---------------------
    Long popTimberCost = totalCosts.pop();     // legacy getPopTimber().cost = totalCost.popCost
    Long crownTimberCost = totalCosts.crown();  // legacy getCrownTimber().cost = totalCost.crownCost
    Long overheadCost = addLong(popTimberCost, crownTimberCost);
    TimberBlock popTimber = new TimberBlock(
        normalizeVolume(popTimberVolume), popTimberCost, perUnit(popTimberCost, popTimberVolume));
    TimberBlock crownTimber = new TimberBlock(
        normalizeVolume(crownTimberVolume), crownTimberCost, perUnit(crownTimberCost, crownTimberVolume));
    TimberBlock totalOverhead = new TimberBlock(
        normalizeVolume(overheadVolume), overheadCost, perUnit(overheadCost, overheadVolume));

    // --- Included Unacceptable count = item-38 rows + 1 when Annual Rents harvest is present -----
    int unacceptableCount = unacceptableRows.size()
        + (annualRentsHarvest != null && annualRentsHarvest != 0 ? 1 : 0);

    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);
    String override = summary.location() == null ? OVERRIDE_DEFAULT : summary.location();

    return new Schedule3Response(
        millId, year, trackStatus, editable, summary.revisionCount(), override, summary.comments(),
        lineItems, popTimber, crownTimber, totalOverhead,
        subtotalOtherCosts, subtotalActualCosts, includedUnacceptableCosts, totalCosts,
        otherAcceptableCount, unacceptableCount,
        List.of(),  // warnings — empty on GET; the PUT crown-push outcome is added in saveSchedule3
        null);      // success message is set by the controller on a mutation echo (AD-8)
  }

  // ---------------------------------------------------------------------------------------------
  // Write path (Story 4.2) — Draft-gated, optimistic-locked, transactional (AD-6/AD-9/AR11).
  // ---------------------------------------------------------------------------------------------

  /**
   * Persist the entered Schedule 3 fields (S01) and return the recomputed document. Writes only the
   * editable rows (11 fixed-line Harvest/PO&P costs, the two timber volumes, comments, override →
   * {@code LOCATION}); never derived or sub-page rows. Enforces the Draft gate (AD-9) and optimistic
   * lock (AR11). When the Crown Timber volume changed, propagates it into Schedule 1 via the
   * {@code schedule1} domain (BR-09, AD-14) and carries WRN-001/002 on the response.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the entered fields + optimistic-lock token
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (for the echoed {@code editable})
   * @param user the acting user id (audit)
   * @return the recomputed document, warnings carrying the BR-09 outcome
   */
  @Transactional
  public Schedule3Response saveSchedule3(
      long millId, int year, Schedule3Request request, boolean callerMayEdit, String user) {
    SummaryRow summary = requireEditableSummary(millId, year);
    int summaryId = summary.summaryId();
    BigDecimal persistedCrownVolume = persistedVolume(summaryId, CODE_CROWN_TIMBER);
    int expectedRevision = request.revisionCount() == null ? -1 : request.revisionCount();
    try {
      int bumped = repository.bumpRevision(
          summaryId, expectedRevision, request.comments(),
          normalizeOverride(request.overrideHarvestTotalPop()), user);
      if (bumped == 0) {
        throw new StaleRevisionException();
      }
      writeLines(summaryId, request, user);
      repository.upsertVolume(summaryId, CODE_POP_TIMBER, request.popTimberVolume(), user);
      repository.upsertVolume(summaryId, CODE_CROWN_TIMBER, request.crownTimberVolume(), user);
    } catch (StaleRevisionException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      // Never log cost/volume values (AD-11) — action + status + exception type only.
      log.warn("Schedule 3 save failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }

    // BR-09 Crown Timber push — only when the entered volume differs from the persisted one.
    List<MessageInfo> warnings = new ArrayList<>();
    if (crownVolumeChanged(persistedCrownVolume, request.crownTimberVolume())) {
      try {
        boolean applied =
            schedule1Service.applyCrownTimberVolume(millId, year, request.crownTimberVolume(), user);
        warnings.add(warning(applied ? WARN_CROWN_APPLIED : WARN_CROWN_NOT_OPENED));
      } catch (DataAccessException ex) {
        // Surface a push failure as ERR-001 (500), consistent with the save writes (the whole save +
        // push is one transaction, so this also rolls back the Schedule 3 writes).
        log.warn("Schedule 3 crown push failed for mill {} year {} [{}]",
            millId, year, ex.getClass().getSimpleName());
        throw new ScheduleNotSavedException();
      }
    }
    return getSchedule3(millId, year, callerMayEdit).withWarnings(warnings);
  }

  /**
   * Delete the whole Schedule 3 row family (summary + all detail rows) for a mill/year (S08). Enforces
   * the same Draft gate as save.
   */
  @Transactional
  public void deleteSchedule3(long millId, int year) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      repository.deleteSchedule(summaryId);
    } catch (DataAccessException ex) {
      log.warn("Schedule 3 delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Other Acceptable Costs sub-resource (Story 4.4) — sole writer of the item-124 TOT+PO&P groups.
  // ---------------------------------------------------------------------------------------------

  /** The Other Acceptable Costs document (groups + subtotal) for a mill/year (does not gate Draft). */
  public OtherAcceptableDocument getOtherAcceptableDocument(
      long millId, int year, boolean callerMayEdit) {
    SummaryRow summary = repository.findSummary(millId, year).orElseThrow(ScheduleNotFoundException::new);
    boolean editable = callerMayEdit
        && STATUS_DRAFT.equals(repository.findTrackStatus(millId, year).orElse(null));
    return buildOtherAcceptableDocument(summary.summaryId(), editable);
  }

  /** Add one Other Acceptable group (a fresh TOT + PO&P pair). Draft-gated; recomputes the document. */
  @Transactional
  public OtherAcceptableDocument addOtherAcceptable(
      long millId, int year, OtherAcceptableRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      // Lock the summary row first so a concurrent add can't read the same max group number and mint
      // a duplicate SCH3_2_*_GRP{n} key (the second add blocks here, then reads the committed max).
      repository.touchSummary(summaryId, user);
      String suffix = String.valueOf(nextGroupNumber(summaryId));
      repository.insertSubPageRow(
          summaryId, CODE_OTHER_ACCEPTABLE, request.total(), request.description(),
          GROUPKEY_TOT + suffix, user);
      repository.insertSubPageRow(
          summaryId, CODE_OTHER_ACCEPTABLE, request.pop(), request.description(),
          GROUPKEY_POP + suffix, user);
    } catch (DataAccessException ex) {
      log.warn("Other-Acceptable add failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherAcceptableDocument(summaryId, true);
  }

  /** Update one Other Acceptable group (by TOT detail id). 404 when the id is not a TOT row here. */
  @Transactional
  public OtherAcceptableDocument updateOtherAcceptable(
      long millId, int year, int id, OtherAcceptableRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      SubPageRow totRow = findTotRow(summaryId, id);
      String popComments = GROUPKEY_POP + totRow.comments().substring(GROUPKEY_TOT.length());
      repository.updateSubPageRowById(
          id, summaryId, CODE_OTHER_ACCEPTABLE, request.total(), request.description(), user);
      repository.updateSubPageRowByComments(
          summaryId, CODE_OTHER_ACCEPTABLE, request.pop(), request.description(), popComments, user);
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Other-Acceptable update failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherAcceptableDocument(summaryId, true);
  }

  /** Delete one Other Acceptable group (TOT by id + its PO&P peer). 404 when the id is not a TOT row. */
  @Transactional
  public OtherAcceptableDocument deleteOtherAcceptable(long millId, int year, int id) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      SubPageRow totRow = findTotRow(summaryId, id);
      String popComments = GROUPKEY_POP + totRow.comments().substring(GROUPKEY_TOT.length());
      repository.deleteSubPageRowById(id, summaryId, CODE_OTHER_ACCEPTABLE);
      repository.deleteSubPageRowByComments(summaryId, CODE_OTHER_ACCEPTABLE, popComments);
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Other-Acceptable delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildOtherAcceptableDocument(summaryId, true);
  }

  /** The TOT row for a group id under this summary, or {@link OtherCostNotFoundException} (404). */
  private SubPageRow findTotRow(int summaryId, int id) {
    return repository.findSubPageRows(summaryId, CODE_OTHER_ACCEPTABLE).stream()
        // Require the full GROUPKEY_TOT prefix (not just isTotalComments' chars 7-9): guarantees the
        // comment is long enough for the substring(GROUPKEY_TOT.length()) peer-key slice below, so a
        // malformed short "…TOT…" row yields a clean 404 rather than a 500.
        .filter(r -> r.detailId() != null && r.detailId() == id
            && r.comments() != null && r.comments().startsWith(GROUPKEY_TOT))
        .findFirst()
        .orElseThrow(OtherCostNotFoundException::new);
  }

  /** Next item-124 group number = max existing TOT suffix + 1 (1 when none). */
  private int nextGroupNumber(int summaryId) {
    int max = 0;
    for (SubPageRow row : repository.findSubPageRows(summaryId, CODE_OTHER_ACCEPTABLE)) {
      if (!isTotalComments(row.comments()) || row.comments().length() <= GROUPKEY_TOT.length()) {
        continue;
      }
      try {
        max = Math.max(max, Integer.parseInt(row.comments().substring(GROUPKEY_TOT.length())));
      } catch (NumberFormatException ignored) {
        // Non-numeric legacy suffix — skip; the sequence only needs a fresh unused number.
      }
    }
    return max + 1;
  }

  /** Assemble the Other Acceptable document: pair TOT+PO&P rows by group key, derive crown + subtotal. */
  private OtherAcceptableDocument buildOtherAcceptableDocument(int summaryId, boolean editable) {
    List<SubPageRow> rows = repository.findSubPageRows(summaryId, CODE_OTHER_ACCEPTABLE);
    Map<String, SubPageRow[]> groups = new LinkedHashMap<>(); // key -> [tot, pop]
    long harvest = 0L;
    long pop = 0L;
    for (SubPageRow row : rows) {
      String key = groupKey(row.comments());
      if (key == null) {
        continue;
      }
      SubPageRow[] pair = groups.computeIfAbsent(key, k -> new SubPageRow[2]);
      if (isTotalComments(row.comments())) {
        pair[0] = row;
        harvest += nz(row.cost());
      } else {
        pair[1] = row;
        pop += nz(row.cost());
      }
    }
    List<OtherAcceptableRow> rowDtos = new ArrayList<>();
    for (SubPageRow[] pair : groups.values()) {
      SubPageRow tot = pair[0];
      if (tot == null) {
        continue; // a group with no TOT row has no identity to edit/delete
      }
      Integer popCost = pair[1] == null ? null : pair[1].cost();
      rowDtos.add(new OtherAcceptableRow(
          tot.detailId(), tot.itemDescription(), tot.cost(), popCost,
          crownCost(tot.cost(), popCost)));
    }
    rowDtos.sort((a, b) -> Integer.compare(a.id(), b.id()));
    return new OtherAcceptableDocument(editable, rowDtos.size(), total(harvest, pop), rowDtos, null);
  }

  // ---------------------------------------------------------------------------------------------
  // Included Unacceptable Costs sub-resource (Story 4.4) — sole writer of the item-38 rows.
  // ---------------------------------------------------------------------------------------------

  /** The Included Unacceptable Costs document (rows + subtotal + read-only Annual Rents S111). */
  public UnacceptableDocument getUnacceptableDocument(long millId, int year, boolean callerMayEdit) {
    SummaryRow summary = repository.findSummary(millId, year).orElseThrow(ScheduleNotFoundException::new);
    boolean editable = callerMayEdit
        && STATUS_DRAFT.equals(repository.findTrackStatus(millId, year).orElse(null));
    return buildUnacceptableDocument(summary.summaryId(), editable);
  }

  /** Add one Included Unacceptable row (item 38, null comments). Draft-gated. */
  @Transactional
  public UnacceptableDocument addUnacceptable(
      long millId, int year, UnacceptableRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      repository.insertSubPageRow(
          summaryId, CODE_UNACCEPTABLE, request.total(), request.description(), null, user);
    } catch (DataAccessException ex) {
      log.warn("Unacceptable add failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildUnacceptableDocument(summaryId, true);
  }

  /** Update one Included Unacceptable row by detail id. 404 when the id is not an item-38 row here. */
  @Transactional
  public UnacceptableDocument updateUnacceptable(
      long millId, int year, int id, UnacceptableRequest request, String user) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      int updated = repository.updateSubPageRowById(
          id, summaryId, CODE_UNACCEPTABLE, request.total(), request.description(), user);
      if (updated == 0) {
        throw new OtherCostNotFoundException();
      }
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Unacceptable update failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildUnacceptableDocument(summaryId, true);
  }

  /** Delete one Included Unacceptable row by detail id. 404 when the id is not an item-38 row here. */
  @Transactional
  public UnacceptableDocument deleteUnacceptable(long millId, int year, int id) {
    int summaryId = requireEditableSummary(millId, year).summaryId();
    try {
      int deleted = repository.deleteSubPageRowById(id, summaryId, CODE_UNACCEPTABLE);
      if (deleted == 0) {
        throw new OtherCostNotFoundException();
      }
    } catch (OtherCostNotFoundException ex) {
      throw ex;
    } catch (DataAccessException ex) {
      log.warn("Unacceptable delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildUnacceptableDocument(summaryId, true);
  }

  /** Assemble the Included Unacceptable document (item-38 rows + subtotal + Annual Rents S111). */
  private UnacceptableDocument buildUnacceptableDocument(int summaryId, boolean editable) {
    List<SubPageRow> rows = repository.findSubPageRows(summaryId, CODE_UNACCEPTABLE);
    List<UnacceptableRow> rowDtos = new ArrayList<>();
    long subtotal = 0L;
    for (SubPageRow row : rows) {
      rowDtos.add(new UnacceptableRow(row.detailId(), row.itemDescription(), row.cost()));
      subtotal += nz(row.cost());
    }
    Integer annualRents = firstCost(summaryId, CODE_ANNUAL_RENTS);
    return new UnacceptableDocument(editable, rowDtos.size(), subtotal, annualRents, rowDtos, null);
  }

  /** The COST of the first detail row for a cost item under a summary (item-29 Annual Rents Harvest). */
  private Integer firstCost(int summaryId, int costItemCode) {
    return repository.findDetails(summaryId).stream()
        .filter(r -> r.costItemCode() != null && r.costItemCode() == costItemCode)
        .map(DetailRow::cost)
        .findFirst()
        .orElse(null);
  }

  /**
   * BR-11/BR-03 Check Status (S09–S12): validate whether the stored Schedule 3 meets all requirements.
   * Read-only — mutates nothing (AD-5). A field is missing when its stored value is null (0 is
   * present). BR-03 (Harvest ≥ PO&P) applies to the eight both-required lines and the Other-Acceptable
   * subtotal. BR-10: Override = "Y" suppresses BR-03 on ALL lines (legacy
   * {@code Schedule3CheckStatus.isHarvestCostGreaterThanPopCost}). Verbatim labels/messages, legacy
   * field order (AD-8).
   */
  public CheckStatusResponse checkSchedule3Status(long millId, int year) {
    SummaryRow summary = repository.findSummary(millId, year)
        .orElseThrow(ScheduleNotFoundException::new);
    List<DetailRow> details = repository.findDetails(summary.summaryId());
    boolean override = OVERRIDE_YES.equals(summary.location());

    Map<Integer, DetailRow> byCode = new HashMap<>();
    for (DetailRow row : details) {
      if (row.costItemCode() != null) {
        byCode.putIfAbsent(row.costItemCode(), row);
      }
    }

    List<MessageInfo> errors = new ArrayList<>();
    for (CheckLine line : CHECK_LINES) {
      Integer harvest = costOf(byCode.get(line.code()));
      if (harvest == null) {
        errors.add(valueRequired(line.name() + " (Harvest Total $)"));
      }
      if (line.hasPop()) {
        Integer pop = costOf(byCode.get(line.popCode()));
        if (pop == null) {
          errors.add(valueRequired(line.name() + " (PO&P Total $)"));
        }
        if (!override && harvest != null && pop != null && harvest < pop) {
          errors.add(harvestNotGreaterThanPop(line.name() + " (Harvest Total $)"));
        }
      }
    }

    // Sub-page checks (Story 4.4) — legacy order places Other Acceptable + Unacceptable BEFORE the
    // Total Overhead (timber) checks (Schedule3MB.java:303-340).
    appendOtherAcceptableCheckErrors(details, override, errors);
    appendUnacceptableCheckErrors(details, errors);

    if (volumeOf(byCode.get(CODE_POP_TIMBER)) == null) {
      errors.add(valueRequired(LABEL_POP_TIMBER));
    }
    if (volumeOf(byCode.get(CODE_CROWN_TIMBER)) == null) {
      errors.add(valueRequired(LABEL_CROWN_TIMBER));
    }

    boolean requirementsMet = errors.isEmpty();
    MessageInfo message = requirementsMet
        ? new MessageInfo(MSG_REQUIREMENTS_MET, resolveText(MSG_REQUIREMENTS_MET))
        : null;
    return new CheckStatusResponse(requirementsMet, errors, List.of(), message);
  }

  /**
   * Other Acceptable (item 124) check-status (legacy {@code Schedule3MB.java:304-320}): one error per
   * failing kind across ALL groups — missing description, missing total, Harvest&lt;PO&amp;P (suppressed
   * when Override="Y", BR-10/S12), missing PO&amp;P. Groups pair TOT+PO&amp;P rows by group key.
   */
  private void appendOtherAcceptableCheckErrors(
      List<DetailRow> details, boolean override, List<MessageInfo> errors) {
    Map<String, DetailRow[]> groups = new LinkedHashMap<>(); // key -> [tot, pop]
    for (DetailRow row : details) {
      if (row.costItemCode() == null || row.costItemCode() != CODE_OTHER_ACCEPTABLE) {
        continue;
      }
      String key = groupKey(row.comments());
      if (key == null) {
        continue;
      }
      DetailRow[] pair = groups.computeIfAbsent(key, k -> new DetailRow[2]);
      if (isTotalComments(row.comments())) {
        pair[0] = row;
      } else {
        pair[1] = row;
      }
    }
    boolean missingDescription = false;
    boolean missingTotal = false;
    boolean harvestLessThanPop = false;
    boolean missingPop = false;
    for (DetailRow[] pair : groups.values()) {
      DetailRow tot = pair[0];
      DetailRow pop = pair[1];
      Integer totalCost = tot == null ? null : tot.cost();
      Integer popCost = pop == null ? null : pop.cost();
      String description = tot != null ? tot.itemDescription()
          : (pop != null ? pop.itemDescription() : null);
      if (StringUtils.isBlank(description)) {
        missingDescription = true;
      }
      if (totalCost == null) {
        missingTotal = true;
      }
      if (totalCost != null && popCost != null && totalCost < popCost) {
        harvestLessThanPop = true;
      }
      if (popCost == null) {
        missingPop = true;
      }
    }
    if (missingDescription) {
      errors.add(valueRequired(LABEL_OA_DESCRIPTION));
    }
    if (missingTotal) {
      errors.add(valueRequired(LABEL_OA_TOTAL));
    }
    if (!override && harvestLessThanPop) {
      errors.add(harvestNotGreaterThanPop(LABEL_OA_TOTAL));
    }
    if (missingPop) {
      errors.add(valueRequired(LABEL_OA_POP));
    }
  }

  /**
   * Included Unacceptable (item 38) check-status (legacy {@code Schedule3MB.java:323-330}): one error
   * per failing kind across ALL rows — missing description, missing total.
   */
  private void appendUnacceptableCheckErrors(List<DetailRow> details, List<MessageInfo> errors) {
    boolean missingDescription = false;
    boolean missingTotal = false;
    for (DetailRow row : details) {
      if (row.costItemCode() == null || row.costItemCode() != CODE_UNACCEPTABLE) {
        continue;
      }
      if (StringUtils.isBlank(row.itemDescription())) {
        missingDescription = true;
      }
      if (row.cost() == null) {
        missingTotal = true;
      }
    }
    if (missingDescription) {
      errors.add(valueRequired(LABEL_UNACCEPT_DESCRIPTION));
    }
    if (missingTotal) {
      errors.add(valueRequired(LABEL_UNACCEPT_TOTAL));
    }
  }

  /** The Draft-gate guard shared by save and delete (AD-9): track must be Draft; summary must exist. */
  private SummaryRow requireEditableSummary(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
    return repository.findSummary(millId, year).orElseThrow(ScheduleNotFoundException::new);
  }

  /** Upsert each entered fixed line: Harvest cost always; PO&P cost only for the PO&P lines. */
  private void writeLines(int summaryId, Schedule3Request request, String user) {
    if (request.lineItems() == null) {
      return;
    }
    for (Schedule3Request.CostLineInput li : request.lineItems()) {
      if (li.costItemCode() == null) {
        continue;
      }
      LineSpec spec = LINE_BY_CODE.get(li.costItemCode());
      if (spec == null) {
        continue; // ignore unknown / non-fixed codes (server is the sole writer of derived rows)
      }
      repository.upsertFixedDetailCost(summaryId, spec.code(), li.harvest(), user);
      if (spec.popCode() != null) {
        repository.upsertFixedDetailCost(summaryId, spec.popCode(), li.pop(), user);
      }
    }
  }

  /** The persisted VOLUME of a detail item (first-by-id), or null — used for crown-change detection. */
  private BigDecimal persistedVolume(int summaryId, int costItemCode) {
    return repository.findDetails(summaryId).stream()
        .filter(r -> r.costItemCode() != null && r.costItemCode() == costItemCode)
        .map(DetailRow::volume)
        .findFirst()
        .orElse(null);
  }

  /** Legacy "Y" or null; normalize the request value to "Y"/"N" for the {@code LOCATION} column. */
  private static String normalizeOverride(String override) {
    return OVERRIDE_YES.equals(override) ? OVERRIDE_YES : "N";
  }

  /** True when the entered Crown volume differs from the persisted one (null-safe by value). */
  private static boolean crownVolumeChanged(BigDecimal persisted, BigDecimal entered) {
    if (persisted == null) {
      return entered != null;
    }
    return entered == null || persisted.compareTo(entered) != 0;
  }

  private MessageInfo valueRequired(String label) {
    return new MessageInfo(MSG_VALUE_REQUIRED, label + ": " + resolveText(MSG_VALUE_REQUIRED));
  }

  private MessageInfo harvestNotGreaterThanPop(String label) {
    return new MessageInfo(MSG_HARVEST_NOT_GT_POP, label + ": " + resolveText(MSG_HARVEST_NOT_GT_POP));
  }

  private MessageInfo warning(String key) {
    return new MessageInfo(key, resolveText(key));
  }

  /** Resolve a legacy bundle key to verbatim text (AD-8); the key doubles as the default. */
  private String resolveText(String key) {
    return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
  }

  /**
   * Resolve a line's PO&P amount: Harvest-only lines (29/37) force 0 when a harvest is present (legacy
   * {@code Schedule3DAO} sets {@code popCost = ZERO}); Scaling (33) derives it from the timber-volume
   * ratio ({@code getScalingExpense}); all others read their PO&P item's cost.
   */
  private Integer resolvePop(LineSpec spec, Integer harvest, Map<Integer, DetailRow> byCode,
      BigDecimal popTimberVolume, BigDecimal overheadVolume) {
    if (spec.harvestOnly()) {
      return harvest == null ? null : 0;
    }
    if (spec.code() == CODE_SCALING) {
      return scalingPop(harvest, popTimberVolume, overheadVolume);
    }
    return costOf(byCode.get(spec.popCode()));
  }

  /**
   * Legacy {@code Schedule3DO.getScalingExpense}: PO&P = round-to-whole-dollars(
   * (popTimberVolume / totalOverheadVolume) × scalingHarvest ). Null when the harvest or a volume is
   * absent or the overhead volume is zero.
   */
  private static Integer scalingPop(Integer scalingHarvest, BigDecimal popTimberVolume,
      BigDecimal overheadVolume) {
    if (scalingHarvest == null || popTimberVolume == null
        || overheadVolume == null || overheadVolume.signum() == 0) {
      return null;
    }
    BigDecimal ratio = popTimberVolume.divide(overheadVolume, 15, RoundingMode.HALF_UP);
    return ratio.multiply(BigDecimal.valueOf(scalingHarvest)).setScale(0, RoundingMode.HALF_UP)
        .intValue();
  }

  /** Subtotal Other Costs from the item-124 group rows: TOT rows sum to harvest, others to PO&P. */
  private static ThreeColumnTotal subtotalOtherCosts(List<DetailRow> acceptableRows) {
    long harvest = 0L;
    long pop = 0L;
    for (DetailRow row : acceptableRows) {
      if (isTotalRow(row)) {
        harvest += nz(row.cost());
      } else {
        pop += nz(row.cost());
      }
    }
    return total(harvest, pop);
  }

  /** The number of Other Acceptable Cost groups (distinct trailing group keys among item-124 rows). */
  private static int countAcceptableGroups(List<DetailRow> acceptableRows) {
    Set<String> groups = new LinkedHashSet<>();
    for (DetailRow row : acceptableRows) {
      String key = groupKey(row.comments());
      if (key != null) {
        groups.add(key);
      }
    }
    return groups.size();
  }

  private static boolean isTotalRow(DetailRow row) {
    return isTotalComments(row.comments());
  }

  /** True when an item-124 {@code COMMENTS} encodes a TOT row (chars 7..10 == "TOT"). */
  private static boolean isTotalComments(String comments) {
    return comments != null && comments.length() >= 10
        && OTHERACCEPT_TYPE_TOTAL.equals(comments.substring(7, 10));
  }

  private static String groupKey(String comments) {
    if (StringUtils.isBlank(comments)) {
      return null;
    }
    return comments.length() >= 4 ? comments.substring(comments.length() - 4) : comments;
  }

  /**
   * Legacy {@code CostType.getCrownCost} = harvest − PO&P, returning null when EITHER side is absent
   * ({@code bigDecimalNotNullCostSubtraction}). Whole dollars.
   */
  private static Integer crownCost(Integer harvest, Integer pop) {
    if (harvest == null || pop == null) {
      return null;
    }
    return harvest - pop;
  }

  /** A three-column total with derived crown (harvest − pop; both sides always present here). */
  private static ThreeColumnTotal total(long harvest, long pop) {
    return new ThreeColumnTotal(harvest, pop, harvest - pop);
  }

  /** Legacy {@code bigDecimalCostAddition}: null-tolerant (null operand treated as absent). */
  private static BigDecimal add(BigDecimal a, BigDecimal b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a.add(b);
  }

  /** Whole-dollar addition with the same null-tolerance as {@link #add}. */
  private static Long addLong(Long a, Long b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * $/m³ = cost / volume, computed server-side to match legacy {@code CostVolumeType.getCostVolume}
   * ({@code CoreUtil.bigDecimalDivision}: divide at scale 10 HALF_UP, then round to scale 2 HALF_UP).
   * Null when cost is null or volume is null/zero.
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
   * Normalize a volume so a whole value serializes as an integer ({@code 54321}, not
   * {@code 54321.0000} or {@code 5.4321E+4}) while a fractional value keeps its decimals.
   */
  private static BigDecimal normalizeVolume(BigDecimal volume) {
    if (volume == null) {
      return null;
    }
    BigDecimal stripped = volume.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  private static Integer costOf(DetailRow row) {
    return row == null ? null : row.cost();
  }

  private static BigDecimal volumeOf(DetailRow row) {
    return row == null ? null : row.volume();
  }

  private static long nz(Integer value) {
    return value == null ? 0L : value;
  }
}
