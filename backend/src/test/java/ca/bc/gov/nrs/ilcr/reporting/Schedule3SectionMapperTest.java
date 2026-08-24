package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule3SectionMapper} (Story 20.7): maps the three-column ledger + the two
 * itemization sub-documents into ONE section row carrying three nested collections (admin lines,
 * Other Acceptable, Other Unacceptable) plus the fixed total rows and the timber block. Covers the
 * null response, the admin-line order + harvest-only blanking, the three total lines (Included
 * Unacceptable = Harvest + Crown only), the Annual-Rents-prepended Unacceptable list, and the
 * timber block. No Spring context or database.
 */
@DisplayName("Schedule3SectionMapper — three-column ledger + itemization tables → one row")
class Schedule3SectionMapperTest {

  @Test
  @DisplayName("null response → null")
  void nullResponse_isNull() {
    assertThat(Schedule3SectionMapper.map(null, acceptableDoc(), unacceptableDoc())).isNull();
  }

  @Test
  @DisplayName(
      "admin lines render in legacy order (35 before 34); harvest-only lines blank PO&P/Crown")
  void adminLines_orderAndHarvestOnly() {
    Map<String, Object> row = firstRow();

    List<Map<String, ?>> admin = collection(row, "adminLines");
    assertThat(admin).hasSize(11);
    // Order: 27,28,29,30,31,32,33,35(Residue),34(Cruising),36,37.
    assertThat(admin.get(0).get("adminLabel")).isEqualTo("Licenses, Fees, Insurance: ");
    assertThat(admin.get(0).get("adminHarvest")).isEqualTo("27,000");
    assertThat(admin.get(7).get("adminLabel")).isEqualTo("Residue & Waste Expense: ");
    assertThat(admin.get(8).get("adminLabel")).isEqualTo("Cruising & Layout Expense: ");
    // Annual Rents (29, index 2) is harvest-only: PO&P / Crown are blank (columns N/A).
    assertThat(admin.get(2).get("adminLabel")).isEqualTo("Annual Rents: ");
    assertThat(admin.get(2).get("adminHarvest")).isEqualTo("29,000");
    assertThat(admin.get(2).get("adminPop")).isEqualTo("");
    assertThat(admin.get(2).get("adminCrown")).isEqualTo("");
    // A normal line carries all three columns.
    assertThat(admin.get(0).get("adminPop")).isEqualTo("13,500");
    assertThat(admin.get(0).get("adminCrown")).isEqualTo("13,500");
  }

  @Test
  @DisplayName(
      "three total lines: Subtotal Other Costs, Included Unacceptable (Harvest+Crown), Total")
  void totalLines() {
    Map<String, Object> row = firstRow();

    assertThat(row)
        .containsEntry("subtotalOtherCostsHarvest", "5,000")
        .containsEntry("subtotalOtherCostsPop", "2,000")
        .containsEntry("subtotalOtherCostsCrown", "3,000")
        .containsEntry("includedUnacceptableHarvest", "800")
        .containsEntry("includedUnacceptableCrown", "800")
        .containsEntry("totalCostHarvest", "600,000")
        .containsEntry("totalCostPop", "150,000")
        .containsEntry("totalCostCrown", "450,000");
    // Included Unacceptable has NO PO&P cell (Harvest == Crown by design).
    assertThat(row).doesNotContainKey("includedUnacceptablePop");
  }

  @Test
  @DisplayName(
      "Unacceptable list prepends the read-only Annual Rents (S111) row; PO&P blank, Crown=Total")
  void unacceptableList_prependsAnnualRents() {
    Map<String, Object> row = firstRow();

    List<Map<String, ?>> unacc = collection(row, "unacceptableRows");
    assertThat(unacc).hasSize(2); // Annual Rents + one item-38 row
    assertThat(unacc.get(0).get("unacceptableDesc")).isEqualTo("Annual Rents (Forest Act, S111)");
    assertThat(unacc.get(0).get("unacceptableTotal")).isEqualTo("800");
    assertThat(unacc.get(0).get("unacceptableCrown")).isEqualTo("800"); // Crown = copy of Total
    assertThat(unacc.get(0).get("unacceptablePop")).isEqualTo(""); // PO&P always blank
    assertThat(unacc.get(1).get("unacceptableDesc")).isEqualTo("Fire rehab");
    assertThat(unacc.get(1).get("unacceptableTotal")).isEqualTo("500");
    assertThat(unacc.get(1).get("unacceptableCrown")).isEqualTo("500");
    assertThat(unacc.get(1).get("unacceptablePop")).isEqualTo("");
  }

