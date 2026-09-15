package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy {@code Schedule3Extract}: one 56-column row per (mill, year) with a Schedule 3 summary.
 *
 * <p>Three legacy quirks kept verbatim: {@code INCL_UNAC_TOTAL_$} and {@code INCL_UNAC_CROWN_$} are
 * the same figure; the Cruising column pair precedes the Residue pair although the screen shows
 * them the other way round; and an empty record is a LONE {@code *** NO DATA FOUND ***} cell,
 * because legacy had commented its identifying cells out.
 */
public final class Schedule3Section implements SectionBuilder {

  private static final int LICENSES_FEES_INSURANCE = 27;
  private static final int TAXES_LEASES_RENTALS = 28;
  private static final int ANNUAL_RENTS = 29;
  private static final int WAGES_SALARIES = 30;
  private static final int VEHICLE_EXPENSE = 31;
  private static final int OFFICE_EXPENSE = 32;
  private static final int SCALING_EXPENSE = 33;
  private static final int CRUISING_LAYOUT = 34;
  private static final int RESIDUE_WASTE = 35;
  private static final int DEPRECIATION = 36;
  private static final int SILVICULTURE_ADMIN = 37;

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "LIC_TOTAL_$",
    "LIC_PO&P_$",
    "LIC_CROWN_$",
    "TAX_TOTAL_$",
    "TAX_PO&P_$",
    "TAX_CROWN_$",
    "RENTS_TOTAL_$",
    "RENTS_CROWN_$",
    "WAGE_TOTAL_$",
    "WAGE_PO&P_$",
    "WAGE_CROWN_$",
    "VEH_TOTAL_$",
    "VEH_PO&P_$",
    "VEH_CROWN_$",
    "OFFICE_TOTAL_$",
    "OFFICE_PO&P_$",
    "OFFICE_CROWN_$",
    "SCALING_TOTAL_$",
    "SCALING_PO&P_$",
    "SCALING_CROWN_$",
    "CRUIS_TOTAL_$",
    "CRUIS_PO&P_$",
    "CRUIS_CROWN_$",
    "RES_TOTAL_$",
    "RES_PO&P_$",
    "RES_CROWN_$",
    "DEPREC_TOTAL_$",
    "DEPREC_PO&P_$",
    "DEPREC_CROWN_$",
    "SILVI_TOTAL_$",
    "SILVI_CROWN_$",
    "SUB_OTH_TOTAL_$",
    "SUB_OTH_PO&P_$",
    "SUB_OTH_CROWN_$",
    "SUB_ACT_TOTAL_$",
    "SUB_ACT_PO&P_$",
    "SUB_ACT_CROWN_$",
    "INCL_UNAC_TOTAL_$",
    "INCL_UNAC_CROWN_$",
    "TOT_TOTAL_$",
    "TOT_PO&P_$",
    "TOT_CROWN_$",
    "PO&P_M3",
    "PO&P_$",
    "PO&P_$/M3",
    "CROWN_M3",
    "CROWN_$",
    "CROWN_$/M3",
    "TOTAL_OH_M3",
    "TOTAL_OH_$",
    "TOTAL_OH_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 3 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one (mill, year), or legacy's lone-cell marker when it holds no figures. */
  public String[] row(RowContext ctx, Schedule3Response s3) {
    if (isEmpty(s3)) {
      return new String[] {NO_DATA_FOUND};
    }
    Map<Integer, CostLine> byCode = index(s3.lineItems());
    CostLine lic = byCode.get(LICENSES_FEES_INSURANCE);
    CostLine tax = byCode.get(TAXES_LEASES_RENTALS);
    CostLine rents = byCode.get(ANNUAL_RENTS);
    CostLine wage = byCode.get(WAGES_SALARIES);
    CostLine veh = byCode.get(VEHICLE_EXPENSE);
    CostLine office = byCode.get(OFFICE_EXPENSE);
    CostLine scaling = byCode.get(SCALING_EXPENSE);
    CostLine cruising = byCode.get(CRUISING_LAYOUT);
    CostLine residue = byCode.get(RESIDUE_WASTE);
    CostLine deprec = byCode.get(DEPRECIATION);
    CostLine silvi = byCode.get(SILVICULTURE_ADMIN);
    ThreeColumnTotal unacceptable = s3.includedUnacceptableCosts();

    return ctx.with(
        harvest(lic),
        pop(lic),
        crown(lic),
        harvest(tax),
        pop(tax),
        crown(tax),
        harvest(rents),
        crown(rents),
        harvest(wage),
        pop(wage),
        crown(wage),
        harvest(veh),
        pop(veh),
        crown(veh),
        harvest(office),
        pop(office),
        crown(office),
        harvest(scaling),
        pop(scaling),
        crown(scaling),
        harvest(cruising),
        pop(cruising),
        crown(cruising),
        harvest(residue),
        pop(residue),
        crown(residue),
        harvest(deprec),
        pop(deprec),
        crown(deprec),
        harvest(silvi),
        crown(silvi),
        harvest(s3.subtotalOtherCosts()),
        pop(s3.subtotalOtherCosts()),
        crown(s3.subtotalOtherCosts()),
        // SUB_ACT_* repeats the SUB_OTH_* triple, deliberately. Legacy filled these three cells
        // from getAcceptableCostsTotals() (Schedule3Extract.java:127-131), which is the same sum of
        // Other Acceptable Costs rows that feeds getSubtotalOtherCosts() (Schedule3DO.java:225-228,
        // :347) — so six columns carried one triple and the real actual-costs subtotal never
        // reached the file. A duplicated column is on the ratified keep-verbatim list, and 21.3
        // compares these positions against legacy samples.
        harvest(s3.subtotalOtherCosts()),
        pop(s3.subtotalOtherCosts()),
        crown(s3.subtotalOtherCosts()),
        harvest(unacceptable),
        harvest(unacceptable),
        harvest(s3.totalCosts()),
        pop(s3.totalCosts()),
        crown(s3.totalCosts()),
        volume(s3.popTimber()),
        cost(s3.popTimber()),
        perUnit(s3.popTimber()),
        volume(s3.crownTimber()),
        cost(s3.crownTimber()),
        perUnit(s3.crownTimber()),
        volume(s3.totalOverhead()),
        cost(s3.totalOverhead()),
        perUnit(s3.totalOverhead()),
        text(s3.comments()));
  }

