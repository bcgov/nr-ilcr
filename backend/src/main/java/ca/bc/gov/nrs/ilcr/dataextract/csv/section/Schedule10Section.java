package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;

import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;

/**
 * Legacy {@code Schedule10Extract}: one 12-column row per construction page, no comments column.
 * The {@code PERIOD} header carries legacy's literal trailing tab.
 */
public final class Schedule10Section implements SectionBuilder {

  /** Legacy's header cell, tab included. */
  static final String PERIOD_HEADER = "PERIOD\t";

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "PAGE_NO",
    "DIVISION",
    PERIOD_HEADER,
    "REGION",
    "TSA_TFL",
    "S_BLOCK",
    "TFL",
    "ROAD_GROUP"
  };

  @Override
  public String title() {
    return "**** Schedule 10 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one page. */
  public String[] row(RowContext ctx, ConstructionPage page) {
    String[] cells = pageCells(page);
    String[] row = new String[cells.length + 1];
    System.arraycopy(cells, 0, row, 0, cells.length);
    row[cells.length] = raw(page.roadGroup());
    return ctx.with(row);
  }

  /** The seven page-level cells shared with the Road Data section, in legacy order. */
  static String[] pageCells(ConstructionPage page) {
    return new String[] {
      text(page.pageLabel()),
      text(page.divisionName()),
      text(page.constructionPeriod()),
      raw(page.forestRegionCode()),
      raw(page.tsaNumber()),
      raw(page.tsbNumberCode()),
      raw(page.tflNumberCode())
    };
  }
}
