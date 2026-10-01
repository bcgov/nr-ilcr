package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CodeLists;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 10 (New Road Construction Costs) read document to the print section datasource:
 * ONE detail row per road detail (flattened across construction pages), each carrying BOTH the road
 * detail's own fields AND its construction-page/project context (division, period, region,
 * TSA-or-TFL, supply block, TFL, road group) plus the page/road ordinals — so {@code
 * schedule10.jrxml} can group by construction page (printing the project header once) and
 * page-break per road detail, and the footer can read {@code Page {pageNumber} - Road {rowNumber}
 * of {roadDetailCount}} (the legacy form layout). The service ({@code Schedule10Service}) has
 * already derived the road group, the sub-grade / stabilizing totals and $/km, and the material
 * percentages — this mapper only formats and resolves code → label for the header.
 *
 * <p>Two deliberate deviations from the legacy report, both following the rebuilt Schedule 10 model
 * (Story 20.4 decisions):
 *
 * <ul>
 *   <li>The LD-1/LD-2/LD-3 fields the legacy report showed — Moisture/ASM Code, Soil Moisture Code,
 *       and Boulder Area % — are OMITTED: the rebuilt Schedule 10 removed them by business
 *       direction and does not expose them on read, so the print does not show them either.
 *   <li>End Haul / Overland {@code $/m3/km} is not carried by the read DTO, so it is computed here
 *       for display: {@code lessEndHaul / (volume × distance)} (and the overland pair), dashed when
 *       the volume or distance is absent/zero.
 * </ul>
 */
final class Schedule10SectionMapper {

  /** The sentinel the frontend/legacy shows in the "TSA or TFL" field for a TFL-located page. */
  private static final String TFL_SENTINEL = "Tree Farm Licensee";

  private Schedule10SectionMapper() {}

  static SectionData map(Schedule10Response response) {
    if (response == null || response.pages() == null || response.pages().isEmpty()) {
      return null;
    }
    Schedule10CodeLists codeLists = response.codeLists();
    List<Map<String, ?>> rows = new ArrayList<>();
    for (ConstructionPage page : response.pages()) {
      List<RoadDetail> details = page.roadDetails() == null ? List.of() : page.roadDetails();
      for (RoadDetail detail : details) {
        rows.add(toRow(page, detail, codeLists));
      }
    }
    if (rows.isEmpty()) {
      return null;
    }
    return new SectionData(rows, new HashMap<>());
  }

