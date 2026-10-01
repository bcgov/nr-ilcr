package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat;

/**
 * One legacy {@code *Extract} builder's fixed shape: its section title row, its header row, and the
 * whole-schedule marker it emits when NO selected mill/year has any rows for it.
 *
 * <p>The rows themselves are schedule-specific and built from the OWNING schedule's read document
 * (AD-14) by each implementation's own method; the generator frames every section the same way —
 * blank row, {@code **** title ****}, header, then the rows or the marker.
 */
public interface SectionBuilder {

  /** The {@code **** ... ****} title cell, verbatim from the legacy builder. */
  String title();

  /** The header row, verbatim from the legacy builder including its typos. */
  String[] header();

  /**
   * The whole-schedule marker row: the selected mill numbers, the year range, two null markers and
   * {@code *** NO DATA FOUND ***}. Five cells in every legacy builder but three, which override.
   *
   * @param millNumbers the {@code Included Mills:} string
   * @param yearRange {@code "<start> - <end>"}
   */
  default String[] noDataRow(String millNumbers, String yearRange) {
    return new String[] {
      millNumbers,
      yearRange,
      ExtractFormat.NULL_VALUE,
      ExtractFormat.NULL_VALUE,
      ExtractFormat.NO_DATA_FOUND
    };
  }
}
