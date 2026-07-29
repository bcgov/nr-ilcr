package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CheckStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the read-only Check Status evaluation (BR-07). Mocked repository — no DB, no Spring.
 * MET when the assembled document's {@code purchasedLogCost.cost} (item 25) is present; ISSUES when
 * it is absent, including the saved-summary-without-item-25 and the unsaved-schedule states. The
 * evaluation runs off the same {@code getSchedule2} assembly and returns the bundle keys only — the
 * controller resolves verbatim text (AD-8).
 */
@ExtendWith(MockitoExtension.class)
class Schedule2CheckStatusServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule2Repository repository;

  @InjectMocks
  private Schedule2Service service;

  private void stubCrossScheduleAbsent(String trackStatus, Optional<SummaryRow> summary,
      List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR)).thenReturn(summary);
    summary.ifPresent(s -> lenient().when(repository.findDetails(s.summaryId())).thenReturn(details));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    lenient().when(repository.findSch3PopTimberVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3PopActualCost(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch3CrownVolume(MILL, YEAR)).thenReturn(Optional.empty());
    lenient().when(repository.findSch1SubtotalLoggingCost(MILL, YEAR)).thenReturn(Optional.empty());
  }

  @Test
  void met_whenItem25CostPresent() {
    stubCrossScheduleAbsent("D", Optional.of(new SummaryRow(1002, "c", 0)),
        List.of(new DetailRow(25, null, 500000), new DetailRow(26, new BigDecimal("2000"), 100000)));

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("MET", status.outcome());
    assertEquals(1, status.messages().size());
    assertEquals("scheduleRequirementsMetMsg", status.messages().get(0).key());
  }

  @Test
  void issues_whenSavedSummaryHasNoItem25() {
    // Saved category-"2" summary, item 26 present but item 25 (purchasedLogCost cost) absent -> ISSUES.
    stubCrossScheduleAbsent("D", Optional.of(new SummaryRow(1028, "c", 3)),
        List.of(new DetailRow(26, new BigDecimal("500"), 25000)));

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", status.outcome());
    assertEquals(1, status.messages().size());
    assertEquals("missingRequiredFieldMsg", status.messages().get(0).key());
  }

  @Test
  void issues_whenUnsavedSchedule() {
    // No category-"2" summary at all (unsaved) -> no item-25 cost -> ISSUES (never 404).
    stubCrossScheduleAbsent("D", Optional.empty(), List.of());

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", status.outcome());
    assertEquals("missingRequiredFieldMsg", status.messages().get(0).key());
  }

  @Test
  void met_whenItem25CostIsZero() {
    // BR-07 is present-vs-absent (null check), NOT > 0: a stored cost of 0 is PRESENT -> MET.
    // Pins the null-vs-zero boundary a `> 0`/truthiness regression would silently flip.
    stubCrossScheduleAbsent("D", Optional.of(new SummaryRow(1002, "c", 0)),
        List.of(new DetailRow(25, null, 0)));

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("MET", status.outcome());
    assertEquals("scheduleRequirementsMetMsg", status.messages().get(0).key());
  }

  @Test
  void issues_whenItem25RowPresentButCostNull() {
    // An item-25 row that exists but whose COST column is null is still "absent cost" -> ISSUES
    // (distinct from the row being missing entirely).
    stubCrossScheduleAbsent("D", Optional.of(new SummaryRow(1002, "c", 0)),
        List.of(new DetailRow(25, null, null)));

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("ISSUES", status.outcome());
    assertEquals("missingRequiredFieldMsg", status.messages().get(0).key());
  }

  @Test
  void met_whenItem25PresentOnNonDraft() {
    // Check Status is read-only and NOT gated on editability: a non-Draft ("S") schedule that has
    // item-25 still evaluates MET. Guards against a future Draft/editable gate creeping in.
    stubCrossScheduleAbsent("S", Optional.of(new SummaryRow(1002, "c", 4)),
        List.of(new DetailRow(25, null, 400000)));

    CheckStatusResponse status = service.checkStatus(MILL, YEAR);

    assertEquals("MET", status.outcome());
  }
}
