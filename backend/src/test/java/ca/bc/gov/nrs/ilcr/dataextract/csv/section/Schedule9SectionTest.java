package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Schedule 9 section's shape, pinned cell by cell.
 *
 * <p>Title and header are written out literally rather than read back off the production constant,
 * because the constant is what these tests guard ({@code Schedule9Extract.java:32, :125-143}).
 */
@DisplayName("Schedule9Section — legacy column fidelity")
class Schedule9SectionTest {

  private final Schedule9Section section = new Schedule9Section();

  private final RowContext ctx = new RowContext("1234", "2021", "Verified", "5678");

  private static final CodeDescriptionDto ITEM = new CodeDescriptionDto("108", "Road Deactivation");
  private static final CodeDescriptionDto UNIT = new CodeDescriptionDto("KM", "Kilometres");
  private static final CodeDescriptionDto ZONE = new CodeDescriptionDto("SBS", "Sub-Boreal Spruce");
  private static final CodeDescriptionDto SOURCE = new CodeDescriptionDto("O", "Other");

  /**
   * A record carrying only the three components the unit gate reads; every descriptor is absent.
   * Built through the no-original-values constructor, whose two primitive components cannot be
   * null.
   */
  private static ContractualWorkRecord costedRecord(
      BigDecimal numberOfUnits, Integer cost, BigDecimal costPerUnit) {
    return new ContractualWorkRecord(
        1,
        1,
        null,
        ITEM,
        null,
        UNIT,
        null,
        numberOfUnits,
        ZONE,
        cost,
        costPerUnit,
        null,
        SOURCE,
        null,
        null);
  }

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title is the bare **** Schedule 9 **** form")
    void pinsTitle() {
      // Schedule9Extract.java:32. Symmetrical padding here; the unpadded spelling belongs to
      // Schedule 7A's title alone.
      assertThat(section.title()).isEqualTo("**** Schedule 9 ****");
    }

