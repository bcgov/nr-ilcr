package ca.bc.gov.nrs.ilcr.millcontext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the mill/year guard decisions (AD-4). Mocked repository — no DB, no Spring. Covers
 * the decision table pinned in the story (getActiveMills / CLS shape).
 */
@ExtendWith(MockitoExtension.class)
class MillContextServiceTest {

  private static final int YEAR = 2021;
  private static final String CATEGORY = "1";

  @Mock
  private MillContextRepository repository;

  @InjectMocks
  private MillContextService service;

  @Test
  void unknownContext_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(999999L, YEAR)).thenReturn(Optional.empty());
    assertThrows(ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(999999L, YEAR, CATEGORY));
  }

  @Test
  void millClosedForYear_throwsMillClosed() {
    when(repository.findMillStatusCodeForYear(516L, YEAR)).thenReturn(Optional.of("CLS"));
    assertThrows(MillClosedException.class,
        () -> service.validateScheduleViewable(516L, YEAR, CATEGORY));
  }

  @Test
  void unexpectedNonActiveStatus_throwsMillClosed() {
    // Legacy has only ACT/CLS, but the guard whitelists ACT: any other status is not viewable.
    when(repository.findMillStatusCodeForYear(518L, YEAR)).thenReturn(Optional.of("SUS"));
    assertThrows(MillClosedException.class,
        () -> service.validateScheduleViewable(518L, YEAR, CATEGORY));
  }

  @Test
  void activeButNoSummary_throwsScheduleNotFound() {
    when(repository.findMillStatusCodeForYear(515L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(515L, YEAR, CATEGORY)).thenReturn(false);
    assertThrows(ScheduleNotFoundException.class,
        () -> service.validateScheduleViewable(515L, YEAR, CATEGORY));
  }

  @Test
  void activeWithSummary_isValid() {
    when(repository.findMillStatusCodeForYear(514L, YEAR)).thenReturn(Optional.of("ACT"));
    when(repository.scheduleSummaryExists(514L, YEAR, CATEGORY)).thenReturn(true);
    assertDoesNotThrow(() -> service.validateScheduleViewable(514L, YEAR, CATEGORY));
  }
}
