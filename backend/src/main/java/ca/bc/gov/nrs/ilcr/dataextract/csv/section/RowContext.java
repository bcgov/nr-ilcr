package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat;

/**
 * The four cells every legacy data row leads with — {@code MILL_NUMBER, REPORTING_YEAR, STATUS,
 * MILL_ID} — resolved once per (mill, year) and prepended to each row a builder emits.
 *
 * <p>{@code millNumber} is the mill NUMBER as stored (legacy rendered the {@code BigDecimal} with
 * {@code toString()}), never the id; {@code status} is the status DESCRIPTION of the track the
 * section belongs to — the Schedules 1–10 track for every section but Schedule 11's, which shows
 * the silviculture track — or legacy's {@code ** NO STATUS **} when the pair has no status row.
 *
 * @param millNumber the mill number cell
 * @param year the reporting year cell
 * @param status the status description cell
 * @param millId the mill id cell
 */
public record RowContext(String millNumber, String year, String status, String millId) {

  /** Legacy {@code ExtractDataType.getReportStatus} default for a pair with no status row. */
  public static final String NO_STATUS = "** NO STATUS **";

  /** The four leading cells followed by {@code rest}. */
  public String[] with(String... rest) {
    String[] row = new String[4 + rest.length];
    row[0] = millNumber;
    row[1] = year;
    row[2] = status;
    row[3] = millId;
    System.arraycopy(rest, 0, row, 4, rest.length);
    return row;
  }

  /** Legacy's per-record marker: the four leading cells then {@code *** NO DATA FOUND ***}. */
  public String[] noDataRow() {
    return with(ExtractFormat.NO_DATA_FOUND);
  }
}
