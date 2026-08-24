package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule1.dto.SilvicultureBlock;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule1SectionMapper} (Story 20.5): maps the Schedule 1 statement + the
 * itemized Other-Cost-List document into ONE section row (format-only), with the itemized rows
 * carried as the nested {@code otherCostRows} collection the jrxml list component iterates. Covers
 * the null response, the full statement mapping (fixed line items, the cross-schedule 143/139/144
 * rows sourced from the derived scalars, Subtotal Other Costs, grand total), absent-line-item
 * dashing, and the nested Other-Cost-List rows. No Spring context or database.
 */
@DisplayName("Schedule1SectionMapper — statement + Other-Cost-List → one print section row")
class Schedule1SectionMapperTest {

  @Test
  @DisplayName("null response → null")
  void nullResponse_isNull() {
    assertThat(Schedule1SectionMapper.map(null, otherCostsDoc())).isNull();
  }

  @Test
  @DisplayName("a full document maps to one row: fixed lines, cross-schedule rows, totals, list")
  void fullDocument_mapsToOneRow() {
    SectionData section = Schedule1SectionMapper.map(fullResponse(), otherCostsDoc());

    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    Map<String, Object> row = firstRow(section);

    // Crown Timber Volume (single cell, cross-schedule) + a couple of fixed line items.
    assertThat(row)
        .containsEntry("crownTimberVolume", "12,345.00")
        .containsEntry("standingTreeToTruckVol", "1,000.50")
        .containsEntry("standingTreeToTruckCos", "500,000")
        .containsEntry("standingTreeToTruckCal", "12.34")
        .containsEntry("depletionAmortizationCos", "9,000");

    // 143 Forest Mgmt Admin: cost/$-per-m³ from the DERIVED SCALARS (BR-04 Schedule-3 pull), volume
    // from the line item.
    assertThat(row)
        .containsEntry("forestManagementVol", "12,345.00")
        .containsEntry("forestManagementCos", "25,000")
        .containsEntry("forestManagementCal", "2.03");

    // Subtotal Other Costs from the Other-Costs document; 144 + silviculture-total + grand total
    // from
    // the derived scalars.
    assertThat(row)
        .containsEntry("subtotalOtherCostsCos", "3,000")
        .containsEntry("subtotalCompanyCostsCos", "537,000")
        .containsEntry("silvAdminCostCos", "1,500") // 139 (Schedule-3 pull)
        .containsEntry("silvTotalVolumeCos", "40,000") // 140 (derived)
        .containsEntry("totalCos", "577,000") // grand total (derived)
        .containsEntry("totalVol", ""); // grand total has no own volume cell

    assertThat(row).containsEntry("comments", "See the notes");

    // Other Cost List — the nested collection the jrxml list iterates.
    assertThat(row).containsKey("otherCostRows");
    @SuppressWarnings("unchecked")
    List<Map<String, ?>> otherCostRows = (List<Map<String, ?>>) row.get("otherCostRows");
    assertThat(otherCostRows).hasSize(2);
    Map<String, ?> firstOther = otherCostRows.get(0);
    assertThat(firstOther.get("otherCostDesc")).isEqualTo("Aerial survey");
    assertThat(firstOther.get("otherCostCos")).isEqualTo("1,200");
    assertThat(firstOther.get("otherCostCal")).isEqualTo("0.10");
    assertThat(firstOther.get("otherCostVol")).isEqualTo(""); // per-row volume shared → blank here
  }

  @Test
  @DisplayName("an absent line item dashes its cells, but a derived scalar still renders its cost")
  void absentLineItem_dashesVolumeButDerivedCostRenders() {
    // No line items at all + no silviculture, but the derived Forest-Mgmt-Admin scalar is present:
    // the 143 cost renders from the scalar while its volume dashes (no line item to source it).
    Schedule1Response response =
        new Schedule1Response(
            514,
            2021,
            "D",
            false,
            null,
            null,
            0,
            null,
            List.of(), // no line items
            null, // no silviculture
            25_000L, // forestMgmtAdminCost (Schedule-3 pull)
            null,
            null,
            new BigDecimal("2.03"), // forestMgmtAdminPerUnit
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null);

    Map<String, Object> row = firstRow(Schedule1SectionMapper.map(response, otherCostsDoc()));

    assertThat(row)
        .containsEntry("forestManagementVol", "-")
        .containsEntry("forestManagementCos", "25,000")
        .containsEntry("forestManagementCal", "2.03")
        .containsEntry("standingTreeToTruckCos", "-") // absent line item → dashes
        .containsEntry("silvActualSpentCos", "-"); // absent silviculture block → dashes
  }

