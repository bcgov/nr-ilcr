package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.divideNoRounding;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.formula;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.nullOnly;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCostsTwoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.twoDecimals;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule4TowingExtract} / {@code Schedule4RehaulExtract} / {@code
 * Schedule4OtherExtract} — three builders of one shape: the list rows of one category for one
 * location, then a {@code Total:} row. Each keeps legacy's own row filter (Towing and Rehaul keep
 * rows with a cost, Other keeps rows with a distance) and legacy's own whole-schedule marker width
 * (Towing's has six cells, the other two five).
 */
public final class Schedule4SubPageSection implements SectionBuilder {

  /** Which of the three legacy builders this instance is. */
  public enum Kind {
    TOWING("**** Schedule 4 - Towing ****", Schedule4Section.TOWING, 6),
    REHAUL("**** Schedule 4 - Rehaul ****", Schedule4Section.TRUCK_REHAUL, 5),
    OTHER("**** Schedule 4 - Other Transport ****", Schedule4Section.OTHER_TRANSPORTATION, 5);

    private final String title;
    private final int code;
    private final int noDataCells;

    Kind(String title, int code, int noDataCells) {
      this.title = title;
      this.code = code;
      this.noDataCells = noDataCells;
    }
  }

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "LOCATION",
    "DESCRIPTION",
    "DIST_KM",
    "VOL_M3",
    "COST_$",
    "CPU_$/M3"
  };

  private final Kind kind;

  public Schedule4SubPageSection(Kind kind) {
    this.kind = kind;
  }

  @Override
  public String title() {
    return kind.title;
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  @Override
  public String[] noDataRow(String millNumbers, String yearRange) {
    if (kind.noDataCells == 6) {
      return new String[] {
        millNumbers, yearRange, NULL_VALUE, NULL_VALUE, NULL_VALUE, NO_DATA_FOUND
      };
    }
    return SectionBuilder.super.noDataRow(millNumbers, yearRange);
  }

  /** The rows for one location: one per list row plus the total, or the per-location marker. */
  public List<String[]> rows(RowContext ctx, Location location) {
    List<SubPageRow> items = rowsOf(location);
    String locationName = nullOnly(location.name());
    if (items.isEmpty()) {
      return List.<String[]>of(ctx.with(locationName, NO_DATA_FOUND));
    }
    List<String[]> rows = new ArrayList<>();
    List<Number> costs = new ArrayList<>();
    List<Number> distances = new ArrayList<>();
    List<Number> volumes = new ArrayList<>();
    for (SubPageRow item : items) {
      rows.add(
          ctx.with(
              locationName,
              text(item.description()),
              whole(item.distance()),
              whole(item.volume()),
              whole(item.cost()),
              formula(item.perUnit())));
      costs.add(item.cost());
      volumes.add(item.volume());
      distances.add(item.distance());
    }
    rows.add(
        new String[] {
          "",
          "",
          "",
          "",
          "",
          "Total:",
          whole(sumCosts(distances)),
          whole(sumCosts(volumes)),
          whole(sumCosts(costs)),
          twoDecimals(divideNoRounding(sumCostsTwoDecimals(costs), sumCostsTwoDecimals(volumes)))
        });
    return rows;
  }

  private List<SubPageRow> rowsOf(Location location) {
    return switch (kind) {
      case TOWING -> towingRows(location);
      case REHAUL -> rehaulRows(location);
      case OTHER -> otherRows(location);
    };
  }

  /** Towing rows legacy counted: code 43 with a cost. */
  static List<SubPageRow> towingRows(Location location) {
    return filter(location, Schedule4Section.TOWING, true);
  }

  /** Truck Rehaul rows legacy counted: code 46 with a cost. */
  static List<SubPageRow> rehaulRows(Location location) {
    return filter(location, Schedule4Section.TRUCK_REHAUL, true);
  }

  /** Other Transportation rows legacy counted: code 55 with a DISTANCE. */
  static List<SubPageRow> otherRows(Location location) {
    return filter(location, Schedule4Section.OTHER_TRANSPORTATION, false);
  }

  private static List<SubPageRow> filter(Location location, int code, boolean byCost) {
    if (location.subPageRows() == null) {
      return List.of();
    }
    return location.subPageRows().stream()
        .filter(r -> r.code() == code)
        .filter(r -> byCost ? r.cost() != null : r.distance() != null)
        .toList();
  }
}
