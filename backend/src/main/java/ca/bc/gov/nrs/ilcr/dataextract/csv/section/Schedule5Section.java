package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryAmount;

/**
 * Legacy {@code Schedule5Extract}: one 56-column row per camp. Every figure is the Schedule 5
 * owner's — including the two sub-page {@code $/m³} cells, which legacy derived with its own
 * ratio-of-sums and the owner derives as the screen does (recorded deviation, AD-14).
 */
public final class Schedule5Section implements SectionBuilder {

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "CAMP_NAME",
    "ROAD_DIST_KM",
    "CAMP_SIZE_PERS",
    "ASSOC_CAMP_VOL_M3",
    "ISOLATED_CAMP",
    "CATERER_M3",
    "CATERER_$",
    "CATERER_$/M3",
    "WAGES_M3",
    "WAGES_$",
    "WAGES_$/M3",
    "DEPREC_M3",
    "DEPREC_$",
    "DEPREC_$/M3",
    "GEN_EXP_M3",
    "GEN_EXP_$",
    "GEN_EXP_$/M3",
    "OTH_EXP_M3",
    "OTH_EXP_$",
    "OTH_EXP_$/M3",
    "SUB_TOT_M3",
    "SUB_TOT_$",
    "SUB_TOT_$/M3",
    "RECOVERIES_$",
    "CAMP_TOT_M3",
    "CAMP_TOT_$",
    "CAMP_TOT_$/M3",
    "CREW_TRANS_M3",
    "CREW_TRANS_$",
    "CREW_TRANS_$/M3",
    "EQ_LAND_M3",
    "EQ_LAND_$",
    "EQ_LAND_$/M3",
    "EQ_RAIL_M3",
    "EQ_RAIL_$",
    "EQ_RAIL_$/M3",
    "EQ_AIR_M3",
    "EQ_AIR_$",
    "EQ_AIR_$/M3",
    "EQ_WATER_M3",
    "EQ_WATER_$",
    "EQ_WATER_$/M3",
    "OTH_ACCESS_M3",
    "OTH_ACCESS_$",
    "OTH_ACCESS_$/M3",
    "ACCESS_TOT_M3",
    "ACCESS_TOT_$",
    "ACCESS_TOT_$/M3",
    "TOT_EXP_M3",
    "TOT_EXP_$",
    "TOT_EXP_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 5 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one camp. */
  public String[] row(RowContext ctx, Camp camp) {
    return ctx.with(
        text(camp.campName()),
        whole(camp.roadDistanceToOperatingArea()),
        whole(camp.sizeOfCamp()),
        whole(camp.associatedCampVolume()),
        isolated(camp.isolatedCamp()),
        volume(camp.cateringAndFood()),
        cost(camp.cateringAndFood()),
        perUnit(camp.cateringAndFood()),
        volume(camp.wagesAndBenefits()),
        cost(camp.wagesAndBenefits()),
        perUnit(camp.wagesAndBenefits()),
        volume(camp.depreciationLease()),
        cost(camp.depreciationLease()),
        perUnit(camp.depreciationLease()),
        volume(camp.generalCampExpenses()),
        cost(camp.generalCampExpenses()),
        perUnit(camp.generalCampExpenses()),
        volume(camp.otherCampExpenses()),
        cost(camp.otherCampExpenses()),
        perUnit(camp.otherCampExpenses()),
        volume(camp.campSubTotal()),
        cost(camp.campSubTotal()),
        perUnit(camp.campSubTotal()),
        cost(camp.recoveries()),
        volume(camp.campTotal()),
        cost(camp.campTotal()),
        perUnit(camp.campTotal()),
        volume(camp.crewTransportation()),
        cost(camp.crewTransportation()),
        perUnit(camp.crewTransportation()),
        volume(camp.equipAndSuppliesLand()),
        cost(camp.equipAndSuppliesLand()),
        perUnit(camp.equipAndSuppliesLand()),
        volume(camp.equipAndSuppliesRail()),
        cost(camp.equipAndSuppliesRail()),
        perUnit(camp.equipAndSuppliesRail()),
        volume(camp.equipAndSuppliesAir()),
        cost(camp.equipAndSuppliesAir()),
        perUnit(camp.equipAndSuppliesAir()),
        volume(camp.equipAndSuppliesWater()),
        cost(camp.equipAndSuppliesWater()),
        perUnit(camp.equipAndSuppliesWater()),
        volume(camp.otherAccessExpenses()),
        cost(camp.otherAccessExpenses()),
        perUnit(camp.otherAccessExpenses()),
        volume(camp.accessExpenseTotal()),
        cost(camp.accessExpenseTotal()),
        perUnit(camp.accessExpenseTotal()),
        volume(camp.campAndAccessTotal()),
        cost(camp.campAndAccessTotal()),
        perUnit(camp.campAndAccessTotal()),
        text(camp.comments()));
  }

  private static String isolated(Boolean value) {
    if (value == null) {
      return NULL_VALUE;
    }
    return value ? "Yes" : "No";
  }

  private static String volume(CategoryAmount a) {
    return whole(a == null ? null : a.volume());
  }

  private static String cost(CategoryAmount a) {
    return whole(a == null ? null : a.cost());
  }

  private static String perUnit(CategoryAmount a) {
    return twoDecimals(a == null ? null : a.costPerVolume());
  }
}
