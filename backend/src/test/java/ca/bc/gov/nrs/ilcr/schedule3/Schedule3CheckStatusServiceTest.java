package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageInfo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 3 Check Status logic (Story 4.2, BR-11/BR-03/BR-10). Mocked repository +
 * message source — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3CheckStatusServiceTest {

  private static final long MILL = 543L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule3Repository repository;

  @Mock
  private Schedule1Service schedule1Service;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule3Service service;

  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null, null);
  }

  private static DetailRow volume(int code, String vol) {
    return new DetailRow(code, new BigDecimal(vol), null, null, null);
  }

  /** A complete, valid document: all 11 Harvest present, 8 PO&P present with harvest ≥ pop, volumes. */
  private static List<DetailRow> fullValid() {
    List<DetailRow> rows = new ArrayList<>();
    for (int harvest : new int[] {27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37}) {
      rows.add(cost(harvest, 1000));
    }
    for (int pop : new int[] {125, 126, 128, 129, 130, 132, 133, 134}) {
      rows.add(cost(pop, 500));
    }
    rows.add(volume(118, "1000"));
    rows.add(volume(119, "1000"));
    return rows;
  }

  private void stub(String location, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1044, location, "c", 0)));
    when(repository.findDetails(1044)).thenReturn(details);
    // Which message keys resolve depends on the scenario, so keep these lenient.
    lenient().when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value Required");
    lenient().when(messageSource.getMessage(eq("harvestNotGreaterThanPopErrorMsg"), any(), any(), any(Locale.class)))
        .thenReturn("Value must be greater than or equal to the corresponding PO&P Cost");
    lenient().when(messageSource.getMessage(eq("scheduleRequirementsMetMsg"), any(), any(), any(Locale.class)))
        .thenReturn("All requirements for this schedule have been met");
  }

  private static boolean hasError(CheckStatusResponse r, String key, String labelFragment) {
    return r.errors().stream()
        .anyMatch(m -> m.key().equals(key) && m.text().contains(labelFragment));
  }

  @Test
  void allPresentValid_requirementsMet() {
    stub("N", fullValid());
    CheckStatusResponse result = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(result.requirementsMet());
    assertTrue(result.errors().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", result.message().key());
  }

  @Test
  void emptyDocument_reportsMissingRequiredFields() {
    stub("N", List.of());
    CheckStatusResponse result = service.checkSchedule3Status(MILL, YEAR);
    assertFalse(result.requirementsMet());
    assertTrue(hasError(result, "missingRequiredFieldMsg", "Licence, Fees, Insurance (Harvest Total $)"));
    assertTrue(hasError(result, "missingRequiredFieldMsg", "Annual Rents (Harvest Total $)"));
    assertTrue(hasError(result, "missingRequiredFieldMsg", "Crown Timber (Harvest Volume)"));
    // Harvest-only lines never emit a PO&P-required error.
    assertFalse(hasError(result, "missingRequiredFieldMsg", "Annual Rents (PO&P Total $)"));
  }

  @Test
  void harvestLessThanPop_reportsBr03Violation() {
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.removeIf(r -> r.costItemCode() == 27 || r.costItemCode() == 125);
    rows.add(cost(27, 100));    // Licenses harvest 100 < pop 500 → BR-03 violation
    rows.add(cost(125, 500));
    stub("N", rows);
    CheckStatusResponse result = service.checkSchedule3Status(MILL, YEAR);
    assertFalse(result.requirementsMet());
    assertTrue(hasError(result, "harvestNotGreaterThanPopErrorMsg",
        "Licence, Fees, Insurance (Harvest Total $)"));
  }

  @Test
  void overrideYes_suppressesBr03OnAllLines() {
    List<DetailRow> rows = new ArrayList<>(fullValid());
    rows.removeIf(r -> r.costItemCode() == 27 || r.costItemCode() == 125);
    rows.add(cost(27, 100));    // harvest < pop, but override = "Y"
    rows.add(cost(125, 500));
    stub("Y", rows);
    CheckStatusResponse result = service.checkSchedule3Status(MILL, YEAR);
    assertTrue(result.requirementsMet());  // BR-03 suppressed; everything else present
    assertFalse(hasError(result, "harvestNotGreaterThanPopErrorMsg",
        "Licence, Fees, Insurance (Harvest Total $)"));
  }
}
