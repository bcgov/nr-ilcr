package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.dto.base.CheckStatusOutcome;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult.CampCheckMessage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryEntry;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageRowRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageSaveRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 5 (Camp and Access Expenses) read document from the stored {@code
 * CAMP_REPORT} camps and their keyed {@code ILCR_COST_REPORT_DETAIL} category amounts, computing
 * every derived value server-side (AD-5, AD-6): the four totals, every $/m&sup3;, the two sub-page
 * row counts, and the item-62/68 cost sums. The mill/year context is validated by {@code
 * MillContextService} in the controller before this runs (AD-4).
 *
 * <p>A valid, ACTIVE mill/year with NO camps is not a 404 — it is the legitimate no-camps state and
 * yields a 200 {@code camps: []} (deviation (a); the 404 is reserved for a missing mill/year
 * context row). That is the norm rather than the exception: the delivery image carries 61 camps and
 * zero camp-linked detail rows (Story 7.1 Task 1 gate (v)), so a real camp today derives entirely
 * from nulls — which is exactly why a null total must stay null and never collapse to {@code 0}.
 *
 * <p><strong>Arithmetic is transcribed from legacy, not invented</strong> ({@code CampReportType} +
 * {@code CoreUtil}); see the per-method notes. Costs are whole dollars widened to {@code long}
 * before any sum, so the legacy per-component {@code setScale(0)} is structurally guaranteed rather
 * than re-performed, and no sum can overflow an {@code int}. Costs and volumes are NEVER logged
 * (AD-11) — only mill/year/camp/item identifiers.
 *
 * <p>Story 7.2 adds the write side and Check Status; {@link #buildDocument} is separated so 7.2 can
 * reuse it with the Draft status its gate just proved, instead of re-querying the track.
 */
@Service
@Slf4j
public class Schedule5Service {

  private static final String STATUS_DRAFT = "D";
  private static final String INDICATOR_YES = "Y";
  private static final String INDICATOR_NO = "N";
  private static final String MSG_SCHEDULE_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_CAMP_MET = "campRequirementsMetMsg";
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";

  // The eight Check Status field names, which are also the CampRequest property names the licensee
  // must fill in. Schedule5Controller maps each to its verbatim legacy label segment; these two
  // places are the ONLY ones the names live in, so a mismatch is a programming error and the
  // controller throws rather than rendering "null" into a line.
  static final String FIELD_CAMP_NAME = "campName";
  static final String FIELD_ROAD_DISTANCE = "roadDistanceToOperatingArea";
  static final String FIELD_SIZE_OF_CAMP = "sizeOfCamp";
  static final String FIELD_ASSOCIATED_CAMP_VOLUME = "associatedCampVolume";
  static final String FIELD_OTHER_CAMP_DESCRIPTION = "otherCampExpenseDescription";
  static final String FIELD_OTHER_CAMP_COST = "otherCampExpenseCost";
  static final String FIELD_OTHER_ACCESS_DESCRIPTION = "otherAccessExpenseDescription";
  static final String FIELD_OTHER_ACCESS_COST = "otherAccessExpenseCost";

  // The two cost ranges CategoryEntry cannot express declaratively (see
  // CampCostOutOfRangeException). Both come straight from ILCRCostValidator +
  // Constants.ScheduleValues:102-109.
  private static final int COST_STANDARD_LIMIT = 9_999_999;
  private static final String COST_KEY_STANDARD = "costSize7ValidatorErrorMsg";
  private static final String COST_KEY_RECOVERIES = "costValidatorSchedule9ErrorMsg";

  // The Other Access sub-page's wider bound: its cost inputs carry NO costSize attribute
  // (schedule5AccessExpenses.xhtml:36-38, :71-76), so legacy validates them at ILCRCostValidator's
  // default. The Camp sub-page reuses COST_STANDARD_LIMIT above — every one of ITS cost inputs
  // does carry costSize="7" (:45, :79), which the committed AC and the UC documents both miss
  // (deviation (A)).
  private static final int COST_WIDE_LIMIT = 99_999_999;
  private static final String COST_KEY_WIDE = "costValidatorErrorMsg";

  // Cost item ids — legacy Constant.REPORT_COST_ITEMS (:336-342), all delivery-registered under
  // ILCR_CATEGORY_ID='5' (Task 1 gate (iv)). Item 57 ("Food") is registered but has NO legacy
  // dispatch branch and no rows anywhere, so it is deliberately absent here and falls through to
  // the unknown-item drop below, exactly as legacy does.
  private static final int ITEM_CATERING_AND_FOOD = 56;
  private static final int ITEM_WAGES_AND_BENEFITS = 58;
  private static final int ITEM_DEPRECIATION_LEASE = 59;
  private static final int ITEM_GENERAL_CAMP_EXPENSES = 60;
  private static final int ITEM_RECOVERIES = 61;
  private static final int ITEM_OTHER_CAMP_EXPENSE_ROW = 62;
  private static final int ITEM_CREW_TRANSPORTATION = 63;
  private static final int ITEM_EQUIP_LAND = 64;
  private static final int ITEM_EQUIP_RAIL = 65;
  private static final int ITEM_EQUIP_AIR = 66;
  private static final int ITEM_EQUIP_WATER = 67;
  private static final int ITEM_OTHER_ACCESS_EXPENSE_ROW = 68;
  private static final int ITEM_OTHER_CAMP_EXPENSES_VOLUME = 141;
  private static final int ITEM_OTHER_ACCESS_EXPENSES_VOLUME = 142;

  /**
   * The single-row items: at most one row per camp, so a second is a duplicate (deviation (f)).
   * Package-private so the tests can tie BOTH consumers — the read routing (7.1's review patch) and
   * {@link #writeCategoryRows}'s twelve-item write map — to this one set.
   */
  static final Set<Integer> SINGLE_ROW_ITEMS =
      Set.of(
          ITEM_CATERING_AND_FOOD,
          ITEM_WAGES_AND_BENEFITS,
          ITEM_DEPRECIATION_LEASE,
          ITEM_GENERAL_CAMP_EXPENSES,
          ITEM_RECOVERIES,
          ITEM_CREW_TRANSPORTATION,
          ITEM_EQUIP_LAND,
          ITEM_EQUIP_RAIL,
          ITEM_EQUIP_AIR,
          ITEM_EQUIP_WATER,
          ITEM_OTHER_CAMP_EXPENSES_VOLUME,
          ITEM_OTHER_ACCESS_EXPENSES_VOLUME);

  private final Schedule5Repository repository;

  /** Wires the Schedule 5 repository. */
  public Schedule5Service(Schedule5Repository repository) {
    this.repository = repository;
  }

  /**
   * The Schedule 5 document for a validated mill/year.
   *
   * <p>A valid mill/year holding no camps is a 200 with an EMPTY list, never a 404 — the 404
   * belongs solely to a missing {@code ILCR_MILL_REPORT_STATUS} row and is raised upstream by
   * {@code MillContextService.validateMillYearActive}. Legacy cannot reach its own not-found branch
   * here at all ({@code Schedule5DAO.getCampReports} returns {@code Query.list()}, an empty list
   * and never null), and the Ministry confirmed the behaviour on PR #370, 2026-08-27 (ruling 4 of
   * {@code docs/decisions/camps-and-access-expenses.md}). The page renders a "no camps" empty state
   * over this empty list; that is presentation, and it does NOT change the contract below.
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (AD-9: combined with the
   *     Draft track status to decide {@code editable}; the server is the sole authority)
   * @return the document — {@code camps: []} when the mill/year stores none
   */
  @Transactional(readOnly = true)
  public Schedule5Response getSchedule5(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit);
  }

  /**
   * Assemble the served document for a KNOWN track status. Story 7.2 reuses this with the {@code D}
   * its Draft gate just proved (same transaction) rather than re-running the track-status query.
   */
  private Schedule5Response buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit) {
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // Camps FIRST, then their details. The two reads are separate statements and Oracle READ
    // COMMITTED gives each its own snapshot, so a camp committed between them is visible to one and
    // not the other. In this order the newcomer is simply not in the camp list yet — a consistent
    // older view. Reversed, it would appear in the list with no details found and be served with
    // every category empty and every total absent, indistinguishable from a genuine zero-detail
    // camp: wrong money, silently. 7.2's write path makes that reachable in normal use.
    List<CampRow> campRows = repository.findCamps(millId, year);
    Map<Integer, CampDetails> detailsByCamp = groupDetails(millId, year);
    List<Camp> camps =
        campRows.stream()
            .map(
                row ->
                    toCamp(
                        millId,
                        year,
                        row,
                        detailsByCamp.getOrDefault(row.campId(), CampDetails.empty())))
            .toList();

    return new Schedule5Response(millId, year, trackStatus, editable, camps, null);
  }

  /**
   * The per-camp detail rows, split into the single-row fixed grid and the two sub-page row lists.
   *
   * <p>Duplicate rows for a single-row item resolve FIRST-BY-DETAIL-ID-WINS with a warning
   * (deviation (f)). Legacy simply overwrote the same {@code CostVolumeType} ({@code
   * Schedule5DAO.java:211-234}), so the survivor was whichever row an identity-hashed {@code
   * HashSet} yielded last — not stable between JVM runs. The repository's detail-id ordering plus
   * {@code putIfAbsent} replaces that chance with determinism, so no derived total depends on row
   * order.
   *
   * <p>An unregistered item id is dropped with a warning, which is legacy-verbatim: the dispatch
   * chain's else-branch logs {@code Cost Item ID Not Found} and discards the row ({@code
   * Schedule5DAO.java:283-285}).
   */
  private Map<Integer, CampDetails> groupDetails(long millId, int year) {
    Map<Integer, CampDetails> byCamp = new HashMap<>();

    for (DetailRow row : repository.findCostDetails(millId, year)) {
      CampDetails camp = byCamp.computeIfAbsent(row.campId(), id -> CampDetails.empty());
      Integer itemId = row.costItemId();

      if (itemId == null) {
        // Delivery declares the column NOT NULL (Task 1 gate (iii)) so this is unreachable there,
        // but the V1 snapshot does not, and an unrecognized id must degrade rather than crash.
        log.warn(
            "Schedule 5 mill {} year {} camp {} has a NULL cost item id (detail id {}); row"
                + " dropped",
            millId,
            year,
            row.campId(),
            row.detailId());
      } else if (itemId == ITEM_OTHER_CAMP_EXPENSE_ROW) {
        camp.otherCampRows().add(row);
      } else if (itemId == ITEM_OTHER_ACCESS_EXPENSE_ROW) {
        camp.otherAccessRows().add(row);
      } else if (SINGLE_ROW_ITEMS.contains(itemId)) {
        DetailRow kept = camp.fixed().putIfAbsent(itemId, row);
        if (kept != null) {
          // DEBUG, not WARN — deviation (O). These two lines fire once per offending ROW on EVERY
          // read, and what they report is a property of stored data, not an event: a camp either
          // has a duplicate/unknown row or it does not, and logging it again on the next GET tells
          // an operator nothing new. Story 7.1 could log them at WARN harmlessly because no
          // camp-linked detail row existed anywhere in delivery (gate (vii)); 7.2's writes create
          // the first ones, which makes both branches reachable and would turn a single malformed
          // camp into permanent per-request WARN noise (deferred-work.md:246). Demoted rather than
          // deduped: dedupe needs cross-request state, and the diagnostic value is in the detail
          // ids, which a per-row line preserves.
          log.debug(
              "Schedule 5 mill {} year {} camp {} has more than one row for cost item {}; keeping"
                  + " detail id {} and ignoring detail id {}",
              millId,
              year,
              row.campId(),
              itemId,
              kept.detailId(),
              row.detailId());
        }
      } else {
        log.debug(
            "Schedule 5 mill {} year {} camp {} references unrecognized cost item {} (detail id"
                + " {}); row dropped, matching legacy Schedule5DAO",
            millId,
            year,
            row.campId(),
            itemId,
            row.detailId());
      }
    }
    return byCamp;
  }

  /**
   * One camp: its descriptors served exactly as stored, its twelve stored category amounts, and the
   * four derived totals.
   *
   * <p><strong>Sub-Total is computed FIRST and unconditionally</strong> — the §T1 trap. Legacy's
   * {@code getCampTotal()} ({@code CampReportType.java:357-361}) reads the FIELD {@code
   * campSubTotal}, which only a prior {@code getCampSubTotal()} call populates (:343). Every legacy
   * consumer happens to call Sub-Total first, masking it; a port that derives only {@code
   * campTotal} would get {@code null - recoveries} and silently collapse {@code campAndAccessTotal}
   * to the Access total alone.
   */
  private Camp toCamp(long millId, int year, CampRow row, CampDetails details) {
    BigDecimal campVolume = row.associatedCampVolume();

    CategoryAmount cateringAndFood = amount(details, ITEM_CATERING_AND_FOOD);
    CategoryAmount wagesAndBenefits = amount(details, ITEM_WAGES_AND_BENEFITS);
    CategoryAmount depreciationLease = amount(details, ITEM_DEPRECIATION_LEASE);
    CategoryAmount generalCampExpenses = amount(details, ITEM_GENERAL_CAMP_EXPENSES);

    // Other Camp Expenses: volume is the STORED item-141 amount (never a sum of the rows); cost is
    // the sum of the item-62 row costs; $/m3 is per-term-rounded (see costPerVolumePerTerm).
    BigDecimal otherCampVolume = volumeOf(details, ITEM_OTHER_CAMP_EXPENSES_VOLUME);
    Long otherCampCost = otherCampExpensesCost(details.otherCampRows(), otherCampVolume);
    CategoryAmount otherCampExpenses =
        new CategoryAmount(
            otherCampVolume,
            otherCampCost,
            costPerVolumePerTerm(details.otherCampRows(), otherCampVolume));

    // Recoveries is the volume-less category: legacy sets cost only (Schedule5DAO.java:242-244).
    CategoryAmount recoveries = new CategoryAmount(null, costOf(details, ITEM_RECOVERIES), null);

    // (1) Sub-Total over EXACTLY five costs — Recoveries excluded (CampReportType.java:335-347).
    Long campSubTotalCost =
        sumCosts(
            cateringAndFood.cost(),
            wagesAndBenefits.cost(),
            depreciationLease.cost(),
            generalCampExpenses.cost(),
            otherCampCost);
    CategoryAmount campSubTotal = derived(campSubTotalCost, campVolume);

    // (2) Camp Total = Sub-Total - Recoveries (BR-04/S09). Recoveries is stored POSITIVE and
    // subtracted; a negative Recoveries therefore INCREASES the total and is never clamped (the
    // 0-floor is client-side only, schedule5ExistingCamp.xhtml:252).
    CategoryAmount campTotal =
        derived(subtractCost(campSubTotalCost, recoveries.cost()), campVolume);

    CategoryAmount crewTransportation = amount(details, ITEM_CREW_TRANSPORTATION);
    CategoryAmount equipAndSuppliesLand = amount(details, ITEM_EQUIP_LAND);
    CategoryAmount equipAndSuppliesRail = amount(details, ITEM_EQUIP_RAIL);
    CategoryAmount equipAndSuppliesAir = amount(details, ITEM_EQUIP_AIR);
    CategoryAmount equipAndSuppliesWater = amount(details, ITEM_EQUIP_WATER);

    BigDecimal otherAccessVolume = volumeOf(details, ITEM_OTHER_ACCESS_EXPENSES_VOLUME);
    Long otherAccessCost = otherAccessExpensesCost(details.otherAccessRows());
    CategoryAmount otherAccessExpenses =
        new CategoryAmount(
            otherAccessVolume,
            otherAccessCost,
            costPerVolumePerTerm(details.otherAccessRows(), otherAccessVolume));

    // (3) Access Expense Total over EXACTLY six costs (CampReportType.java:413-425). It sums the
    // CORRECT item-68 total; legacy's getOtherAccessExpenses() cross-wiring bug (:404-407, which
    // assigns the CAMP total) is deliberately NOT ported — deviation (d). The bug is latent in
    // legacy precisely because this total, and every display path, reads the item-68 sum instead.
    Long accessExpenseTotalCost =
        sumCosts(
            crewTransportation.cost(),
            equipAndSuppliesLand.cost(),
            equipAndSuppliesRail.cost(),
            equipAndSuppliesAir.cost(),
            equipAndSuppliesWater.cost(),
            otherAccessCost);
    CategoryAmount accessExpenseTotal = derived(accessExpenseTotalCost, campVolume);

    // (4) Camp and Access Total — null-tolerant addition, null only when BOTH sides are null
    // (CampReportType.java:427-431 via CoreUtil.bigDecimalCostAddition).
    CategoryAmount campAndAccessTotal =
        derived(addCost(campTotal.cost(), accessExpenseTotalCost), campVolume);

    return new Camp(
        row.campId(),
        row.revisionCount(),
        row.campName(),
        row.distanceToOperatingArea(),
        row.sizeOfCamp(),
        campVolume,
        isolatedCamp(millId, year, row),
        row.comments(),
        cateringAndFood,
        wagesAndBenefits,
        depreciationLease,
        generalCampExpenses,
        otherCampExpenses,
        campSubTotal,
        recoveries,
        campTotal,
        crewTransportation,
        equipAndSuppliesLand,
        equipAndSuppliesRail,
        equipAndSuppliesAir,
        equipAndSuppliesWater,
        otherAccessExpenses,
        accessExpenseTotal,
        campAndAccessTotal,
        // Counts are the raw row counts — NO filtering. Rows with a null description and a null
        // cost still count (CampReportType.java:474-482; DescriptionCostVolumeType.
        // countTowardsTotal() exists for that case and is never called on the read path).
        details.otherCampRows().size(),
        details.otherAccessRows().size());
  }

  /**
   * The {@code Y}/{@code N} indicator as a Boolean; anything that is not {@code Y} is false,
   * exactly as legacy's {@code .equals(POSITIVE_IND) ? true : false} decides ({@code
   * Schedule5DAO.java:87}).
   *
   * <p>A NULL indicator serves null rather than failing the request — deviation (e). Legacy calls
   * {@code .equals()} unguarded and {@code Schedule5MB.init()} catches only {@code ILCSException}
   * (:73-77), so a NULL there NPEs the whole page. The delivery column is NOT NULL DEFAULT {@code
   * 'N'} (Task 1 gate (i)) and no stored row is null (gate (v)), so this branch is defensive
   * hardening against future data, not a live case — hence the warning if it ever fires.
   */
  private Boolean isolatedCamp(long millId, int year, CampRow row) {
    if (row.isolatedCampInd() == null) {
      log.warn(
          "Schedule 5 mill {} year {} camp {} has a NULL ISOLATED_CAMP_IND despite the column being"
              + " NOT NULL; serving null rather than failing the request",
          millId,
          year,
          row.campId());
      return null;
    }
    return INDICATOR_YES.equals(row.isolatedCampInd());
  }

  /** A stored category amount: volume and cost as saved, $/m&sup3; derived from the pair. */
  private CategoryAmount amount(CampDetails details, int itemId) {
    DetailRow row = details.fixed().get(itemId);
    if (row == null) {
      // The category still exists in the response as an empty object — legacy pre-initializes every
      // CostVolumeType field, so an absent row reads as null cost/volume, not as a missing
      // category.
      return new CategoryAmount(null, null, null);
    }
    Long cost = row.cost() == null ? null : row.cost().longValue();
    return new CategoryAmount(row.volume(), cost, costPerVolume(cost, row.volume()));
  }

  /** A derived total: the camp's associated volume, the computed cost, and their $/m&sup3;. */
  private CategoryAmount derived(Long cost, BigDecimal campVolume) {
    return new CategoryAmount(campVolume, cost, costPerVolume(cost, campVolume));
  }

  private Long costOf(CampDetails details, int itemId) {
    DetailRow row = details.fixed().get(itemId);
    return row == null || row.cost() == null ? null : row.cost().longValue();
  }

  private BigDecimal volumeOf(CampDetails details, int itemId) {
    DetailRow row = details.fixed().get(itemId);
    return row == null ? null : row.volume();
  }

  /**
   * {@code CoreUtil.sumBigDecimalCosts} (:302-316): sum the non-null costs, returning null — never
   * {@code 0} — when every component is null. Legacy rounds each component to scale 0 first; whole
   * dollars in a {@code long} make that a structural no-op rather than a step to re-perform.
   */
  private Long sumCosts(Long... costs) {
    long total = 0L;
    boolean any = false;
    for (Long cost : costs) {
      if (cost != null) {
        total += cost;
        any = true;
      }
    }
    return any ? total : null;
  }

  /**
   * {@code CoreUtil.bigDecimalCostSubtraction} (:391-401): a null subtrahend yields the total
   * unchanged, but a null TOTAL yields null regardless of the subtrahend.
   */
  private Long subtractCost(Long total, Long subtract) {
    if (total == null) {
      return null;
    }
    return subtract == null ? total : total - subtract;
  }

  /**
   * {@code CoreUtil.bigDecimalCostAddition} (:334-345): null-tolerant addition, null only when both
   * sides are null.
   */
  private Long addCost(Long left, Long right) {
    if (left == null) {
      return right;
    }
    return right == null ? left : left + right;
  }

  /**
   * {@code CoreUtil.bigDecimalDivision} (:413-424): null when either side is null OR the
   * denominator is zero (no divide-by-zero), else divide at scale 10 HALF_UP then round to scale 2.
   */
  private BigDecimal costPerVolume(Long cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return BigDecimal.valueOf(cost)
        .divide(volume, 10, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * The Other Camp Expenses cost — {@code CoreUtil.sumDescriptionCostVolumeType} (:610-632).
   *
   * <p><strong>Deliberately asymmetric with the access side, and that is legacy-faithful.</strong>
   * This helper sets its "something was added" flag when a row has a non-null cost OR a non-null
   * VOLUME, and {@code getOtherCampExpensesList()} ({@code CampReportType.java:433-438}) stamps
   * every row's volume with the camp-level item-141 volume before the sum runs. So a camp with at
   * least one item-62 row, every cost null, but a non-null item-141 volume yields {@code 0} here —
   * not null — and that zero then makes Camp Sub-Total {@code 0} instead of null. The access-side
   * helper checks cost only and correctly yields null in the mirror-image case. Ported as observed;
   * unreachable in delivery data today (no camp-linked detail rows exist at all) but pinned by unit
   * tests on both sides so a future "tidy-up" cannot silently change served figures.
   */
  private Long otherCampExpensesCost(List<DetailRow> rows, BigDecimal stampedVolume) {
    long total = 0L;
    boolean any = false;
    for (DetailRow row : rows) {
      if (row.cost() != null) {
        total += row.cost();
        any = true;
      } else if (stampedVolume != null) {
        // The row's own volume was overwritten with the camp-level item-141 volume, so a non-null
        // camp volume alone is enough to make legacy treat the list as contributing.
        any = true;
      }
    }
    return any ? total : null;
  }

  /**
   * The Other Access Expenses cost — {@code CoreUtil.sumDescriptionCostVolumeTypeCostOnly}
   * (:590-608): cost-only, so an all-null-cost list yields null whatever the stored item-142 volume
   * is. See {@link #otherCampExpensesCost} for why the two sides differ.
   */
  private Long otherAccessExpensesCost(List<DetailRow> rows) {
    long total = 0L;
    boolean any = false;
    for (DetailRow row : rows) {
      if (row.cost() != null) {
        total += row.cost();
        any = true;
      }
    }
    return any ? total : null;
  }

  /**
   * The sub-page $/m&sup3; — {@code CampReportType.calculateCostVolume} (:541-557). This is NOT
   * {@code totalCost / volume}: legacy divides EACH row by its volume, rounds each quotient to
   * scale 2, sums those, then rounds again. Because every row's volume was overwritten with the
   * same camp-level volume, the result equals {@code sum / volume} only up to per-term rounding, so
   * the per-term rounding is reproduced rather than short-cut.
   *
   * <p>Rows with a null cost are skipped, and a zero volume makes every quotient null, which yields
   * null overall rather than {@code 0.00}.
   *
   * <p>⚠ {@code Schedule5CampExtract.java:93-96} computes the CSV's figure differently (a true
   * ratio-of-sums at scale 15). The SCREEN formula is the one this contract pins; the disagreement
   * is a recorded Open Question for the Ministry, not something to resolve here.
   */
  private BigDecimal costPerVolumePerTerm(List<DetailRow> rows, BigDecimal stampedVolume) {
    BigDecimal total = BigDecimal.ZERO;
    boolean any = false;
    for (DetailRow row : rows) {
      if (row.cost() == null) {
        continue;
      }
      BigDecimal term = costPerVolume(row.cost().longValue(), stampedVolume);
      if (term != null) {
        total = total.add(term);
        any = true;
      }
    }
    return any ? total.setScale(2, RoundingMode.HALF_UP) : null;
  }

  // ===============================================================================================
  // Writes (Story 7.2). Each is ONE transaction whose FIRST statement is the Draft gate, and each
  // ends by returning the recomputed document built from the "D" that gate just proved rather than
  // re-querying the track (the Schedule 6/11 idiom). The success message is attached by the
  // controller via Schedule5Response.withMessage (AD-8), so the service stays message-free. Legacy
  // had NO concurrency control, NO server-side edit gate, and swallowed every failure:
  // saveCampReport sets REVISION_COUNT = 0 and never increments it (Schedule5DAO.java:363, 649),
  // save()/deleteExistingCamp() are protected only by the buttons' disabled= attribute, and the DAO
  // returns -1/false on any exception (:410-427, :557-569). The optimistic lock (deviation (K)),
  // the Draft gate, and ScheduleNotSavedException (deviation (P)) are all ADDED here per the house
  // pattern — there is no legacy code to port for them. Costs and volumes are NEVER logged (AD-11)
  // — only mill/year/camp/item identifiers.
  // ===============================================================================================

  /**
   * Create one camp and return the recomputed document (S01). Persists one {@code CAMP_REPORT} row
   * plus exactly twelve keyed {@code ILCR_COST_REPORT_DETAIL} rows (§ ITEM WRITE MAP).
   *
   * <p>A renamed copy (BR-10) arrives here as an ordinary create — legacy's {@code copyCamp()}
   * makes NO database call at all ({@code Schedule5MB.java:270-275}: it clones in memory, blanks
   * the name and PK, and warns), so there is no server copy endpoint to build (deviation (B)). The
   * copied camp also starts with zero sub-page rows, because the copy constructor skips them
   * ({@code CampReportType.java:150-153}) — which falls out naturally from this path writing only
   * the twelve fixed rows.
   *
   * @param millId the mill id (context already validated by the controller, AD-4)
   * @param year the reporting year
   * @param request the entered camp fields
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (for the echoed {@code
   *     editable})
   * @param user the acting user id (audit columns)
   * @return the recomputed document, the new camp included
   */
  @Transactional
  public Schedule5Response addCamp(
      long millId, int year, CampRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    String campName = trimmedCampName(request);
    validateCostRanges(request);
    // BR-02 as a PRE-CHECK, not a caught constraint violation: nothing in delivery enforces
    // camp-name uniqueness (Task 1 gates (i)/(vi) — CAMP_REPORT has only its PK, the category FK
    // and eleven NOT NULL checks), so a duplicate would simply persist if this were left to the
    // database. The pre-check is therefore check-then-act — but NOT a race: requireDraft above
    // holds
    // a FOR UPDATE lock on this mill/year's report-status row for the rest of this transaction, so
    // a
    // second create for the same mill/year cannot reach this count until the first has committed
    // and
    // become visible to it. That lock is the substitute for the unique index this project cannot
    // add
    // (no DDL on THE); see findTrackStatusForUpdate. Ordering matters: the count MUST stay below
    // the
    // gate.
    if (repository.countCampsNamed(millId, year, campName) > 0) {
      throw new CampNameConflictException();
    }
    try {
      int campId = repository.nextCampReportId();
      repository.insertCamp(
          campId,
          millId,
          year,
          campName,
          request.roadDistanceToOperatingArea(),
          request.sizeOfCamp(),
          request.associatedCampVolume(),
          indicator(request.isolatedCamp()),
          request.comments(),
          user);
      // The camp MUST be inserted before its details: the delivery trigger ILCR_CRDA_B_I_U resolves
      // the parent's mill/year/category with its own SELECT against CAMP_REPORT for
      // :NEW.camp_report_id (Task 1 gate (iv-bis)), so a detail row written first would find no
      // parent.
      writeCategoryRows(campId, request, user);
    } catch (DataAccessException ex) {
      // Class name plus most-specific cause, the Schedule 2 idiom (Schedule2Service.java:143-144):
      // an ORA code and its message carry no cost or volume values (AD-11), and without them a
      // production save failure is undiagnosable.
      log.warn(
          "Schedule 5 add failed for mill {} year {} [{}]: {}",
          millId,
          year,
          ex.getClass().getSimpleName(),
          NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Edit one camp in place and return the recomputed document (S02). Optimistic-locked on the
   * camp's own {@code REVISION_COUNT} (AR11 per-camp keying, 7.1 deviation (b)): a stale token →
   * 409, an unknown or foreign id → 404.
   *
   * <p>Detail rows are UPSERTED per item id, never deleted and reinserted (deviation (N)), and a
   * cleared value writes {@code NULL} into the surviving row. Because delivery holds no camp-linked
   * detail rows at all, the first edit of a real camp takes the upsert's INSERT branch twelve
   * times.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param campId the camp to edit
   * @param request the entered fields plus the required {@code revisionCount} token
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the acting user id (audit columns)
   * @return the recomputed document
   */
  @Transactional
  public Schedule5Response updateCamp(
      long millId, int year, int campId, CampRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    // Defence in depth for the AR11 token: the API's @Validated OnUpdate group already rejects a
    // null revisionCount as a clean 400, but this method unboxes it, so a direct caller that
    // bypassed the group would otherwise NPE into a 500. Never a coerced 409 (the Story 2.1
    // lesson).
    if (request.revisionCount() == null) {
      throw new RevisionCountRequiredException();
    }
    String campName = trimmedCampName(request);
    validateCostRanges(request);
    // Excluding THIS camp by id — never by its old name, which a rename would have invalidated.
    // Legacy disarmed the check entirely after a new camp's first save (:315 guards on
    // !isCampSaved, set true at :296), so a rename-then-save bypassed BR-02 outright; that is a
    // defect, not ported (deviation (I)).
    if (repository.countCampsNamedExcluding(millId, year, campName, campId) > 0) {
      // The 404 contract outranks the 409: for an absent or foreign id the exclusion excludes
      // nothing, so a colliding name would otherwise answer "Camp name already exists." about a
      // camp the caller cannot even see — leaking name existence across the tenancy boundary the
      // scoped probe exists to hold (deviation (M)). Probed only on the conflict path, so a clean
      // save costs no extra query.
      if (repository.countCamp(campId, millId, year) == 0) {
        throw new CampNotFoundException();
      }
      throw new CampNameConflictException();
    }
    try {
      int updated =
          repository.updateCamp(
              campId,
              millId,
              year,
              request.revisionCount(),
              campName,
              request.roadDistanceToOperatingArea(),
              request.sizeOfCamp(),
              request.associatedCampVolume(),
              indicator(request.isolatedCamp()),
              request.comments(),
              user);
      if (updated == 0) {
        // Zero rows means the id is absent/foreign OR the token is stale, and the guarded UPDATE
        // cannot tell which. Only the scoped probe can.
        if (repository.countCamp(campId, millId, year) == 0) {
          throw new CampNotFoundException();
        }
        throw new StaleRevisionException();
      }
      writeCategoryRows(campId, request, user);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 5 update failed for mill {} year {} camp {} [{}]: {}",
          millId,
          year,
          campId,
          ex.getClass().getSimpleName(),
          NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Delete one camp and its whole expense family, then return the recomputed document (S07, BR-09).
   *
   * <p>Children go FIRST and that is mandatory: {@code ILCR_LCRD_CMP_RPT_FK} is {@code ON DELETE NO
   * ACTION} in delivery (Task 1 gate (ii)), so deleting the camp first raises ORA-02292. Legacy
   * only appeared to cascade because {@code CampReport.java:93} declares Hibernate's {@code
   * CascadeType.ALL}; no DDL cascade exists. The delete takes the item-62/68 sub-page rows with it
   * — the camp family goes together.
   *
   * <p>Carries no revision token (deviation (L), shared with Schedules 4/7A/11), so a delete cannot
   * be rejected as stale. The scoped existence probe runs first so a foreign or unknown id is a 404
   * before anything is removed (deviation (M) — legacy deleted by primary key alone).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param campId the camp to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the recomputed document without the deleted camp
   */
  @Transactional
  public Schedule5Response deleteCamp(long millId, int year, int campId, boolean callerMayEdit) {
    requireDraft(millId, year);
    try {
      if (repository.countCamp(campId, millId, year) == 0) {
        throw new CampNotFoundException();
      }
      repository.deleteCostDetailsForCamp(campId, millId, year);
      if (repository.deleteCamp(campId, millId, year) == 0) {
        // The probe above passed, so a zero here means a concurrent delete won the race. Acting on
        // the count rather than assuming success is the 8.2 lesson: a void delete reported "Data
        // deleted successfully" while the row survived.
        throw new CampNotFoundException();
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 5 delete failed for mill {} year {} camp {} [{}]: {}",
          millId,
          year,
          campId,
          ex.getClass().getSimpleName(),
          NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Check Status for Schedule 5 (S06, S20, BR-08) — read-only, mutates nothing, and NOT Draft-gated
   * ({@code VIEW_SCHEDULE} only; the 2.6 precedent, {@code deferred-work.md:23}), so a Submitted
   * mill can still be checked.
   *
   * <p>Camps are evaluated in {@code CAMP_REPORT_ID} order (7.1 deviation (c) — legacy iterates a
   * {@code HashMap} with no ORDER BY, {@code Schedule5DAO.java:58, 111-113}). A schedule passes iff
   * every camp passes, and an EMPTY camp list passes vacuously ({@code
   * Schedule5CheckStatus.java:89-97} returns true before its loop runs). On a pass the schedule
   * banner is emitted ALONE with no per-camp results — the legacy pass branch never enters the
   * per-camp loop ({@code Schedule5MB.java:324-333}) — which is deviation (C), contradicting both
   * the epics AC and {@code UC-SCH5-001-detailed.md:151}.
   *
   * <p>The service emits bundle KEYS with null text; {@link Schedule5CheckStatusResolver} resolves
   * and composes (AD-8).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the MET/ISSUES result with key-only messages for the controller to resolve
   */
  @Transactional(readOnly = true)
  public Schedule5CheckStatusResponse checkStatus(long millId, int year) {
    List<CampRow> campRows = repository.findCamps(millId, year);
    Map<Integer, CampDetails> detailsByCamp = groupDetails(millId, year);

    List<CampCheckResult> camps = new ArrayList<>();
    boolean schedulePasses = true;
    for (CampRow row : campRows) {
      CampDetails details = detailsByCamp.getOrDefault(row.campId(), CampDetails.empty());
      List<CampCheckMessage> issues =
          evaluateCamp(row, details.otherCampRows(), details.otherAccessRows());
      boolean met = issues.isEmpty();
      schedulePasses = schedulePasses && met;
      camps.add(
          new CampCheckResult(
              row.campId(),
              row.campName(),
              met,
              met ? List.of(new CampCheckMessage(MSG_CAMP_MET, null, null)) : issues));
    }

    if (schedulePasses) {
      return new Schedule5CheckStatusResponse(
          CheckStatusOutcome.MET, List.of(new MessageInfo(MSG_SCHEDULE_MET, null)), List.of());
    }
    return new Schedule5CheckStatusResponse(CheckStatusOutcome.ISSUES, List.of(), camps);
  }

  /**
   * One camp's missing-field findings in the verbatim legacy EMISSION order — camp name, road
   * distance, size of camp, associated camp volume, then the four sub-list conditions ({@code
   * Schedule5MB.checkValidatedCurrentCamp():348-359, 425-436}).
   *
   * <p>The emission order deliberately differs from the order the flags are COMPUTED in ({@code
   * Schedule5CheckStatus.java:17-20} does name, volume, distance, size); only emission order is
   * observable, so that is the one reproduced.
   *
   * <p>Three parity details that a tidier implementation would get wrong:
   *
   * <ul>
   *   <li>the camp-name test is TRIMMED ({@code CoreUtil.isNullOrEmptyString(name, true)} at :17),
   *       so a whitespace-only name FAILS;
   *   <li>the three numeric descriptors are PURE null tests (:18-20), so a stored {@code 0} PASSES
   *       — the D2 precedent ({@code deferred-work.md:135});
   *   <li>the sub-list description test is NOT trimmed ({@code CheckStatusUtil.java:134} is {@code
   *       == null || "".equals(…)}), so a whitespace-only description PASSES. Using {@code isBlank}
   *       here would silently tighten legacy.
   * </ul>
   *
   * <p>The twelve category cost/volume fields are NOT tested (deviation (D) — legacy's conditions
   * and its ~65 lines of emission are commented out at {@code Schedule5CheckStatus.java:21-34,
   * 60-82} and {@code Schedule5MB.java:360-424}), and neither is {@code isolatedCamp} (deviation
   * (E)), even though Save requires it.
   */
  static List<CampCheckMessage> evaluateCamp(
      CampRow row, List<DetailRow> otherCampRows, List<DetailRow> otherAccessRows) {
    List<CampCheckMessage> issues = new ArrayList<>();
    if (StringUtils.isBlank(row.campName())) {
      issues.add(valueRequired(FIELD_CAMP_NAME));
    }
    if (row.distanceToOperatingArea() == null) {
      issues.add(valueRequired(FIELD_ROAD_DISTANCE));
    }
    if (row.sizeOfCamp() == null) {
      issues.add(valueRequired(FIELD_SIZE_OF_CAMP));
    }
    if (row.associatedCampVolume() == null) {
      issues.add(valueRequired(FIELD_ASSOCIATED_CAMP_VOLUME));
    }
    if (anyDescriptionMissing(otherCampRows)) {
      issues.add(valueRequired(FIELD_OTHER_CAMP_DESCRIPTION));
    }
    if (anyCostMissing(otherCampRows)) {
      issues.add(valueRequired(FIELD_OTHER_CAMP_COST));
    }
    if (anyDescriptionMissing(otherAccessRows)) {
      issues.add(valueRequired(FIELD_OTHER_ACCESS_DESCRIPTION));
    }
    if (anyCostMissing(otherAccessRows)) {
      issues.add(valueRequired(FIELD_OTHER_ACCESS_COST));
    }
    return issues;
  }

  /**
   * {@code CheckStatusUtil.isCostVolumeDescriptionMissing} (:132-139): true if ANY row's
   * description is null or the empty string. Deliberately NOT {@code isBlank} — legacy compares
   * {@code "".equals(…)}, so a single space satisfies it.
   *
   * <p>False for an empty list: a camp with no sub-page rows has nothing missing.
   */
  private static boolean anyDescriptionMissing(List<DetailRow> rows) {
    return rows.stream()
        .anyMatch(row -> row.itemDescription() == null || row.itemDescription().isEmpty());
  }

  /**
   * {@code CheckStatusUtil.isCostVolumeTypeCostMissing} (:113-120): true if ANY row's cost is null.
   */
  private static boolean anyCostMissing(List<DetailRow> rows) {
    return rows.stream().anyMatch(row -> row.cost() == null);
  }

  private static CampCheckMessage valueRequired(String field) {
    return new CampCheckMessage(MSG_VALUE_REQUIRED, field, null);
  }

  /**
   * The Draft gate for every write: the Schedules 1–10 track must be {@code D}, else 409 (BR-06,
   * AD-9). Never reads the silviculture track. The mill/year context (400/404/409) is already
   * validated by the controller before this runs (AD-4).
   *
   * <p><strong>The read is {@code FOR UPDATE}, and that is load-bearing rather than
   * defensive.</strong> An unlocked {@code SELECT} makes this gate advisory: under Oracle READ
   * COMMITTED nothing pins the status for the rest of the transaction, so a transition committing
   * between the gate and the write lets the write land on a schedule that is no longer Draft.
   * Holding the row for the whole transaction also serializes concurrent Schedule 5 writes per
   * mill/year, which is the only available backstop for BR-02's count-then-insert and for {@code
   * upsertCostDetail}'s update-then-insert — this project owns no DDL on {@code THE}, so neither
   * race can be closed by a unique constraint. {@link Schedule5Repository#findTrackStatusForUpdate}
   * carries the full reasoning; Schedule 2 established the pattern ({@code
   * Schedule2Service.java:193-198}).
   *
   * <p>Called only from the three {@code @Transactional} write methods, so the lock is always held
   * to commit. {@code checkStatus} is read-only and ungated, and the 7.1 read path keeps the
   * unlocked {@code findTrackStatus} — a reader must never take a row lock.
   *
   * <p>Acknowledged cost: this adds a SECOND track-status query to this repository, so the
   * duplication {@code deferred-work.md} already records against the status read (nine repositories
   * carrying a near-identical copy, contradicting AD-9's stated millcontext ownership) gets
   * marginally worse rather than better. Taken deliberately — the two variants differ in locking,
   * which is exactly the distinction that must not be lost, and the alternative is leaving a known
   * race open until the hoist happens. Both variants go when that read moves into {@code
   * MillYearContext}.
   *
   * <p>Recorded hardening: legacy has no server-side gate at all — {@code save()} and {@code
   * deleteExistingCamp()} are guarded only by the {@code disabled=} attribute on the buttons
   * ({@code schedule5.xhtml:69, 92, 115, 159, 182, 211, 234}), which a crafted post ignores.
   */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatusForUpdate(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /**
   * The camp name as it will be BOTH compared and stored — trimmed on both paths (deviation (I)).
   * Legacy trimmed only before the insert-path check ({@code Schedule5MB.java:289}), compared the
   * stored value untrimmed on edit (:309), and persisted untrimmed either way ({@code
   * Schedule5DAO.java:373}), so {@code " Cedar "} and {@code "Cedar"} could coexist and only one of
   * them would ever match. Bean Validation's {@code @NotBlank} has already rejected a
   * whitespace-only name, so the trim here cannot produce an empty string.
   */
  private static String trimmedCampName(CampRequest request) {
    return request.campName().trim();
  }

  /**
   * The {@code Y}/{@code N} indicator for a validated non-null {@code isolatedCamp} ({@code
   * Schedule5DAO.java:377}, which dereferences the Boolean unguarded and would NPE on null —
   * {@code @NotNull} on the request makes that unreachable here).
   */
  private static String indicator(Boolean isolatedCamp) {
    return Boolean.TRUE.equals(isolatedCamp) ? INDICATOR_YES : INDICATOR_NO;
  }

  /**
   * The two cost ranges {@link CategoryEntry} cannot express (see {@link
   * CampCostOutOfRangeException} for why one record type cannot vary a constraint per property):
   * &plusmn;9,999,999 for the eight ordinary categories that carry {@code costSize="7"}, and a 0
   * FLOOR for {@code recoveries}, whose input carries {@code costSize="0"} ({@code
   * schedule5ExistingCamp.xhtml:252}).
   *
   * <p>{@code wagesAndBenefits} is deliberately absent: its input is missing the {@code costSize}
   * attribute in BOTH pages, so legacy validates it at the default &plusmn;99,999,999 — which
   * {@code CategoryEntry} already enforces declaratively (deviation (F), <strong>RATIFIED by the
   * Ministry</strong> on PR #370, 2026-08-27: "Keep it as is, as this is somewhat on purpose."
   * Adding it to the standard-range checks below would CONTRADICT that ruling — ruling 2 of {@code
   * docs/decisions/camps-and-access-expenses.md}).
   *
   * <p>{@code otherCampExpenses}/{@code otherAccessExpenses} are absent too: this path writes
   * {@code null} for their cost regardless of what was sent (§ ITEM WRITE MAP), because those costs
   * are the item-62/68 row sums.
   *
   * <p>Deviation (G): Recoveries is capped at 9,999,999 — the legacy MESSAGE's range — even though
   * {@code COST NUMBER(8,0)} would hold 99,999,999. No stored Schedule 5 row exceeds it today (Task
   * 1 gate (vii): all 16 over-range costs in delivery are summary-parented rows of other
   * schedules), so this blocks no existing edit.
   */
  private static void validateCostRanges(CampRequest request) {
    requireStandardCostRange(request.cateringAndFood());
    requireStandardCostRange(request.depreciationLease());
    requireStandardCostRange(request.generalCampExpenses());
    requireStandardCostRange(request.crewTransportation());
    requireStandardCostRange(request.equipAndSuppliesLand());
    requireStandardCostRange(request.equipAndSuppliesRail());
    requireStandardCostRange(request.equipAndSuppliesAir());
    requireStandardCostRange(request.equipAndSuppliesWater());

    Integer recoveries = entryCost(request.recoveries());
    if (recoveries != null && (recoveries < 0 || recoveries > COST_STANDARD_LIMIT)) {
      throw new CampCostOutOfRangeException(COST_KEY_RECOVERIES);
    }
  }

  private static void requireStandardCostRange(CategoryEntry entry) {
    Integer cost = entryCost(entry);
    if (cost != null && (cost < -COST_STANDARD_LIMIT || cost > COST_STANDARD_LIMIT)) {
      throw new CampCostOutOfRangeException(COST_KEY_STANDARD);
    }
  }

  /**
   * The twelve keyed category rows, upserted in the exact order legacy issues them ({@code
   * Schedule5DAO.java:387-398}). The order itself is not observable, but keeping it makes the diff
   * against legacy readable.
   *
   * <p><strong>The volume/cost asymmetry is legacy's, not a simplification.</strong> Item 141 is
   * written volume-only with a hard-coded null cost (:391), item 61 (Recoveries) cost-only with a
   * hard-coded null volume (:392), and item 142 volume-only (:398) — so whatever a client sends in
   * the excluded half is discarded rather than rejected, exactly as legacy's {@code
   * disabled}/hidden inputs post and are ignored.
   *
   * <p>Items <strong>57, 62 and 68 are never written here</strong>: 57 is registered in delivery
   * but dead (no legacy dispatch branch, zero rows anywhere), and 62/68 are the sub-page rows Story
   * 7.4 owns. A camp created by this path therefore has zero sub-page rows, which is also what a
   * legacy copy produces ({@code CampReportType.java:150-153}).
   *
   * <p>An omitted (null) {@link CategoryEntry} clears both halves rather than leaving them alone.
   * Legacy's form always posted all twelve rows, so a partial grid is not a legacy-reachable state,
   * and treating absent as cleared keeps "null means cleared" true for the whole request.
   */
  private void writeCategoryRows(int campId, CampRequest request, String user) {
    upsertPair(campId, ITEM_CATERING_AND_FOOD, request.cateringAndFood(), user);
    upsertPair(campId, ITEM_WAGES_AND_BENEFITS, request.wagesAndBenefits(), user);
    upsertPair(campId, ITEM_DEPRECIATION_LEASE, request.depreciationLease(), user);
    upsertPair(campId, ITEM_GENERAL_CAMP_EXPENSES, request.generalCampExpenses(), user);
    upsertVolumeOnly(campId, ITEM_OTHER_CAMP_EXPENSES_VOLUME, request.otherCampExpenses(), user);
    upsertCostOnly(campId, ITEM_RECOVERIES, request.recoveries(), user);
    upsertPair(campId, ITEM_CREW_TRANSPORTATION, request.crewTransportation(), user);
    upsertPair(campId, ITEM_EQUIP_LAND, request.equipAndSuppliesLand(), user);
    upsertPair(campId, ITEM_EQUIP_RAIL, request.equipAndSuppliesRail(), user);
    upsertPair(campId, ITEM_EQUIP_AIR, request.equipAndSuppliesAir(), user);
    upsertPair(campId, ITEM_EQUIP_WATER, request.equipAndSuppliesWater(), user);
    upsertVolumeOnly(
        campId, ITEM_OTHER_ACCESS_EXPENSES_VOLUME, request.otherAccessExpenses(), user);
  }

  private void upsertPair(int campId, int itemId, CategoryEntry entry, String user) {
    repository.upsertCostDetail(campId, itemId, entryVolume(entry), entryCost(entry), user);
  }

  private void upsertVolumeOnly(int campId, int itemId, CategoryEntry entry, String user) {
    repository.upsertCostDetail(campId, itemId, entryVolume(entry), null, user);
  }

  private void upsertCostOnly(int campId, int itemId, CategoryEntry entry, String user) {
    repository.upsertCostDetail(campId, itemId, null, entryCost(entry), user);
  }

  /**
   * The entry's volume, or null when the whole category was omitted. Named distinctly from the read
   * path's {@code volumeOf(CampDetails, int)} rather than overloading it: the two take unrelated
   * arguments and answer unrelated questions, and google_checks requires overloads to sit adjacent.
   */
  private static BigDecimal entryVolume(CategoryEntry entry) {
    return entry == null ? null : entry.volume();
  }

  /** The entry's cost, or null when the whole category was omitted. */
  private static Integer entryCost(CategoryEntry entry) {
    return entry == null ? null : entry.cost();
  }

  /**
   * One camp's detail rows: the single-row fixed grid keyed by item id (insertion-ordered so the
   * first-wins survivor is visible in order), plus the two sub-page row lists.
   */
  private record CampDetails(
      Map<Integer, DetailRow> fixed,
      List<DetailRow> otherCampRows,
      List<DetailRow> otherAccessRows) {

    static CampDetails empty() {
      return new CampDetails(new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  // ===============================================================================================
  // Sub-pages (Story 7.4) — the itemized Other Camp (62) and Other Access (68) expense rows.
  //
  // This is the SOLE writer of those two item ids (AD-5). The camp write path above never touches
  // them, and nothing here touches the twelve fixed items.
  // ===============================================================================================

  /**
   * The two sub-pages, and every way in which they differ, in one place.
   *
   * <p>Keeping the differences here rather than in branches is what makes the asymmetries
   * auditable: they are not arbitrary, but neither are they symmetric, and each one is a separately
   * verified legacy fact. The cost bound is per PAGE, not per control — every cost input on the
   * Camp sub-page carries {@code costSize="7"} ({@code schedule5CampExpenses.xhtml:45} add-form and
   * {@code :79} grid) and neither Access one does ({@code schedule5AccessExpenses.xhtml:36-38},
   * {@code :71-76}), which the story's committed AC and the UC documents both record incorrectly
   * (deviation (A)).
   */
  enum SubPage {

    /** Other Camp Expenses — item 62, &plusmn;9,999,999, footer volume = SUM of row volumes. */
    CAMP(ITEM_OTHER_CAMP_EXPENSE_ROW, COST_STANDARD_LIMIT, COST_KEY_STANDARD),

    /** Other Access Expenses — item 68, &plusmn;99,999,999, footer volume = the camp volume. */
    ACCESS(ITEM_OTHER_ACCESS_EXPENSE_ROW, COST_WIDE_LIMIT, COST_KEY_WIDE);

    private final int itemId;
    private final int costLimit;
    private final String costMessageKey;

    SubPage(int itemId, int costLimit, String costMessageKey) {
      this.itemId = itemId;
      this.costLimit = costLimit;
      this.costMessageKey = costMessageKey;
    }

    int itemId() {
      return itemId;
    }
  }

  /**
   * One sub-page's rows and totals for a validated mill/year and camp (S04).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param campId the parent camp
   * @param page which sub-page
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the sub-page document
   * @throws CampNotFoundException when the camp is unknown or belongs to another mill/year
   */
  @Transactional(readOnly = true)
  public SubPageDocument getSubPage(
      long millId, int year, int campId, SubPage page, boolean callerMayEdit) {
    CampRow camp = requireCamp(millId, year, campId);
    return buildSubPageDocument(
        millId, year, camp, page, subPageEditable(millId, year, callerMayEdit));
  }

  /**
   * The served {@code editable} flag, derived the same way on the read AND on every write echo
   * (AD-9: server-authoritative). The writes could hardcode {@code callerMayEdit} because {@code
   * requireDraft} just proved the Draft half under its lock — but that would couple the echoed flag
   * to the gate staying exactly as strict as it is today; deriving it here keeps the invariant
   * structural rather than incidental.
   */
  private boolean subPageEditable(long millId, int year, boolean callerMayEdit) {
    return callerMayEdit && STATUS_DRAFT.equals(trackStatus(millId, year));
  }

  /**
   * Reconcile one sub-page's whole row list in a single transaction (S04) — the sole writer of this
   * item id.
   *
   * <p>Reconcile semantics are Schedule 3's ({@code Schedule3Service.classifySaveRow}, {@code
   * :441-449}): a null {@code rowId} INSERTs, a known one UPDATEs in place, a row absent from the
   * body is DELETEd, and an id this camp does not hold for this item raises 404 with NOTHING
   * persisted. The 404 is raised BEFORE any statement runs, so a stale id cannot half-apply a batch
   * — the classification pass is separate from the write pass for exactly that reason.
   *
   * <p>{@code requireDraft} runs first and its {@code SELECT … FOR UPDATE} on the mill/year status
   * row is what serializes concurrent sub-page writers; the schema offers no unique key to lean on
   * (no DDL on {@code THE}).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param campId the parent camp
   * @param page which sub-page
   * @param request the complete row set the camp should hold afterwards
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the acting user id (audit columns)
   * @return the refreshed sub-page document
   */
  @Transactional
  public SubPageDocument saveSubPage(
      long millId,
      int year,
      int campId,
      SubPage page,
      SubPageSaveRequest request,
      boolean callerMayEdit,
      String user) {
    requireDraft(millId, year);
    final CampRow camp = requireCamp(millId, year, campId);
    // Non-null by Bean Validation: an omitted rows field is a 400, never a silent delete-all.
    List<SubPageRowRequest> incoming = request.rows();
    validateSubPageCosts(page, incoming);

    // Classify EVERYTHING before writing ANYTHING: an unknown id must 404 with nothing persisted.
    Map<Integer, DetailRow> storedById = new LinkedHashMap<>();
    for (DetailRow row : repository.findSubPageRows(campId, page.itemId(), millId, year)) {
      storedById.put(row.detailId(), row);
    }
    Set<Integer> kept = new LinkedHashSet<>();
    for (SubPageRowRequest row : incoming) {
      if (row.rowId() == null) {
        continue;
      }
      if (!storedById.containsKey(row.rowId()) || !kept.add(row.rowId())) {
        // Unknown, foreign, belonging to the other sub-page item, or repeated within one body —
        // all of them mean the client is working from a list this camp does not have.
        throw new CampNotFoundException();
      }
    }

    try {
      for (SubPageRowRequest row : incoming) {
        if (row.rowId() == null) {
          repository.insertSubPageRow(
              repository.nextCostDetailId(),
              campId,
              page.itemId(),
              row.cost(),
              row.description(),
              user);
        } else if (repository.updateSubPageRow(
                row.rowId(), campId, page.itemId(), row.cost(), row.description(), user)
            == 0) {
          // Classified as present a moment ago, so a zero here means it vanished under the lock.
          // Checked rather than assumed — the 4.4 lesson, where an edit silently dropped its value.
          throw new CampNotFoundException();
        }
      }
      for (Integer storedId : storedById.keySet()) {
        if (!kept.contains(storedId)
            && repository.deleteSubPageRow(storedId, campId, page.itemId()) == 0) {
          throw new CampNotFoundException();
        }
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 5 sub-page save failed for mill {} year {} camp {} item {} [{}]: {}",
          millId,
          year,
          campId,
          page.itemId(),
          ex.getClass().getSimpleName(),
          NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
    return buildSubPageDocument(
        millId, year, camp, page, subPageEditable(millId, year, callerMayEdit));
  }

  /**
   * Delete one sub-page row immediately (S07) — the legacy Delete button, which persists on click
   * rather than at Save ({@code Schedule5CampExpensesMB.deleteCampExpense()}, {@code :158-167}).
   *
   * <p>Camp- and item-scoped, so a foreign or unknown id can never delete another camp's row; it is
   * a 404 instead. Carries no revision token (AR11 house deviation (N)).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param campId the parent camp
   * @param page which sub-page
   * @param rowId the row to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed sub-page document
   */
  @Transactional
  public SubPageDocument deleteSubPageRow(
      long millId, int year, int campId, SubPage page, int rowId, boolean callerMayEdit) {
    requireDraft(millId, year);
    CampRow camp = requireCamp(millId, year, campId);
    try {
      if (repository.deleteSubPageRow(rowId, campId, page.itemId()) == 0) {
        throw new CampNotFoundException();
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 5 sub-page delete failed for mill {} year {} camp {} item {} [{}]: {}",
          millId,
          year,
          campId,
          page.itemId(),
          ex.getClass().getSimpleName(),
          NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
      throw new ScheduleNotSavedException();
    }
    return buildSubPageDocument(
        millId, year, camp, page, subPageEditable(millId, year, callerMayEdit));
  }

  /**
   * The camp, scoped to the mill/year and category {@code '5'} — the IDOR guard every sub-page
   * entry point shares. Reuses {@link Schedule5Repository#findCamps} rather than adding a
   * single-camp query: it is already correctly scoped, and a mill/year holds at most a few dozen
   * camps (61 in all of delivery).
   */
  private CampRow requireCamp(long millId, int year, int campId) {
    return repository.findCamps(millId, year).stream()
        .filter(camp -> camp.campId() == campId)
        .findFirst()
        .orElseThrow(CampNotFoundException::new);
  }

  /** The 1–10 track status for the mill/year, or null when there is no status row. */
  private String trackStatus(long millId, int year) {
    return repository.findTrackStatus(millId, year).orElse(null);
  }

  /**
   * The per-page cost bounds, applied HERE and only here — {@link SubPageRowRequest} deliberately
   * carries no declarative {@code @Min}/{@code @Max}, because a DTO-level bound at the wider Access
   * limit would fire first and reject an out-of-band Camp cost with the ACCESS message (AD-8). Each
   * page's own bound pairs with its own message key, exactly as {@link #validateCostRanges} narrows
   * the eight {@code costSize="7"} camp categories that {@code CategoryEntry} cannot.
   */
  private static void validateSubPageCosts(SubPage page, List<SubPageRowRequest> rows) {
    for (SubPageRowRequest row : rows) {
      Integer cost = row.cost();
      if (cost != null && (cost < -page.costLimit || cost > page.costLimit)) {
        throw new CampCostOutOfRangeException(page.costMessageKey);
      }
    }
  }

  /** Reads the rows back and assembles the document — never hand-patches a total. */
  private SubPageDocument buildSubPageDocument(
      long millId, int year, CampRow camp, SubPage page, boolean editable) {
    BigDecimal stampedVolume = stampedVolume(millId, year, camp.campId(), page);
    List<DetailRow> stored = repository.findSubPageRows(camp.campId(), page.itemId(), millId, year);
    List<SubPageRow> rows =
        stored.stream()
            .map(
                row ->
                    new SubPageRow(
                        row.detailId(),
                        row.itemDescription(),
                        stampedVolume,
                        row.cost(),
                        costPerVolume(
                            row.cost() == null ? null : row.cost().longValue(), stampedVolume)))
            .toList();
    return new SubPageDocument(
        camp.campId(),
        camp.campName(),
        stampedVolume,
        editable,
        rows,
        subPageTotals(page, stored, stampedVolume),
        null);
  }

  /**
   * The volume every row on this sub-page displays: the camp's item-141 (camp) or item-142 (access)
   * amount, NOT anything stored on the row itself (deviation (B)).
   *
   * <p>Read through the same {@code findCostDetails} projection the camp document uses, so the two
   * can never disagree about which row is canonical when a camp/item pair holds more than one
   * (first-by-detail-id wins, 7.1 deviation (f)).
   */
  private BigDecimal stampedVolume(long millId, int year, int campId, SubPage page) {
    int volumeItemId =
        page == SubPage.CAMP ? ITEM_OTHER_CAMP_EXPENSES_VOLUME : ITEM_OTHER_ACCESS_EXPENSES_VOLUME;
    return repository.findCostDetails(millId, year).stream()
        .filter(
            row ->
                row.campId() == campId
                    && row.costItemId() != null
                    && row.costItemId() == volumeItemId)
        .map(DetailRow::volume)
        .findFirst()
        .orElse(null);
  }

  /**
   * The footer triple — <strong>two genuinely different shapes</strong> (deviation (C)).
   *
   * <p>CAMP is {@code CoreUtil.sumDescriptionCostVolumeType} ({@code :610-632}): it sums cost AND
   * volume, and sets its "something contributed" flag on a non-null cost <em>or</em> a non-null
   * volume. Because {@code CampReportType.getOtherCampExpensesList()} ({@code :433-438}) stamps
   * every row's volume with the camp-level amount before the sum runs, the summed volume is {@code
   * n × campVolume} — and, critically, a list of rows whose costs are ALL null still flags as
   * contributing whenever the camp volume is non-null, yielding a cost of {@code 0} rather than
   * null. That zero then propagates into Camp Sub-Total, Camp Total and Camp and Access Total. This
   * is 7.1 deviation (h)/(L), unreachable until this story writes the first item-62 rows.
   *
   * <p>ACCESS is {@code sumDescriptionCostVolumeTypeCostOnly} ({@code :590-608}) — cost only, so an
   * all-null-cost list correctly yields null whatever the volume is — after which {@code
   * getOtherAccessExpensesTotal()} ({@code :460-464}) overwrites the total's volume with the SINGLE
   * camp volume, unconditionally, including on the empty list.
   *
   * <p>Do not symmetrize these. They look identical on screen and are not, and each side is pinned
   * by its own test.
   */
  private CategoryAmount subPageTotals(
      SubPage page, List<DetailRow> rows, BigDecimal stampedVolume) {
    long cost = 0L;
    boolean contributed = false;
    for (DetailRow row : rows) {
      if (row.cost() != null) {
        cost += row.cost();
        contributed = true;
      } else if (page == SubPage.CAMP && stampedVolume != null) {
        contributed = true;
      }
    }
    if (page == SubPage.ACCESS) {
      // Volume is the single camp volume, set even when no cost contributed.
      return new CategoryAmount(
          stampedVolume,
          contributed ? cost : null,
          contributed ? costPerVolume(cost, stampedVolume) : null);
    }
    if (!contributed) {
      return new CategoryAmount(null, null, null);
    }
    // Legacy starts the running volume at ZERO and only adds non-null row volumes, so a camp whose
    // item-141 volume is null totals 0 here rather than null — matching
    // sumDescriptionCostVolumeType
    // returning its zero-initialised accumulator once any cost flagged it.
    BigDecimal summedVolume =
        stampedVolume == null
            ? BigDecimal.ZERO
            : stampedVolume.multiply(BigDecimal.valueOf(rows.size()));
    return new CategoryAmount(summedVolume, cost, costPerVolume(cost, summedVolume));
  }
}