  @Test
  @DisplayName(
      "Other Acceptable list keeps all three money cells; timber block feeds perUnit into CostVol")
  void acceptableListAndTimber() {
    Map<String, Object> row = firstRow();

    List<Map<String, ?>> acc = collection(row, "acceptableRows");
    assertThat(acc).hasSize(1);
    assertThat(acc.get(0).get("acceptableDesc")).isEqualTo("Consulting");
    assertThat(acc.get(0).get("acceptableTotal")).isEqualTo("4,000");
    assertThat(acc.get(0).get("acceptablePop")).isEqualTo("1,500");
    assertThat(acc.get(0).get("acceptableCrown")).isEqualTo("2,500");

    // Timber: volume | cost | $-per-m³ (perUnit → *CostVol).
    assertThat(row)
        .containsEntry("crownTimberVol", "12,345.00")
        .containsEntry("crownTimberCost", "450,000")
        .containsEntry("crownTimberCostVol", "36.45");
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  private static Map<String, Object> firstRow() {
    SectionData section =
        Schedule3SectionMapper.map(response(), acceptableDoc(), unacceptableDoc());
    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    return firstRowOf(section);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstRowOf(SectionData section) {
    return (Map<String, Object>) section.rows().get(0);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, ?>> collection(Map<String, Object> row, String key) {
    return (List<Map<String, ?>>) row.get(key);
  }

  private static CostLine line(int code, Integer harvest, Integer pop, Integer crown) {
    return new CostLine(code, harvest, pop, crown);
  }

  private static Schedule3Response response() {
    // 11 fixed lines (codes 27–37). Non-harvest-only lines get pop=crown=harvest/2; the
    // harvest-only
    // 29 / 37 carry the legacy pop=0 / crown=harvest (which the mapper blanks on display).
    List<CostLine> lines =
        List.of(
            line(27, 27_000, 13_500, 13_500),
            line(28, 28_000, 14_000, 14_000),
            line(29, 29_000, 0, 29_000), // Annual Rents (harvest-only)
            line(30, 30_000, 15_000, 15_000),
            line(31, 31_000, 15_500, 15_500),
            line(32, 32_000, 16_000, 16_000),
            line(33, 33_000, 16_500, 16_500),
            line(34, 34_000, 17_000, 17_000), // Cruising & Layout
            line(35, 35_000, 17_500, 17_500), // Residue & Waste
            line(36, 36_000, 18_000, 18_000),
            line(37, 37_000, 0, 37_000)); // Silviculture Admin (harvest-only)
    TimberBlock popTimber =
        new TimberBlock(new BigDecimal("1000"), 150_000L, new BigDecimal("150.00"));
    TimberBlock crownTimber =
        new TimberBlock(new BigDecimal("12345"), 450_000L, new BigDecimal("36.45"));
    TimberBlock totalOverhead =
        new TimberBlock(new BigDecimal("13345"), 600_000L, new BigDecimal("44.96"));
    return new Schedule3Response(
        514,
        2021,
        "D",
        false,
        0,
        "N",
        "See the notes",
        lines,
        popTimber,
        crownTimber,
        totalOverhead,
        new ThreeColumnTotal(5_000L, 2_000L, 3_000L), // subtotalOtherCosts
        new ThreeColumnTotal(605_000L, 152_000L, 453_000L), // subtotalActualCosts (NOT printed)
        new ThreeColumnTotal(800L, 0L, 800L), // includedUnacceptableCosts (Harvest == Crown)
        new ThreeColumnTotal(600_000L, 150_000L, 450_000L), // totalCosts
        1,
        1,
        List.of(),
        null);
  }

  private static OtherAcceptableDocument acceptableDoc() {
    return new OtherAcceptableDocument(
        false,
        1,
        new ThreeColumnTotal(5_000L, 2_000L, 3_000L),
        List.of(new OtherAcceptableRow(1, "Consulting", 4_000, 1_500, 2_500)),
        null);
  }

  private static UnacceptableDocument unacceptableDoc() {
    return new UnacceptableDocument(
        false,
        1,
        500L,
        800, // annualRentsTotal (item-29 harvest)
        List.of(new UnacceptableRow(1, "Fire rehab", 500)),
        null);
  }
}
