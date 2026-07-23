package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request.CostLineInput;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for the Schedule 3 write path (Story 4.2): save row-writing, override normalization,
 * optimistic lock, the BR-09 Crown Timber push (changed/unchanged/applied/not-opened), and delete.
 * Mocked repository + {@code Schedule1Service} — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3WriteServiceTest {

  private static final long MILL = 540L;
  private static final int YEAR = 2021;
  private static final String USER = "tester";

  @Mock
  private Schedule3Repository repository;

  @Mock
  private Schedule1Service schedule1Service;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule3Service service;

  private void stubDraft(BigDecimal persistedCrownVolume) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1040, "N", "c", 0)));
    lenient().when(repository.findDetails(1040))
        .thenReturn(List.of(new DetailRow(119, persistedCrownVolume, null, null, null)));
    lenient().when(repository.bumpRevision(anyInt(), anyInt(), any(), any(), anyString()))
        .thenReturn(1);
    lenient().when(messageSource.getMessage(anyString(), any(), any(), any())).thenReturn("text");
  }

  private Schedule3Request request(String override, BigDecimal crownVolume, List<CostLineInput> lines) {
    return new Schedule3Request(0, "comment", override, lines, new BigDecimal("5000"), crownVolume);
  }

  @Test
  void save_writesHarvestAndPop_forPopLines_andHarvestOnly_for29_33_37() {
    stubDraft(new BigDecimal("5000"));
    service.saveSchedule3(MILL, YEAR, request("N", new BigDecimal("5000"), List.of(
        new CostLineInput(27, 1000, 400),   // pop line
        new CostLineInput(29, 500, 999),    // Annual Rents — Harvest-only
        new CostLineInput(33, 600, 999),    // Scaling — Harvest-only (no 131)
        new CostLineInput(37, 700, 999))),  // Silviculture Admin — Harvest-only
        true, USER);

    verify(repository).upsertFixedDetailCost(1040, 27, 1000, USER);
    verify(repository).upsertFixedDetailCost(1040, 125, 400, USER);  // Licenses PO&P written
    verify(repository).upsertFixedDetailCost(1040, 29, 500, USER);
    verify(repository).upsertFixedDetailCost(1040, 33, 600, USER);
    verify(repository).upsertFixedDetailCost(1040, 37, 700, USER);
    // Harvest-only lines never write a PO&P row (29/37 forced-zero on read; 33 derived; 131 never).
    verify(repository, never()).upsertFixedDetailCost(eq(1040), eq(131), any(), anyString());
    // Both timber volumes upserted.
    verify(repository).upsertVolume(1040, 118, new BigDecimal("5000"), USER);
    verify(repository).upsertVolume(1040, 119, new BigDecimal("5000"), USER);
  }

  @Test
  void save_persistsCommentsAndNormalizedOverride() {
    stubDraft(new BigDecimal("5000"));
    service.saveSchedule3(MILL, YEAR, request("Y", new BigDecimal("5000"), List.of()), true, USER);
    verify(repository).bumpRevision(1040, 0, "comment", "Y", USER);

    stubDraft(new BigDecimal("5000"));
    service.saveSchedule3(MILL, YEAR, request("anything", new BigDecimal("5000"), List.of()), true, USER);
    verify(repository).bumpRevision(1040, 0, "comment", "N", USER);  // non-"Y" normalizes to "N"
  }

  @Test
  void save_staleRevision_throws() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1040, "N", "c", 3)));
    lenient().when(repository.findDetails(1040)).thenReturn(List.of());
    when(repository.bumpRevision(anyInt(), anyInt(), any(), any(), anyString())).thenReturn(0);
    assertThrows(StaleRevisionException.class, () ->
        service.saveSchedule3(MILL, YEAR, request("N", new BigDecimal("5000"), List.of()), true, USER));
  }

  @Test
  void save_notDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(ScheduleNotEditableException.class, () ->
        service.saveSchedule3(MILL, YEAR, request("N", new BigDecimal("5000"), List.of()), true, USER));
  }

  @Test
  void crownPush_whenChangedAndSchedule1Open_appliesAndWarnsWrn001() {
    stubDraft(new BigDecimal("5000"));  // persisted crown = 5000
    when(schedule1Service.applyCrownTimberVolume(eq(MILL), eq(YEAR), any(), eq(USER))).thenReturn(true);
    Schedule3Response doc = service.saveSchedule3(
        MILL, YEAR, request("N", new BigDecimal("7000"), List.of()), true, USER);  // changed → 7000
    verify(schedule1Service).applyCrownTimberVolume(MILL, YEAR, new BigDecimal("7000"), USER);
    assertEquals(1, doc.warnings().size());
    assertEquals("crownVolumeChangeSchedule1", doc.warnings().get(0).key());
  }

  @Test
  void crownPush_whenNotChanged_doesNotPushNorWarn() {
    stubDraft(new BigDecimal("5000"));  // persisted crown = 5000
    Schedule3Response doc = service.saveSchedule3(
        MILL, YEAR, request("N", new BigDecimal("5000"), List.of()), true, USER);  // unchanged
    verify(schedule1Service, never()).applyCrownTimberVolume(any(Long.class), anyInt(), any(), anyString());
    assertTrue(doc.warnings().isEmpty());
  }

  @Test
  void crownPush_whenSchedule1NotOpen_warnsWrn002() {
    stubDraft(new BigDecimal("5000"));
    when(schedule1Service.applyCrownTimberVolume(eq(MILL), eq(YEAR), any(), eq(USER))).thenReturn(false);
    Schedule3Response doc = service.saveSchedule3(
        MILL, YEAR, request("N", new BigDecimal("7000"), List.of()), true, USER);
    assertEquals("crownVolumeNotSetSchedule1", doc.warnings().get(0).key());
  }

  @Test
  void delete_draftGated_removesFamily() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1040, "N", "c", 0)));
    service.deleteSchedule3(MILL, YEAR);
    verify(repository).deleteSchedule(1040);
  }

  @Test
  void delete_notDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("V"));
    assertThrows(ScheduleNotEditableException.class, () -> service.deleteSchedule3(MILL, YEAR));
  }
}
