package ca.bc.gov.nrs.ilcr.schedule4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.LocationRow;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository.SubPageRowRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the {@link Schedule4Repository} {@code default} mapper/compose methods — the
 * entity→ service-record adapters and the sequence-insert / upsert / delete sequences. The
 * {@code @Query} abstract methods are mocked; each default is exercised via {@code
 * thenCallRealMethod}. The SQL itself is proven by the Testcontainers {@code *IT} suite; this locks
 * the in-memory mapping/branching.
 */
@ExtendWith(MockitoExtension.class)
class Schedule4RepositoryMapperTest {

  private static final long MILL = 546L;
  private static final int YEAR = 2021;

  @Mock private Schedule4Repository repo;

  @Test
  void findLocations_mapsReportEntitiesToLocationRows() {
    when(repo.findReportEntities(MILL, YEAR))
        .thenReturn(List.of(new TransportationReportEntity(8001, "Dump A", null, null, null, 0)));
    when(repo.findLocations(MILL, YEAR)).thenCallRealMethod();

    List<LocationRow> rows = repo.findLocations(MILL, YEAR);

    assertEquals(1, rows.size());
    assertEquals(8001, rows.get(0).transportationReportId());
    assertEquals("Dump A", rows.get(0).locationDescription());
  }

  @Test
  void findInScopeDetails_mapsDetailEntitiesToDetailRows() {
    when(repo.findInScopeDetailEntities(MILL, YEAR))
        .thenReturn(
            List.of(new CostReportDetailEntity(5001, 8001, 47, new BigDecimal("10"), 500, null)));
    when(repo.findInScopeDetails(MILL, YEAR)).thenCallRealMethod();

    List<DetailRow> rows = repo.findInScopeDetails(MILL, YEAR);

    assertEquals(1, rows.size());
    assertEquals(8001, rows.get(0).transportationReportId());
    assertEquals(47, rows.get(0).costItemCode());
    assertEquals(500, rows.get(0).cost());
  }

  @Test
  void findSubPageRows_joinsReportContext_andToleratesMissingReport() {
    when(repo.findReportEntities(MILL, YEAR))
        .thenReturn(
            List.of(
                new TransportationReportEntity(
                    8010, "Rehaul A", new BigDecimal("5"), 30, null, 0)));
    when(repo.findSubPageDetailEntities(MILL, YEAR))
        .thenReturn(
            List.of(
                new CostReportDetailEntity(5100, 8010, 46, new BigDecimal("2"), 200, "note"),
                new CostReportDetailEntity(5101, 9999, 46, new BigDecimal("3"), 300, "orphan")));
    when(repo.findSubPageRows(MILL, YEAR)).thenCallRealMethod();

    List<SubPageRowRow> rows = repo.findSubPageRows(MILL, YEAR);

    assertEquals(2, rows.size());
    SubPageRowRow matched = rows.get(0);
    assertEquals("Rehaul A", matched.locationDescription());
    assertEquals(new BigDecimal("5"), matched.distance());
    assertEquals(30, matched.cycle());
    assertEquals("note", matched.description());
    // Detail whose report isn't in the family → null location context, not a crash.
    SubPageRowRow orphan = rows.get(1);
    assertNull(orphan.locationDescription());
    assertNull(orphan.distance());
    assertNull(orphan.cycle());
  }

  @Test
  void nameExists_create_usesCountByName() {
    when(repo.countByName(MILL, YEAR, "Dup")).thenReturn(1);
    when(repo.nameExists(MILL, YEAR, "Dup", null)).thenCallRealMethod();

    assertTrue(repo.nameExists(MILL, YEAR, "Dup", null));
    verify(repo).countByName(MILL, YEAR, "Dup");
  }

  @Test
  void nameExists_edit_excludesOwnFamily() {
    when(repo.countByNameExcluding(MILL, YEAR, "New", "Old")).thenReturn(0);
    when(repo.nameExists(MILL, YEAR, "New", "Old")).thenCallRealMethod();

    assertFalse(repo.nameExists(MILL, YEAR, "New", "Old"));
    verify(repo).countByNameExcluding(MILL, YEAR, "New", "Old");
  }

  @Test
  void isSubPageRowOfLocation_reflectsCount() {
    when(repo.countSubPageRowOfLocation(9001, "Dump A", MILL, YEAR)).thenReturn(1);
    when(repo.isSubPageRowOfLocation(9001, "Dump A", MILL, YEAR)).thenCallRealMethod();

    assertTrue(repo.isSubPageRowOfLocation(9001, "Dump A", MILL, YEAR));
  }

  @Test
  void insertReport_drawsSequenceThenInsertsPrimaryWithNullDistance() {
    when(repo.nextReportId()).thenReturn(9500);
    when(repo.insertReport(MILL, YEAR, "Loc", null, "user")).thenCallRealMethod();

    int id = repo.insertReport(MILL, YEAR, "Loc", null, "user");

    assertEquals(9500, id);
    verify(repo).insertReportRow(9500, MILL, YEAR, "Loc", null, null, "user");
  }

  @Test
  void insertSubPageReport_drawsSequenceThenInsertsWithDistanceAndCycle() {
    when(repo.nextReportId()).thenReturn(9600);
    when(repo.insertSubPageReport(MILL, YEAR, "Loc", new BigDecimal("5"), 30, "user"))
        .thenCallRealMethod();

    int id = repo.insertSubPageReport(MILL, YEAR, "Loc", new BigDecimal("5"), 30, "user");

    assertEquals(9600, id);
    verify(repo).insertReportRow(9600, MILL, YEAR, "Loc", new BigDecimal("5"), 30, "user");
  }

  @Test
  void upsertDetail_updateHit_doesNotInsert() {
    when(repo.updateDetailRow(8001, 47, new BigDecimal("1"), 100, "user")).thenReturn(1);
    doCallRealMethod().when(repo).upsertDetail(8001, 47, new BigDecimal("1"), 100, "user");

    repo.upsertDetail(8001, 47, new BigDecimal("1"), 100, "user");

    verify(repo).updateDetailRow(8001, 47, new BigDecimal("1"), 100, "user");
    verify(repo, never()).insertDetailRow(anyInt(), anyInt(), any(), any(), anyString());
  }

  @Test
  void upsertDetail_updateMiss_insertsRow() {
    when(repo.updateDetailRow(8001, 47, null, null, "user")).thenReturn(0);
    doCallRealMethod().when(repo).upsertDetail(8001, 47, null, null, "user");

    repo.upsertDetail(8001, 47, null, null, "user");

    verify(repo).insertDetailRow(8001, 47, null, null, "user");
  }

  @Test
  void deleteReport_removesDetailsThenReport() {
    doCallRealMethod().when(repo).deleteReport(8001);

    repo.deleteReport(8001);

    verify(repo).deleteDetailsByReport(8001);
    verify(repo).deleteReportRow(8001);
  }

  @Test
  void deleteFamily_removesFamilyDetailsThenReports() {
    doCallRealMethod().when(repo).deleteFamily(MILL, YEAR, "Dump A");

    repo.deleteFamily(MILL, YEAR, "Dump A");

    verify(repo).deleteFamilyDetails(MILL, YEAR, "Dump A");
    verify(repo).deleteFamilyReports(MILL, YEAR, "Dump A");
  }
}
