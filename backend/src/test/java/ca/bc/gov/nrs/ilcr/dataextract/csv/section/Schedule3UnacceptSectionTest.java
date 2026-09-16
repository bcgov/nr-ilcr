package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Schedule3UnacceptSection} — the Included Unacceptable Costs sub-page
 * section: its fixed Annual Rents row, its {@code CROWN_$}-copies-{@code TOTAL_$} rule, and the two
 * markers, whose shapes differ from each other.
 *
 * <p>The header and the title are written out literally rather than read from the production
 * constant, so a column renamed or reordered fails here instead of quietly changing a file that
 * ministry spreadsheets parse by column position.
 */
@DisplayName("Schedule3UnacceptSection — legacy Schedule3UnacceptExtract column fidelity")
class Schedule3UnacceptSectionTest {

  private static final Schedule3UnacceptSection SECTION = new Schedule3UnacceptSection();

  private static final RowContext CTX = new RowContext("670", "2021", "Submitted", "8201");

  private static final String RENTS = "Annual Rents (Forest Act, S111)";

  /**
   * A figure no cell of a correctly built section may carry. It is parked on the document's own
   * {@code subtotalTotal}, which legacy ignored in favour of summing the rows it had just written,
   * so a wiring mistake shows up as this number appearing in the {@code Total:} row.
   */
  private static final Long NEVER_READ = 777L;

  private static UnacceptableRow row(String description, Integer total) {
    return new UnacceptableRow(9001, description, total);
  }

  private static UnacceptableDocument document(Integer annualRents, List<UnacceptableRow> rows) {
    return new UnacceptableDocument(
        false, rows == null ? 0 : rows.size(), NEVER_READ, annualRents, rows, null);
  }

  @Nested
  @DisplayName("the fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the header is legacy's seven columns, in legacy's order")
    void headerIsTheLegacySevenColumns() {
      // Transcribed from Schedule3UnacceptExtract.java:118-126. There is no PO&P column: an
      // item-38 row stores a single detail row, so the sub-page has no PO&P or derived crown.
      String[] expected = {
        "MILL_NUMBER", "REPORTING_YEAR", "STATUS", "MILL_ID", "DESCRIPTION", "TOTAL_$", "CROWN_$"
      };

      assertThat(SECTION.header()).hasSize(7).containsExactly(expected);
    }

    @Test
    @DisplayName("the title is legacy's title cell")
    void titleIsTheLegacyTitleCell() {
      // Schedule3UnacceptExtract.java:32.
      assertThat(SECTION.title()).isEqualTo("**** Schedule 3 - Unacceptable Costs ****");
    }

