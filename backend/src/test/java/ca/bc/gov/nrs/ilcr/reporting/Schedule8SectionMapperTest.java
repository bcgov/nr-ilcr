package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule8SectionMapper} (Story 20.8): maps the three-level Tree-to-Truck read
 * model into one print section row per report PAGE, each carrying its samples as a nested
 * collection, and each sample carrying its additions/deductions as two further nested collections
 * (maps of maps of maps). Covers the null/empty skip-empty signal, the full three-level shaping
 * with server-computed figures consumed verbatim, the {@code displayAddRates}/{@code
 * displayDedRates} gate booleans, a page with no samples, and the label-or-code fallback. No Spring
 * context or database.
 */
@DisplayName("Schedule8SectionMapper — three-level Tree to Truck → print section rows")
class Schedule8SectionMapperTest {

  @Test
  @DisplayName("null response → null (skip-empty)")
  void nullResponse_isNull() {
    assertThat(Schedule8SectionMapper.map(null)).isNull();
  }

  @Test
  @DisplayName("empty pages → null (skip-empty, BR-09)")
  void emptyPages_isNull() {
    assertThat(Schedule8SectionMapper.map(response(List.of()))).isNull();
  }

  @Test
  @DisplayName("a full page → one row: descriptors + nested sample + nested additions/deductions")
  void fullPage_mapsThreeLevels() {
    SectionData section = Schedule8SectionMapper.map(response(List.of(fullPage())));

    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    Map<String, Object> row = firstRow(section);

    // Level 1 — page descriptors (resolved labels preferred over raw codes).
    assertThat(row)
        .containsEntry("pageDivision", "North Div")
        .containsEntry("pageLicense", "L570")
        .containsEntry("pageSupportCentre", "Support Centre One") // label, not the "SC1" code
        .containsEntry("pageRegion", "Region One")
        .containsEntry("pageTfl", "-"); // absent code + label → dash

    // Level 2 — the single sample, with server-computed figures consumed verbatim.
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> samples = (List<Map<String, Object>>) row.get("samples");
    assertThat(samples).hasSize(1);
    Map<String, Object> sample = samples.get(0);
    assertThat(sample)
        .containsEntry("sampleContractId", "C1")
        .containsEntry("pctTotal", "100 %") // the 100% rule sum, not recomputed
        .containsEntry("volActualHarvested", "1000") // server sum
        .containsEntry("rateOriginal", "25.50")
        .containsEntry("rateFinal", "28.50")
        .containsEntry("direction", "Uphill") // uphillDirection = true
        .containsEntry("destination", "Land") // waterDumpDestination = false
        .containsEntry("displayAddRates", true)
        .containsEntry("displayDedRates", true);

    // Level 3 — the additions list (one row), four columns.
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> additions = (List<Map<String, Object>>) sample.get("additions");
    assertThat(additions).hasSize(1);
    assertThat(additions.get(0))
        .containsEntry("rateItem", "82")
        .containsEntry("rateDesc", "Add A")
        .containsEntry("rateUnitCost", "5.00")
        .containsEntry("rateCostType", "Cost Type One");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> deductions = (List<Map<String, Object>>) sample.get("deductions");
    assertThat(deductions).hasSize(1);
    assertThat(deductions.get(0)).containsEntry("rateItem", "101");
  }

  @Test
  @DisplayName("a page with no samples still emits its row (its descriptors print)")
  void pageWithNoSamples_stillEmitsRow() {
    Page page = page("Empty Div", List.of());
    SectionData section = Schedule8SectionMapper.map(response(List.of(page)));

    assertThat(section).isNotNull();
    Map<String, Object> row = firstRow(section);
    assertThat(row).containsEntry("pageDivision", "Empty Div");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> samples = (List<Map<String, Object>>) row.get("samples");
    assertThat(samples).isEmpty();
  }

  @Test
  @DisplayName("a sample with no additions/deductions → gate booleans false, empty lists")
  void sampleWithNoRates_gatesOff() {
    Sample bare = sample("C2", List.of(), List.of());
    Page page = page("Div", List.of(bare));
    Map<String, Object> row = firstRow(Schedule8SectionMapper.map(response(List.of(page))));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> samples = (List<Map<String, Object>>) row.get("samples");
    Map<String, Object> sample = samples.get(0);
    assertThat(sample)
        .containsEntry("displayAddRates", false)
        .containsEntry("displayDedRates", false);
    assertThat((List<?>) sample.get("additions")).isEmpty();
    assertThat((List<?>) sample.get("deductions")).isEmpty();
  }

  @Test
  @DisplayName("descriptor falls back to the raw code when no label resolved")
  void descriptor_fallsBackToCode() {
    // A page whose region has a code but NO resolved label → the code is rendered.
    Page page =
        new Page(
            1, 0, "Div", null, null, null, null, null, null, "R9", null, null, null, null, null,
            null, null, null, null, null, 0, List.of());
    Map<String, Object> row = firstRow(Schedule8SectionMapper.map(response(List.of(page))));
    assertThat(row).containsEntry("pageRegion", "R9");
  }

  // ---- builders -------------------------------------------------------------------------------

  private static Schedule8Response response(List<Page> pages) {
    return new Schedule8Response(576, 2021, "D", true, pages, null);
  }

  /**
   * The 576/2021 canonical fixture shape: full descriptors, one sample, one addition + deduction.
   */
  private static Page fullPage() {
    return new Page(
        8500,
        0,
        "North Div",
        "L570",
        "Pat Contact",
        "250-555-0100",
        "CP1",
        "SC1",
        "Support Centre One",
        "R1",
        "Region One",
        "BZ1",
        "BEC Zone One",
        "T5",
        "Test TSA Five",
        null,
        null,
        "SB2",
        "Supply Block B",
        "Page note",
        1,
        List.of(fullSample()));
  }

  private static Sample fullSample() {
    RateRow add = new RateRow(1, 0, 82, "Add A", new BigDecimal("5.00"), "CT1", "Cost Type One");
    RateRow ded = new RateRow(2, 0, 101, "Ded A", new BigDecimal("2.00"), "CT2", "Cost Type Two");
    return new Sample(
        8600,
        0,
        "C1",
        "CB1",
        100,
        0,
        0,
        0,
        0,
        0,
        100,
        null,
        null,
        null,
        new BigDecimal("12.0"),
        new BigDecimal("30.0"),
        true,
        false,
        "ST1",
        "Skid Type One",
        600,
        400,
        1000,
        new BigDecimal("25.50"),
        new BigDecimal("5.00"),
        new BigDecimal("2.00"),
        new BigDecimal("28.50"),
        1,
        1,
        List.of(add),
        List.of(ded));
  }

  private static Page page(String division, List<Sample> samples) {
    return new Page(
        1,
        0,
        division,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        samples.size(),
        samples);
  }

  private static Sample sample(
      String contractId, List<RateRow> additions, List<RateRow> deductions) {
    return new Sample(
        1,
        0,
        contractId,
        "CB",
        100,
        0,
        0,
        0,
        0,
        0,
        100,
        null,
        null,
        null,
        null,
        null,
        false,
        false,
        "ST1",
        "Skid Type One",
        0,
        0,
        0,
        new BigDecimal("10.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("10.00"),
        additions.size(),
        deductions.size(),
        additions,
        deductions);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRow(SectionData section) {
    return (Map<String, Object>) section.rows().get(0);
  }
}
