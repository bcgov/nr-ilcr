package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@link Schedule3AcceptSection} — the Other Acceptable Costs sub-page section, its
 * {@code Total:} row, and the two markers.
 *
 * <p>The header and the title are written out literally rather than read from the production
 * constant, so a column renamed or reordered fails here instead of quietly changing a file that
 * ministry spreadsheets parse by column position.
 */
@DisplayName("Schedule3AcceptSection — legacy Schedule3AcceptExtract column fidelity")
class Schedule3AcceptSectionTest {

  private static final Schedule3AcceptSection SECTION = new Schedule3AcceptSection();

  private static final RowContext CTX = new RowContext("670", "2021", "Submitted", "8201");

  /**
   * A figure no cell of a correctly built section may carry. It is parked on the document's own
   * {@code subtotal}, which legacy ignored in favour of summing the rows it had just written, so a
   * wiring mistake shows up as this number appearing in the {@code Total:} row.
   */
  private static final ThreeColumnTotal NEVER_READ = new ThreeColumnTotal(777L, 777L, 777L);

  private static OtherAcceptableRow row(String description, Integer total, Integer pop) {
    // crown is derived (total - pop) on the read path; the section writes it through untouched.
    Integer crown = total == null || pop == null ? null : total - pop;
    return new OtherAcceptableRow(8201, description, total, pop, crown);
  }

  private static OtherAcceptableDocument document(List<OtherAcceptableRow> rows) {
    return new OtherAcceptableDocument(
        false, rows == null ? 0 : rows.size(), NEVER_READ, rows, null);
  }

  @Nested
  @DisplayName("the fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the header is legacy's eight columns, in legacy's order")
    void headerIsTheLegacyEightColumns() {
      // Transcribed from Schedule3AcceptExtract.java:105-114.
      String[] expected = {
        "MILL_NUMBER",
        "REPORTING_YEAR",
        "STATUS",
        "MILL_ID",
        "DESCRIPTION",
        "TOTAL_$",
        "PO&P_$",
        "CROWN_$"
      };

      assertThat(SECTION.header()).hasSize(8).containsExactly(expected);
    }

