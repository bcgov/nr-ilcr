package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CodeLists;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule10SectionMapper} (Story 20.4): maps the Schedule 10 read document to
 * the print section datasource (one row per road detail, flattened across construction pages,
 * carrying page/project context), reusing the service's derived values and only formatting +
 * resolving code → label. Covers skip-empty (BR-09), the populated row mapping, TSA-vs-TFL location
 * (BR-05), code-list resolution, the mapper-computed End-Haul/Overland {@code $/m3/km}, null
 * substructures, and multi-page flattening. No Spring context or database.
 */
@DisplayName("Schedule10SectionMapper — read document → print section rows")
class Schedule10SectionMapperTest {

  // ---- Skip-empty (BR-09 / AC-4): no data → null so the orchestrator skips the section ----

  @Test
  @DisplayName("null response → null")
  void nullResponse_isNull() {
    assertThat(Schedule10SectionMapper.map(null)).isNull();
  }

  @Test
  @DisplayName("null pages → null")
  void nullPages_isNull() {
    assertThat(Schedule10SectionMapper.map(response(null))).isNull();
  }

  @Test
  @DisplayName("empty pages → null")
  void emptyPages_isNull() {
    assertThat(Schedule10SectionMapper.map(response(List.of()))).isNull();
  }

  @Test
  @DisplayName("pages present but with no road details → null (no rows to render)")
  void pagesWithNoDetails_isNull() {
    ConstructionPage emptyPage = page("TSA", 0, List.of());
    ConstructionPage nullDetails = page("TSA", 0, null);

    assertThat(Schedule10SectionMapper.map(response(List.of(emptyPage, nullDetails)))).isNull();
  }

  // ---- Populated mapping (AC-2): one row per detail, derived values reused, formatted ----

  @Test
  @DisplayName("a full road detail maps to one row with formatted values + page context")
  void fullDetail_mapsToOneFormattedRow() {
    SectionData section =
        Schedule10SectionMapper.map(response(List.of(page("TSA", 1, List.of(fullDetail(1))))));

    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    Map<String, Object> row = firstRow(section);

    // Construction-page / project context (drives the Jasper group header + footer).
    assertThat(row)
        .containsEntry("pageNumber", 1)
        .containsEntry("rowNumber", 1)
        .containsEntry("roadDetailCount", 1)
        .containsEntry("division", "North Division")
        .containsEntry("period", "2017-06")
        .containsEntry("region", "Cariboo") // code RCB resolved via forestRegions
        .containsEntry("tsaOrTfl", "Cariboo TSA") // TSA07 resolved via tsaNumbers
        .containsEntry("supplyBlock", "Block 1") // SB1 resolved via supplyBlocks
        .containsEntry("tfl", "") // TSA-located: no TFL
        .containsEntry("roadGroup", "Group A");

    // Road information (measures keep stored scale; integers as text).
    assertThat(row)
        .containsEntry("roadName", "Main Line")
        .containsEntry("roadType", "Permanent")
        .containsEntry("biogeoVariant", "ICHdw1")
        .containsEntry("rsmsClass", "3 - Class 3")
        .containsEntry("sideSlope", "30 %")
        .containsEntry("matSolidRock", "40 %")
        .containsEntry("matTotal", "95 %");

    // Sub-grade: BigDecimal costs render grouped, no decimals; $/km at two decimals.
    assertThat(row)
        .containsEntry("sgLength", "4.5 km")
        .containsEntry("sgActualCost", "25,000")
        .containsEntry("sgTotal", "20,000")
        .containsEntry("sgCostPerLength", "1,234.50");

    // Additional stabilizing.
    assertThat(row)
        .containsEntry("stCode", "Continuous")
        .containsEntry("stLength", "2.0 km")
        .containsEntry("stType", "Gravel Material")
        .containsEntry("stDistanceToSource", "1.5 km")
        .containsEntry("stCostPerLength", "500.00");

    // Include-detail-engineering flag + comments.
    assertThat(row)
        .containsEntry("includeDetailEng", "Y")
        .containsEntry("comments", "See survey notes");
  }

  @Test
  @DisplayName("End-Haul / Overland $/m3/km is computed in the mapper (cost / (volume x distance))")
  void endHaulOverlandPerUnit_isComputed() {
    // Legacy-verified: lessEndHaul 1500 / (vol 1500 x dist 10) = 0.10;
    //                  lessOverland 2500 / (vol 500 x dist 20) = 0.25.
    Map<String, Object> row =
        firstRow(
            Schedule10SectionMapper.map(response(List.of(page("TSA", 1, List.of(fullDetail(1)))))));

    assertThat(row)
        .containsEntry("endHaulPerUnit", "0.10")
        .containsEntry("overlandPerUnit", "0.25");
  }

