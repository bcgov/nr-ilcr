package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_SCHEDULE_3;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsSummary;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy {@code Schedule1Extract}: one 51-column row per (mill, year) with a Schedule 1 summary.
 *
 * <p>Eleven of the cells are Schedule 3-derived — the Crown Timber volume, the Forest Management
 * Admin cost and its {@code $/m³}, Subtotal Company Logging, Less Silviculture Admin, Total
 * Silviculture and the grand total — and legacy wrote {@code *** NO SCHEDULE 3 ***} into every one
 * of them when the pair had no Schedule 3 summary. The figures themselves come from the Schedule 1
 * owner's derived scalars, which already fold in the Schedule 3 pulls (AD-14); this builder only
 * formats them and decides sentinel-or-figure by whether a Schedule 3 document exists.
 *
 * <p>A summary whose every figure is null is legacy's "empty record" and gets the per-record marker
 * rather than a row of dashes.
 */
public final class Schedule1Section implements SectionBuilder {

  private static final int STANDING_TREE_TO_TRUCK = 12;
  private static final int LOG_TRANSPORTATION = 13;
  private static final int ROAD_MANAGEMENT = 14;
  private static final int ROAD_CONSTRUCTION = 15;
  private static final int POST_LOGGING_TREATMENT = 16;
  private static final int STUMPAGE_ROYALTY = 17;
  private static final int DEPLETION_AMORTIZATION = 18;
  private static final int FOREST_MGMT_ADMIN = 143;
  private static final int SUBTOTAL_COMPANY_LOGGING = 144;

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "CROWN_VOL_SCH_3",
    "STAND_TTT_M3",
    "STAND_TTT_$",
    "STAND_TTT_$/M3",
    "LOG_TRANS_M3",
    "LOG_TRANS_$",
    "LOG_TRANS_$/M3",
    "ROAD_MNGMT_M3",
    "ROAD_MNGMT_$",
    "ROAD_MNGMT_$/M3",
    "ROAD_CONS_M3",
    "ROAD_CONS_$",
    "ROAD_CONS_$/M3",
    "POST_LOG_M3",
    "POST_LOG_$",
    "POST_LOG_$/M3",
    "MGMT_ADMIN_M3",
    "MGMT_ADMIN_$",
    "MGMT_ADMIN_$/M3",
    "STUMP_M3",
    "STUMP_$",
    "STUMP_$/M3",
    "DEPLETION_M3",
    "DEPLETION_$",
    "DEPLETION_$/M3",
    "SUB_OTHER_COST_M3",
    "SUB_OTHER_COST_$",
    "SUB_OTHER_COST_$/M3",
    "SUB_COMPANY_LOGGING_M3",
    "SUB_COMPANY_LOGGING_$",
    "SUB_COMPANY_LOGGING_$/M3",
    "SILV_ACT_M3",
    "SILV_ACT_$",
    "SILV_ACT_$/M3",
    "SILV_LESS_ADMIN_M3",
    "SILV_LESS_ADMIN_$",
    "SILV_LESS_ADMIN_$/M3",
    "SILV_ACCRUED_M3",
    "SILV_ACCRUED_$",
    "SILV_ACCRUED_$/M3",
    "SILVIC_TOTAL_M3",
    "SILVIC_TOTAL_$",
    "SILVIC_TOTAL_$/M3",
    "TOTAL_LOG_COSTS_M3",
    "TOTAL_LOG_COSTS_$",
    "TOTAL_LOG_COSTS_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 1 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /**
   * The row for one (mill, year), or the per-record marker when the summary holds no figures.
   *
   * @param ctx the leading cells
   * @param s1 the Schedule 1 document (present)
   * @param s3 the Schedule 3 document, or null when the pair has none — every Schedule 3-derived
   *     cell then carries the sentinel
   */
  public String[] row(RowContext ctx, Schedule1Response s1, Schedule3Response s3) {
    if (isEmpty(s1)) {
      return ctx.noDataRow();
    }
    Map<Integer, LineItem> byCode = index(s1.lineItems());
    SilvicultureBlock silv = s1.silviculture();
    OtherCostsSummary other = s1.otherCosts();
    boolean hasSch3 = s3 != null;

    String crownVolume = hasSch3 ? whole(s1.schedule3CrownVolume()) : NO_SCHEDULE_3;
    String mgmtAdminCost = hasSch3 ? whole(s1.forestMgmtAdminCost()) : NO_SCHEDULE_3;
    String mgmtAdminPerUnit = hasSch3 ? twoDecimals(s1.forestMgmtAdminPerUnit()) : NO_SCHEDULE_3;
    String subCompanyCost = hasSch3 ? whole(s1.subtotalCompanyLoggingCost()) : NO_SCHEDULE_3;
    String subCompanyPerUnit =
        hasSch3 ? twoDecimals(s1.subtotalCompanyLoggingPerUnit()) : NO_SCHEDULE_3;
    String lessSilvAdminCost = hasSch3 ? whole(s1.lessSilvAdminCost()) : NO_SCHEDULE_3;
    String lessSilvAdminPerUnit = hasSch3 ? twoDecimals(s1.lessSilvAdminPerUnit()) : NO_SCHEDULE_3;
    String silvTotalCost = hasSch3 ? whole(s1.totalSilvicultureCost()) : NO_SCHEDULE_3;
    String silvTotalPerUnit = hasSch3 ? twoDecimals(s1.totalSilviculturePerUnit()) : NO_SCHEDULE_3;
    String totalCost = hasSch3 ? whole(s1.totalCompanyLoggingCost()) : NO_SCHEDULE_3;
    String totalPerUnit = hasSch3 ? twoDecimals(s1.totalCompanyLoggingPerUnit()) : NO_SCHEDULE_3;

    return ctx.with(
        crownVolume,
        volume(byCode.get(STANDING_TREE_TO_TRUCK)),
        cost(byCode.get(STANDING_TREE_TO_TRUCK)),
        perUnit(byCode.get(STANDING_TREE_TO_TRUCK)),
        volume(byCode.get(LOG_TRANSPORTATION)),
        cost(byCode.get(LOG_TRANSPORTATION)),
        perUnit(byCode.get(LOG_TRANSPORTATION)),
        volume(byCode.get(ROAD_MANAGEMENT)),
        cost(byCode.get(ROAD_MANAGEMENT)),
        perUnit(byCode.get(ROAD_MANAGEMENT)),
        volume(byCode.get(ROAD_CONSTRUCTION)),
        cost(byCode.get(ROAD_CONSTRUCTION)),
        perUnit(byCode.get(ROAD_CONSTRUCTION)),
        volume(byCode.get(POST_LOGGING_TREATMENT)),
        cost(byCode.get(POST_LOGGING_TREATMENT)),
        perUnit(byCode.get(POST_LOGGING_TREATMENT)),
        volume(byCode.get(FOREST_MGMT_ADMIN)),
        mgmtAdminCost,
        mgmtAdminPerUnit,
        volume(byCode.get(STUMPAGE_ROYALTY)),
        cost(byCode.get(STUMPAGE_ROYALTY)),
        perUnit(byCode.get(STUMPAGE_ROYALTY)),
        volume(byCode.get(DEPLETION_AMORTIZATION)),
        cost(byCode.get(DEPLETION_AMORTIZATION)),
        perUnit(byCode.get(DEPLETION_AMORTIZATION)),
        whole(other == null ? null : other.volume()),
        whole(other == null ? null : other.costSubtotal()),
        twoDecimals(other == null ? null : other.perUnit()),
        volume(byCode.get(SUBTOTAL_COMPANY_LOGGING)),
        subCompanyCost,
        subCompanyPerUnit,
        volume(silv == null ? null : silv.actualSpent()),
        cost(silv == null ? null : silv.actualSpent()),
        perUnit(silv == null ? null : silv.actualSpent()),
        volume(silv == null ? null : silv.lessAdmin()),
        lessSilvAdminCost,
        lessSilvAdminPerUnit,
        volume(silv == null ? null : silv.accruedLessActual()),
        cost(silv == null ? null : silv.accruedLessActual()),
        perUnit(silv == null ? null : silv.accruedLessActual()),
        volume(silv == null ? null : silv.total()),
        silvTotalCost,
        silvTotalPerUnit,
        crownVolume,
        totalCost,
        totalPerUnit,
        text(s1.comments()));
  }

