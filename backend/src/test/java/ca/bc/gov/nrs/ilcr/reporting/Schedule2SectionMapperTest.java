package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule2.dto.CostBlock;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule2SectionMapper} (Story 20.6): maps the Schedule 2 aggregate document
 * to the print section datasource — exactly ONE row holding the seven fixed cost blocks, each as a
 * volume/cost/$-per-m³ triple, reusing the service's derived figures and only formatting
 * (whole-dollar cost, two-decimal volume + $-per-m³, {@code "-"} for null). Covers skip-empty
 * (BR-09) keyed on the data (not a row count), the populated mapping, partial data, and null-cell
 * dashing. No Spring context or database.
 */
@DisplayName("Schedule2SectionMapper — aggregate document → one print section row")
class Schedule2SectionMapperTest {

  // ---- Skip-empty (BR-09): no cost figure anywhere → null so the orchestrator skips the section
  // ----

  @Test
  @DisplayName("null response → null")
  void nullResponse_isNull() {
    assertThat(Schedule2SectionMapper.map(null)).isNull();
  }

  @Test
  @DisplayName("all blocks null → null")
  void allBlocksNull_isNull() {
    assertThat(Schedule2SectionMapper.map(response(null, null, null, null, null, null, null, null)))
        .isNull();
  }

  @Test
  @DisplayName("blocks present but every figure null → null (empty is data-based, not row-count)")
  void allBlocksEmpty_isNull() {
    CostBlock empty = new CostBlock(null, null, null);
    assertThat(
            Schedule2SectionMapper.map(
                response(null, empty, empty, empty, empty, empty, empty, empty)))
        .isNull();
  }

  // ---- Populated mapping ----

  @Test
  @DisplayName("a full document maps to one row with the seven formatted cost blocks")
  void fullDocument_mapsToOneRow() {
    Schedule2Response response =
        response(
            "See survey notes",
            new CostBlock(new BigDecimal("1000.5"), 500000, new BigDecimal("12.34")),
            new CostBlock(null, 20000, null),
            new CostBlock(new BigDecimal("1000.5"), 520000, new BigDecimal("15")),
            new CostBlock(new BigDecimal("200.0"), 30000, new BigDecimal("5.5")),
            new CostBlock(new BigDecimal("800.5"), 490000, new BigDecimal("14.2")),
            new CostBlock(new BigDecimal("5000"), 1200000, new BigDecimal("20")),
            new CostBlock(new BigDecimal("5000"), 1690000, new BigDecimal("25.75")));

    SectionData section = Schedule2SectionMapper.map(response);

    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    assertThat(section.parameters()).isEmpty();
    Map<String, Object> row = firstRow(section);

    // Row 1 — Purchased/Private Log Costs: whole-dollar cost, 2dp volume + $/m³.
    assertThat(row)
        .containsEntry("purchasedLogCostVol", "1,000.50")
        .containsEntry("purchasedLogCostCos", "500,000")
        .containsEntry("purchasedLogCostCal", "12.34");
    // Row 2 — Wood Overhead: null cells render "-", the present cost renders grouped.
    assertThat(row)
        .containsEntry("purchasedWoodOverheadVol", "-")
        .containsEntry("purchasedWoodOverheadCos", "20,000")
        .containsEntry("purchasedWoodOverheadCal", "-");
    // The derived blocks (already computed by the service) are reused verbatim, format-only.
    assertThat(row)
        .containsEntry("subtotalCos", "520,000")
        .containsEntry("lessLogSalesCos", "30,000")
        .containsEntry("netPurchasedCos", "490,000")
        .containsEntry("totalCompanyLoggingCos", "1,200,000")
        .containsEntry("totalAverageCos", "1,690,000")
        .containsEntry("totalAverageCal", "25.75");
    assertThat(row).containsEntry("comments", "See survey notes");
  }

  @Test
  @DisplayName("partial data (only one block carries a figure) still renders — not skip-empty")
  void partialData_rendersNotSkipped() {
    Schedule2Response response =
        response(
            null,
            new CostBlock(null, 12345, null), // only purchasedLogCost cost present
            null,
            null,
            null,
            null,
            null,
            null);

    SectionData section = Schedule2SectionMapper.map(response);

    assertThat(section).isNotNull();
    Map<String, Object> row = firstRow(section);
    assertThat(row)
        .containsEntry("purchasedLogCostCos", "12,345")
        .containsEntry("purchasedLogCostVol", "-")
        .containsEntry("subtotalCos", "-") // a null block dashes across all three cells
        .containsEntry("totalAverageCos", "-")
        .containsEntry("comments", "-"); // null comments render "-"
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  /**
   * The mapper builds a {@code LinkedHashMap<String, Object>}; narrow the single row for asserts.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRow(SectionData section) {
    return (Map<String, Object>) section.rows().get(0);
  }

  private static Schedule2Response response(
      String comments,
      CostBlock purchasedLogCost,
      CostBlock purchasedWoodOverhead,
      CostBlock subtotal,
      CostBlock lessLogSales,
      CostBlock netPurchased,
      CostBlock totalCompanyLogging,
      CostBlock totalAverage) {
    return new Schedule2Response(
        514,
        2021,
        "D",
        false,
        0,
        comments,
        purchasedLogCost,
        purchasedWoodOverhead,
        subtotal,
        lessLogSales,
        netPurchased,
        totalCompanyLogging,
        totalAverage,
        null);
  }
}
