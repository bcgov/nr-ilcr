package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 1 read document ("Average Cost of Logging") to the print section datasource.
 * Schedule 1 is a FLAT, single-page fixed-line-item statement, so this emits ONE section row
 * holding every labelled cell (volume / cost / $-per-m³) — reusing the values {@code
 * Schedule1Service} already computed, format-only (whole-dollar cost, two-decimal volume + $/m³,
 * {@code "-"} for null).
 *
 * <p>The one dynamic block is the "Other Cost List" at the bottom: the itemized {@code
 * OtherCostRow}s from {@link OtherCostsDocument} are carried on the single statement row as the
 * nested collection field {@code otherCostRows} (a {@code List<Map>}), which the {@code
 * schedule1.jrxml} list component iterates via its own sub-datasource. The Subtotal Other Costs
 * LINE above it uses the document's shared volume / cost subtotal / per-unit.
 *
 * <p>Cross-schedule (BR-04): the Forest Management Administration (143), Less Silviculture Admin
 * (139), Subtotal Company Logging (144) and the Total-Silviculture / grand-total rows take their
 * cost + $/m³ from the DTO's derived scalars (which fold in the Schedule 3 pulls), never recomputed
 * here; their VOLUME comes from the corresponding structured line item when present. The
 * absent-summary case throws {@code ScheduleNotFoundException} in the service and is translated to
 * a null skip by the {@code ReportService} dispatch (BR-09).
 */
final class Schedule1SectionMapper {

  private static final int CODE_STANDING_TREE_TO_TRUCK = 12;
  private static final int CODE_LOG_TRANSPORTATION = 13;
  private static final int CODE_ROAD_MANAGEMENT = 14;
  private static final int CODE_ROAD_CONSTRUCTION = 15;
  private static final int CODE_POST_LOGGING_TREATMENT = 16;
  private static final int CODE_STUMPAGE_ROYALTY = 17;
  private static final int CODE_DEPLETION_AMORTIZATION = 18;
  private static final int CODE_FOREST_MGMT_ADMIN = 143;
  private static final int CODE_SUBTOTAL_COMPANY_LOGGING = 144;

  private Schedule1SectionMapper() {}

  static SectionData map(Schedule1Response response, OtherCostsDocument otherCosts) {
    if (response == null) {
      return null;
    }
    Map<Integer, LineItem> byCode = index(response.lineItems());
    Map<String, Object> row = new LinkedHashMap<>();

    // Crown Timber Volume (single Volume cell; cross-schedule, BR-04).
    row.put("crownTimberVolume", SectionFormat.decimal(response.schedule3CrownVolume()));

    // Fixed logging line items (straight passthroughs).
    putLine(row, "standingTreeToTruck", byCode.get(CODE_STANDING_TREE_TO_TRUCK));
    putLine(row, "logTransportation", byCode.get(CODE_LOG_TRANSPORTATION));
    putLine(row, "roadManagement", byCode.get(CODE_ROAD_MANAGEMENT));
    putLine(row, "roadConstructionCost", byCode.get(CODE_ROAD_CONSTRUCTION));
    putLine(row, "postLoggingTreatment", byCode.get(CODE_POST_LOGGING_TREATMENT));
    // 143 Forest Management Administration: cost/$-per-m³ are the Schedule-3 pulls (BR-04); volume
    // from the (possibly crown-prefilled) line item.
    putDerived(
        row,
        "forestManagement",
        byCode.get(CODE_FOREST_MGMT_ADMIN),
        SectionFormat.money(response.forestMgmtAdminCost()),
        SectionFormat.decimal(response.forestMgmtAdminPerUnit()));
    putLine(row, "stumpageRoyalty", byCode.get(CODE_STUMPAGE_ROYALTY));
    putLine(row, "depletionAmortization", byCode.get(CODE_DEPLETION_AMORTIZATION));

    // Subtotal Other Costs — the shared volume + derived subtotal / $-per-m³.
    row.put("subtotalOtherCostsVol", SectionFormat.decimal(volumeOf(otherCosts)));
    row.put("subtotalOtherCostsCos", SectionFormat.money(costSubtotalOf(otherCosts)));
    row.put("subtotalOtherCostsCal", SectionFormat.decimal(perUnitOf(otherCosts)));

    // 144 Subtotal Company Logging Costs (no silv.): derived scalar cost/$-per-m³; volume from LI.
    putDerived(
        row,
        "subtotalCompanyCosts",
        byCode.get(CODE_SUBTOTAL_COMPANY_LOGGING),
        SectionFormat.money(response.subtotalCompanyLoggingCost()),
        SectionFormat.decimal(response.subtotalCompanyLoggingPerUnit()));

    // Silviculture block.
    SilvicultureBlock silv = response.silviculture();
    putLine(row, "silvActualSpent", silv == null ? null : silv.actualSpent());
    // 139 Less Silviculture Admin Costs: cost/$-per-m³ are the Schedule-3 pull (BR-04); volume from
    // the silviculture line item.
    putDerived(
        row,
        "silvAdminCost",
        silv == null ? null : silv.lessAdmin(),
        SectionFormat.money(response.lessSilvAdminCost()),
        SectionFormat.decimal(response.lessSilvAdminPerUnit()));
    putLine(row, "silvAccruedSpent", silv == null ? null : silv.accruedLessActual());
    // 140 Total Silviculture: derived scalar; volume from the silviculture total line item.
    putDerived(
        row,
        "silvTotalVolume",
        silv == null ? null : silv.total(),
        SectionFormat.money(response.totalSilvicultureCost()),
        SectionFormat.decimal(response.totalSilviculturePerUnit()));

    // Grand total (Total Company Logging Costs incl. total Silviculture) — no own volume cell.
    row.put("totalVol", "");
    row.put("totalCos", SectionFormat.money(response.totalCompanyLoggingCost()));
    row.put("totalCal", SectionFormat.decimal(response.totalCompanyLoggingPerUnit()));

    row.put("comments", SectionFormat.text(response.comments()));

    // Other Cost List — the nested sub-datasource the jrxml list component iterates.
    row.put("otherCostRows", otherCostRows(otherCosts));

    return new SectionData(List.of(row), Map.of());
  }