  @Test
  @DisplayName("an empty Other-Cost-List document yields an empty nested collection")
  void emptyOtherCostList_yieldsEmptyCollection() {
    OtherCostsDocument empty = new OtherCostsDocument(null, null, null, 0, List.of(), false, null);

    Map<String, Object> row = firstRow(Schedule1SectionMapper.map(fullResponse(), empty));

    @SuppressWarnings("unchecked")
    List<Map<String, ?>> otherCostRows = (List<Map<String, ?>>) row.get("otherCostRows");
    assertThat(otherCostRows).isEmpty();
    assertThat(row).containsEntry("subtotalOtherCostsCos", "-"); // no subtotal on an empty doc
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRow(SectionData section) {
    return (Map<String, Object>) section.rows().get(0);
  }

  private static LineItem li(int code, String vol, Integer cost, String perUnit) {
    return new LineItem(
        code,
        vol == null ? null : new BigDecimal(vol),
        cost,
        perUnit == null ? null : new BigDecimal(perUnit));
  }

  private static Schedule1Response fullResponse() {
    List<LineItem> lineItems =
        List.of(
            li(12, "1000.5", 500_000, "12.34"),
            li(13, "1000.5", 8_000, "0.80"),
            li(14, "1000.5", 7_000, "0.70"),
            li(15, "1000.5", 6_000, "0.60"),
            li(16, "1000.5", 5_000, "0.50"),
            li(143, "12345", 25_000, "2.03"), // volume source for the 143 row
            li(17, "1000.5", 10_000, "1.00"),
            li(18, "1000.5", 9_000, "0.90"),
            li(144, "12345", 537_000, "43.50")); // volume source for the 144 row
    SilvicultureBlock silv =
        new SilvicultureBlock(
            li(1, "12345", 41_500, "3.36"),
            li(2, "12345", 0, "0.00"),
            li(139, "12345", 1_500, "0.12"),
            li(140, "12345", 40_000, "3.24"));
    return new Schedule1Response(
        621,
        2021,
        "D",
        false,
        12_345,
        new BigDecimal("12345"), // schedule3CrownVolume
        0,
        "See the notes",
        lineItems,
        silv,
        25_000L, // forestMgmtAdminCost (143 cost — Schedule 3 pull)
        1_500, // lessSilvAdminCost (139 cost — Schedule 3 pull)
        null, // otherCosts summary — unused by the mapper (uses the OtherCostsDocument)
        new BigDecimal("2.03"), // forestMgmtAdminPerUnit
        new BigDecimal("0.12"), // lessSilvAdminPerUnit
        40_000L, // totalSilvicultureCost (140)
        new BigDecimal("3.24"), // totalSilviculturePerUnit
        537_000L, // subtotalCompanyLoggingCost (144)
        new BigDecimal("43.50"), // subtotalCompanyLoggingPerUnit
        577_000L, // totalCompanyLoggingCost (grand total)
        new BigDecimal("46.74"), // totalCompanyLoggingPerUnit
        List.of(),
        null);
  }

  private static OtherCostsDocument otherCostsDoc() {
    return new OtherCostsDocument(
        new BigDecimal("12345"),
        3_000L, // costSubtotal → Subtotal Other Costs line
        new BigDecimal("0.24"),
        2,
        List.of(
            new OtherCostRow(1, "Aerial survey", 1_200, new BigDecimal("0.10")),
            new OtherCostRow(2, "Consulting", 1_800, new BigDecimal("0.14"))),
        false,
        null);
  }
}
