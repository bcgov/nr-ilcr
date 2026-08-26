package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 4 read document to the legacy fixed-form print section. Each location is one
 * detail row. The DTO supplies all category values; this class only formats them and shapes the
 * three sub-page collections for the nested Jasper lists.
 */
final class Schedule4SectionMapper {

  private static final int LAKESIDE_DRY_DUMP = 40;
  private static final int WATER_DUMP = 41;
  private static final int WATER_BOOM = 42;
  private static final int TOWING = 43;
  private static final int WILLISTON_DEWATER_ONLY = 44;
  private static final int DEWATER_AND_RELOAD = 45;
  private static final int TRUCK_REHAUL = 46;
  private static final int TRUCK_BARGE_FERRY = 47;
  private static final int CREW_BARGE_FERRY = 48;
  private static final int HYDRO_DAM_LOG_TRANSFER = 49;
  private static final int TRUCK_TO_TRUCK_TRANSFER = 50;
  private static final int TRUCK_TO_RAIL_TRANSFER = 51;
  private static final int RAIL_HAUL = 52;
  private static final int LOW_WATER_BRIDGE = 53;
  private static final int OTHER_TRANSPORTATION = 55;

  private Schedule4SectionMapper() {}

  static SectionData map(Schedule4Response response) {
    if (response == null || response.locations() == null || response.locations().isEmpty()) {
      return null;
    }
    return new SectionData(
        response.locations().stream().map(Schedule4SectionMapper::toRow).toList(), Map.of());
  }

  private static Map<String, ?> toRow(Location location) {
    Map<Integer, CategoryAmount> categories = categoriesByCode(location.categories());
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("locationName", SectionFormat.text(location.name()));
    row.put("comments", SectionFormat.text(location.comments()));

    putCategory(row, "lakeSideDryDump", categories.get(LAKESIDE_DRY_DUMP), false);
    putCategory(row, "waterDump", categories.get(WATER_DUMP), false);
    putCategory(row, "waterBoom", categories.get(WATER_BOOM), false);
    putCategory(row, "willistonLakeDewaterOnly", categories.get(WILLISTON_DEWATER_ONLY), false);
    putCategory(row, "dewaterAndReload", categories.get(DEWATER_AND_RELOAD), false);
    putCategory(row, "hydroDamLogTransfer", categories.get(HYDRO_DAM_LOG_TRANSFER), false);
    putCategory(row, "truckToTruckTransfer", categories.get(TRUCK_TO_TRUCK_TRANSFER), false);
    putCategory(row, "truckToRailTransfer", categories.get(TRUCK_TO_RAIL_TRANSFER), false);
    putCategory(row, "lowWaterBridge", categories.get(LOW_WATER_BRIDGE), false);
    putCategory(row, "truckBargeFerry", categories.get(TRUCK_BARGE_FERRY), true);
    putCategory(row, "crewBargeFerry", categories.get(CREW_BARGE_FERRY), true);
    putCategory(row, "railHaul", categories.get(RAIL_HAUL), true);

    List<SubPageRow> subPageRows =
        location.subPageRows() == null ? List.of() : location.subPageRows();
    putSubPage(
        row,
        "towingRows",
        "towingTotal",
        subPageRows.stream().filter(r -> r.code() == TOWING).toList());
    putSubPage(
        row,
        "truckRehaulRows",
        "truckRehaul",
        subPageRows.stream().filter(r -> r.code() == TRUCK_REHAUL).toList());
    putSubPage(
        row,
        "otherTransportationRows",
        "otherTransportation",
        subPageRows.stream().filter(r -> r.code() == OTHER_TRANSPORTATION).toList());
    return row;
  }

  private static Map<Integer, CategoryAmount> categoriesByCode(List<CategoryAmount> categories) {
    Map<Integer, CategoryAmount> byCode = new LinkedHashMap<>();
    if (categories != null) {
      for (CategoryAmount category : categories) {
        byCode.put(category.code(), category);
      }
    }
    return byCode;
  }

  private static void putCategory(
      Map<String, Object> row, String prefix, CategoryAmount category, boolean withDistance) {
    row.put(prefix + "Vol", SectionFormat.whole(category == null ? null : category.volume()));
    row.put(prefix + "Cost", SectionFormat.money(category == null ? null : category.cost()));
    row.put(
        prefix + "CostVol", SectionFormat.decimal(category == null ? null : category.perUnit()));
    if (withDistance) {
      row.put(prefix + "Dist", SectionFormat.whole(category == null ? null : category.distance()));
    }
  }

  private static void putSubPage(
      Map<String, Object> row, String rowsKey, String prefix, List<SubPageRow> values) {
    List<Map<String, ?>> displayRows = new ArrayList<>();
    for (SubPageRow value : values) {
      displayRows.add(subPageRow(prefix, value));
    }
    if (displayRows.isEmpty()) {
      displayRows.add(emptySubPageRow(prefix));
    }
    row.put(rowsKey, displayRows);

    BigDecimal distance = sum(values.stream().map(SubPageRow::distance).toList());
    BigDecimal volume = sum(values.stream().map(SubPageRow::volume).toList());
    long cost =
        values.stream()
            .map(SubPageRow::cost)
            .filter(v -> v != null)
            .mapToLong(Integer::longValue)
            .sum();
    row.put(prefix + "TotalDist", SectionFormat.whole(distance));
    row.put(prefix + "TotalVol", SectionFormat.whole(volume));
    row.put(prefix + "TotalCost", SectionFormat.money(cost));
    row.put(prefix + "TotalCostVol", SectionFormat.decimal(perUnit(cost, volume)));
  }

  private static Map<String, String> subPageRow(String prefix, SubPageRow value) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put(prefix + "Description", SectionFormat.text(value.description()));
    row.put(prefix + "Dist", SectionFormat.whole(value.distance()));
    row.put(prefix + "Volume", SectionFormat.whole(value.volume()));
    row.put(prefix + "Cost", SectionFormat.money(value.cost()));
    row.put(prefix + "CostVolume", SectionFormat.decimal(value.perUnit()));
    return row;
  }

  private static Map<String, String> emptySubPageRow(String prefix) {
    Map<String, String> row = new LinkedHashMap<>();
    row.put(prefix + "Description", "-");
    row.put(prefix + "Dist", "-");
    row.put(prefix + "Volume", "-");
    row.put(prefix + "Cost", "-");
    row.put(prefix + "CostVolume", "-");
    return row;
  }

  private static BigDecimal sum(List<BigDecimal> values) {
    return values.stream().filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal perUnit(long cost, BigDecimal volume) {
    if (volume == null || volume.signum() == 0) {
      return BigDecimal.ZERO.setScale(2);
    }
    return BigDecimal.valueOf(cost).divide(volume, 2, RoundingMode.HALF_UP);
  }
}
