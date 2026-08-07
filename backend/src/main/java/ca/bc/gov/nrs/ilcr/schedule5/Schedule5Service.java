package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 5 (Camp and Access Expenses) read document from the stored
 * {@code CAMP_REPORT} camps and their keyed {@code ILCR_COST_REPORT_DETAIL} category amounts,
 * computing every derived value server-side (AD-5, AD-6): the four totals, every $/m&sup3;, the two
 * sub-page row counts, and the item-62/68 cost sums. The mill/year context is validated by
 * {@code MillContextService} in the controller before this runs (AD-4).
 *
 * <p>A valid, ACTIVE mill/year with NO camps is not a 404 — it is the legitimate no-camps state
 * and yields a 200 {@code camps: []} (deviation (a); the 404 is reserved for a missing mill/year
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

  /** The single-row items: at most one row per camp, so a second is a duplicate (deviation (f)). */
  private static final Set<Integer> SINGLE_ROW_ITEMS = Set.of(
      ITEM_CATERING_AND_FOOD, ITEM_WAGES_AND_BENEFITS, ITEM_DEPRECIATION_LEASE,
      ITEM_GENERAL_CAMP_EXPENSES, ITEM_RECOVERIES, ITEM_CREW_TRANSPORTATION, ITEM_EQUIP_LAND,
      ITEM_EQUIP_RAIL, ITEM_EQUIP_AIR, ITEM_EQUIP_WATER, ITEM_OTHER_CAMP_EXPENSES_VOLUME,
      ITEM_OTHER_ACCESS_EXPENSES_VOLUME);

  private final Schedule5Repository repository;

  /** Wires the Schedule 5 repository. */
  public Schedule5Service(Schedule5Repository repository) {
    this.repository = repository;
  }

  /**
   * The Schedule 5 document for a validated mill/year.
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
    // COMMITTED gives each its own snapshot, so a camp committed between them is visible to one
    // and not the other. In this order the newcomer is simply not in the camp list yet — a
    // consistent older view. Reversed, it would appear in the list with no details found and be
    // served with every category empty and every total absent, indistinguishable from a genuine
    // zero-detail camp: wrong money, silently. 7.2's write path makes that reachable in normal use.
    List<CampRow> campRows = repository.findCamps(millId, year);
    Map<Integer, CampDetails> detailsByCamp = groupDetails(millId, year);
    List<Camp> camps = campRows.stream()
        .map(row -> toCamp(millId, year, row,
            detailsByCamp.getOrDefault(row.campId(), CampDetails.empty())))
        .toList();

    return new Schedule5Response(millId, year, trackStatus, editable, camps, null);
  }

  /**
   * The per-camp detail rows, split into the single-row fixed grid and the two sub-page row lists.
   *
   * <p>Duplicate rows for a single-row item resolve FIRST-BY-DETAIL-ID-WINS with a warning
   * (deviation (f)). Legacy simply overwrote the same {@code CostVolumeType}
   * ({@code Schedule5DAO.java:211-234}), so the survivor was whichever row an identity-hashed
   * {@code HashSet} yielded last — not stable between JVM runs. The repository's detail-id ordering
   * plus {@code putIfAbsent} replaces that chance with determinism, so no derived total depends on
   * row order.
   *
   * <p>An unregistered item id is dropped with a warning, which is legacy-verbatim: the dispatch
   * chain's else-branch logs {@code Cost Item ID Not Found} and discards the row
   * ({@code Schedule5DAO.java:283-285}).
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
            millId, year, row.campId(), row.detailId());
      } else if (itemId == ITEM_OTHER_CAMP_EXPENSE_ROW) {
        camp.otherCampRows().add(row);
      } else if (itemId == ITEM_OTHER_ACCESS_EXPENSE_ROW) {
        camp.otherAccessRows().add(row);
      } else if (SINGLE_ROW_ITEMS.contains(itemId)) {
        DetailRow kept = camp.fixed().putIfAbsent(itemId, row);
        if (kept != null) {
          log.warn(
              "Schedule 5 mill {} year {} camp {} has more than one row for cost item {}; keeping"
                  + " detail id {} and ignoring detail id {}",
              millId, year, row.campId(), itemId, kept.detailId(), row.detailId());
        }
      } else {
        log.warn(
            "Schedule 5 mill {} year {} camp {} references unrecognized cost item {} (detail id"
                + " {}); row dropped, matching legacy Schedule5DAO",
            millId, year, row.campId(), itemId, row.detailId());
      }
    }
    return byCamp;
  }

  /**
   * One camp: its descriptors served exactly as stored, its twelve stored category amounts, and the
   * four derived totals.
   *
   * <p><strong>Sub-Total is computed FIRST and unconditionally</strong> — the §T1 trap. Legacy's
   * {@code getCampTotal()} ({@code CampReportType.java:357-361}) reads the FIELD
   * {@code campSubTotal}, which only a prior {@code getCampSubTotal()} call populates (:343). Every
   * legacy consumer happens to call Sub-Total first, masking it; a port that derives only
   * {@code campTotal} would get {@code null - recoveries} and silently collapse
   * {@code campAndAccessTotal} to the Access total alone.
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
    CategoryAmount otherCampExpenses = new CategoryAmount(otherCampVolume, otherCampCost,
        costPerVolumePerTerm(details.otherCampRows(), otherCampVolume));

    // Recoveries is the volume-less category: legacy sets cost only (Schedule5DAO.java:242-244).
    CategoryAmount recoveries = new CategoryAmount(null, costOf(details, ITEM_RECOVERIES), null);

    // (1) Sub-Total over EXACTLY five costs — Recoveries excluded (CampReportType.java:335-347).
    Long campSubTotalCost = sumCosts(cateringAndFood.cost(), wagesAndBenefits.cost(),
        depreciationLease.cost(), generalCampExpenses.cost(), otherCampCost);
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
    CategoryAmount otherAccessExpenses = new CategoryAmount(otherAccessVolume, otherAccessCost,
        costPerVolumePerTerm(details.otherAccessRows(), otherAccessVolume));

    // (3) Access Expense Total over EXACTLY six costs (CampReportType.java:413-425). It sums the
    // CORRECT item-68 total; legacy's getOtherAccessExpenses() cross-wiring bug (:404-407, which
    // assigns the CAMP total) is deliberately NOT ported — deviation (d). The bug is latent in
    // legacy precisely because this total, and every display path, reads the item-68 sum instead.
    Long accessExpenseTotalCost = sumCosts(crewTransportation.cost(), equipAndSuppliesLand.cost(),
        equipAndSuppliesRail.cost(), equipAndSuppliesAir.cost(), equipAndSuppliesWater.cost(),
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
   * exactly as legacy's {@code .equals(POSITIVE_IND) ? true : false} decides
   * ({@code Schedule5DAO.java:87}).
   *
   * <p>A NULL indicator serves null rather than failing the request — deviation (e). Legacy calls
   * {@code .equals()} unguarded and {@code Schedule5MB.init()} catches only {@code ILCSException}
   * (:73-77), so a NULL there NPEs the whole page. The delivery column is NOT NULL DEFAULT
   * {@code 'N'} (Task 1 gate (i)) and no stored row is null (gate (v)), so this branch is defensive
   * hardening against future data, not a live case — hence the warning if it ever fires.
   */
  private Boolean isolatedCamp(long millId, int year, CampRow row) {
    if (row.isolatedCampInd() == null) {
      log.warn(
          "Schedule 5 mill {} year {} camp {} has a NULL ISOLATED_CAMP_IND despite the column being"
              + " NOT NULL; serving null rather than failing the request",
          millId, year, row.campId());
      return null;
    }
    return INDICATOR_YES.equals(row.isolatedCampInd());
  }

  /** A stored category amount: volume and cost as saved, $/m&sup3; derived from the pair. */
  private CategoryAmount amount(CampDetails details, int itemId) {
    DetailRow row = details.fixed().get(itemId);
    if (row == null) {
      // The category still exists in the response as an empty object — legacy pre-initializes
      // every CostVolumeType field, so an absent row reads as null cost/volume, not as a
      // missing category.
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
   * same camp-level volume, the result equals {@code sum / volume} only up to per-term rounding,
   * so the per-term rounding is reproduced rather than short-cut.
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

  /**
   * One camp's detail rows: the single-row fixed grid keyed by item id (insertion-ordered so the
   * first-wins survivor is visible in order), plus the two sub-page row lists.
   */
  private record CampDetails(
      Map<Integer, DetailRow> fixed, List<DetailRow> otherCampRows,
      List<DetailRow> otherAccessRows) {

    static CampDetails empty() {
      return new CampDetails(new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>());
    }
  }
}
