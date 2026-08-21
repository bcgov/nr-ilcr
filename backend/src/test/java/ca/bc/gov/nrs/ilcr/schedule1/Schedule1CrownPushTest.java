package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the BR-09 Crown Timber push (Story 4.2) — the single entry point Schedule 3's save
 * uses to overwrite Schedule 1 detail VOLUMEs (COST preserved), plus the repository upsert that
 * backs it. Mocked repository — no DB — isolating the "Schedule 1 opened?" gate and the per-item
 * fan-out.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1CrownPushTest {

  private static final long MILL = 522L;
  private static final int YEAR = 2021;
  private static final int SUMMARY_ID = 1003;
  private static final String USER = "dev-admin";

  @Mock private Schedule1Repository repository;

  @InjectMocks private Schedule1Service service;

  @Test
  void applyCrownTimberVolume_writesVolumes_whenSchedule1Opened() {
    BigDecimal volume = new BigDecimal("54321");
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D")); // Draft → editable

    boolean pushed = service.applyCrownTimberVolume(MILL, YEAR, volume, USER);

    assertTrue(pushed);
    // The aggregate revision is bumped (AR11) so a stale-token main-page save is rejected.
    verify(repository).touchSummary(SUMMARY_ID, USER);
    // Fixed-line items get a VOLUME-only upsert; the item-19 Other-Costs rows are overwritten en
    // masse.
    verify(repository).upsertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);
    verify(repository).upsertFixedDetailVolume(SUMMARY_ID, 144, volume, USER);
    verify(repository).updateAllOtherCostVolumes(SUMMARY_ID, volume, USER);
  }

  @Test
  void applyCrownTimberVolume_noOp_whenSchedule1NotOpened() {
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());

    boolean pushed = service.applyCrownTimberVolume(MILL, YEAR, new BigDecimal("1"), USER);

    assertFalse(pushed); // WRN-002: nothing written when Schedule 1 has no summary
    verify(repository, never()).touchSummary(anyInt(), any());
    verify(repository, never()).upsertFixedDetailVolume(anyInt(), anyInt(), any(), any());
    verify(repository, never()).updateAllOtherCostVolumes(anyInt(), any(), any());
  }

  @Test
  void applyCrownTimberVolume_noOp_whenSchedule1NotDraft() {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY_ID, null, "c", 1)));
    when(repository.findTrackStatus(MILL, YEAR))
        .thenReturn(Optional.of("S")); // submitted, not Draft

    boolean pushed = service.applyCrownTimberVolume(MILL, YEAR, new BigDecimal("1"), USER);

    // Defence-in-depth: a present-but-non-Draft Schedule 1 must NOT be overwritten by the crown
    // push.
    assertFalse(pushed);
    verify(repository, never()).touchSummary(anyInt(), any());
    verify(repository, never()).upsertFixedDetailVolume(anyInt(), anyInt(), any(), any());
    verify(repository, never()).updateAllOtherCostVolumes(anyInt(), any(), any());
  }

  @Test
  void upsertFixedDetailVolume_updatesInPlace_whenRowExists() {
    Schedule1Repository repo = mock(Schedule1Repository.class);
    BigDecimal volume = new BigDecimal("100");
    when(repo.updateFixedDetailVolume(SUMMARY_ID, 12, volume, USER)).thenReturn(1); // row existed
    doCallRealMethod().when(repo).upsertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);

    repo.upsertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);

    verify(repo, never()).insertFixedDetailVolume(anyInt(), anyInt(), any(), any());
  }

  @Test
  void upsertFixedDetailVolume_inserts_whenNoRowUpdated() {
    Schedule1Repository repo = mock(Schedule1Repository.class);
    BigDecimal volume = new BigDecimal("100");
    when(repo.updateFixedDetailVolume(SUMMARY_ID, 12, volume, USER))
        .thenReturn(0); // nothing to update
    doCallRealMethod().when(repo).upsertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);

    repo.upsertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);

    verify(repo).insertFixedDetailVolume(SUMMARY_ID, 12, volume, USER);
  }
}