  /** Index the fixed line items by their legacy cost-item code for O(1) row lookup. */
  private static Map<Integer, LineItem> index(List<LineItem> lineItems) {
    Map<Integer, LineItem> byCode = new HashMap<>();
    if (lineItems != null) {
      for (LineItem li : lineItems) {
        if (li != null && li.costItemCode() != null) {
          byCode.put(li.costItemCode(), li);
        }
      }
    }
    return byCode;
  }

  /** A straight line item: volume / cost / $-per-m³ from the DTO, or "-" across the board. */
  private static void putLine(Map<String, Object> row, String name, LineItem li) {
    row.put(name + "Vol", SectionFormat.decimal(li == null ? null : li.volume()));
    row.put(name + "Cos", SectionFormat.money(li == null ? null : li.cost()));
    row.put(name + "Cal", SectionFormat.decimal(li == null ? null : li.perUnit()));
  }

  /**
   * A derived/cross-schedule row: cost and $-per-m³ are pre-formatted from the DTO's derived
   * scalars (never recomputed); the volume comes from the corresponding structured line item, or
   * "-".
   */
  private static void putDerived(
      Map<String, Object> row, String name, LineItem volumeSource, String cost, String perUnit) {
    row.put(
        name + "Vol", SectionFormat.decimal(volumeSource == null ? null : volumeSource.volume()));
    row.put(name + "Cos", cost);
    row.put(name + "Cal", perUnit);
  }

  private static List<Map<String, ?>> otherCostRows(OtherCostsDocument doc) {
    List<Map<String, ?>> rows = new ArrayList<>();
    if (doc == null || doc.rows() == null) {
      return rows;
    }
    for (OtherCostRow r : doc.rows()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("otherCostDesc", SectionFormat.text(r.description()));
      // The itemized rows share the section's Other-Costs volume (not a per-row figure), so the
      // per-row Volume cell is left blank; the shared volume shows on the Subtotal Other Costs
      // line.
      m.put("otherCostVol", "");
      m.put("otherCostCos", SectionFormat.money(r.cost()));
      m.put("otherCostCal", SectionFormat.decimal(r.perUnit()));
      rows.add(m);
    }
    return rows;
  }

  private static BigDecimal volumeOf(OtherCostsDocument doc) {
    return doc == null ? null : doc.volume();
  }

  private static Long costSubtotalOf(OtherCostsDocument doc) {
    return doc == null ? null : doc.costSubtotal();
  }

  private static BigDecimal perUnitOf(OtherCostsDocument doc) {
    return doc == null ? null : doc.perUnit();
  }
}
