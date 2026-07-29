package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.SubPageRowRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.LocationCheckResult;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@code Schedule4Service.checkStatus} (Story 4.4) — the requirement rule (AD-5). Mocked
 * repository so it isolates the null-only Cost check (0 = present), the distance-not-enforced default
 * (§Decision 2), sub-page-row Cost enforcement, per-location aggregation, and the schedule
 * all-or-nothing MET.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4CheckStatusServiceTest {

  private static final long MILL = 560L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule4Repository repository;

  @InjectMocks
  private Schedule4Service service;

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private void draft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  @Test
  void allCostsPresent_scheduleMet() {
    draft();
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(1, "Loc A", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), 5000)));

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertEquals(1, r.messages().size());               // schedule banner present
    assertEquals("scheduleRequirementsMetMsg", r.messages().get(0).key());
    LocationCheckResult a = r.locations().get(0);
    assertTrue(a.met());
    assertEquals("locationRequirementsMetMsg", a.messages().get(0).key());
    assertTrue(a.issues().isEmpty());
  }

  @Test
  void zeroCost_countsAsPresent_met() {
    draft();
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(new LocationRow(1, "Loc A", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), 0))); // cost 0 is present, NOT missing

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertTrue(r.locations().get(0).met());
  }

  @Test
  void nullCategoryCost_issuesWithValueRequired() {
    draft();
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(new LocationRow(1, "Loc A", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), null))); // cost missing

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome());
    assertTrue(r.messages().isEmpty());                 // no schedule banner on ISSUES
    LocationCheckResult a = r.locations().get(0);
    assertFalse(a.met());
    assertEquals(1, a.issues().size());
    assertEquals(40, a.issues().get(0).code());
    assertEquals("missingRequiredFieldMsg", a.issues().get(0).message().key());
    assertTrue(a.messages().isEmpty());                 // no per-location met message when failing
  }

  @Test
  void distanceNotEnforced_costPresentButDistanceNull_met() {
    draft();
    // A distance category (47) whose report DISTANCE is null but Cost is present -> still MET
    // (§Decision 2: distance is not enforced; only Cost is).
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(1, "Loc A", null, 0),
        new LocationRow(2, "Loc A", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), 5000),
        new DetailRow(2, 47, bd("50"), 2000)));

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
  }

  @Test
  void subPageRowNullCost_fails() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(new LocationRow(1, "Loc A", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), 5000)));
    when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of(
        new SubPageRowRow(2, "Loc A", 43, "Towing", bd("10"), null, bd("5"), null))); // cost null

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome());
    assertFalse(r.locations().get(0).met());
    assertEquals(43, r.locations().get(0).issues().get(0).code());
  }

  @Test
  void mixed_someLocationsPassOthersFail_scheduleNotMet() {
    draft();
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of(
        new LocationRow(1, "Pass Loc", null, 0),
        new LocationRow(2, "Fail Loc", null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of(
        new DetailRow(1, 40, bd("100"), 5000),   // Pass Loc OK
        new DetailRow(2, 41, bd("200"), null))); // Fail Loc missing cost

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome());          // all-or-nothing: one failure fails the schedule
    assertTrue(r.messages().isEmpty());
    assertTrue(r.locations().get(0).met());       // Pass Loc
    assertFalse(r.locations().get(1).met());      // Fail Loc
  }

  @Test
  void noLocations_vacuouslyMet() {
    draft();
    when(repository.findLocations(MILL, YEAR)).thenReturn(List.of());
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertTrue(r.locations().isEmpty());
  }
}
