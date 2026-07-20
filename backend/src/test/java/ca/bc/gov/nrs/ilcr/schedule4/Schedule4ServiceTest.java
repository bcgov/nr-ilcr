package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 4 read assembly + server-side derivation (AD-5/AD-6). Mocked repository
 * — no DB, no Spring. Covers TR-row→location grouping, in-scope category mapping, FIXED vs DISTANCE
 * kind, per-location distance mirrored onto distance-based categories, perUnit derivation, the
 * missing-category-data (null cost) case, the name-only location, editability, and the no-locations
 * empty list.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule4Repository repository;

  @InjectMocks
  private Schedule4Service service;

  private static void eq(String expected, BigDecimal actual) {
    assertEquals(0, new BigDecimal(expected).compareTo(actual),
        () -> "expected " + expected + " but was " + actual);
  }

  private static CategoryAmount categoryByCode(Location location, int code) {
    return location.categories().stream()
        .filter(c -> c.code() == code)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no category " + code + " in " + location.name()));
  }

  /** Two-location Draft fixture matching the V7 514/2021 numbers. */
  private void stubTwoLocationDraft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Harbour Dump", new BigDecimal("120.5")),
        new LocationRow(7002, "Empty Landing", null)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, new BigDecimal("2000"), 100000), // fixed
        new DetailRow(7001, 41, new BigDecimal("4000"), 60000),  // fixed
        new DetailRow(7001, 47, new BigDecimal("500"), 25000),   // distance
        new DetailRow(7001, 52, new BigDecimal("300"), null)));  // distance, null cost
  }

  @Test
  void locationsGroupedByTrRow_orderedAndNameOnlyLocationHasEmptyCategories() {
    stubTwoLocationDraft();
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, true);
    assertEquals(2, doc.locations().size());
    Location a = doc.locations().get(0);
    Location b = doc.locations().get(1);
    assertEquals("Harbour Dump", a.name());
    assertEquals(4, a.categories().size());
    // Name-only location (no detail rows) present with empty category list.
    assertEquals("Empty Landing", b.name());
    assertTrue(b.categories().isEmpty());
    assertNull(b.distance());
  }

  @Test
  void perLocationDistance_mirroredOntoDistanceCategoriesOnly() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    eq("120.5", a.distance());
    // FIXED category: no distance.
    CategoryAmount lakeside = categoryByCode(a, 40);
    assertEquals("FIXED", lakeside.kind());
    assertNull(lakeside.distance());
    // DISTANCE category: distance mirrors the location distance.
    CategoryAmount truckBarge = categoryByCode(a, 47);
    assertEquals("DISTANCE", truckBarge.kind());
    eq("120.5", truckBarge.distance());
  }

  @Test
  void perUnit_computedServerSide() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    eq("50.0", categoryByCode(a, 40).perUnit()); // 100000/2000
    eq("15.0", categoryByCode(a, 41).perUnit()); // 60000/4000
    eq("50.0", categoryByCode(a, 47).perUnit()); // 25000/500
  }

  @Test
  void missingCategoryData_nullCost_showsVolumeAndNullPerUnit() {
    stubTwoLocationDraft();
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    CategoryAmount railHaul = categoryByCode(a, 52);
    eq("300", railHaul.volume());  // present value shown
    assertNull(railHaul.cost());   // missing cost
    assertNull(railHaul.perUnit()); // perUnit null when cost null
  }

  @Test
  void noLocations_returnsEmptyListNot404() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, true);
    assertTrue(doc.locations().isEmpty());
    assertTrue(doc.editable()); // editable per Draft track even with no locations
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stubTwoLocationDraft();
    assertTrue(service.getSchedule4(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenNotDraft_locationsStillListed() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7003, "Submitted Dump", new BigDecimal("42.0"))));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7003, 42, new BigDecimal("1000"), 20000)));
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, true);
    assertFalse(doc.editable());
    assertEquals(1, doc.locations().size()); // still listed
    assertEquals("S", doc.trackStatus());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stubTwoLocationDraft();
    assertFalse(service.getSchedule4(MILL, YEAR, false).editable());
  }

  @Test
  void perUnit_nullWhenVolumeZero() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Zero Vol", null)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, BigDecimal.ZERO, 25000)));
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    assertNull(categoryByCode(a, 40).perUnit());
  }

  @Test
  void perUnit_roundsToScale4HalfUp_onNonTerminatingQuotient() {
    // 200000 / 30000 = 6.66666... -> scale-4 HALF_UP -> 6.6667.
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Round", null)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, new BigDecimal("30000"), 200000)));
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    assertEquals("6.6667", categoryByCode(a, 40).perUnit().toPlainString());
  }

  @Test
  void volumeAndDistance_normalizedToNaturalForm() {
    // Oracle NUMBER(18,4) returns scale-4 values (2000.0000, 120.5000). Normalize so a whole value
    // serializes as an integer and a decimal drops trailing zeros — Schedule 1/2 wire-contract
    // parity (compareTo is scale-insensitive and would not catch a 2000.0000 regression).
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Scale", new BigDecimal("120.5000"))));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, new BigDecimal("2000.0000"), 100000),
        new DetailRow(7001, 47, new BigDecimal("500.0000"), 25000)));
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    assertEquals("120.5", a.distance().toPlainString());
    assertEquals("2000", categoryByCode(a, 40).volume().toPlainString());
    assertEquals("120.5", categoryByCode(a, 47).distance().toPlainString());
  }
}
