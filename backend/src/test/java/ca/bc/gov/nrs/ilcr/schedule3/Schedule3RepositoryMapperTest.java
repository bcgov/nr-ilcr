package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRowMapper;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SubPageRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SubPageRowMapper;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRowMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the {@link Schedule3Repository} {@code default} upsert/delete compose methods and the
 * three {@link org.springframework.jdbc.core.RowMapper} implementations. The default methods are driven
 * with a mocked repository (abstract {@code @Query} methods stubbed) and the mappers with a mocked
 * {@link ResultSet}, so no DB is needed — covering the logic that otherwise only the Oracle {@code *IT}s
 * exercise. Mirrors {@code Schedule2RepositoryMapperTest}.
 */
class Schedule3RepositoryMapperTest {

  private final Schedule3Repository repo = mock(Schedule3Repository.class);

  // ---- default upsert/delete compose methods ----------------------------------------------------

  @Test
  void upsertFixedDetailCost_updatesInPlace_whenRowExists() {
    when(repo.updateFixedDetailCost(42, 27, 500000, "u")).thenReturn(1); // row existed
    doCallRealMethod().when(repo).upsertFixedDetailCost(42, 27, 500000, "u");

    repo.upsertFixedDetailCost(42, 27, 500000, "u");

    verify(repo, never()).insertFixedDetailCost(anyInt(), anyInt(), any(), any());
  }

  @Test
  void upsertFixedDetailCost_inserts_whenNoRowUpdated() {
    when(repo.updateFixedDetailCost(42, 28, 10, "u")).thenReturn(0); // nothing to update
    doCallRealMethod().when(repo).upsertFixedDetailCost(42, 28, 10, "u");

    repo.upsertFixedDetailCost(42, 28, 10, "u");

    verify(repo).insertFixedDetailCost(42, 28, 10, "u");
  }

  @Test
  void upsertVolume_updatesInPlace_whenRowExists() {
    BigDecimal vol = new BigDecimal("54321");
    when(repo.updateVolume(42, 118, vol, "u")).thenReturn(1); // row existed
    doCallRealMethod().when(repo).upsertVolume(42, 118, vol, "u");

    repo.upsertVolume(42, 118, vol, "u");

    verify(repo, never()).insertVolume(anyInt(), anyInt(), any(), any());
  }

  @Test
  void upsertVolume_inserts_whenNoRowUpdated() {
    BigDecimal vol = new BigDecimal("100");
    when(repo.updateVolume(42, 119, vol, "u")).thenReturn(0); // nothing to update
    doCallRealMethod().when(repo).upsertVolume(42, 119, vol, "u");

    repo.upsertVolume(42, 119, vol, "u");

    verify(repo).insertVolume(42, 119, vol, "u");
  }

  @Test
  void deleteSchedule_deletesDetailsThenSummary() {
    doCallRealMethod().when(repo).deleteSchedule(42);

    repo.deleteSchedule(42);

    verify(repo).deleteDetailsBySummary(42);
    verify(repo).deleteSummary(42);
  }

  // ---- row mappers ------------------------------------------------------------------------------

  @Test
  void summaryRowMapper_mapsColumns() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("ILCR_REPORT_SUMMARY_ID")).thenReturn(new BigDecimal("1003"));
    when(rs.getString("LOCATION")).thenReturn("Y");
    when(rs.getString("COMMENTS")).thenReturn("a note");
    when(rs.getBigDecimal("REVISION_COUNT")).thenReturn(new BigDecimal("3"));

    SummaryRow row = new SummaryRowMapper().mapRow(rs, 1);

    assertEquals(1003, row.summaryId());
    assertEquals("Y", row.location());
    assertEquals("a note", row.comments());
    assertEquals(3, row.revisionCount());
  }

  @Test
  void summaryRowMapper_toleratesNullNumbers() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("ILCR_REPORT_SUMMARY_ID")).thenReturn(null);
    when(rs.getString("LOCATION")).thenReturn(null);
    when(rs.getString("COMMENTS")).thenReturn(null);
    when(rs.getBigDecimal("REVISION_COUNT")).thenReturn(null);

    SummaryRow row = new SummaryRowMapper().mapRow(rs, 1);

    assertNull(row.summaryId());
    assertNull(row.revisionCount());
  }

  @Test
  void detailRowMapper_mapsColumns() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("ILCR_REPORT_COST_ITEM_ID")).thenReturn(new BigDecimal("118"));
    when(rs.getBigDecimal("VOLUME")).thenReturn(new BigDecimal("54321"));
    when(rs.getBigDecimal("COST")).thenReturn(null);
    when(rs.getString("ITEM_DESCRIPTION")).thenReturn(null);
    when(rs.getString("COMMENTS")).thenReturn(null);

    DetailRow row = new DetailRowMapper().mapRow(rs, 1);

    assertEquals(118, row.costItemCode());
    assertEquals(new BigDecimal("54321"), row.volume());
    assertNull(row.cost());
  }

  @Test
  void subPageRowMapper_mapsColumns() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getBigDecimal("ILCR_COST_REPORT_DETAIL_ID")).thenReturn(new BigDecimal("5258"));
    when(rs.getBigDecimal("COST")).thenReturn(new BigDecimal("2500"));
    when(rs.getString("ITEM_DESCRIPTION")).thenReturn("Some acceptable cost");
    when(rs.getString("COMMENTS")).thenReturn("SCH3_2_TOT_GRP1");

    SubPageRow row = new SubPageRowMapper().mapRow(rs, 1);

    assertEquals(5258, row.detailId());
    assertEquals(2500, row.cost());
    assertEquals("Some acceptable cost", row.itemDescription());
    assertEquals("SCH3_2_TOT_GRP1", row.comments());
  }
}
