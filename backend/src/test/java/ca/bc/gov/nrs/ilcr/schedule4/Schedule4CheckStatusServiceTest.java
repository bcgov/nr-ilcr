package ca.bc.gov.nrs.ilcr.schedule4;

import static ca.bc.gov.nrs.ilcr.support.TestAmounts.bd;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.SubPageRowRow;
import ca.bc.gov.nrs.ilcr.schedule4.dto.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule4.dto.LocationCheckResult;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@code Schedule4Service.checkStatus} (Story 4.4) — the requirement rule (AD-5) as
 * re-grounded by issue #465: legacy's Schedule 4 check required the location description and
 * nothing else. Mocked repository so it isolates that rule — a null Cost on a category or a
 * sub-page row is NOT a finding, a blank description is — plus per-location aggregation and the
 * schedule all-or-nothing MET.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4CheckStatusServiceTest {

  private static final long MILL = 560L;
  private static final int YEAR = 2021;

  @Mock private Schedule4Repository repository;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule4Service service;

  private void draft() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    lenient().when(repository.findSubPageRows(MILL, YEAR)).thenReturn(List.of());
  }

  @Test
  void namedLocationWithCosts_scheduleMet() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(1, "Loc A", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(1, 40, bd("100"), 5000)));

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertEquals(1, r.messages().size()); // schedule banner present
    assertEquals("scheduleRequirementsMetMsg", r.messages().get(0).key());
    LocationCheckResult a = r.locations().get(0);
    assertTrue(a.met());
    assertEquals("locationRequirementsMetMsg", a.messages().get(0).key());
    assertTrue(a.issues().isEmpty());
  }

  /**
   * Issue #465 — the finding the old rule raised. Legacy gated every category Cost check behind an
   * {@code isXxxToCheck} flag that was never true, so a Volume-only category was never reported.
   */
  @Test
  void nullCategoryCost_isNotAFinding_met() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(1, "Loc A", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(1, 40, bd("100"), null))); // Volume, no Cost

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertEquals("scheduleRequirementsMetMsg", r.messages().get(0).key());
    LocationCheckResult a = r.locations().get(0);
    assertTrue(a.met());
    assertTrue(a.issues().isEmpty());
    assertEquals("locationRequirementsMetMsg", a.messages().get(0).key());
  }

  /** The same for a distance-based category: neither its Cost nor its Distance is enforced. */
  @Test
  void nullDistanceCategoryCostAndDistance_notFindings_met() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(1, "Loc A", null, null, 0),
                new LocationRow(2, "Loc A", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(
            List.of(new DetailRow(1, 40, bd("100"), 5000), new DetailRow(2, 47, bd("50"), null)));

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertTrue(r.locations().get(0).issues().isEmpty());
  }

  /** Sub-page list rows (43/46/55) follow the same rule: a null Cost on a row is not a finding. */
  @Test
  void subPageRowNullCost_isNotAFinding_met() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(1, "Loc A", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(1, 40, bd("100"), 5000)));
    when(repository.findSubPageRows(MILL, YEAR))
        .thenReturn(
            List.of(
                new SubPageRowRow(
                    2, "Loc A", 43, "Towing", bd("10"), null, bd("5"), null))); // cost null

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("MET", r.outcome());
    assertTrue(r.locations().get(0).met());
    assertTrue(r.locations().get(0).issues().isEmpty());
  }

  /**
   * The one field legacy DID require (Schedule4CheckStatus.java:19-23). Unreachable through the
   * app's own write path (the request is {@code NotBlank}), so it is exercised here rather than
   * against the Oracle fixtures.
   */
  @Test
  void blankDescription_issuesWithValueRequired() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(1, "   ", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(List.of(new DetailRow(1, 40, bd("100"), 5000))); // Cost present — irrelevant

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome());
    assertTrue(r.messages().isEmpty()); // no schedule banner on ISSUES
    LocationCheckResult a = r.locations().get(0);
    assertFalse(a.met());
    assertEquals(1, a.issues().size());
    assertEquals(FieldIssue.LOCATION_DESCRIPTION, a.issues().get(0).code());
    assertEquals("missingRequiredFieldMsg", a.issues().get(0).message().key());
    assertTrue(a.messages().isEmpty()); // no per-location met message when failing
  }

  @Test
  void nullDescription_issues() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(List.of(new LocationRow(1, null, null, null, 0)));
    lenient().when(repository.findInScopeDetails(MILL, YEAR)).thenReturn(List.of());

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome());
    assertEquals(FieldIssue.LOCATION_DESCRIPTION, r.locations().get(0).issues().get(0).code());
  }

  @Test
  void mixed_someLocationsPassOthersFail_scheduleNotMet() {
    draft();
    when(repository.findLocations(MILL, YEAR))
        .thenReturn(
            List.of(
                new LocationRow(1, "Pass Loc", null, null, 0),
                new LocationRow(2, "", null, null, 0)));
    when(repository.findInScopeDetails(MILL, YEAR))
        .thenReturn(
            List.of(
                new DetailRow(1, 40, bd("100"), null), // Pass Loc: Volume-only, still passes
                new DetailRow(2, 41, bd("200"), 300))); // blank name fails regardless of its Cost

    Schedule4CheckStatusResponse r = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", r.outcome()); // all-or-nothing: one failure fails the schedule
    assertTrue(r.messages().isEmpty());
    assertTrue(r.locations().get(0).met()); // Pass Loc
    assertFalse(r.locations().get(1).met()); // the unnamed one
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