  /** Legacy {@code checkEmpty}: no entered admin figure, no timber volume, no comment. */
  static boolean isEmpty(Schedule3Response s3) {
    if (s3.comments() != null && !s3.comments().trim().isEmpty()) {
      return false;
    }
    if (s3.lineItems() != null) {
      for (CostLine line : s3.lineItems()) {
        if (line != null
            && (line.harvest() != null || line.pop() != null || line.crown() != null)) {
          return false;
        }
      }
    }
    return volumeOf(s3.popTimber()) == null
        && volumeOf(s3.crownTimber()) == null
        && volumeOf(s3.totalOverhead()) == null;
  }

  private static Object volumeOf(TimberBlock block) {
    return block == null ? null : block.volume();
  }

  private static Map<Integer, CostLine> index(List<CostLine> lines) {
    Map<Integer, CostLine> byCode = new HashMap<>();
    if (lines != null) {
      for (CostLine line : lines) {
        if (line != null && line.costItemCode() != null) {
          byCode.put(line.costItemCode(), line);
        }
      }
    }
    return byCode;
  }

  private static String harvest(CostLine line) {
    return whole(line == null ? null : line.harvest());
  }

  private static String harvest(ThreeColumnTotal total) {
    return whole(total == null ? null : total.harvest());
  }

  private static String pop(CostLine line) {
    return whole(line == null ? null : line.pop());
  }

  private static String pop(ThreeColumnTotal total) {
    return whole(total == null ? null : total.pop());
  }

  private static String crown(CostLine line) {
    return whole(line == null ? null : line.crown());
  }

  private static String crown(ThreeColumnTotal total) {
    return whole(total == null ? null : total.crown());
  }

  private static String volume(TimberBlock block) {
    return whole(block == null ? null : block.volume());
  }

  private static String cost(TimberBlock block) {
    return whole(block == null ? null : block.cost());
  }

  private static String perUnit(TimberBlock block) {
    return twoDecimals(block == null ? null : block.perUnit());
  }
}
