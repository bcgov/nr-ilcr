package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.CostRow;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.RecordRow;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 9 document assembly + server-side derivation (Story 9.1 / T3) with a
 * mocked repository — the $/Unit null-safe divide, the server-authoritative {@code editable} branch,
 * and the record↔cost-line join, without a database. The SQL itself is exercised against real
 * Oracle by {@link Schedule9DocumentIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule9Service — assembly + $/Unit derivation")
class Schedule9ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule9Repository repository;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule9Service service;

  /** A record row with the given id, units, and free-text descriptions blank unless overridden. */
  private static RecordRow record(int id, BigDecimal units) {
    return new RecordRow(id, 0, "CTR-1", units, 25, "comment", "M3", "Cubic Metres", null, "A",
        "Actual Cost", null, "BZ1", "BEC Zone One");
  }

  private void stub(String trackStatus, List<RecordRow> records, List<CostRow> costLines) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    when(repository.findRecords(MILL, YEAR)).thenReturn(records);
    when(repository.findCostLines(MILL, YEAR)).thenReturn(costLines);
  }

  @Test
  @DisplayName("$/Unit = cost ÷ units, scale 2 HALF_UP, joined to the record's cost line")
  void derivesCostPerUnit() {
    stub("D", List.of(record(9101, new BigDecimal("100.0"))),
        List.of(new CostRow(9101, 108, "Cattleguard", null, 5000)));

    ContractualWorkRecord row = service.getSchedule9(MILL, YEAR, true).records().get(0);

    assertEquals(0, new BigDecimal("50.00").compareTo(row.costPerUnit()));
    assertEquals(5000, row.cost());
    assertEquals("108", row.contractualItem().code());
    assertEquals("Cattleguard", row.contractualItem().description());
  }

  @Test
  @DisplayName("zero units -> $/Unit null (S14), even though a cost is stored")
  void nullCostPerUnit_whenUnitsZero() {
    stub("D", List.of(record(9102, BigDecimal.ZERO)),
        List.of(new CostRow(9102, 109, "Pipeline Crossing", null, 3000)));

    ContractualWorkRecord row = service.getSchedule9(MILL, YEAR, true).records().get(0);

    assertNull(row.costPerUnit());
    assertEquals(3000, row.cost());
  }

  @Test
  @DisplayName("null units -> $/Unit null (no NPE on the divide)")
  void nullCostPerUnit_whenUnitsNull() {
    stub("D", List.of(record(9103, null)),
        List.of(new CostRow(9103, 110, "Remedial Fence", null, 2000)));

    assertNull(service.getSchedule9(MILL, YEAR, true).records().get(0).costPerUnit());
  }

  @Test
  @DisplayName("a record with no cost line -> null Contractual Item, cost, and $/Unit")
  void nullCostLine_leavesItemAndCostNull() {
    stub("D", List.of(record(9104, new BigDecimal("10.0"))), List.of());

    ContractualWorkRecord row = service.getSchedule9(MILL, YEAR, true).records().get(0);

    assertNull(row.contractualItem());
    assertNull(row.cost());
    assertNull(row.costPerUnit());
  }

  @Test
  @DisplayName("editable = caller holds EDIT_SCHEDULE AND the track is Draft (server authority)")
  void editableRequiresDraftAndPermission() {
    stub("D", List.of(), List.of());
    assertTrue(service.getSchedule9(MILL, YEAR, true).editable());
  }

  @Test
  @DisplayName("Draft but caller lacks EDIT_SCHEDULE -> editable false")
  void notEditable_whenCallerMayNotEdit() {
    stub("D", List.of(), List.of());
    assertFalse(service.getSchedule9(MILL, YEAR, false).editable());
  }

  @Test
  @DisplayName("non-Draft track with EDIT_SCHEDULE -> editable false; records still served")
  void notEditable_whenNonDraft() {
    stub("S", List.of(record(9110, new BigDecimal("40.0"))),
        List.of(new CostRow(9110, 111, "Semi-permanent Road Deactivation", null, 8000)));

    Schedule9Response response = service.getSchedule9(MILL, YEAR, true);

    assertFalse(response.editable());
    assertEquals("S", response.trackStatus());
    assertEquals(1, response.records().size());
  }
}
