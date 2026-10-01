package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.plain;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeCodeLists;
import java.util.List;

/**
 * Legacy {@code Schedule7aExtract}: one 31-column row per bridge. The title's missing space before
 * its closing stars and the {@code COMMMENT_A} header are legacy's and kept. Code cells are {@code
 * "<code> - <description>"}; legacy threw on a null code, the rebuild writes the null marker.
 */
public final class Schedule7aSection implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "LOCATION_NAME",
    "BUILT_DATE",
    "NEW/USED",
    "LIFE",
    "SS_TYPE",
    "ABUT_TYPE",
    "ABUT_HT_M",
    "LOAD_RATING",
    "LENGTH_M",
    "DECK_WIDTH_M",
    "DECK_TYPE",
    "DISTANCE_KM",
    "SITE_PLAN_$",
    "SS_MATERIAL_$",
    "SS_DELIVER_$",
    "SS_INSTALL_$",
    "ABUT_MATERIAL_$",
    "ABUT_DELIVER_$",
    "ABUT_INSTALL_$",
    "TOT_MATERIAL_$",
    "TOT_DELIVER_$",
    "TOT_INSTALL_$",
    "APPROACH_$",
    "AFTER_INSTALL_$",
    "OTH_COSTS_$",
    "GRAND_TOT_$",
    "COMMMENT_A"
  };

  @Override
  public String title() {
    return "**** Schedule 7A - Bridge****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one bridge. */
  public String[] row(RowContext ctx, Bridge bridge, BridgeCodeLists codes) {
    return ctx.with(
        bridge.locationName(),
        builtDate(bridge.builtDate()),
        code(bridge.constructionTypeCode(), codes == null ? null : codes.constructionTypes()),
        plain(bridge.lifeSpan()),
        code(bridge.superstructureTypeCode(), codes == null ? null : codes.superstructureTypes()),
        code(bridge.abutmentTypeCode(), codes == null ? null : codes.abutmentTypes()),
        plain(bridge.abutmentHeight()),
        code(bridge.loadRatingCode(), codes == null ? null : codes.loadRatings()),
        plain(bridge.length()),
        plain(bridge.width()),
        code(bridge.deckTypeCode(), codes == null ? null : codes.deckTypes()),
        plain(bridge.distance()),
        whole(bridge.sitePlanCost()),
        whole(bridge.superstructureMaterialCost()),
        whole(bridge.superstructureDeliverCost()),
        whole(bridge.superstructureInstallCost()),
        whole(bridge.abutmentMaterialCost()),
        whole(bridge.abutmentDeliverCost()),
        whole(bridge.abutmentInstallCost()),
        whole(bridge.totalMaterial()),
        whole(bridge.totalDeliver()),
        whole(bridge.totalInstall()),
        whole(bridge.approachCost()),
        whole(bridge.afterInstallCost()),
        whole(bridge.otherCost()),
        whole(bridge.grandTotal()),
        text(bridge.comments()));
  }

  /** The owner's {@code yyyy-MM} as legacy's {@code MM/yyyy}; anything else passes through. */
  static String builtDate(String yyyyMm) {
    if (yyyyMm == null) {
      return NULL_VALUE;
    }
    if (yyyyMm.length() == 7 && yyyyMm.charAt(4) == '-') {
      return yyyyMm.substring(5) + "/" + yyyyMm.substring(0, 4);
    }
    return yyyyMm;
  }

  /** {@code "<code> - <description>"}; a null or unlisted code is the null marker. */
  static String code(String code, List<CodeDescriptionDto> options) {
    if (code == null) {
      return NULL_VALUE;
    }
    if (options != null) {
      for (CodeDescriptionDto option : options) {
        if (code.equals(option.code())) {
          return code + " - " + (option.description() == null ? "" : option.description());
        }
      }
    }
    return NULL_VALUE;
  }
}
