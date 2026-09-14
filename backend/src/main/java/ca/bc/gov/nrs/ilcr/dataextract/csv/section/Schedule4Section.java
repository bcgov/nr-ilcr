package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.formula;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy {@code Schedule4Extract}: one 58-column row per location. Every {@code $/m³} cell is the
 * Excel-formula form ({@code ="1,234.56"}) legacy used for this schedule, {@code TRUCK_TRAN_$/M3}
 * keeps its legacy typo, and the location name is written RAW — a null name is an empty field, not
 * a dash, as legacy wrote it.
 *
 * <p>The three list-type categories (Towing, Truck Rehaul, Other Transportation) are summed here
 * from the location's OWN sub-page rows. Legacy matched those rows to the location by name across
 * every mill and year in the extract, merging same-named locations of different mills; the owner
 * hands each location its own rows, so the merge cannot recur (recorded deviation).
 */
public final class Schedule4Section implements SectionBuilder {

  static final int LAKESIDE_DRY_DUMP = 40;
  static final int WATER_DUMP = 41;
  static final int WATER_BOOM = 42;
  static final int TOWING = 43;
  static final int WILLISTON_DEWATER_ONLY = 44;
  static final int DEWATER_AND_RELOAD = 45;
  static final int TRUCK_REHAUL = 46;
  static final int TRUCK_BARGE_FERRY = 47;
  static final int CREW_BARGE_FERRY = 48;
  static final int HYDRO_DAM_LOG_TRANSFER = 49;
  static final int TRUCK_TO_TRUCK_TRANSFER = 50;
  static final int TRUCK_TO_RAIL_TRANSFER = 51;
  static final int RAIL_HAUL = 52;
  static final int LOW_WATER_BRIDGE = 53;
  static final int OTHER_TRANSPORTATION = 55;

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "LOCATION",
    "LAKESIDE_M3",
    "LAKESIDE_$",
    "LAKESIDE_$/M3",
    "WATER_DUMP_M3",
    "WATER_DUMP_$",
    "WATER_DUMP_$/M3",
    "WATER_BOOM_M3",
    "WATER_BOOM_$",
    "WATER_BOOM_$/M3",
    "TOW_TOT_KM",
    "TOW_TOT_M3",
    "TOW_TOT_$",
    "TOW_TOT_$/M3",
    "WILLISTON_M3",
    "WILLISTON_$",
    "WILLISTON_$/M3",
    "DEWATER_RELOAD_M3",
    "DEWATER_RELOAD_$",
    "DEWATER_RELOAD_$/M3",
    "TRUCK_REHAUL_KM",
    "TRUCK_REHAUL_M3",
    "TRUCK_REHAUL_$",
    "TRUCK_REHAUL_$/M3",
    "TRUCK_REHAUL_CYCLE",
    "TRUCK_BARGE_KM",
    "TRUCK_BARGE_M3",
    "TRUCK_BARGE_$",
    "TRUCK_BARGE_$/M3",
    "CREW_BARGE_KM",
    "CREW_BARGE_M3",
    "CREW_BARGE_$",
    "CREW_BARGE_$/M3",
    "DAM_TRANS_M3",
    "DAM_TRANS_$",
    "DAM_TRANS_$/M3",
    "TRUCK_TRANS_M3",
    "TRUCK_TRANS_$",
    "TRUCK_TRAN_$/M3",
    "RAIL_TRANS_M3",
    "RAIL_TRANS_$",
    "RAIL_TRANS_$/M3",
    "RAIL_HAUL_KM",
    "RAIL_HAUL_M3",
    "RAIL_HAUL_$",
    "RAIL_HAUL_$/M3",
    "LOW_BRIDGE_M3",
    "LOW_BRIDGE_$",
    "LOW_BRIDGE_$/M3",
    "OTH_TRANS_KM",
    "OTH_TRANS_M3",
    "OTH_TRANS_$",
    "OTH_TRANS_$/M3",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 4 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The row for one location. */
  public String[] row(RowContext ctx, Location location) {
    Map<Integer, CategoryAmount> categories = index(location.categories());
    SubPageTotals towing = SubPageTotals.of(Schedule4SubPageSection.towingRows(location));
    SubPageTotals rehaul = SubPageTotals.of(Schedule4SubPageSection.rehaulRows(location));
    SubPageTotals other = SubPageTotals.of(Schedule4SubPageSection.otherRows(location));
    CategoryAmount truckBarge = reported(categories.get(TRUCK_BARGE_FERRY));
    CategoryAmount crewBarge = reported(categories.get(CREW_BARGE_FERRY));
    CategoryAmount railHaul = reported(categories.get(RAIL_HAUL));

    return ctx.with(
        location.name(),
        volume(categories.get(LAKESIDE_DRY_DUMP)),
        cost(categories.get(LAKESIDE_DRY_DUMP)),
        perUnit(categories.get(LAKESIDE_DRY_DUMP)),
        volume(categories.get(WATER_DUMP)),
        cost(categories.get(WATER_DUMP)),
        perUnit(categories.get(WATER_DUMP)),
        volume(categories.get(WATER_BOOM)),
        cost(categories.get(WATER_BOOM)),
        perUnit(categories.get(WATER_BOOM)),
        whole(towing.distance()),
        whole(towing.volume()),
        whole(towing.cost()),
        formula(towing.perUnit()),
        volume(categories.get(WILLISTON_DEWATER_ONLY)),
        cost(categories.get(WILLISTON_DEWATER_ONLY)),
        perUnit(categories.get(WILLISTON_DEWATER_ONLY)),
        volume(categories.get(DEWATER_AND_RELOAD)),
        cost(categories.get(DEWATER_AND_RELOAD)),
        perUnit(categories.get(DEWATER_AND_RELOAD)),
        whole(rehaul.distance()),
        whole(rehaul.volume()),
        whole(rehaul.cost()),
        formula(rehaul.perUnit()),
        whole(rehaul.cycle()),
        distance(truckBarge),
        volume(truckBarge),
        cost(truckBarge),
        perUnitBare(truckBarge),
        distance(crewBarge),
        volume(crewBarge),
        cost(crewBarge),
        perUnitBare(crewBarge),
        volume(categories.get(HYDRO_DAM_LOG_TRANSFER)),
        cost(categories.get(HYDRO_DAM_LOG_TRANSFER)),
        perUnit(categories.get(HYDRO_DAM_LOG_TRANSFER)),
        volume(categories.get(TRUCK_TO_TRUCK_TRANSFER)),
        cost(categories.get(TRUCK_TO_TRUCK_TRANSFER)),
        perUnit(categories.get(TRUCK_TO_TRUCK_TRANSFER)),
        volume(categories.get(TRUCK_TO_RAIL_TRANSFER)),
        cost(categories.get(TRUCK_TO_RAIL_TRANSFER)),
        perUnit(categories.get(TRUCK_TO_RAIL_TRANSFER)),
        distance(railHaul),
        volume(railHaul),
        cost(railHaul),
        perUnitBare(railHaul),
        volume(categories.get(LOW_WATER_BRIDGE)),
        cost(categories.get(LOW_WATER_BRIDGE)),
        perUnit(categories.get(LOW_WATER_BRIDGE)),
        whole(other.distance()),
        whole(other.volume()),
        whole(other.cost()),
        formula(other.perUnit()),
        text(location.comments()));
  }