  /**
   * Legacy {@code checkEmpty}: no entered figure anywhere, no shared volume, no comment. Public
   * because the generator needs it to decide the whole section's all-empty case, which legacy also
   * decided outside the per-record builder ({@code Schedule1Extract.java:38}).
   */
  public static boolean isEmpty(Schedule1Response s1) {
    if (s1.comments() != null && !s1.comments().trim().isEmpty()) {
      return false;
    }
    if (s1.lineItems() != null) {
      for (LineItem li : s1.lineItems()) {
        if (li != null && (li.volume() != null || li.cost() != null || li.perUnit() != null)) {
          return false;
        }
      }
    }
    SilvicultureBlock silv = s1.silviculture();
    if (silv != null
        && (hasFigure(silv.actualSpent())
            || hasFigure(silv.accruedLessActual())
            || hasFigure(silv.lessAdmin())
            || hasFigure(silv.total()))) {
      return false;
    }
    return s1.otherCosts() == null || s1.otherCosts().volume() == null;
  }

  private static boolean hasFigure(LineItem li) {
    return li != null && (li.volume() != null || li.cost() != null || li.perUnit() != null);
  }

  private static Map<Integer, LineItem> index(List<LineItem> items) {
    Map<Integer, LineItem> byCode = new HashMap<>();
    if (items != null) {
      for (LineItem li : items) {
        if (li != null && li.costItemCode() != null) {
          byCode.put(li.costItemCode(), li);
        }
      }
    }
    return byCode;
  }

  private static String volume(LineItem li) {
    return whole(li == null ? null : li.volume());
  }

  private static String cost(LineItem li) {
    return whole(li == null ? null : li.cost());
  }

  private static String perUnit(LineItem li) {
    return twoDecimals(li == null ? null : li.perUnit());
  }
}
