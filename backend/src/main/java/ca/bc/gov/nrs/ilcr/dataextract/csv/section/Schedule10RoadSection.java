package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divide;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.plain;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;

import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule10RoadExtract}: one 58-column row per road detail, with the pages that have
 * NO details collected and appended AFTER every detail row, as legacy did.
 *
 * <p>Most numeric cells are the raw {@code toString()} of the stored value — no grouping, native
 * scale — because legacy used {@code String.valueOf} here and nowhere else; only the four
 * per-length and per-volume rates are formatted. {@code MOISTURE}, {@code RSMS_CLASS} and {@code
 * BOULDER_%} are the Soil Moisture Code, the ASM Code and the Boulder Area the Ministry removed
 * (LD-2, LD-1, LD-3): all three headers stay for column parity and every row carries the null
 * marker (ratified). {@code RSMS_CLASS} is in particular NOT the owner's {@code
 * REL_SOIL_MOIST_RGM_CLS_CODE} — that is the RSMR class, a field legacy declared separately and
 * never wrote to this column.
 *
 * <p>{@code SG_END_HAUL_CALC} and {@code SG_OVERLAND_CALC} are legacy's two chained divisions — the
 * deduction over the volume, then over the distance, each at legacy's scale-10-then-2 — over
 * figures the owner exposes; the owner derives no such rate itself.
 */