  private static Map<String, ?> toRow(
      ConstructionPage page, RoadDetail detail, Schedule10CodeLists codeLists) {
    boolean tflLocated = page.tflNumberCode() != null && !page.tflNumberCode().isBlank();
    Map<String, Object> row = new LinkedHashMap<>();

    // Construction-page / project context (drives the Jasper group header + the page/road footer).
    row.put("pageNumber", page.pageNumber());
    row.put("rowNumber", detail.rowNumber());
    row.put("roadDetailCount", page.roadDetailCount());
    row.put("division", SectionFormat.text(page.divisionName()));
    row.put("period", SectionFormat.text(page.constructionPeriod()));
    row.put(
        "region",
        describe(codeLists == null ? null : codeLists.forestRegions(), page.forestRegionCode()));
    row.put(
        "tsaOrTfl",
        tflLocated
            ? TFL_SENTINEL
            : describe(codeLists == null ? null : codeLists.tsaNumbers(), page.tsaNumber()));
    row.put(
        "supplyBlock",
        tflLocated
            ? ""
            : describe(codeLists == null ? null : codeLists.supplyBlocks(), page.tsbNumberCode()));
    row.put("tfl", tflLocated ? SectionFormat.text(page.tflNumberCode()) : "");
    row.put("roadGroup", page.roadGroup() == null ? "" : page.roadGroup());

    // Road information.
    row.put("roadName", SectionFormat.text(detail.roadName()));
    row.put(
        "roadType",
        describe(codeLists == null ? null : codeLists.roadLifetimes(), detail.roadLifetimeCode()));
    row.put(
        "biogeoVariant",
        SectionFormat.text(
            detail.becClassification() == null ? null : detail.becClassification().label()));
    row.put(
        "rsmsClass",
        describeRsmr(
            codeLists == null ? null : codeLists.rsmrClasses(), detail.relSoilMoistRgmClsCode()));
    row.put("sideSlope", SectionFormat.percent(detail.sideSlopePct()));

    // Material composition percentages.
    MaterialComposition material = detail.materialComposition();
    row.put(
        "matSolidRock", SectionFormat.percent(material == null ? null : material.solidRockPct()));
    row.put(
        "matRippableRock",
        SectionFormat.percent(material == null ? null : material.rippableRockPct()));
    row.put("matCoarse", SectionFormat.percent(material == null ? null : material.coarsePct()));
    row.put("matFine", SectionFormat.percent(material == null ? null : material.finePct()));
    row.put("matOrganic", SectionFormat.percent(material == null ? null : material.organicPct()));
    row.put("matTotal", SectionFormat.percent(material == null ? null : material.totalPct()));

    // Sub-grade.
    SubGrade sg = detail.subGrade();
    row.put("sgLength", SectionFormat.measure(sg == null ? null : sg.length(), "km"));
    row.put("sgSurfaceWidth", SectionFormat.measure(sg == null ? null : sg.surfaceWidth(), "m"));
    row.put("sgActualCost", SectionFormat.money(sg == null ? null : sg.actualCost()));
    row.put("sgTtTransfer", SectionFormat.money(sg == null ? null : sg.ttTransfer()));
    row.put("sgOtherTransfer", SectionFormat.money(sg == null ? null : sg.otherTransfer()));
    row.put("sgTotalCosts", SectionFormat.money(sg == null ? null : sg.totalCosts()));
    row.put("sgLessBridges", SectionFormat.money(sg == null ? null : sg.lessBridges()));
    row.put("sgLessCulverts", SectionFormat.money(sg == null ? null : sg.lessCulverts()));
    row.put("sgLessLandings", SectionFormat.money(sg == null ? null : sg.lessLandings()));
    row.put("sgLessEndHaul", SectionFormat.money(sg == null ? null : sg.lessEndHaul()));
    row.put("sgLessOverland", SectionFormat.money(sg == null ? null : sg.lessOverland()));
    row.put("sgLessOtherEng", SectionFormat.money(sg == null ? null : sg.lessOtherEng()));
    row.put("sgTotal", SectionFormat.money(sg == null ? null : sg.total()));
    row.put("sgCostPerLength", SectionFormat.decimal(sg == null ? null : sg.costPerLength()));

    // Additional stabilizing.
    Stabilizing st = detail.stabilizing();
    row.put(
        "stCode",
        describe(
            codeLists == null ? null : codeLists.ballastMethods(),
            st == null ? null : st.ballastMethodCode()));
    row.put("stLength", SectionFormat.measure(st == null ? null : st.length(), "km"));
    row.put("stSurfaceWidth", SectionFormat.measure(st == null ? null : st.surfaceWidth(), "m"));
    row.put(
        "stType",
        describe(
            codeLists == null ? null : codeLists.ballastMaterials(),
            st == null ? null : st.ballastMaterialCode()));
    row.put("stDepth", SectionFormat.measure(st == null ? null : st.depth(), "m"));
    row.put(
        "stDistanceToSource",
        SectionFormat.measure(st == null ? null : st.distanceToSource(), "km"));
    row.put("stActualCost", SectionFormat.money(st == null ? null : st.actualCost()));
    row.put("stTtTransfer", SectionFormat.money(st == null ? null : st.ttTransfer()));
    row.put("stOtherTransfer", SectionFormat.money(st == null ? null : st.otherTransfer()));
    row.put("stTotal", SectionFormat.money(st == null ? null : st.total()));
    row.put("stCostPerLength", SectionFormat.decimal(st == null ? null : st.costPerLength()));

    // Include-detail-engineering flag + end-haul / overland detail table.
    row.put("includeDetailEng", SectionFormat.text(detail.detailedEngineeringCostInd()));
    row.put("endHaulDistance", SectionFormat.plain(detail.endHaulDistance()));
    row.put("endHaulVolume", SectionFormat.plain(detail.endHaulVolume()));
    row.put(
        "endHaulPerUnit",
        SectionFormat.decimal(
            perM3PerKm(
                sg == null ? null : sg.lessEndHaul(),
                detail.endHaulVolume(),
                detail.endHaulDistance())));
    row.put("overlandDistance", SectionFormat.plain(detail.overlandDistance()));
    row.put("overlandVolume", SectionFormat.plain(detail.overlandVolume()));
    row.put(
        "overlandPerUnit",
        SectionFormat.decimal(
            perM3PerKm(
                sg == null ? null : sg.lessOverland(),
                detail.overlandVolume(),
                detail.overlandDistance())));

    row.put("comments", SectionFormat.text(detail.comments()));
    return row;
  }

  /**
   * The legacy {@code $/m3/km} for an end-haul / overland row: the deducted cost spread over the
   * hauled volume-kilometres, {@code cost / (volume × distance)} at two decimals. Returns {@code
   * null} (rendered as a dash) when the cost, volume, or distance is absent or the denominator is
   * zero — so a road with no end-haul / overland leaves the column dashed, matching the legacy
   * report.
   */
  private static BigDecimal perM3PerKm(BigDecimal cost, BigDecimal volume, BigDecimal distance) {
    if (cost == null || volume == null || distance == null) {
      return null;
    }
    BigDecimal denominator = volume.multiply(distance);
    if (denominator.signum() <= 0) {
      return null;
    }
    return cost.divide(denominator, 2, RoundingMode.HALF_UP);
  }

  /** Resolve an RSMR class code to `{code} - {desc}` via the code list. */
  private static String describeRsmr(List<CodeDescriptionDto> options, String code) {
    if (code == null || code.isBlank()) {
      return "-";
    }
    if (options != null) {
      for (CodeDescriptionDto option : options) {
        if (code.equals(option.code())) {
          return code + " - " + option.description();
        }
      }
    }
    return code;
  }

  /**
   * Resolve a code to its description via the code list, falling back to the raw code (never
   * dashed).
   */
  private static String describe(List<CodeDescriptionDto> options, String code) {
    if (code == null || code.isBlank()) {
      return "-";
    }
    if (options != null) {
      for (CodeDescriptionDto option : options) {
        if (code.equals(option.code())) {
          return option.description();
        }
      }
    }
    return code;
  }
}