  @Test
  @DisplayName("$/m3/km is dashed when volume or distance is zero (divide-by-zero guard)")
  void perUnit_dashedOnZeroDenominator() {
    RoadDetail zeroHaul =
        detail(
            1,
            fullSubGrade(),
            fullStabilizing(),
            fullMaterial(),
            BigDecimal.ZERO, // endHaulDistance
            BigDecimal.ZERO, // endHaulVolume
            BigDecimal.ZERO, // overlandDistance
            BigDecimal.ZERO, // overlandVolume
            "See survey notes");

    Map<String, Object> row =
        firstRow(Schedule10SectionMapper.map(response(List.of(page("TSA", 1, List.of(zeroHaul))))));

    assertThat(row).containsEntry("endHaulPerUnit", "-").containsEntry("overlandPerUnit", "-");
  }

  @Test
  @DisplayName("null substructures render dashes, not zeros, and no NPE")
  void nullSubstructures_renderDashes() {
    RoadDetail bare =
        new RoadDetail(
            10, 1, "Road #1", "Spur 4", null, null, null, null, null, null, null, null, null, null,
            null, null, null, 0);

    Map<String, Object> row =
        firstRow(Schedule10SectionMapper.map(response(List.of(page("TSA", 1, List.of(bare))))));

    assertThat(row)
        .containsEntry("roadName", "Spur 4")
        .containsEntry("roadType", "-")
        .containsEntry("sgActualCost", "-")
        .containsEntry("sgCostPerLength", "-")
        .containsEntry("stCode", "-")
        .containsEntry("matTotal", "-")
        .containsEntry("endHaulPerUnit", "-")
        .containsEntry("comments", "-");
  }

  // ---- TSA-vs-TFL location (BR-05) ----

  @Test
  @DisplayName(
      "a TFL-located page shows the TFL sentinel, blanks supply block, and carries the TFL")
  void tflLocatedPage_usesSentinel() {
    Map<String, Object> row =
        firstRow(
            Schedule10SectionMapper.map(response(List.of(page("TFL", 1, List.of(fullDetail(1)))))));

    assertThat(row)
        .containsEntry("tsaOrTfl", "Tree Farm Licensee")
        .containsEntry("supplyBlock", "")
        .containsEntry("tfl", "TFL52");
  }

  // ---- Code resolution: falls back to the raw code, tolerates a null code list ----

  @Test
  @DisplayName("an unknown region code falls back to the raw code (never dashed)")
  void unknownCode_fallsBackToRawCode() {
    ConstructionPage page =
        new ConstructionPage(
            100,
            1,
            "label",
            "ZZZ",
            "TSA07",
            "SB1",
            null,
            "Group A",
            "North Division",
            "2017-06",
            1,
            0,
            List.of(fullDetail(1)));

    Map<String, Object> row = firstRow(Schedule10SectionMapper.map(response(List.of(page))));

    assertThat(row).containsEntry("region", "ZZZ");
  }

  @Test
  @DisplayName("a null code-lists document is tolerated (codes pass through)")
  void nullCodeLists_tolerated() {
    Schedule10Response response =
        new Schedule10Response(
            514, 2017, "1", false, List.of(page("TSA", 1, List.of(fullDetail(1)))), null, null);

    Map<String, Object> row = firstRow(Schedule10SectionMapper.map(response));

    assertThat(row).containsEntry("region", "RCB").containsEntry("tsaOrTfl", "TSA07");
  }

  // ---- Flattening: one row per detail across pages, in order ----

