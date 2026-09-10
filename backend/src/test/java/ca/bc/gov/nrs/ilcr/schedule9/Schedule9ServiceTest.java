package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.CostRow;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.RecordRow;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 9 document assembly + server-side derivation (Story 9.1 / T3) with a
 * mocked repository — the $/Unit null-safe divide, the server-authoritative {@code editable}
 * branch, and the record↔cost-line join, without a database. The SQL itself is exercised against
 * real Oracle by {@link Schedule9DocumentIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule9Service — assembly + $/Unit derivation")
class Schedule9ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule9Repository repository;

  @Mock private MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", so a mock would turn every
  // original-value assertion into an assertion about the mock (Story 16.2, OriginalValuesFixture).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule9Service service;

  /** A record row with the given id, units, and free-text descriptions blank unless overridden. */
  private static RecordRow record(int id, BigDecimal units) {
    return new RecordRow(
        id,
        0,
        "CTR-1",
        units,
        25,
        "comment",
        "M3",
        "Cubic Metres",
        null,
        "A",
        "Actual Cost",
        null,
        "BZ1",
        "BEC Zone One");
  }

  private void stub(String trackStatus, List<RecordRow> records, List<CostRow> costLines) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
    when(repository.findRecords(MILL, YEAR)).thenReturn(records);
    when(repository.findCostLines(MILL, YEAR)).thenReturn(costLines);
  }

  @Test
  @DisplayName("$/Unit = cost ÷ units, scale 2 HALF_UP, joined to the record's cost line")
  void derivesCostPerUnit() {
    stub(
        "D",
        List.of(record(9101, new BigDecimal("100.0"))),
        List.of(new CostRow(9101, 108, "Cattleguard", null, 5000)));

    ContractualWorkRecord row =
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0);

    assertEquals(0, new BigDecimal("50.00").compareTo(row.costPerUnit()));
    assertEquals(5000, row.cost());
    assertEquals("108", row.contractualItem().code());
    assertEquals("Cattleguard", row.contractualItem().description());
  }

  @Test
  @DisplayName("zero units -> $/Unit null (S14), even though a cost is stored")
  void nullCostPerUnit_whenUnitsZero() {
    stub(
        "D",
        List.of(record(9102, BigDecimal.ZERO)),
        List.of(new CostRow(9102, 109, "Pipeline Crossing", null, 3000)));

    ContractualWorkRecord row =
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0);

    assertNull(row.costPerUnit());
    assertEquals(3000, row.cost());
  }

  @Test
  @DisplayName("null units -> $/Unit null (no NPE on the divide)")
  void nullCostPerUnit_whenUnitsNull() {
    stub(
        "D",
        List.of(record(9103, null)),
        List.of(new CostRow(9103, 110, "Remedial Fence", null, 2000)));

    assertNull(
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0).costPerUnit());
  }

  @Test
  @DisplayName("a record with no cost line -> null Contractual Item, cost, and $/Unit")
  void nullCostLine_leavesItemAndCostNull() {
    stub("D", List.of(record(9104, new BigDecimal("10.0"))), List.of());

    ContractualWorkRecord row =
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0);

    assertNull(row.contractualItem());
    assertNull(row.cost());
    assertNull(row.costPerUnit());
  }

  @Test
  @DisplayName("editable = caller holds EDIT_SCHEDULE AND the track is Draft (server authority)")
  void editableRequiresDraftAndPermission() {
    stub("D", List.of(), List.of());
    assertTrue(service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).editable());
  }

  @Test
  @DisplayName("Draft but caller lacks EDIT_SCHEDULE -> editable false")
  void notEditable_whenCallerMayNotEdit() {
    stub("D", List.of(), List.of());
    assertFalse(service.getSchedule9(MILL, YEAR, CallerRights.NONE).editable());
  }

  @Test
  @DisplayName("non-Draft track with EDIT_SCHEDULE -> editable false; records still served")
  void notEditable_whenNonDraft() {
    stub(
        "S",
        List.of(record(9110, new BigDecimal("40.0"))),
        List.of(new CostRow(9110, 111, "Semi-permanent Road Deactivation", null, 8000)));

    Schedule9Response response = service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER);

    assertFalse(response.editable());
    assertEquals("S", response.trackStatus());
    assertEquals(1, response.records().size());
  }

  // -----------------------------------------------------------------------------------------
  // Original values (Story 16.2, BR-04). A record's submitted figures come from two snapshots
  // joined on the record id — the contractual-work report view for its own ten fields, the
  // shared cost view for the contractual item, its free text and its cost.
  // -----------------------------------------------------------------------------------------

  private static void assertOriginal(
      Map<String, OriginalValue> originals, String field, String value, String formatted) {
    OriginalValue original = originals.get(field);
    assertNotNull(original, () -> "no original for " + field + " in " + originals.keySet());
    assertEquals(value, original.value());
    assertEquals(OriginalValuesFixture.tooltip(formatted), original.tooltip());
  }

  @Test
  @DisplayName("at Draft nothing is exposed and neither snapshot view is read")
  void originalValues_absentAtDraft_andNoSnapshotQueryIssued() {
    stub(
        "D",
        List.of(record(9001, new BigDecimal("100"))),
        List.of(new CostRow(9001, 108, "Falling", null, 50000)));

    Schedule9Response doc = service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER);

    assertNull(doc.records().get(0).originalValues());
    verify(repository, never()).findContractualSnapshots(anyLong(), anyInt());
    verify(costSnapshots, never()).findByContractualWorkReports(anyList());
  }

  @Test
  @DisplayName("beyond Draft a record joins its report snapshot to its cost snapshot by record id")
  void originalValues_submitted_joinReportAndCostSnapshotsOnTheRecordId() {
    stub(
        "S",
        List.of(record(9001, new BigDecimal("100"))),
        List.of(new CostRow(9001, 108, "Falling", null, 50000)));
    when(repository.findContractualSnapshots(MILL, YEAR))
        .thenReturn(
            List.of(
                new Schedule9Repository.ContractualSnapshotRow(
                    9001,
                    "CTR-0",
                    new BigDecimal("90.5"),
                    30,
                    "HA",
                    "Hectares",
                    "E",
                    "Estimated",
                    "BZ2",
                    "submitted comment")));
    when(costSnapshots.findByContractualWorkReports(List.of(9001L)))
        .thenReturn(
            List.of(
                new CostDetailSnapshotRepository.Row(
                    1, 9001L, 114, null, 44000, "Other work", null)));

    Map<String, OriginalValue> originals =
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0).originalValues();

    // Off the report snapshot.
    assertOriginal(originals, "contractorId", "CTR-0", "CTR-0");
    assertOriginal(originals, "numberOfUnits", "90.5", "90.5");
    assertOriginal(originals, "sideSlopePct", "30", "30");
    assertOriginal(originals, "unitType", "HA", "HA");
    assertOriginal(originals, "source", "E", "E");
    assertOriginal(originals, "biogeoclimaticZone", "BZ2", "BZ2");
    assertOriginal(originals, "comments", "submitted comment", "submitted comment");
    // Off the shared cost snapshot — the half the record-id join has to reach.
    assertOriginal(originals, "contractualItem", "114", "114");
    assertOriginal(originals, "itemDescription", "Other work", "Other work");
    assertOriginal(originals, "cost", "44000", "44,000");
  }

  @Test
  @DisplayName("beyond Draft with nothing on file the map is empty, never null")
  void originalValues_submittedButNoSnapshotOnFile_isEmptyMapNotNull() {
    stub(
        "S",
        List.of(record(9002, new BigDecimal("100"))),
        List.of(new CostRow(9002, 108, "Falling", null, 50000)));
    when(repository.findContractualSnapshots(MILL, YEAR)).thenReturn(List.of());
    when(costSnapshots.findByContractualWorkReports(List.of(9002L))).thenReturn(List.of());

    Map<String, OriginalValue> originals =
        service.getSchedule9(MILL, YEAR, CallerRights.SUBMITTER).records().get(0).originalValues();

    assertNotNull(originals);
    assertTrue(originals.isEmpty());
  }
}
