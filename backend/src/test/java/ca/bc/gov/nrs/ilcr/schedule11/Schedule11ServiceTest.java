package ca.bc.gov.nrs.ilcr.schedule11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the BR-08 derivations, the editable matrix, and the empty-document state (Story
 * 25.1 AC1/AC2/AC6/AC7). Mocked repository + millcontext — no DB, no Spring. Legacy arithmetic is
 * transcribed VERBATIM from {@code CoreUtil} ({@code bigDecimalAddition} /
 * {@code bigDecimalDivision} / {@code sumBigDecimalAreas} / {@code sumBigDecimalCosts}): null is
 * the no-data signal everywhere — never zero. The null/zero-NAR division branches live HERE only:
 * delivery's {@code REFORESTED_NET_AREA} is NOT NULL (AC9 finding), so the IT seed cannot produce
 * them — the service stays defensive regardless.
 */
@ExtendWith(MockitoExtension.class)
class Schedule11ServiceTest {

  private static final long MILL = 610L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule11Repository repository;

  @Mock
  private MillContextService millContextService;

  @InjectMocks
  private Schedule11Service service;

  private void stubTrack(String code) {
    when(millContextService.findSchedule11TrackStatusCode(MILL, YEAR))
        .thenReturn(Optional.ofNullable(code));
  }

  private static SilvicultureLocationEntity location(
      long id, BigDecimal netArea, String enhancedInd) {
    return new SilvicultureLocationEntity(
        id, "Location " + id, enhancedInd, 8801L, "ICH", "dw", "1", null, netArea, null, 0);
  }

  private static SilvicultureCostEntity cost(long detailId, long locationId, int itemId, Integer cost) {
    return new SilvicultureCostEntity(detailId, locationId, itemId, cost);
  }

  // ---- BR-08 row derivations -------------------------------------------------------------------

  @Test
  void bothCostsPresent_totalAndPerNetAreaComputed() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, new BigDecimal("120.5"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(
        cost(1L, 9101L, 24, 25000), cost(2L, 9101L, 23, 10000)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertEquals(25000, row.actualCost());
    assertEquals(10000, row.plannedCost());
    assertEquals(35000, row.totalCost());
    // 35000 / 120.5 = 290.45643... -> scale 4 HALF_UP (recorded deviation from legacy display scale 2).
    assertEquals(new BigDecimal("290.4564"), row.costPerNetArea());
  }

  @Test
  void oneCostNull_additionIsNullTolerant() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, new BigDecimal("33.3"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(cost(1L, 9101L, 24, 4500)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertEquals(4500, row.actualCost());
    assertNull(row.plannedCost());
    assertEquals(4500, row.totalCost()); // null + x = x (CoreUtil.bigDecimalAddition)
    assertEquals(new BigDecimal("135.1351"), row.costPerNetArea());
  }

  @Test
  void noCostRows_totalAndPerNetAreaNull() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, new BigDecimal("10.25"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of());

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertNull(row.actualCost());
    assertNull(row.plannedCost());
    assertNull(row.totalCost()); // null + null = null — never zero
    assertNull(row.costPerNetArea());
  }

  @Test
  void wholeQuotient_servesMinScaleOne() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, new BigDecimal("50"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(cost(1L, 9101L, 23, 7000)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    // 7000 / 50 = 140 exactly: stripTrailingZeros then min scale 1 -> 140.0, not 140 or 1.4E+2.
    assertEquals(new BigDecimal("140.0"), row.costPerNetArea());
  }

  @Test
  void zeroNetArea_divisionIsNullNotError() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, BigDecimal.ZERO, "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(cost(1L, 9101L, 24, 1000)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertEquals(1000, row.totalCost());
    assertNull(row.costPerNetArea()); // CoreUtil.bigDecimalDivision: zero denominator -> null
  }

  @Test
  void nullNetArea_divisionIsNullNotError() {
    // Defensive-only branch: delivery REFORESTED_NET_AREA is NOT NULL (AC9), but the legacy
    // arithmetic tolerates null and the service must not NPE on unexpected data.
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, null, "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(cost(1L, 9101L, 24, 1000)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertNull(row.netArea());
    assertNull(row.costPerNetArea());
  }

  @Test
  void outOfScopeCostItem_isIgnoredInUnpacking() {
    // Defense in depth: the SQL filters IN (23,24), and the unpacking loop (legacy
    // Schedule11DAO.getSilvicultureReport) ALSO only assigns 23/24.
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL))
        .thenReturn(List.of(location(9101L, new BigDecimal("10"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(
        cost(1L, 9101L, 19, 99999), cost(2L, 9101L, 24, 500)));

    SilvicultureLocation row = service.getSchedule11(MILL, YEAR, true).locations().get(0);

    assertEquals(500, row.totalCost());
    assertNull(row.plannedCost());
  }

  @Test
  void enhancedIndicator_mapsYToTrueElseFalse() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of(
        location(9101L, BigDecimal.ONE, "Y"), location(9102L, BigDecimal.ONE, "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of());

    List<SilvicultureLocation> rows = service.getSchedule11(MILL, YEAR, true).locations();

    assertTrue(rows.get(0).enhancedIndicator());
    assertFalse(rows.get(1).enhancedIndicator());
  }

  @Test
  void becLabel_concatenatesWithNullsAsEmpty() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of(
        new SilvicultureLocationEntity(9101L, "A", "N", 8803L, "ESSF", "wc", "4", "a",
            BigDecimal.ONE, null, 0),
        new SilvicultureLocationEntity(9102L, "B", "N", 8802L, "CWH", "vm", null, null,
            BigDecimal.ONE, null, 0)));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of());

    List<SilvicultureLocation> rows = service.getSchedule11(MILL, YEAR, true).locations();

    assertEquals("ESSFwc4a", rows.get(0).becLabel()); // full zone+subzone+variant+phase
    assertEquals("CWHvm", rows.get(1).becLabel());    // nulls -> "" (getBiogeoSubZoneVariantPase)
  }

