package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 8 page write control flow (Story 14.2) — mocked repository, no DB. Covers
 * the Draft gate (409), the optimistic-lock stale path (409), create → insert + revision bump, the
 * TFL⇄supply-block normalization, and the idempotent delete guard. The full create/edit/delete +
 * cascade behaviour is proven end-to-end against Oracle in {@link Schedule8PageWriteIT}.
 */
@ExtendWith(MockitoExtension.class)
class Schedule8PageWriteServiceTest {

  private static final long MILL = 580L;
  private static final int YEAR = 2021;
  private static final String USER = "tester";

  @Mock
  private Schedule8Repository repository;

  @InjectMocks
  private Schedule8Service service;

  @BeforeEach
  void stubReadsForRecompute() {
    // savePage/deletePage recompute the document at the end via getSchedule8 — default its reads empty.
    org.mockito.Mockito.lenient().when(repository.findPages(MILL, YEAR)).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(repository.findSamples(MILL, YEAR)).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(repository.findRateRows(MILL, YEAR)).thenReturn(List.of());
    org.mockito.Mockito.lenient().when(repository.costItemSubcategories()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.supportCentreLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.regionLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.becZoneLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.tsaNumberLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.supplyBlockLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.tflNumberLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.skidTypeLabels()).thenReturn(Map.of());
    org.mockito.Mockito.lenient().when(repository.costTypeLabels()).thenReturn(Map.of());
  }

  private static Schedule8PageRequest create(String tflNumber, String supplyBlock) {
    return new Schedule8PageRequest(null, null, "LIC1", "SC1", "R1", "BZ1", "TSA5",
        tflNumber, supplyBlock, "Div", "Contact", "250", "CP", "notes");
  }

  @Test
  void nonDraft_throwsNotEditable() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
    assertThrows(ScheduleNotEditableException.class,
        () -> service.savePage(MILL, YEAR, create(null, "B"), true, USER));
    verify(repository, never()).insertPage(anyLong(), anyInt(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void editWithStaleRevision_throwsStale() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.bumpPageRevision(8800, 5, USER)).thenReturn(0);
    Schedule8PageRequest edit = new Schedule8PageRequest(8800, 5, "LIC1", "SC1", "R1", "BZ1",
        "TSA5", null, "B", null, null, null, null, null);
    assertThrows(StaleRevisionException.class,
        () -> service.savePage(MILL, YEAR, edit, true, USER));
    verify(repository, never()).updatePageFields(anyInt(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void create_insertsThenBumpsRevision_andClearsSupplyBlockWhenTfl() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.insertPage(anyLong(), anyInt(), any(), any(), any(), any(), any(), any(), any(),
        any(), any(), any(), any(), any(), any())).thenReturn(9001);
    // TFL selected + a supply block supplied → the supply block must be cleared (null) on insert.
    service.savePage(MILL, YEAR, create("48", "B"), true, USER);
    // supplyBlock (arg 7, 0-based) null, tflNumber (arg 8) "48".
    verify(repository).insertPage(eq(MILL), eq(YEAR), eq("SC1"), eq("R1"), eq("BZ1"), eq("TSA5"),
        isNull(), eq("48"), eq("CP"), eq("LIC1"), eq("Div"), eq("Contact"), eq("250"), eq("notes"),
        eq(USER));
    verify(repository).bumpPageRevision(9001, 0, USER);
  }

  @Test
  void delete_unknownPage_isNoOp() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.pageExists(99999, MILL, YEAR)).thenReturn(false);
    service.deletePage(MILL, YEAR, 99999);
    verify(repository, never()).deletePage(anyInt());
  }

  @Test
  void delete_existingPage_cascades() {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
    when(repository.pageExists(8810, MILL, YEAR)).thenReturn(true);
    service.deletePage(MILL, YEAR, 8810);
    verify(repository).deletePage(8810);
  }
}
