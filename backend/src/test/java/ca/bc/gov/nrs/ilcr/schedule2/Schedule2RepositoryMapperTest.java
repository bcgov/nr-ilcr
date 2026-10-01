package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository.SummaryRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@link Schedule2Repository} {@code default} mapper/compose methods — the
 * entity-to-DTO mapping, the MERGE-then-re-read create, and the update-or-insert upsert — driven
 * with a mocked repository (abstract {@code @Query} methods stubbed) so no DB is needed. Covers the
 * logic that otherwise only the Oracle {@code *IT}s exercise.
 */
class Schedule2RepositoryMapperTest {

  private final Schedule2Repository repo = mock(Schedule2Repository.class);

  @Test
  void findSummary_mapsEntityToRow() {
    when(repo.findSummaryEntity(514L, 2021))
        .thenReturn(Optional.of(new Schedule2SummaryEntity(42, "a note", 3)));
    when(repo.findSummary(514L, 2021)).thenCallRealMethod();

    Optional<SummaryRow> row = repo.findSummary(514L, 2021);

    assertTrue(row.isPresent());
    assertEquals(42, row.get().summaryId());
    assertEquals("a note", row.get().comments());
    assertEquals(3, row.get().revisionCount());
  }

  @Test
  void findSummary_emptyWhenNoEntity() {
    when(repo.findSummaryEntity(515L, 2021)).thenReturn(Optional.empty());
    when(repo.findSummary(515L, 2021)).thenCallRealMethod();

    assertTrue(repo.findSummary(515L, 2021).isEmpty());
  }

  @Test
  void findDetails_mapsEntitiesToRows_preservingNulls() {
    when(repo.findDetailEntities(42))
        .thenReturn(
            List.of(
                new Schedule2DetailEntity(1001, 25, null, 500000),
                new Schedule2DetailEntity(1002, 26, new BigDecimal("120"), 30000)));
    when(repo.findDetails(42)).thenCallRealMethod();

    List<DetailRow> rows = repo.findDetails(42);

    assertEquals(2, rows.size());
    assertEquals(25, rows.get(0).costItemCode());
    assertNull(rows.get(0).volume());
    assertEquals(500000, rows.get(0).cost());
    assertEquals(new BigDecimal("120"), rows.get(1).volume());
    assertEquals(26, rows.get(1).costItemCode());
  }

  @Test
  void insertSummary_mergesThenReReadsForId() {
    when(repo.mergeSummaryRow(514L, 2021, "c", "u")).thenReturn(1);
    when(repo.findSummaryEntity(514L, 2021))
        .thenReturn(Optional.of(new Schedule2SummaryEntity(77, "c", 0)));
    when(repo.findSummary(514L, 2021)).thenCallRealMethod();
    when(repo.insertSummary(514L, 2021, "c", "u")).thenCallRealMethod();

    assertEquals(77, repo.insertSummary(514L, 2021, "c", "u"));
    verify(repo).mergeSummaryRow(514L, 2021, "c", "u");
  }

  @Test
  void insertSummary_throwsWhenSummaryMissingAfterMerge() {
    when(repo.mergeSummaryRow(516L, 2021, "c", "u")).thenReturn(0);
    when(repo.findSummaryEntity(516L, 2021)).thenReturn(Optional.empty());
    when(repo.findSummary(516L, 2021)).thenCallRealMethod();
    when(repo.insertSummary(516L, 2021, "c", "u")).thenCallRealMethod();

    assertThrows(IllegalStateException.class, () -> repo.insertSummary(516L, 2021, "c", "u"));
  }

  @Test
  void upsertDetail_updatesInPlace_whenRowExists() {
    when(repo.updateDetail(42, 25, null, 500000, "u")).thenReturn(1); // row existed
    doCallRealMethod().when(repo).upsertDetail(42, 25, null, 500000, "u");

    repo.upsertDetail(42, 25, null, 500000, "u");

    verify(repo, never()).insertDetail(anyInt(), anyInt(), any(), any(), any());
  }

  @Test
  void upsertDetail_inserts_whenNoRowUpdated() {
    when(repo.updateDetail(42, 26, new BigDecimal("1"), 10, "u"))
        .thenReturn(0); // nothing to update
    doCallRealMethod().when(repo).upsertDetail(42, 26, new BigDecimal("1"), 10, "u");

    repo.upsertDetail(42, 26, new BigDecimal("1"), 10, "u");

    verify(repo).insertDetail(42, 26, new BigDecimal("1"), 10, "u");
  }
}
