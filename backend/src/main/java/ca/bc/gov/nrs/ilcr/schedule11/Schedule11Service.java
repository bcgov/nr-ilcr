package ca.bc.gov.nrs.ilcr.schedule11;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureTotals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 11 (Basic Silviculture) locations document and computes all BR-08
 * derivations server-side (AD-5, AD-6). The mill/year context is already validated by
 * {@code MillContextService} (AD-4) — zero locations is a valid state, never re-checked here. The
 * track status is read via millcontext (AD-9 single owner) and is the SILVICULTURE track's code —
 * the 1–10 track never touches this document (AR7).
 *
 * <p>Legacy arithmetic is transcribed verbatim from {@code CoreUtil}
 * ({@code bigDecimalAddition}/{@code bigDecimalDivision}/{@code sumBigDecimalAreas}/
 * {@code sumBigDecimalCosts}): null — never zero — signals "no data" at every level. Row and
 * footer figures are computed directly from their two operands, NOT via the legacy getter-side-
 * effect ordering quirk (recorded in the story; correct only by JSF render order).
 */
@Service
public class Schedule11Service {

  private static final String STATUS_DRAFT = "D";

  // Legacy Constant.REPORT_COST_ITEMS.Schedule11_1_* ids (delivery-verified: 23='Planned',
  // 24='Actual', category '11').
  private static final int CODE_PLANNED = 23;
  private static final int CODE_ACTUAL = 24;

  // Legacy Constant.POSITIVE_IND.
  private static final String POSITIVE_IND = "Y";

  private final Schedule11Repository repository;
  private final MillContextService millContextService;

  public Schedule11Service(
      Schedule11Repository repository, MillContextService millContextService) {
    this.repository = repository;
    this.millContextService = millContextService;
  }

  /**
   * The Schedule 11 aggregate document for a mill/year (S01 serve half). Context is already
   * validated by {@code MillContextService} in the controller (AD-4).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (from
   *     {@code SchedulePermissions} — never inlined, AC7)
   * @return the document with server-computed BR-08 figures and track-independent editability
   */
  @Transactional(readOnly = true)
  public Schedule11Response getSchedule11(long millId, int year, boolean callerMayEdit) {
    String trackStatus = millContextService.findSchedule11TrackStatusCode(millId, year)
        .orElse(null);
    // Editable = EDIT_SCHEDULE ∧ silviculture track Draft (legacy disableUserInputSchedule11
    // D+Licensee row; a null code cannot be Draft). The 1–10 track plays no part (S10).
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<SilvicultureLocationEntity> locationRows = repository.findLocations(year, millId);
    Map<Long, CostPair> costs = unpackCosts(repository.findCostDetails(year, millId));

    List<SilvicultureLocation> locations = locationRows.stream()
        .map(row -> toLocation(row, costs.getOrDefault(row.locationId(), CostPair.EMPTY)))
        .toList();

    // Document revisionCount is ALWAYS null: no ILCR_REPORT_SUMMARY row exists for this list
    // schedule (recorded AR11 keying delta — 25.2 keys per-row).
    return new Schedule11Response(
        millId, year, trackStatus, editable, null, locations, totalsOf(locations));
  }

  /**
   * Unpack the 23/24 cost rows per location (legacy {@code Schedule11DAO.getSilvicultureReport}
   * loop). Re-checks the item ids even though the SQL already filters — an out-of-scope item must
   * reach no figure. Duplicate items keep the last row read, in detail-id order (duplicates are
   * legal; never a single-row expectation).
   */
  private Map<Long, CostPair> unpackCosts(List<SilvicultureCostEntity> rows) {
    Map<Long, CostPair> byLocation = new HashMap<>();
    for (SilvicultureCostEntity row : rows) {
      CostPair pair = byLocation.getOrDefault(row.basicSilvicultureReportId(), CostPair.EMPTY);
      if (row.costItemId() == CODE_ACTUAL) {
        pair = new CostPair(row.cost(), pair.planned());
      } else if (row.costItemId() == CODE_PLANNED) {
        pair = new CostPair(pair.actual(), row.cost());
      } else {
        continue;
      }
      byLocation.put(row.basicSilvicultureReportId(), pair);
    }
    return byLocation;
  }

  /** Map one location row + its cost pair to the wire shape, computing the BR-08 row figures. */
  private SilvicultureLocation toLocation(SilvicultureLocationEntity row, CostPair costs) {
    Integer totalCost = addNullTolerant(costs.actual(), costs.planned());
    return new SilvicultureLocation(
        row.locationId(),
        row.location(),
        POSITIVE_IND.equals(row.enhancedInd()),
        row.biogeoclimaticCatalogueId(),
        becLabel(row),
        row.netArea(),
        costs.actual(),
        costs.planned(),
        totalCost,
        perNetArea(totalCost == null ? null : totalCost.longValue(), row.netArea()),
        row.comments(),
        row.revisionCount());
  }