  private static Map<Integer, CategoryAmount> index(List<CategoryAmount> categories) {
    Map<Integer, CategoryAmount> byCode = new HashMap<>();
    if (categories != null) {
      for (CategoryAmount category : categories) {
        byCode.put(category.code(), category);
      }
    }
    return byCode;
  }

  private static String volume(CategoryAmount c) {
    return whole(c == null ? null : c.volume());
  }

  private static String cost(CategoryAmount c) {
    return whole(c == null ? null : c.cost());
  }

  private static String perUnit(CategoryAmount c) {
    return formula(c == null ? null : c.perUnit());
  }

  /**
   * The three distance-based blocks — Truck Barge/Ferry, Crew Barge/Ferry and Rail Haul — as legacy
   * saw them: a matched report counts only if it carries a cost or a volume, otherwise the block
   * keeps its empty defaults and all four of its cells render as the null marker ({@code
   * Schedule4Extract.java:69-80}). A distance reported with neither figure therefore never reached
   * the legacy file, and does not reach this one. The suppression is legacy's, not a judgement
   * about the data: 21.3 compares these columns against legacy samples, and inventing a distance
   * legacy withheld would read as a rebuild defect in that comparison.
   */
  private static CategoryAmount reported(CategoryAmount c) {
    return c != null && (c.cost() != null || c.volume() != null) ? c : null;
  }

  /**
   * The {@code $/m³} of a distance-based block. Legacy guarded these three cells with a ternary on
   * the matched report rather than letting the formatter wrap the marker, so an absent block prints
   * the BARE {@code -} here where its populated siblings print the Excel-wrapped {@code ="-"}
   * ({@code Schedule4Extract.java:153, :161, :190}). The inconsistency is legacy's and is kept.
   */
  private static String perUnitBare(CategoryAmount c) {
    return c == null ? ExtractFormat.NULL_VALUE : formula(c.perUnit());
  }

  private static String distance(CategoryAmount c) {
    return whole(c == null ? null : c.distance());
  }

  /**
   * The summed distance, volume, cost, cycle and {@code $/m³} of one list-type category — legacy
   * {@code CoreUtil.sumDescriptionCostVolume} over the filtered rows. Legacy seeded all four sums
   * at zero and kept them once ANY figure had been added, so a category with costs but no volumes
   * shows {@code 0} for volume and a null (zero-divisor) {@code $/m³}; with nothing added at all,
   * every figure is null and renders as the null marker (the formula cell as {@code ="-"}).
   */
  record SubPageTotals(
      BigDecimal distance,
      BigDecimal volume,
      BigDecimal cost,
      BigDecimal cycle,
      BigDecimal perUnit) {

    static SubPageTotals of(List<SubPageRow> rows) {
      boolean added = false;
      BigDecimal distance = BigDecimal.ZERO;
      BigDecimal volume = BigDecimal.ZERO;
      BigDecimal cost = BigDecimal.ZERO;
      BigDecimal cycle = BigDecimal.ZERO;
      for (SubPageRow row : rows) {
        if (row.cost() != null) {
          cost = cost.add(ExtractFormat.decimal(row.cost()));
          added = true;
        }
        if (row.volume() != null) {
          volume = volume.add(row.volume());
          added = true;
        }
        if (row.distance() != null) {
          distance = distance.add(row.distance());
          added = true;
        }
        if (row.cycle() != null) {
          cycle = cycle.add(ExtractFormat.decimal(row.cycle()));
          added = true;
        }
      }
      if (!added) {
        return new SubPageTotals(null, null, null, null, null);
      }
      return new SubPageTotals(distance, volume, cost, cycle, ExtractFormat.divide(cost, volume));
    }
  }
}