  @Test
  @DisplayName("details across multiple pages flatten to one row each, in page/detail order")
  void multiplePages_flattenInOrder() {
    ConstructionPage page1 = page("TSA", 2, List.of(fullDetail(1), fullDetail(2)));
    ConstructionPage page2 = pageNumbered("TSA", 1, 2, List.of(fullDetail(1)));

    List<Map<String, ?>> rows = Schedule10SectionMapper.map(response(List.of(page1, page2))).rows();

    assertThat(rows).hasSize(3);
    assertThat(rows.get(0).get("pageNumber")).isEqualTo(1);
    assertThat(rows.get(0).get("rowNumber")).isEqualTo(1);
    assertThat(rows.get(1).get("pageNumber")).isEqualTo(1);
    assertThat(rows.get(1).get("rowNumber")).isEqualTo(2);
    assertThat(rows.get(2).get("pageNumber")).isEqualTo(2);
    assertThat(rows.get(2).get("rowNumber")).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  /**
   * The section's rows are built by the mapper as {@code LinkedHashMap<String, Object>}; the record
   * exposes them as {@code Map<String, ?>}, so this narrows the first row for value assertions.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRow(SectionData section) {
    return (Map<String, Object>) section.rows().get(0);
  }

  private static Schedule10Response response(List<ConstructionPage> pages) {
    return new Schedule10Response(514, 2017, "1", false, pages, codeLists(), null);
  }

  private static Schedule10CodeLists codeLists() {
    return new Schedule10CodeLists(
        List.of(new CodeDescriptionDto("RCB", "Cariboo")),
        List.of(new CodeDescriptionDto("TSA07", "Cariboo TSA")),
        List.of(new CodeDescriptionDto("SB1", "Block 1")),
        List.of(new CodeDescriptionDto("P", "Permanent")),
        List.of(new CodeDescriptionDto("C", "Continuous")),
        List.of(new CodeDescriptionDto("GRAVEL", "Gravel Material")),
        List.of(new CodeDescriptionDto("3", "Class 3")),
        List.of());
  }

  /** A page located by TSA or TFL, at page number 1. */
  private static ConstructionPage page(String location, int detailCount, List<RoadDetail> details) {
    return pageNumbered(location, detailCount, 1, details);
  }

  private static ConstructionPage pageNumbered(
      String location, int detailCount, int pageNumber, List<RoadDetail> details) {
    boolean tfl = "TFL".equals(location);
    return new ConstructionPage(
        100 + pageNumber,
        pageNumber,
        "label",
        "RCB",
        tfl ? null : "TSA07",
        tfl ? null : "SB1",
        tfl ? "TFL52" : null,
        "Group A",
        "North Division",
        "2017-06",
        detailCount,
        0,
        details);
  }

  private static RoadDetail fullDetail(int rowNumber) {
    return detail(
        rowNumber,
        fullSubGrade(),
        fullStabilizing(),
        fullMaterial(),
        new BigDecimal("10"), // endHaulDistance
        new BigDecimal("1500"), // endHaulVolume
        new BigDecimal("20"), // overlandDistance
        new BigDecimal("500"), // overlandVolume
        "See survey notes");
  }

  private static RoadDetail detail(
      int rowNumber,
      SubGrade subGrade,
      Stabilizing stabilizing,
      MaterialComposition material,
      BigDecimal endHaulDistance,
      BigDecimal endHaulVolume,
      BigDecimal overlandDistance,
      BigDecimal overlandVolume,
      String comments) {
    return new RoadDetail(
        1000 + rowNumber,
        rowNumber,
        "Road #" + rowNumber,
        "Main Line",
        "P",
        new BecClassification(7, "ICH", "dw", "1", null, "ICHdw1"),
        "3",
        30,
        subGrade,
        stabilizing,
        material,
        "Y",
        endHaulDistance,
        endHaulVolume,
        overlandDistance,
        overlandVolume,
        comments,
        0);
  }

  private static SubGrade fullSubGrade() {
    return new SubGrade(
        new BigDecimal("4.5"), // length
        new BigDecimal("6.0"), // surfaceWidth
        new BigDecimal("25000"), // actualCost
        new BigDecimal("1000"), // ttTransfer
        new BigDecimal("500"), // otherTransfer
        new BigDecimal("2000"), // lessBridges
        new BigDecimal("1000"), // lessCulverts
        new BigDecimal("500"), // lessLandings
        new BigDecimal("2500"), // lessOverland
        new BigDecimal("1000"), // lessOtherEng
        new BigDecimal("1500"), // lessEndHaul
        new BigDecimal("26500"), // totalCosts
        new BigDecimal("8500"), // totalDeductions
        new BigDecimal("20000"), // total
        new BigDecimal("1234.5")); // costPerLength
  }

  private static Stabilizing fullStabilizing() {
    return new Stabilizing(
        "C",
        "GRAVEL",
        new BigDecimal("2.0"),
        new BigDecimal("6.0"),
        new BigDecimal("0.3"),
        new BigDecimal("1.5"),
        new BigDecimal("3000"),
        new BigDecimal("0"),
        new BigDecimal("0"),
        new BigDecimal("3000"),
        new BigDecimal("500"));
  }

  private static MaterialComposition fullMaterial() {
    return new MaterialComposition(40, 25, 15, 10, 5, 95);
  }
}