public final class Schedule10RoadSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "PAGE_NO",
    "DIVISION",
    Schedule10Section.PERIOD_HEADER,
    "REGION",
    "TSA_TFL",
    "S_BLOCK",
    "TFL",
    "ROAD_NAME",
    "ROAD_TYPE",
    "MOISTURE",
    "BIOGEO_SUBZONE_VARIANT",
    "RSMS_CLASS",
    "SLOPE_%",
    "BOULDER_%",
    "MAT_ROCK_SOLID_%",
    "MAT_ROCK_RIP_%",
    "MAT_COARSE_%",
    "MAT_FINE_%",
    "MAT_ORGANIC_%",
    "MAT_TOT_%",
    "ENG_COST",
    "ENG_COST_IND",
    "SG_LENGTH_KM",
    "SG_WIDTH_M",
    "SG_ACTUAL_$",
    "SG_TRANSFER_$",
    "SG_OTHER_$",
    "SG_COST_TOT_$",
    "SG_BRIDGE_$",
    "SG_CULVERTS_$",
    "SG_LANDINGS_$",
    "SG_END_HAUL_$",
    "SG_OVERLAND_$",
    "SG_OTHER_ENG_$",
    "SG_TOTAL_$",
    "SG_$/KM",
    "SG_END_HAUL_KM",
    "SG_END_HAUL_VOL",
    "SG_END_HAUL_CALC",
    "SG_OVERLAND_DIST",
    "SG_OVERLAND_VOL",
    "SG_OVERLAND_CALC",
    "AS_CODE",
    "AS_LEN_KM",
    "AS_WIDTH_M",
    "AS_TYPE",
    "AS_DEPTH_M",
    "AS_SOURCE_KM",
    "AS_ACTUAL_$",
    "AS_TT_TRANS_$",
    "AS_OTHER_$",
    "AS_TOTAL_$",
    "AS_$/KM",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 10 - Road Data ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /**
   * The detail rows for one page, in order. A page with no details contributes nothing here; its
   * marker comes from {@link #noDetailsRow} and is appended after every detail row of the section.
   */
  public List<String[]> detailRows(RowContext ctx, ConstructionPage page) {
    List<RoadDetail> details = page.roadDetails() == null ? List.of() : page.roadDetails();
    List<String[]> rows = new ArrayList<>();
    for (RoadDetail detail : details) {
      rows.add(row(ctx, page, detail));
    }
    return rows;
  }

  /** Whether a page has no road details, and so gets the appended marker. */
  public boolean hasNoDetails(ConstructionPage page) {
    return page.roadDetails() == null || page.roadDetails().isEmpty();
  }

  /** Legacy's appended marker for a page with no road data: the page cells then the marker. */
  public String[] noDetailsRow(RowContext ctx, ConstructionPage page) {
    String[] cells = Schedule10Section.pageCells(page);
    String[] rest = new String[cells.length + 1];
    System.arraycopy(cells, 0, rest, 0, cells.length);
    rest[cells.length] = NO_DATA_FOUND;
    return ctx.with(rest);
  }

  private static String[] row(RowContext ctx, ConstructionPage page, RoadDetail detail) {
    SubGrade sg = detail.subGrade();
    Stabilizing as = detail.stabilizing();
    MaterialComposition mat = detail.materialComposition();
    String[] pageCells = Schedule10Section.pageCells(page);
    String[] detailCells = {
      raw(detail.roadName()),
      raw(detail.roadLifetimeCode()),
      NULL_VALUE,
      detail.becClassification() == null ? NULL_VALUE : raw(detail.becClassification().label()),
      // RSMS_CLASS held the ASM Code, which the Ministry removed from the product, so this column
      // keeps its header and its position and carries the null marker — the same disposition as
      // MOISTURE and BOULDER_% above and below. NOT the owner's REL_SOIL_MOIST_RGM_CLS_CODE: that
      // is a different legacy field (the RSMR class), and legacy never put it in this column.
      NULL_VALUE,
      plain(detail.sideSlopePct()),
      NULL_VALUE,
      plain(mat == null ? null : mat.solidRockPct()),
      plain(mat == null ? null : mat.rippableRockPct()),
      plain(mat == null ? null : mat.coarsePct()),
      plain(mat == null ? null : mat.finePct()),
      plain(mat == null ? null : mat.organicPct()),
      plain(mat == null ? null : mat.totalPct()),
      plain(sg == null ? null : sg.lessOtherEng()),
      "Y".equals(detail.detailedEngineeringCostInd()) ? "Yes" : "NO",
      plain(sg == null ? null : sg.length()),
      plain(sg == null ? null : sg.surfaceWidth()),
      plain(sg == null ? null : sg.actualCost()),
      plain(sg == null ? null : sg.ttTransfer()),
      plain(sg == null ? null : sg.otherTransfer()),
      plain(sg == null ? null : sg.totalCosts()),
      plain(sg == null ? null : sg.lessBridges()),
      plain(sg == null ? null : sg.lessCulverts()),
      plain(sg == null ? null : sg.lessLandings()),
      plain(sg == null ? null : sg.lessEndHaul()),
      plain(sg == null ? null : sg.lessOverland()),
      plain(sg == null ? null : sg.lessOtherEng()),
      plain(sg == null ? null : sg.total()),
      twoDecimals(sg == null ? null : sg.costPerLength()),
      plain(detail.endHaulDistance()),
      plain(detail.endHaulVolume()),
      twoDecimals(
          perVolumePerLength(
              sg == null ? null : sg.lessEndHaul(),
              detail.endHaulVolume(),
              detail.endHaulDistance())),
      plain(detail.overlandDistance()),
      plain(detail.overlandVolume()),
      twoDecimals(
          perVolumePerLength(
              sg == null ? null : sg.lessOverland(),
              detail.overlandVolume(),
              detail.overlandDistance())),
      raw(as == null ? null : as.ballastMethodCode()),
      plain(as == null ? null : as.length()),
      plain(as == null ? null : as.surfaceWidth()),
      raw(as == null ? null : as.ballastMaterialCode()),
      plain(as == null ? null : as.depth()),
      plain(as == null ? null : as.distanceToSource()),
      plain(as == null ? null : as.actualCost()),
      plain(as == null ? null : as.ttTransfer()),
      plain(as == null ? null : as.otherTransfer()),
      plain(as == null ? null : as.total()),
      twoDecimals(as == null ? null : as.costPerLength()),
      text(detail.comments())
    };
    String[] rest = new String[pageCells.length + detailCells.length];
    System.arraycopy(pageCells, 0, rest, 0, pageCells.length);
    System.arraycopy(detailCells, 0, rest, pageCells.length, detailCells.length);
    return ctx.with(rest);
  }

  /**
   * Legacy {@code getEndHaulCostPerVolumePerLength} / {@code getOverlandCostPerVolumePerLength}: a
   * zero seed plus the deduction, divided by the volume, then by the distance — two legacy
   * divisions, each null on a null or zero operand.
   */
  static BigDecimal perVolumePerLength(
      BigDecimal deduction, BigDecimal volume, BigDecimal distance) {
    BigDecimal sum = deduction == null ? null : BigDecimal.ZERO.add(deduction);
    sum = divide(sum, volume);
    return divide(sum, distance);
  }
}
