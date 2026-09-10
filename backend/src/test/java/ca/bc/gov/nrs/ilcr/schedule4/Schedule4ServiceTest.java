package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.SubPageRowRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.TransportationSnapshotRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 4 read assembly + server-side derivation (AD-5/AD-6). Mocked
 * repository — no DB, no Spring. Covers grouping a FAMILY of TRANSPORTATION_REPORT rows (sharing
 * LOCATION_DESCRIPTION) into one location, in-scope category mapping, FIXED vs DISTANCE kind,
 * <b>per-category distance taken from each category's own report</b> (delivery-DB confirmed — two
 * distance categories on one location can differ), perUnit derivation, the missing-category-data
 * (null cost) case, the name-only location, editability, and the no-locations empty list.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule4Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule4Service service;

  @BeforeEach
  void noSubPageRowsByDefault() {
    // Story 4.3 read extension: getSchedule4 now queries sub-page rows; these read tests assert the
    // category grid only, so default the sub-page query to empty (per-test overrides as needed).
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  private static void eq(String expected, BigDecimal actual) {
    assertEquals(
        0,
        new BigDecimal(expected).compareTo(actual),
        () -> "expected " + expected + " but was " + actual);
  }

  private static CategoryAmount categoryByCode(Location location, int code) {
    return location.categories().stream()
        .filter(c -> c.code() == code)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no category " + code + " in " + location.name()));
  }

  /**
   * Two-location Draft fixture matching the V7 514/2021 numbers. "Harbour Dump" is a FAMILY: a
   * primary report 7001 (distance null) with the fixed categories, plus report 7011 (distance
   * 120.5) for category 47 and report 7012 (distance 88.5 — DIFFERENT) for category 52.
   */
  private void stubTwoLocationDraft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(7001, "Harbour Dump", null, null, 0), // primary, no distance
                new LocationRow(
                    7011, "Harbour Dump", new BigDecimal("120.5"), null, 0), // 47's own report
                new LocationRow(
                    7012, "Harbour Dump", new BigDecimal("88.5"), null, 0), // 52's own report
                new LocationRow(7002, "Empty Landing", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(
            List.of(
                new DetailRow(7001, 40, new BigDecimal("2000"), 100000), // fixed
                new DetailRow(7001, 41, new BigDecimal("4000"), 60000), // fixed
                new DetailRow(7011, 47, new BigDecimal("500"), 25000), // distance (own report 7011)
                new DetailRow(
                    7012,
                    52,
                    new BigDecimal("300"),
                    null))); // distance (own report 7012), null cost
  }

  @Test
  void reportFamilyGroupedByName_orderedAndNameOnlyLocationHasEmptyCategories() {
    stubTwoLocationDraft();
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER);
    // Four TR rows collapse to TWO locations (grouped by LOCATION_DESCRIPTION).
    assertEquals(2, doc.locations().size());
    Location a = doc.locations().get(0);
    Location b = doc.locations().get(1);
    assertEquals("Harbour Dump", a.name());
    assertEquals(4, a.categories().size()); // 40, 41, 47, 52
    // Name-only location (no detail rows) present with empty category list.
    assertEquals("Empty Landing", b.name());
    assertTrue(b.categories().isEmpty());
  }

  @Test
  void perCategoryDistance_fromOwnReport_canDifferAcrossDistanceCategories() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    // FIXED category: no distance.
    CategoryAmount lakeside = categoryByCode(a, 40);
    assertEquals("FIXED", lakeside.kind());
    assertNull(lakeside.distance());
    // DISTANCE categories carry their OWN report's distance — and they differ.
    CategoryAmount truckBarge = categoryByCode(a, 47);
    assertEquals("DISTANCE", truckBarge.kind());
    eq("120.5", truckBarge.distance());
    CategoryAmount railHaul = categoryByCode(a, 52);
    assertEquals("DISTANCE", railHaul.kind());
    eq("88.5", railHaul.distance());
  }

  @Test
  void perUnit_computedServerSide() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    eq("50.0", categoryByCode(a, 40).perUnit()); // 100000/2000
    eq("15.0", categoryByCode(a, 41).perUnit()); // 60000/4000
    eq("50.0", categoryByCode(a, 47).perUnit()); // 25000/500
  }

  @Test
  void missingCategoryData_nullCost_showsVolumeAndNullPerUnit() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    CategoryAmount railHaul = categoryByCode(a, 52);
    eq("300", railHaul.volume()); // present value shown
    assertNull(railHaul.cost()); // missing cost
    assertNull(railHaul.perUnit()); // perUnit null when cost null
  }

  @Test
  void noLocations_returnsEmptyListNot404() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER);
    assertTrue(doc.locations().isEmpty());
    assertTrue(doc.editable()); // editable per Draft track even with no locations
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stubTwoLocationDraft();
    assertTrue(service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  void editable_falseWhenNotDraft_locationsStillListed() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(7003, "Submitted Dump", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(7003, 42, new BigDecimal("1000"), 20000)));
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER);
    assertFalse(doc.editable());
    assertEquals(1, doc.locations().size()); // still listed
    assertEquals("S", doc.trackStatus());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stubTwoLocationDraft();
    assertFalse(service.getSchedule4(MILL, YEAR, CallerRights.NONE).editable());
  }

  @Test
  void perUnit_nullWhenVolumeZero() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(7001, "Zero Vol", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(7001, 40, BigDecimal.ZERO, 25000)));
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    assertNull(categoryByCode(a, 40).perUnit());
  }

  @Test
  void perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient() {
    // 200000 / 30000 = 6.66666... -> scale-4 HALF_UP -> 6.6667.
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(7001, "Round", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(7001, 40, new BigDecimal("30000"), 200000)));
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    assertEquals("6.6667", categoryByCode(a, 40).perUnit().toPlainString());
  }

  @Test
  void volumeAndDistance_normalizedToNaturalForm() {
    // Oracle NUMBER(18,4) returns scale-4 values (2000.0000, 120.5000). Normalize so a whole value
    // serializes as an integer and a decimal drops trailing zeros — Schedule 1/2 wire-contract
    // parity
    // (compareTo is scale-insensitive and would not catch a 2000.0000 regression). Distance is
    // per-category (from category 47's own report 7011).
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(7001, "Scale", null, null, 0),
                new LocationRow(7011, "Scale", new BigDecimal("120.5000"), null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(
            List.of(
                new DetailRow(7001, 40, new BigDecimal("2000.0000"), 100000),
                new DetailRow(7011, 47, new BigDecimal("500.0000"), 25000)));
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    assertEquals("2000", categoryByCode(a, 40).volume().toPlainString());
    assertEquals("120.5", categoryByCode(a, 47).distance().toPlainString());
  }

  // -----------------------------------------------------------------------------------------
  // Original values (Story 16.2, BR-04). Schedule 4 addresses a submitted value TWO ways — the
  // report-level fields by report id, the cost figures by (report id, cost item) — because one
  // location's family spans several reports that all reuse the same cost items. A key slip
  // between the two is invisible to every other assertion in this class.
  // -----------------------------------------------------------------------------------------

  /** A submitted cost-detail row, addressed by its owning report + cost item. */
  private static CostDetailSnapshotRepository.Row snapshotDetail(
      int reportId, int costItemCode, String volume, Integer cost, String description) {
    return new CostDetailSnapshotRepository.Row(
        reportId * 10 + costItemCode,
        (long) reportId,
        costItemCode,
        volume == null ? null : new BigDecimal(volume),
        cost,
        description,
        null);
  }

  private static void assertOriginal(
      Map<String, OriginalValue> originals, String field, String value, String formatted) {
    OriginalValue original = originals.get(field);
    assertNotNull(original, () -> "no original for " + field + " in " + originals.keySet());
    assertEquals(value, original.value());
    assertEquals(OriginalValuesFixture.tooltip(formatted), original.tooltip());
  }

  /**
   * One submitted location family: primary report 7001 (fixed category 40) plus report 7011, which
   * owns distance category 47 and its own distance.
   */
  private void stubSubmittedFamily() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(7001, "Harbour Dump", null, null, 3),
                new LocationRow(7011, "Harbour Dump", new BigDecimal("120.5"), null, 3)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(
            List.of(
                new DetailRow(7001, 40, new BigDecimal("2000"), 100000),
                new DetailRow(7011, 47, new BigDecimal("500"), 25000)));
  }

  @Test
  void originalValues_absentAtDraft_andNoSnapshotQueryIssued() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    assertNull(a.originalValues(), "Draft must serve no original-value map at all");
    assertNull(categoryByCode(a, 40).originalValues());
    verify(repository, never()).findTransportationSnapshots(anyLong(), anyInt());
    verify(costSnapshots, never()).findByTransportationReports(anyList());
  }

  @Test
  void originalValues_submitted_locationNameAndPerCategoryFigures() {
    stubSubmittedFamily();
    when(repository.findTransportationSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(
                new TransportationSnapshotRow(7001, "Harbour Dock", null, null, "old comment"),
                new TransportationSnapshotRow(
                    7011, "Harbour Dock", new BigDecimal("110.5"), null, null)));
    when(costSnapshots.findByTransportationReports(List.of(7001L, 7011L)))
        .thenReturn(
            List.of(
                snapshotDetail(7001, 40, "1900", 95000, null),
                snapshotDetail(7011, 47, "450", 22000, null)));

    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);

    // The location name is read off the PRIMARY report (7001) of the family.
    assertOriginal(a.originalValues(), "name", "Harbour Dock", "Harbour Dock");
    // Legacy declares no original for a location's comments, so the key must stay absent even
    // though the snapshot row carries one.
    assertEquals(Set.of("name"), a.originalValues().keySet());

    // A FIXED category: volume + cost from its own report's row, and no distance at all.
    Map<String, OriginalValue> fixed = categoryByCode(a, 40).originalValues();
    assertOriginal(fixed, "volume", "1900", "1,900");
    assertOriginal(fixed, "cost", "95000", "95,000");
    assertEquals(Set.of("volume", "cost"), fixed.keySet());
  }

  @Test
  void originalValues_distanceCategory_readsItsOwnReportsSubmittedDistance() {
    stubSubmittedFamily();
    when(repository.findTransportationSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(
                new TransportationSnapshotRow(7001, "Harbour Dock", null, null, null),
                new TransportationSnapshotRow(
                    7011, "Harbour Dock", new BigDecimal("110.5"), null, null)));
    when(costSnapshots.findByTransportationReports(List.of(7001L, 7011L)))
        .thenReturn(List.of(snapshotDetail(7011, 47, "450", 22000, null)));

    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);

    // Distance lives on the REPORT, not the detail — and on 47's own report (7011), not the
    // family primary. Reading the primary would yield no distance key at all here.
    Map<String, OriginalValue> distance = categoryByCode(a, 47).originalValues();
    assertOriginal(distance, "volume", "450", "450");
    assertOriginal(distance, "cost", "22000", "22,000");
    assertOriginal(distance, "distance", "110.5", "110.5");
  }

  @Test
  void originalValues_subPageRow_allFiveFields_cycleFromTheReport() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(7001, "Harbour Dump", null, null, 3),
                new LocationRow(7046, "Harbour Dump", new BigDecimal("30.0"), null, 3)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    when(repository.findSubPageRows(MILL, YEAR))
        .thenReturn(
            List.of(
                new SubPageRowRow(
                    7046,
                    "Harbour Dump",
                    46, // Truck Rehaul — the one sub-page whose report carries a cycle time
                    "Rehaul A",
                    new BigDecimal("30.0"),
                    7,
                    new BigDecimal("800"),
                    12000)));
    when(repository.findTransportationSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(
                new TransportationSnapshotRow(7001, "Harbour Dock", null, null, null),
                new TransportationSnapshotRow(
                    7046, "Harbour Dock", new BigDecimal("28.5"), new BigDecimal("6.5"), null)));
    when(costSnapshots.findByTransportationReports(List.of(7001L, 7046L)))
        .thenReturn(List.of(snapshotDetail(7046, 46, "750", 11000, "Rehaul Original")));

    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);
    SubPageRow row = a.subPageRows().get(0);

    // Description/volume/cost off the cost row; distance/cycle off the row's own report — the two
    // sources legacy tracked together on these rows (Schedule4DAO.java:346-382).
    assertOriginal(row.originalValues(), "description", "Rehaul Original", "Rehaul Original");
    assertOriginal(row.originalValues(), "volume", "750", "750");
    assertOriginal(row.originalValues(), "cost", "11000", "11,000");
    assertOriginal(row.originalValues(), "distance", "28.5", "28.5");
    assertOriginal(row.originalValues(), "cycle", "6.5", "6.5");
  }

  @Test
  void originalValues_submittedButNoSnapshotOnFile_isEmptyMapNotNull() {
    // Beyond Draft with nothing on file is NOT Draft: the page must still evaluate the
    // "value added since submission" branch per field, so the maps are empty rather than null.
    stubSubmittedFamily();
    when(repository.findTransportationSnapshots(MILL, YEAR)).thenReturn(List.of());
    when(costSnapshots.findByTransportationReports(List.of(7001L, 7011L))).thenReturn(List.of());

    Location a = service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().get(0);

    assertNotNull(a.originalValues());
    assertTrue(a.originalValues().isEmpty());
    assertNotNull(categoryByCode(a, 40).originalValues());
    assertTrue(categoryByCode(a, 40).originalValues().isEmpty());
  }

  @Test
  void originalValues_noLocations_readsNoCostSnapshot() {
    // No locations beyond Draft: there is no parent id to filter on, so the bulk cost query must
    // not be issued at all — an empty IN-list is a syntax error on Oracle.
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());

    assertTrue(service.getSchedule4(MILL, YEAR, CallerRights.SUBMITTER).locations().isEmpty());
    verify(costSnapshots, never()).findByTransportationReports(anyList());
  }
}