    @Test
    @DisplayName("the title says 'Other Costs', not 'Other Acceptable Costs'")
    void titleIsTheLegacyTitleCell() {
      // The screen and every other artefact call this sub-page Other ACCEPTABLE Costs; legacy's
      // title cell does not (Schedule3AcceptExtract.java:32), and consumers split the file on it.
      assertThat(SECTION.title()).isEqualTo("**** Schedule 3 - Other Costs ****");
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

    @Test
    @DisplayName("one row per group, then the Total: row")
    void oneRowPerGroupThenTheTotal() {
      List<OtherAcceptableRow> items =
          List.of(row("Bridge\tinspection&nbsp;fees", 1234567, 234567), row(null, 45000, null));

      List<String[]> rows = SECTION.rows(CTX, document(items));

      assertThat(rows).hasSize(3);
      // Costs take the whole-number pattern: thousands grouping, no decimals. The description is
      // sanitised as legacy sanitised it — tab to two spaces, the &nbsp; entity dropped
      // (ExtractFormat.java:98-100).
      String[] first = {
        "670",
        "2021",
        "Submitted",
        "8201",
        "Bridge  inspectionfees",
        "1,234,567",
        "234,567",
        "1,000,000"
      };
      assertThat(rows.get(0)).hasSize(8).containsExactly(first);
      // An absent description is the null marker, and so is each absent figure — never a zero
      // (ExtractFormat.java:46, :82-87).
      String[] second = {"670", "2021", "Submitted", "8201", "-", "45,000", "-", "-"};
      assertThat(rows.get(1)).hasSize(8).containsExactly(second);
    }

    @Test
    @DisplayName("every row is exactly as wide as the header")
    void everyRowIsAsWideAsTheHeader() {
      List<String[]> rows = SECTION.rows(CTX, document(List.of(row("Fees", 45000, 5000))));

      assertThat(rows).allSatisfy(row -> assertThat(row).hasSameSizeAs(SECTION.header()));
    }

    @Test
    @DisplayName("the Total: row sums the rows it just wrote, not the document's own subtotal")
    void totalRowSumsTheWrittenRows() {
      // Legacy accumulated the three columns while writing the rows and summed them locally
      // (Schedule3AcceptExtract.java:88-90) rather than printing the screen's subtotal, so the
      // Total: row is arithmetically consistent with the lines above it in the file.
      List<OtherAcceptableRow> items =
          List.of(row("Bridge inspection", 1234567, 234567), row("Road survey", 45000, 5000));

      List<String[]> rows = SECTION.rows(CTX, document(items));
      String[] total = rows.get(2);

      // The four leading cells are blank on the Total: row — it identifies no mill or year.
      String[] expected = {"", "", "", "", "Total:", "1,279,567", "239,567", "1,040,000"};
      assertThat(total).hasSize(8).containsExactly(expected);
      assertThat(total).doesNotContain("777");
    }

    @Test
    @DisplayName("a column with nothing to add totals to the null marker, not to zero")
    void totalOfAnEmptyColumnIsTheNullMarker() {
      // Legacy's sumBigDecimalCosts returned null when no term was added, and the null formatted
      // as the marker (ExtractFormat.java:159-170) — so an unfilled PO&P column shows "-" rather
      // than a zero that would read as a figure someone had entered.
      List<String[]> rows = SECTION.rows(CTX, document(List.of(row("Fees", 45000, null))));

      String[] expected = {"", "", "", "", "Total:", "45,000", "-", "-"};
      assertThat(rows.get(1)).containsExactly(expected);
    }

    @Test
    @DisplayName("each term is rounded to a whole number before it is added")
    void termsAreRoundedBeforeAddition() {
      // sumBigDecimalCosts rounded each term, not the sum. The stored costs are whole dollars, so
      // this only pins the ordering; the figures below are the plain integer sums.
      List<OtherAcceptableRow> items = List.of(row("A", 1, 1), row("B", 2, 1), row("C", 3, 1));

      List<String[]> rows = SECTION.rows(CTX, document(items));

      assertThat(Arrays.copyOfRange(rows.get(3), 5, 8)).containsExactly("6", "3", "3");
    }
  }

  @Nested
  @DisplayName("the markers")
  class Markers {

    @Test
    @DisplayName("a pair with no groups gets the five-cell per-record marker and no Total: row")
    void pairWithNoGroupsGetsThePerRecordMarker() {
      // Schedule3AcceptExtract.java:52-60: the four leading cells then the marker, and legacy's
      // Total: row lived inside the else branch, so an empty sub-page contributes ONE line.
      String[] expected = {"670", "2021", "Submitted", "8201", "*** NO DATA FOUND ***"};

      List<String[]> rows = SECTION.rows(CTX, document(List.of()));

      assertThat(rows).hasSize(1);
      assertThat(rows.get(0)).hasSize(5).containsExactly(expected);
    }

    @Test
    @DisplayName("a null document and a document with null rows get the same marker")
    void absentDocumentGetsTheSameMarker() {
      String[] expected = {"670", "2021", "Submitted", "8201", "*** NO DATA FOUND ***"};

      assertThat(SECTION.rows(CTX, null)).hasSize(1);
      assertThat(SECTION.rows(CTX, null).get(0)).containsExactly(expected);
      assertThat(SECTION.rows(CTX, document(null))).hasSize(1);
      assertThat(SECTION.rows(CTX, document(null)).get(0)).containsExactly(expected);
    }

    @Test
    @DisplayName("the whole-schedule marker is legacy's five cells")
    void wholeScheduleMarkerIsFiveCells() {
      // Schedule3AcceptExtract.java:36-43: the mills string, the year range, two null markers, the
      // marker text. The same shape as the per-record marker, but the first two cells carry the
      // whole selection rather than one pair.
      String[] expected = {"670, 671", "2020 - 2021", "-", "-", "*** NO DATA FOUND ***"};

      assertThat(SECTION.noDataRow("670, 671", "2020 - 2021")).hasSize(5).containsExactly(expected);
    }
  }
}