    @Test
    @DisplayName("the header is legacy's seventeen columns, in order")
    void pinsHeader() {
      // Schedule9Extract.java:125-143. OTH_ITEM_DESC and OTHER_DESC are two different columns —
      // the first belongs to an "Other" contractual item, the second to an "Other" unit type —
      // and their near-identical names are legacy's.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "COMPANY_ID",
              "CONTRACTUAL_ITEM",
              "OTH_ITEM_DESC",
              "SIDE_SLOPE_%",
              "NO_OF_UNITS",
              "UNIT_TYPE",
              "OTHER_DESC",
              "BIOGEO_ZONE",
              "COST_$",
              "$/UNIT",
              "SOURCE",
              "SOURCE_OTHER",
              "COMMENTS")
          .hasSize(17);
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the shared array")
    void headerIsDefensivelyCopied() {
      String[] first = section.header();
      first[5] = "MUTATED";

      assertThat(section.header()[5]).isEqualTo("CONTRACTUAL_ITEM");
    }
  }

  @Nested
  @DisplayName("data rows")
  class DataRows {

    @Test
    @DisplayName("a fully populated record renders all seventeen cells")
    void rendersFullRow() {
      ContractualWorkRecord record =
          new ContractualWorkRecord(
              1,
              1,
              "C12345",
              ITEM,
              null,
              UNIT,
              "   ",
              new BigDecimal("1234567"),
              ZONE,
              98765,
              new BigDecimal("1234.5"),
              15,
              SOURCE,
              "Contract\tinvoice",
              "Deactivated\nspur 7");

      String[] row = section.row(ctx, record);

      assertThat(row)
          .containsExactly(
              "1234",
              "2021",
              "Verified",
              "5678",
              "C12345",
              // Name first, cost-item id second — the reverse of every other code cell in the
              // file, which leads with the code.
              "Road Deactivation - 108",
              // Only carried when the item is "Other".
              "-",
              // The raw String.valueOf of the percentage, so no grouping and no decimals.
              "15",
              // ###,###,##0 — grouped, no decimals.
              "1,234,567",
              // The four code-list cells carry the CODE alone, not code-and-description.
              "KM",
              // Blank after trimming counts as absent for this cell.
              "-",
              "SBS",
              "98,765",
              // ###,###,##0.00 — always exactly two decimals.
              "1,234.50",
              "O",
              // Tab to two spaces, newline to one space, as legacy sanitised free text.
              "Contract  invoice",
              "Deactivated spur 7")
          .hasSize(17);
    }

    @Test
    @DisplayName("an absent side slope is the null marker, not a zero")
    void rendersAbsentSideSlopeAsNullMarker() {
      String[] row = section.row(ctx, costedRecord(new BigDecimal("10"), 500, null));

      assertThat(row[7]).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent Company ID is the null marker")
    void rendersAbsentContractorIdAsNullMarker() {
      String[] row = section.row(ctx, costedRecord(new BigDecimal("10"), 500, null));

      assertThat(row[4]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("the unit gate on the two money cells")
  class UnitGate {

    @Test
    @DisplayName("a record with a cost but NO units dashes both money cells — deliberately")
    void dashesBothMoneyCellsWhenUnitsAreAbsentEvenThoughTheCostIsPresent() {
      // Schedule9Extract.java:93-99 guarded COST_$ and $/UNIT on the UNIT count rather than on the
      // figures themselves, so a record with a real cost and no units printed neither. It reads
      // like a copy-paste defect and it is preserved on purpose: legacy sample files show the
      // dashes, and a corrected cell would no longer match them.
      ContractualWorkRecord record = costedRecord(null, 98765, new BigDecimal("12.34"));

      String[] row = section.row(ctx, record);

      assertThat(row[8]).isEqualTo("-");
      assertThat(row[12]).isEqualTo("-");
      assertThat(row[13]).isEqualTo("-");
    }

    @Test
    @DisplayName("zero units still opens the gate — it tests for null, not for a non-zero count")
    void opensTheGateOnZeroUnits() {
      // The guard is a null check, so a stored zero is "has units" and the money cells render.
      // $/UNIT is null in that case because the server refuses the divide-by-zero, and the cell
      // therefore falls back to the marker on its own.
      ContractualWorkRecord record = costedRecord(BigDecimal.ZERO, 98765, null);

      String[] row = section.row(ctx, record);

      assertThat(row[8]).isEqualTo("0");
      assertThat(row[12]).isEqualTo("98,765");
      assertThat(row[13]).isEqualTo("-");
    }

    @Test
    @DisplayName("units present but cost absent leaves the marker from the formatter, not the gate")
    void dashesAnAbsentCostThroughTheFormatter() {
      ContractualWorkRecord record = costedRecord(new BigDecimal("10"), null, null);

      String[] row = section.row(ctx, record);

      assertThat(row[8]).isEqualTo("10");
      assertThat(row[12]).isEqualTo("-");
      assertThat(row[13]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("code resolution")
  class CodeResolution {

    @Test
    @DisplayName("an absent contractual item is the null marker")
    void rendersAbsentContractualItemAsNullMarker() {
      ContractualWorkRecord record =
          new ContractualWorkRecord(
              1, 1, null, null, null, null, null, null, null, null, null, null, null, null, null);

      assertThat(section.row(ctx, record)[5]).isEqualTo("-");
    }

    @Test
    @DisplayName("an absent code-list selection is the null marker on all four cells")
    void rendersAbsentSelectionsAsNullMarker() {
      ContractualWorkRecord record =
          new ContractualWorkRecord(
              1, 1, null, null, null, null, null, null, null, null, null, null, null, null, null);

      String[] row = section.row(ctx, record);

      assertThat(row[9]).isEqualTo("-");
      assertThat(row[11]).isEqualTo("-");
      assertThat(row[14]).isEqualTo("-");
    }

    @Test
    @DisplayName("a selection whose code is blank is treated as absent")
    void rendersBlankCodeAsNullMarker() {
      // The guard trims, so a code column holding spaces does not reach the file as a blank cell.
      ContractualWorkRecord record =
          new ContractualWorkRecord(
              1,
              1,
              null,
              ITEM,
              null,
              new CodeDescriptionDto("   ", "Kilometres"),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null);

      assertThat(section.row(ctx, record)[9]).isEqualTo("-");
    }
  }

  @Nested
  @DisplayName("markers")
  class Markers {

    @Test
    @DisplayName("the per-record marker is the four leading cells then *** NO DATA FOUND ***")
    void pinsPerRecordMarker() {
      assertThat(ctx.noDataRow())
          .containsExactly("1234", "2021", "Verified", "5678", "*** NO DATA FOUND ***");
    }

    @Test
    @DisplayName("the whole-schedule marker is the default five cells")
    void pinsWholeScheduleMarker() {
      assertThat(section.noDataRow("Included Mills: 673", "2020 - 2021"))
          .hasSize(5)
          .containsExactly("Included Mills: 673", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***");
    }
  }
}
