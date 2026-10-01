package ca.bc.gov.nrs.ilcr.dataextract.csv;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes rows the way legacy's opencsv 3.5 {@code CSVWriter} did with its default settings, so the
 * rebuilt extract is byte-comparable with a legacy sample: every cell wrapped in double quotes, an
 * embedded quote doubled, cells joined by a comma, rows ended by a bare {@code \n}. A {@code null}
 * cell is written as nothing at all between its delimiters — not as {@code ""} — which is how a
 * null location name or bridge name reached the legacy file.
 *
 * <p>Legacy's "blank line" is a one-cell row holding a single space, not an empty line; {@link
 * #writeBlankRow()} reproduces that exactly ({@code " "}). Legacy declared UTF-8 but wrote the
 * platform default charset; this writes genuine UTF-8 (recorded deviation).
 *
 * <p>Hand-rolled rather than a library on purpose: thirty lines reproduce the legacy bytes exactly,
 * whereas every CSV library on offer has its own null and quoting rules to be argued around.
 */
public final class CsvWriter implements AutoCloseable {

  /** Legacy's blank-line row: one cell holding a single space. */
  public static final String BLANK_CELL = " ";

  private final Writer out;

  /**
   * The UTF-8 byte-order mark, written once before the first row.
   *
   * <p>The stated reader is Excel on Windows, which opens a CSV with no BOM as the local ANSI code
   * page and shows every accented name and {@code m³} as mojibake. Legacy's platform-default bytes
   * happened to match that assumption; a genuine UTF-8 body (recorded deviation) needs the mark to
   * be read as such. Every other reader ignores it. Recorded alongside the charset deviation so the
   * legacy comparison skips these three bytes.
   */
  static final String BOM = "\uFEFF";

  public CsvWriter(OutputStream out) throws IOException {
    this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    this.out.write(BOM);
  }

  /** Write one row. Every non-null cell is quoted; a null cell is an empty field. */
  public void writeRow(String... cells) throws IOException {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        out.write(',');
      }
      String cell = cells[i];
      if (cell != null) {
        out.write('"');
        out.write(cell.replace("\"", "\"\""));
        out.write('"');
      }
    }
    out.write('\n');
  }

  /** Write every row of {@code rows} in order. */
  public void writeRows(List<String[]> rows) throws IOException {
    for (String[] row : rows) {
      writeRow(row);
    }
  }

  /** Legacy's separator line — a single-cell row containing one space. */
  public void writeBlankRow() throws IOException {
    writeRow(BLANK_CELL);
  }

  @Override
  public void close() throws IOException {
    out.flush();
    out.close();
  }
}
