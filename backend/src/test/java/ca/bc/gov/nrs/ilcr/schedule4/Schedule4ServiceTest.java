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
 * — no DB, no Spring. Covers grouping a FAMILY of TRANSPORTATION_REPORT rows (sharing
 * LOCATION_DESCRIPTION) into one location, in-scope category mapping, FIXED vs DISTANCE kind,
 * <b>per-category distance taken from each category's own report</b> (delivery-DB confirmed — two
 * distance categories on one location can differ), perUnit derivation, the missing-category-data
 * (null cost) case, the name-only location, editability, and the no-locations empty list.
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

  /**
   * Two-location Draft fixture matching the V7 514/2021 numbers. "Harbour Dump" is a FAMILY: a primary
   * report 7001 (distance null) with the fixed categories, plus report 7011 (distance 120.5) for
   * category 47 and report 7012 (distance 88.0 — DIFFERENT) for category 52.
   */
  private void stubTwoLocationDraft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Harbour Dump", null),                 // primary, no distance
        new LocationRow(7011, "Harbour Dump", new BigDecimal("120.5")), // 47's own report
        new LocationRow(7012, "Harbour Dump", new BigDecimal("88.0")),  // 52's own report
        new LocationRow(7002, "Empty Landing", null)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, new BigDecimal("2000"), 100000), // fixed
        new DetailRow(7001, 41, new BigDecimal("4000"), 60000),  // fixed
        new DetailRow(7011, 47, new BigDecimal("500"), 25000),   // distance (own report 7011)
        new DetailRow(7012, 52, new BigDecimal("300"), null)));  // distance (own report 7012), null cost
  }

  @Test
  void reportFamilyGroupedByName_orderedAndNameOnlyLocationHasEmptyCategories() {
    stubTwoLocationDraft();
    Schedule4Response doc = service.getSchedule4(MILL, YEAR, true);
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
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
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
    eq("88.0", railHaul.distance());
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
        new LocationRow(7003, "Submitted Dump", null)));
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
    // serializes as an integer and a decimal drops trailing zeros — Schedule 1/2 wire-contract parity
    // (compareTo is scale-insensitive and would not catch a 2000.0000 regression). Distance is
    // per-category (from category 47's own report 7011).
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(7001, "Scale", null),
        new LocationRow(7011, "Scale", new BigDecimal("120.5000"))));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(7001, 40, new BigDecimal("2000.0000"), 100000),
        new DetailRow(7011, 47, new BigDecimal("500.0000"), 25000)));
    Location a = service.getSchedule4(MILL, YEAR, true).locations().get(0);
    assertEquals("2000", categoryByCode(a, 40).volume().toPlainString());
    assertEquals("120.5", categoryByCode(a, 47).distance().toPlainString());
  }
}