  /**
   * The BR-08 footer, computed from the served rows ({@code Schedule11DO} getters):
   * null-not-zero sums, area total rounded to scale 1, and the per-area figure divided by the
   * ROUNDED footer area (the value legacy's getter chain used).
   */
  private SilvicultureTotals totalsOf(List<SilvicultureLocation> locations) {
    BigDecimal netArea = sumAreas(locations);
    Long actual = sumCosts(locations, SilvicultureLocation::actualCost);
    Long planned = sumCosts(locations, SilvicultureLocation::plannedCost);
    Long totalCost = addNullTolerant(actual, planned);
    return new SilvicultureTotals(netArea, actual, planned, totalCost,
        perNetArea(totalCost, netArea));
  }

  /** {@code CoreUtil.sumBigDecimalAreas}: sum of non-null areas, scale 1 HALF_UP; null when none. */
  private static BigDecimal sumAreas(List<SilvicultureLocation> locations) {
    List<BigDecimal> areas = locations.stream()
        .map(SilvicultureLocation::netArea)
        .filter(Objects::nonNull)
        .toList();
    if (areas.isEmpty()) {
      return null;
    }
    return areas.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(1, RoundingMode.HALF_UP);
  }

  /**
   * {@code CoreUtil.sumBigDecimalCosts}: sum of non-null whole-dollar costs; null when none
   * (the legacy per-item scale-0 rounding is a no-op on integer storage). Accumulates in
   * {@code long} — each cost is {@code NUMBER(8,0)} and a footer sum can exceed
   * {@code Integer.MAX_VALUE}; an {@code int} sum would wrap where legacy's {@code BigDecimal} did
   * not.
   */
  private static Long sumCosts(
      List<SilvicultureLocation> locations,
      java.util.function.Function<SilvicultureLocation, Integer> cost) {
    List<Integer> values = locations.stream()
        .map(cost)
        .filter(Objects::nonNull)
        .toList();
    if (values.isEmpty()) {
      return null;
    }
    return values.stream().mapToLong(Integer::intValue).sum();
  }

  /**
   * {@code CoreUtil.bigDecimalAddition} on whole-dollar row costs: null+null=null, null+x=x. Row
   * operands each fit {@code int} and their sum (≤ 199,999,998) stays in {@code int} range.
   */
  private static Integer addNullTolerant(Integer a, Integer b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * {@code CoreUtil.bigDecimalAddition} on the {@code long} footer cost totals: same null-tolerant
   * semantics, but in {@code long} so the footer {@code totalCost = actual + planned} of two
   * already-large sums cannot overflow (legacy {@code BigDecimal} parity).
   */
  private static Long addNullTolerant(Long a, Long b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * {@code CoreUtil.bigDecimalDivision} with the recorded project-wide per-unit deviation
   * (deferred-work.md): null when the total OR the area is null OR the area is zero; otherwise
   * scale 4 HALF_UP, {@code stripTrailingZeros}, min scale 1 (so {@code 140.0}, not {@code 140}).
   * Legacy display masks (scale 2) are 25.3's formatting duty. Numerator is {@code Long} so both
   * the row total (widened) and the footer total (already {@code long}) divide overflow-free.
   */
  private static BigDecimal perNetArea(Long totalCost, BigDecimal netArea) {
    if (totalCost == null || netArea == null || netArea.signum() == 0) {
      return null;
    }
    BigDecimal result = BigDecimal.valueOf(totalCost)
        .divide(netArea, 4, RoundingMode.HALF_UP)
        .stripTrailingZeros();
    return result.scale() < 1 ? result.setScale(1, RoundingMode.HALF_UP) : result;
  }

  /**
   * Legacy {@code BiogeoclimaticCatalogue.getBiogeoSubZoneVariantPase()}: zone+subzone+variant+
   * phase with null variant/phase as {@code ""}. A missing catalogue row (null zone — no FK in
   * delivery, AC9) yields null rather than a partial label.
   */
  private static String becLabel(SilvicultureLocationEntity row) {
    if (row.becZoneCode() == null) {
      return null;
    }
    String variant = row.variant() != null ? row.variant() : "";
    String phase = row.phase() != null ? row.phase() : "";
    return row.becZoneCode() + row.subzone() + variant + phase;
  }

  /** The 24/23 whole-dollar cost pair of one location; either side may be null. */
  private record CostPair(Integer actual, Integer planned) {
    static final CostPair EMPTY = new CostPair(null, null);
  }
}