  // ---- BR-08 footer totals ---------------------------------------------------------------------

  @Test
  void footerTotals_sumRoundAndDivideLikeLegacy() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of(
        location(9101L, new BigDecimal("120.5"), "N"),
        location(9102L, new BigDecimal("33.3"), "N"),
        location(9103L, new BigDecimal("50"), "N"),
        location(9104L, new BigDecimal("10.25"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(
        cost(1L, 9101L, 24, 25000), cost(2L, 9101L, 23, 10000),
        cost(3L, 9102L, 24, 4500), cost(4L, 9103L, 23, 7000)));

    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);

    // sumBigDecimalAreas: 214.05 -> scale 1 HALF_UP -> 214.1 (footer rounding is real).
    assertEquals(new BigDecimal("214.1"), doc.totals().netArea());
    assertEquals(29500L, (long) doc.totals().actualCost());
    assertEquals(17000L, (long) doc.totals().plannedCost());
    assertEquals(46500L, (long) doc.totals().totalCost());
    // Divided by the ROUNDED footer area (legacy getter chain): 46500/214.1 -> 217.1882.
    assertEquals(new BigDecimal("217.1882"), doc.totals().costPerNetArea());
  }

  @Test
  void footerCostSum_beyondIntMax_doesNotOverflow() {
    // NUMBER(8,0) allows up to 99,999,999 per cost row; legacy summed with BigDecimal. A footer
    // sum across enough locations exceeds Integer.MAX_VALUE (~2.147e9) and must NOT wrap — an int
    // sum would silently corrupt (often negate) the footer money figures. 25 * 99,999,999 =
    // 2,499,999,975 proves it.
    stubTrack("D");
    List<SilvicultureLocationEntity> locs = new ArrayList<>();
    List<SilvicultureCostEntity> costs = new ArrayList<>();
    for (int i = 0; i < 25; i++) {
      long id = 9200 + i;
      locs.add(location(id, new BigDecimal("10"), "N"));
      costs.add(cost(7000 + i, id, 24, 99_999_999));
    }
    when(repository.findLocations(YEAR, MILL)).thenReturn(locs);
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(costs);

    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);

    assertEquals(2_499_999_975L, (long) doc.totals().actualCost());
    assertNull(doc.totals().plannedCost());
    assertEquals(2_499_999_975L, (long) doc.totals().totalCost());
  }

  @Test
  void emptyDocument_nullTotalsNeverZero() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of());
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of());

    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);

    assertTrue(doc.locations().isEmpty());
    assertNull(doc.totals().netArea());
    assertNull(doc.totals().actualCost());
    assertNull(doc.totals().plannedCost());
    assertNull(doc.totals().totalCost());
    assertNull(doc.totals().costPerNetArea());
    assertNull(doc.revisionCount()); // ALWAYS null: no ILCR_REPORT_SUMMARY row (AR11 keying delta)
  }

  @Test
  void footerCostsPartiallyNull_onlyContributorsSum() {
    stubTrack("D");
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of(
        location(9101L, new BigDecimal("10"), "N"), location(9102L, new BigDecimal("20"), "N")));
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of(cost(1L, 9101L, 24, 3000)));

    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);

    assertEquals(3000L, (long) doc.totals().actualCost());
    assertNull(doc.totals().plannedCost()); // no planned contributors anywhere -> null, not 0
    assertEquals(3000L, (long) doc.totals().totalCost());
  }

  // ---- editable matrix (AC6/AC7): EDIT_SCHEDULE ∧ silviculture track 'D' ------------------------

  @Test
  void draftTrackAndEditPermission_editable() {
    stubTrack("D");
    stubEmpty();
    assertTrue(service.getSchedule11(MILL, YEAR, true).editable());
  }

  @Test
  void draftTrackWithoutEditPermission_notEditable() {
    stubTrack("D");
    stubEmpty();
    assertFalse(service.getSchedule11(MILL, YEAR, false).editable());
  }

  @Test
  void submittedTrack_notEditableEvenWithPermission() {
    stubTrack("S");
    stubEmpty();
    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);
    assertEquals("S", doc.trackStatus());
    assertFalse(doc.editable());
  }

  @Test
  void verifiedTrack_notEditableForSubmitter() {
    stubTrack("V");
    stubEmpty();
    assertFalse(service.getSchedule11(MILL, YEAR, true).editable());
  }

  @Test
  void nullTrackStatus_servedAsNullAndNotEditable() {
    // Status row exists but MILL_SILVICULTUR_STATUS_CODE is NULL: cannot be Draft. Legacy renders
    // "Not Initiated" — display text is 25.3's concern.
    stubTrack(null);
    stubEmpty();
    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);
    assertNull(doc.trackStatus());
    assertFalse(doc.editable());
  }

  @Test
  void deadOpenedCode_passesThroughReadOnly() {
    // Dead 'O' passes through read-only per A-8 (stored legacy code served verbatim, not editable).
    stubTrack("O");
    stubEmpty();
    Schedule11Response doc = service.getSchedule11(MILL, YEAR, true);
    assertEquals("O", doc.trackStatus());
    assertFalse(doc.editable());
  }

  private void stubEmpty() {
    when(repository.findLocations(YEAR, MILL)).thenReturn(List.of());
    when(repository.findCostDetails(YEAR, MILL)).thenReturn(List.of());
  }
}
