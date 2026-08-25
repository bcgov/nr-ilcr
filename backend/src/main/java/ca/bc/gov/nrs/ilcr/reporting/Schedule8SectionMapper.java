package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 8 (Tree to Truck Costs) read model to the print section datasource. Schedule 8
 * is the only THREE-level print section: each {@link Page} carries its {@link Sample}s, and each
 * sample carries its additions/deductions ({@link RateRow}s). The fill is still the flat {@code
 * fillBean} path — the nesting is expressed entirely in the template: the top level is one section
 * row per page, and each deeper level is a {@code java.util.Collection} field the jrxml's nested
 * {@code <list>} components iterate with {@code new JRMapCollectionDataSource($F{...})} (the same
 * Collection-of-Map mechanism Schedule 1/3 use for their single nested list, extended one level
 * deeper). So the mapper emits maps-of-maps-of-maps and no engine change is needed.
 *
 * <p>Format-only, reusing the server-computed figures verbatim (AC-2, the 20.2 "no re-ported
 * arithmetic" principle): {@code percentTotal} (the six-way skidding "100% rule" sum), {@code
 * actualHarvested}, {@code additionsTotal}/{@code deductionsTotal}/{@code finalRate}, and the
 * add/deduct split (already resolved into the DTO's two lists by cost-item subcategory) are
 * consumed as-is — the mapper never recomputes any of them. Values arrive at the template
 * PRE-FORMATTED as Strings ({@code "-"} for null), and the per-sample {@code
 * displayAddRates}/{@code displayDedRates} booleans gate the additions/deductions sub-blocks so an
 * empty list leaks no stray heading (AC-1).
 *
 * <p>Deliberate scope decisions, grounded in the legacy report (Story 20.8 CONFIRMED PRINT SPEC)
 * and the FROZEN Schedule 8 read model:
 *
 * <ul>
 *   <li>The rate-detail table renders the FOUR columns the frozen {@link RateRow} exposes — Item
 *       ({@code costItemCode}), Description ({@code itemDescription}), Unit Cost ({@code
 *       costingRate}, 2-dp), Cost Type ({@code costTypeDescription}). The legacy 5th "Cost
 *       Description" write-in ({@code otherCostTypeDesc}) has no field in the read DTO and is
 *       descoped rather than widen the frozen Schedule 8 contract.
 *   <li>{@code costingRate} formats uniformly at two decimals for both lists (the legacy deductions
 *       table's numeric pattern was inert on a String-bound field).
 *   <li>Haul direction / dump destination render from the DTO's booleans as {@code Uphill}/{@code
 *       Downhill} and {@code Water}/{@code Land} (best-effort labels for the legacy Y/N
 *       indicators). Because the frozen read DTO exposes these as primitive {@code boolean}, a
 *       not-entered indicator ({@code UPHILL_DIRECTION_IND}/{@code WATER_DUMP_DESTINATION_IND} null
 *       in the DB) is knowingly collapsed to the {@code false} label ({@code Downhill}/{@code
 *       Land}) rather than the {@code "-"} every other absent field uses — a deliberate consequence
 *       of the frozen contract, not a value the mapper can distinguish. The delivery-DB visual
 *       check should confirm a sample with the indicator unset against the legacy print.
 * </ul>
 */
final class Schedule8SectionMapper {

  private Schedule8SectionMapper() {}

  /**
   * Shape the Schedule 8 response into one section row per report page, or {@code null} when the
   * mill/year has no category-8 pages (the BR-09 skip-empty signal). A page with no samples still
   * emits its row (its descriptors print); a sample with no additions and/or deductions emits the
   * corresponding empty collection and the {@code displayAddRates}/{@code displayDedRates} gate
   * suppresses that sub-block.
   */
  static SectionData map(Schedule8Response response) {
    if (response == null || response.pages() == null || response.pages().isEmpty()) {
      return null;
    }
    List<Map<String, ?>> pageRows = new ArrayList<>();
    for (Page page : response.pages()) {
      if (page == null) {
        continue;
      }
      pageRows.add(pageRow(page));
    }
    if (pageRows.isEmpty()) {
      return null;
    }
    return new SectionData(pageRows, Map.of());
  }

  /**
   * One page row: descriptors (code labels preferred) + comments + the nested samples collection.
   */
  private static Map<String, Object> pageRow(Page page) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("pageDivision", SectionFormat.text(page.division()));
    row.put("pageLicense", SectionFormat.text(page.license()));
    row.put("pageContact", SectionFormat.text(page.contact()));
    row.put("pagePhone", SectionFormat.text(page.phone()));
    row.put("pageCuttingPermit", SectionFormat.text(page.cuttingPermit()));
    row.put("pageSupportCentre", labelOrCode(page.supportCentreLabel(), page.supportCentre()));
    row.put("pageRegion", labelOrCode(page.regionLabel(), page.region()));
    row.put("pageBecZone", labelOrCode(page.becZoneLabel(), page.becZone()));
    row.put("pageTsa", labelOrCode(page.tsaNumberLabel(), page.tsaNumber()));
    row.put("pageTfl", labelOrCode(page.tflNumberLabel(), page.tflNumber()));
    row.put("pageSupplyBlock", labelOrCode(page.supplyBlockLabel(), page.supplyBlock()));
    row.put("pageComments", SectionFormat.text(page.comments()));
    row.put("samples", sampleRows(page.samples()));
    return row;
  }

  /** The samples nested collection (one map per sample), or an empty list for a page with none. */
  private static List<Map<String, ?>> sampleRows(List<Sample> samples) {
    List<Map<String, ?>> rows = new ArrayList<>();
    if (samples == null) {
      return rows;
    }
    for (Sample sample : samples) {
      if (sample == null) {
        continue;
      }
      rows.add(sampleRow(sample));
    }
    return rows;
  }

  /**
   * One sample row: identity, skidding %s, supports, haul, volumes, unit-cost roll-ups, rate lists.
   */
  private static Map<String, Object> sampleRow(Sample s) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("sampleContractId", SectionFormat.text(s.contractId()));
    row.put("sampleCutBlock", SectionFormat.text(s.cutBlock()));

    // Six skidding percentages + the server-computed total (the "100% rule" sum — never
    // recomputed).
    row.put("pctGroundBase", SectionFormat.percent(s.groundBasePct()));
    row.put("pctGrapple", SectionFormat.percent(s.grapplePct()));
    row.put("pctSkyline", SectionFormat.percent(s.skylinePct()));
    row.put("pctHighlead", SectionFormat.percent(s.highleadPct()));
    row.put("pctHelicopter", SectionFormat.percent(s.helicopterPct()));
    row.put("pctOther", SectionFormat.percent(s.otherSkiddingPct()));
    row.put("pctTotal", SectionFormat.percent(s.percentTotal()));

    // Skyline supports — pre-formatted as "-" when absent (SectionFormat never yields null), so the
    // template renders a dash in place, like every other optional field, rather than collapsing.
    row.put("slopeDistance", SectionFormat.integer(s.skylineSlopeDistance()));
    row.put("supportNumber", SectionFormat.integer(s.skylineSupportNumber()));
    row.put("supportAvgDist", SectionFormat.measure(s.supportAvgDistance(), "m"));

    // Haul.
    row.put("haulDistance", SectionFormat.measure(s.distance(), "km"));
    row.put("cycleTime", SectionFormat.measure(s.cycleTime(), "min"));
    row.put("direction", s.uphillDirection() ? "Uphill" : "Downhill");
    row.put("destination", s.waterDumpDestination() ? "Water" : "Land");
    row.put("skidType", labelOrCode(s.skidTypeDescription(), s.skidTypeCode()));

    // Harvested volumes (m³) — actualHarvested is the server sum, never recomputed.
    row.put("volConiferous", SectionFormat.integer(s.coniferousVolume()));
    row.put("volDeciduous", SectionFormat.integer(s.deciduousVolume()));
    row.put("volActualHarvested", SectionFormat.integer(s.actualHarvested()));

    // Unit costs ($/m³) — the roll-ups are all server-computed (finalRate = original + add −
    // deduct).
    row.put("rateOriginal", SectionFormat.decimal(s.originalRate()));
    row.put("rateAdditions", SectionFormat.decimal(s.additionsTotal()));
    row.put("rateDeductions", SectionFormat.decimal(s.deductionsTotal()));
    row.put("rateFinal", SectionFormat.decimal(s.finalRate()));

    List<Map<String, ?>> additions = rateRows(s.additions());
    List<Map<String, ?>> deductions = rateRows(s.deductions());
    row.put("additions", additions);
    row.put("deductions", deductions);
    // Gate booleans: suppress a sub-block (and its heading) when its list is empty (AC-1).
    row.put("displayAddRates", !additions.isEmpty());
    row.put("displayDedRates", !deductions.isEmpty());
    return row;
  }

  /** A rate-detail collection (additions or deductions), format-only, four columns. */
  private static List<Map<String, ?>> rateRows(List<RateRow> rateRows) {
    List<Map<String, ?>> rows = new ArrayList<>();
    if (rateRows == null) {
      return rows;
    }
    for (RateRow rr : rateRows) {
      if (rr == null) {
        continue;
      }
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("rateItem", SectionFormat.integer(rr.costItemCode()));
      r.put("rateDesc", SectionFormat.text(rr.itemDescription()));
      r.put("rateUnitCost", SectionFormat.decimal(rr.costingRate()));
      r.put("rateCostType", SectionFormat.text(rr.costTypeDescription()));
      rows.add(r);
    }
    return rows;
  }

  /**
   * Prefer the resolved code label; fall back to the raw code; {@code "-"} when both are absent.
   */
  private static String labelOrCode(String label, String code) {
    if (label != null && !label.isBlank()) {
      return label;
    }
    return SectionFormat.text(code);
  }
}
