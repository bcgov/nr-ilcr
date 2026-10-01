package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.plain;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertCodeLists;

/** Legacy {@code Schedule7bExtract}: one 13-column row per culvert. */
public final class Schedule7bSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "TYPE",
    "SPAN_MM",
    "RISE_MM",
    "LENGTH_M",
    "NO_OF_PIECES",
    "MATERIAL_$",
    "INSTALL_$",
    "TOTAL_$",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 7B - Culvert ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one culvert. */
  public String[] row(RowContext ctx, Culvert culvert, CulvertCodeLists codes) {
    return ctx.with(
        Schedule7aSection.code(
            culvert.culvertTypeCode(), codes == null ? null : codes.culvertTypes()),
        plain(culvert.spanSize()),
        plain(culvert.riseSize()),
        whole(culvert.length()),
        plain(culvert.culvertPieceCount()),
        whole(culvert.materialCost()),
        whole(culvert.installCost()),
        whole(culvert.totalCost()),
        text(culvert.comments()));
  }
}
