package ca.bc.gov.nrs.ilcr.dataextract.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Byte-level tests for the writer, because byte-level is the whole point of it.
 *
 * <p>The rebuilt extract is compared against legacy CSV samples, so what matters is not that the
 * output parses but that it is the same bytes legacy's opencsv 3.5 {@code CSVWriter} produced on
 * its defaults: every cell quoted, an embedded quote doubled, a bare {@code \n} line end, a null
 * cell written as nothing at all, and a "blank line" that is really a one-cell row holding a space.
 * Every assertion here reads the raw string rather than a parsed row for that reason.
 */
@DisplayName("CsvWriter — legacy opencsv byte fidelity")
class CsvWriterTest {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  /**
   * The bytes written so far, decoded as UTF-8 and with the byte-order mark removed — what a reader
   * of the file sees after the first three bytes, which have their own test below.
   */
  private String written() {
    String text = out.toString(StandardCharsets.UTF_8);
    assertThat(text).startsWith(CsvWriter.BOM);
    return text.substring(CsvWriter.BOM.length());
  }

  @Nested
  @DisplayName("quoting")
  class Quoting {

    @Test
    @DisplayName("every cell is quoted, even one that needs no quoting")
    void quotesEveryCell() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("MILL_NUMBER", "2021", "Verified");
      }

      // Quote-all is opencsv's default, not a reaction to the content: a cell with no comma, quote
      // or newline in it is quoted too.
      assertThat(written()).isEqualTo("\"MILL_NUMBER\",\"2021\",\"Verified\"\n");
    }

    @Test
    @DisplayName("an embedded double quote is doubled, not escaped with a backslash")
    void doublesEmbeddedQuotes() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("the \"Ridge\" block");
      }

      assertThat(written()).isEqualTo("\"the \"\"Ridge\"\" block\"\n");
    }

    @Test
    @DisplayName("an Excel-formula cell survives quoting as a formula")
    void quotesTheFormulaCell() throws IOException {
      // The Schedule 4 and 6 per-unit cells are handed over as ="1,234.56"; the writer's doubling
      // of their inner quotes is what makes a spreadsheet read them as a formula rather than text.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow(ExtractFormat.formula(new java.math.BigDecimal("1234.56")));
      }

      assertThat(written()).isEqualTo("\"=\"\"1,234.56\"\"\"\n");
    }

    @Test
    @DisplayName("an embedded newline is preserved inside the quotes")
    void keepsEmbeddedNewlines() throws IOException {
      // A comment cell can hold a newline. opencsv wrote it through unchanged, which makes the row
      // span two physical lines — legible to a CSV parser, and part of what a sample comparison
      // will see.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("first\nsecond");
      }

      assertThat(written()).isEqualTo("\"first\nsecond\"\n");
    }

    @Test
    @DisplayName("a comma inside a cell does not split it")
    void keepsEmbeddedCommas() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("1,234", "5,678");
      }

      assertThat(written()).isEqualTo("\"1,234\",\"5,678\"\n");
    }
  }

  @Nested
  @DisplayName("nulls and blanks")
  class NullsAndBlanks {

    @Test
    @DisplayName("a null cell is an EMPTY field, not a pair of quotes")
    void writesNullAsNothing() throws IOException {
      // The distinction is visible in the file and legacy had it: opencsv wrote nothing between the
      // delimiters for a null. A null text cell (an unnamed bridge, say) therefore differs from a
      // cell holding the empty string, which is quoted.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("a", null, "c");
      }

      assertThat(written()).isEqualTo("\"a\",,\"c\"\n");
    }

    @Test
    @DisplayName("an empty-string cell IS quoted, unlike a null")
    void writesEmptyStringAsQuotedEmpty() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("a", "", "c");
      }

      assertThat(written()).isEqualTo("\"a\",\"\",\"c\"\n");
    }

    @Test
    @DisplayName("a blank row is one cell holding a single space")
    void writesBlankRowAsASpaceCell() throws IOException {
      // Legacy's separator was not an empty line. A parser reading the file sees a single-column
      // record containing " ", and a legacy sample shows the same, so this must not be "tidied"
      // into a bare newline.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeBlankRow();
      }

      assertThat(written()).isEqualTo("\" \"\n");
    }

    @Test
    @DisplayName("a row of every-null cells is a row of bare delimiters")
    void writesAllNullRow() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow(null, null, null);
      }

      assertThat(written()).isEqualTo(",,\n");
    }
  }

  @Nested
  @DisplayName("line ends and encoding")
  class LineEndsAndEncoding {

    @Test
    @DisplayName("rows end with a bare LF, never CRLF")
    void usesLineFeedOnly() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("a");
        csv.writeRow("b");
      }

      assertThat(written()).isEqualTo("\"a\"\n\"b\"\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("writeRows emits every row in the order given")
    void writesRowsInOrder() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRows(List.of(new String[] {"1"}, new String[] {"2"}, new String[] {"3"}));
      }

      assertThat(written()).isEqualTo("\"1\"\n\"2\"\n\"3\"\n");
    }

    @Test
    @DisplayName("the file opens with the three-byte UTF-8 mark, once, before any row")
    void opensWithTheByteOrderMark() throws IOException {
      // Excel on Windows — the reader this file is for — opens a BOM-less CSV as the local ANSI
      // page and garbles every accented name. Asserted on the BYTES, and asserted to be there
      // exactly once so a second writer or a reopen cannot double it.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("a");
      }

      byte[] bytes = out.toByteArray();
      assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
      assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
      assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
      assertThat(out.toString(StandardCharsets.UTF_8).indexOf(CsvWriter.BOM, 1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("the body is genuine UTF-8")
    void writesUtf8() throws IOException {
      // The recorded deviation: legacy declared UTF-8 in its content type and then wrote the
      // platform default charset, so a non-ASCII character was whatever the server's locale made
      // of it. Asserted on the BYTES, since a String comparison would pass either way.
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow("m³");
      }

      assertThat(out.toByteArray())
          .isEqualTo((CsvWriter.BOM + "\"m³\"\n").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("an empty row still ends its line")
    void writesEmptyRow() throws IOException {
      try (CsvWriter csv = new CsvWriter(out)) {
        csv.writeRow();
      }

      assertThat(written()).isEqualTo("\n");
    }
  }
}
