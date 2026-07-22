package ca.bc.gov.nrs.ilcr.schedule4;

import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 4 read document from the stored {@code TRANSPORTATION_REPORT} locations and
 * their in-scope category detail rows. Every derived value ({@code perUnit}, {@code kind},
 * {@code editable}, per-category {@code distance}) is computed here (AD-5/AD-6) — never read from
 * storage as a client-supplied figure.
 *
 * <p>The mill/year context is validated by {@code MillContextService} in the controller before this
 * runs (AD-4). A valid, active mill/year with NO category-{@code "4"} locations is NOT a 404 — it is
 * the legitimate no-locations state and yields a 200 empty {@code locations: []}, editable per track
 * (mirrors the legacy JPA {@code getResultList()}-empty → empty-doc behaviour; the DAO returned null
 * only on a thrown exception).
 *
 * <p>Storage shape (from legacy {@code Schedule4DAO.buildSchedule4Results} / {@code saveReport}, see
 * the spec Completion Notes): one {@code TRANSPORTATION_REPORT} row = one location; its category
 * amounts are {@code ILCR_COST_REPORT_DETAIL} rows joined by {@code TRANSPORTATION_REPORT_ID}; the
 * single per-location {@code DISTANCE} is shared by the distance-based categories.
 */
@Service
public class Schedule4Service {

  private static final String STATUS_DRAFT = "D";

  private static final String KIND_FIXED = "FIXED";
  private static final String KIND_DISTANCE = "DISTANCE";

  /** The 3 distance-based cost-item codes (47 Truck Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul). */
  private static final Set<Integer> DISTANCE_CODES = Set.of(47, 48, 52);

  private final Schedule4Repository repository;

  public Schedule4Service(Schedule4Repository repository) {
    this.repository = repository;
  }

  /**
   * Assemble the Schedule 4 read document for a mill/year.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds the EDIT_SCHEDULE action (from the controller)
   * @return the read document (never null; {@code locations: []} when the mill/year has none)
   */
  @Transactional(readOnly = true)
  public Schedule4Response getSchedule4(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // A location spans MULTIPLE TRANSPORTATION_REPORT rows sharing LOCATION_DESCRIPTION (delivery-DB
    // confirmed): one primary report carries the 9 fixed categories (distance null); each distance
    // category 47/48/52 is its OWN report with its OWN distance. So group by location NAME and take a
    // distance-category's distance from the report that detail belongs to — never a single
    // per-location value. Report order (legacy findTransportationReportDetails) is by report id;
    // the LinkedHashMap keeps first-seen (lowest report id) location order.
    List<LocationRow> locationRows = repository.findLocations(millId, year);
    Map<Integer, BigDecimal> distanceByReport = new HashMap<>();
    Map<Integer, String> nameByReport = new HashMap<>();
    Map<String, List<CategoryAmount>> categoriesByName = new LinkedHashMap<>();
    for (LocationRow loc : locationRows) {
      distanceByReport.put(loc.transportationReportId(), loc.distance());
      nameByReport.put(loc.transportationReportId(), loc.locationDescription());
      categoriesByName.computeIfAbsent(loc.locationDescription(), k -> new ArrayList<>());
    }

    for (DetailRow d : repository.findInScopeDetails(millId, year)) {
      if (d.costItemCode() == null) {
        continue;
      }
      String name = nameByReport.get(d.transportationReportId());
      if (name == null) {
        continue; // detail's report is not a category-"4" location row (defensive)
      }
      boolean isDistance = DISTANCE_CODES.contains(d.costItemCode());
      BigDecimal categoryDistance =
          isDistance ? normalize(distanceByReport.get(d.transportationReportId())) : null;
      categoriesByName.get(name).add(new CategoryAmount(
          d.costItemCode(),
          isDistance ? KIND_DISTANCE : KIND_FIXED,
          normalize(d.volume()),
          d.cost(),
          categoryDistance,
          perUnit(bd(d.cost()), d.volume())));
    }

    List<Location> locations = new ArrayList<>(categoriesByName.size());
    categoriesByName.forEach((name, categories) -> locations.add(new Location(name, categories)));

    return new Schedule4Response(millId, year, trackStatus, editable, locations);
  }

  private static BigDecimal bd(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  /**
   * Strip an Oracle {@code NUMBER(18,4)} volume/distance to its natural form so a whole value
   * serializes as an integer ({@code 2000}, not {@code 2000.0000}) — Schedule 1/2 wire-contract
   * parity. Null-safe.
   */
  private static BigDecimal normalize(BigDecimal value) {
    if (value == null) {
      return null;
    }
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * $/m³ = cost / volume, computed server-side (AD-6, legacy {@code costVolumeConverter} display
   * figure). Null when either operand is null or volume is zero. Scale 4 HALF_UP
   * {@code stripTrailingZeros}, kept at scale &ge; 1 so it serializes as a decimal (e.g. {@code 50.0},
   * not {@code 50}) — Schedule 1/2 parity.
   */
  private static BigDecimal perUnit(BigDecimal cost, BigDecimal volume) {
    if (cost == null || volume == null || volume.signum() == 0) {
      return null;
    }
    BigDecimal result = cost.divide(volume, 4, RoundingMode.HALF_UP).stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }
}