    @Test
    @DisplayName("header() hands back a copy, so a caller cannot edit the columns")
    void headerIsDefensivelyCopied() {
      SECTION.header()[0] = "TAMPERED";

      assertThat(SECTION.header()[0]).isEqualTo("MILL_NUMBER");
    }
  }

  @Nested
  @DisplayName("the data rows")
  class DataRows {

    private List<String[]> twoItemsAndRents() {
      return SECTION.rows(
          CTX, document(30000, List.of(row("Penalty\tinterest&nbsp;", 1234567), row(null, 45000))));
    }

    @Test
    @DisplayName("the fixed Annual Rents row leads the section, before any item-38 row")
    void annualRentsRowLeadsTheSection() {
      // Legacy wrote this row from the item-29 Harvest figure before iterating the item-38 rows
      // (Schedule3UnacceptExtract.java:69-81). The description is a fixed literal, not stored
      // text, so it is never sanitised and never the null marker.
      String[] expected = {"670", "2021", "Submitted", "8201", RENTS, "30,000", "30,000"};

      assertThat(twoItemsAndRents().get(0)).hasSize(7).containsExactly(expected);
    }

    @Test
    @DisplayName("the Annual Rents row is written even when the figure is absent")
    void annualRentsRowIsWrittenEvenWhenAbsent() {
      // Legacy guarded only the FIGURE, not the row (Schedule3UnacceptExtract.java:77-80), so the
      // sub-page's read-only rents line is always visible in the file once there is an item-38
      // row — and its two cells carry the null marker rather than a zero.
      String[] expected = {"670", "2021", "Submitted", "8201", RENTS, "-", "-"};

      List<String[]> rows = SECTION.rows(CTX, document(null, List.of(row("Penalty", 45000))));

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0)).containsExactly(expected);
    }

    @Test
    @DisplayName("each item-38 row formats its description and its cost")
    void itemRowsFormatDescriptionAndCost() {
      List<String[]> rows = twoItemsAndRents();

      // Tab to two spaces and the &nbsp; entity dropped (ExtractFormat.java:98-100); grouping and
      // no decimals on the cost.
      String[] first = {
        "670", "2021", "Submitted", "8201", "Penalty  interest", "1,234,567", "1,234,567"
      };
      assertThat(rows.get(1)).hasSize(7).containsExactly(first);
      // An absent description is the null marker (ExtractFormat.java:82-87).
      String[] second = {"670", "2021", "Submitted", "8201", "-", "45,000", "45,000"};
      assertThat(rows.get(2)).hasSize(7).containsExactly(second);
    }

    @Test
    @DisplayName("CROWN_$ is a copy of TOTAL_$ on every row, including the rents and Total: rows")
    void crownCellCopiesTheTotalCell() {
      // Every one of legacy's three row shapes wrote the SAME expression into both cells
      // (Schedule3UnacceptExtract.java:77-80, :93-94, :103-104): these costs are wholly
      // unacceptable, so the Crown share is the whole figure. A later reader must not derive it.
      List<String[]> rows = twoItemsAndRents();

      assertThat(rows).hasSize(4);
      assertThat(rows).allSatisfy(row -> assertThat(row[6]).isEqualTo(row[5]));
    }

    @Test
    @DisplayName("every row is exactly as wide as the header")
    void everyRowIsAsWideAsTheHeader() {
      // isNotEmpty() first: allSatisfy passes vacuously on an empty list.
      assertThat(twoItemsAndRents())
          .isNotEmpty()
          .allSatisfy(row -> assertThat(row).hasSameSizeAs(SECTION.header()));
    }

    @Test
    @DisplayName("the Total: row includes the Annual Rents figure")
    void totalRowIncludesTheAnnualRents() {
      // Legacy seeded its accumulator with the rents figure before the loop
      // (Schedule3UnacceptExtract.java:82), so the Total: is the sum of the rents plus every
      // item-38 row: 30,000 + 1,234,567 + 45,000. It also sums locally rather than printing the
      // document's own subtotal, which is why the never-read sentinel must not appear.
      String[] expected = {"", "", "", "", "Total:", "1,309,567", "1,309,567"};

      String[] total = twoItemsAndRents().get(3);

      assertThat(total).hasSize(7).containsExactly(expected);
      assertThat(total).doesNotContain("777");
    }

    @Test
    @DisplayName("an absent rents figure contributes nothing to the Total:")
    void absentRentsContributesNothingToTheTotal() {
      // sumBigDecimalCosts skipped null terms and returned null only when NOTHING was added
      // (ExtractFormat.java:159-170), so the total here is the item row alone.
      List<String[]> rows = SECTION.rows(CTX, document(null, List.of(row("Penalty", 45000))));

      String[] expected = {"", "", "", "", "Total:", "45,000", "45,000"};
      assertThat(rows.get(2)).containsExactly(expected);
    }

    @Test
    @DisplayName("a Total: with nothing to add is the null marker, not zero")
    void totalWithNothingToAddIsTheNullMarker() {
      List<String[]> rows = SECTION.rows(CTX, document(null, List.of(row("Penalty", null))));

      String[] expected = {"", "", "", "", "Total:", "-", "-"};
      assertThat(rows.get(2)).containsExactly(expected);
    }
  }

  @Nested
  @DisplayName("the two markers, whose shapes differ")
  class Markers {

    @Test
    @DisplayName("the per-record marker carries TWO trailing empty cells — seven in all")
    void perRecordMarkerCarriesTwoTrailingEmptyCells() {
      // Schedule3UnacceptExtract.java:55-64 padded this marker out to the full header width with
      // two empty strings, which no other legacy builder did. They are EMPTY cells, not null
      // markers, and dropping them would shorten the line a consumer expects to be seven wide.
      String[] expected = {"670", "2021", "Submitted", "8201", "*** NO DATA FOUND ***", "", ""};

      List<String[]> rows = SECTION.rows(CTX, document(30000, List.of()));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0)).hasSize(7).containsExactly(expected);
    }

    @Test
    @DisplayName("no rows means no Annual Rents row either, even when the figure is present")
    void noRowsMeansNoAnnualRentsRow() {
      // Legacy gated on getNumberOfUnacceptableCosts() == 0 (Schedule3UnacceptExtract.java:53),
      // and the rents row lived in the else branch — so a mill with an item-29 figure but no
      // item-38 rows gets the marker alone and its rents figure never reaches the file.
      List<String[]> rows = SECTION.rows(CTX, document(30000, List.of()));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0)).doesNotContain(RENTS, "30,000");
    }

    @Test
    @DisplayName("a null document and a document with null rows get the same seven-cell marker")
    void absentDocumentGetsTheSameMarker() {
      String[] expected = {"670", "2021", "Submitted", "8201", "*** NO DATA FOUND ***", "", ""};

      assertThat(SECTION.rows(CTX, null)).hasSize(1);
      assertThat(SECTION.rows(CTX, null).get(0)).containsExactly(expected);
      assertThat(SECTION.rows(CTX, document(30000, null)).get(0)).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is FIVE cells, unlike the seven-cell per-record one")
    void wholeScheduleMarkerIsFiveCells() {
      // Legacy padded only the per-record marker; the whole-schedule one kept the ordinary five
      // cells (Schedule3UnacceptExtract.java:37-44). The two shapes are deliberately different
      // and must not be reconciled with each other.
      String[] expected = {"670, 671", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***"};

      assertThat(SECTION.noDataRow("670, 671", "2020 - 2021")).hasSize(5).containsExactly(expected);
    }
  }
}
