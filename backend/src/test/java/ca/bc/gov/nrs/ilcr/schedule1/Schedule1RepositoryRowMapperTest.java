package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRowMapper;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRowMapper;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRowMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the repository {@link org.springframework.jdbc.core.RowMapper}s. Drives each mapper
 * with a mocked {@link ResultSet} — no DB — and pins the Oracle-NUMBER-to-nullable-Integer handling
 * (a null NUMBER column maps to a null Integer, not 0).
 */
@ExtendWith(MockitoExtension.class)
class Schedule1RepositoryRowMapperTest {

  @Mock
  private ResultSet rs;

  @Test
  void summaryRowMapper_mapsColumns_andNullNumberBecomesNull() throws Exception {
    when(rs.getBigDecimal("ILCR_REPORT_SUMMARY_ID")).thenReturn(new BigDecimal("42"));
    when(rs.getBigDecimal("CROWN_VOLUME")).thenReturn(null); // exercises the null branch
    when(rs.getString("COMMENTS")).thenReturn("a note");
    when(rs.getBigDecimal("REVISION_COUNT")).thenReturn(new BigDecimal("3"));

    SummaryRow row = new SummaryRowMapper().mapRow(rs, 1);

    assertEquals(42, row.summaryId());
    assertNull(row.crownVolume());
    assertEquals("a note", row.comments());
    assertEquals(3, row.revisionCount());
  }

  @Test
  void detailRowMapper_mapsColumns() throws Exception {
    when(rs.getBigDecimal("ILCR_REPORT_COST_ITEM_ID")).thenReturn(new BigDecimal("7"));
    when(rs.getBigDecimal("VOLUME")).thenReturn(new BigDecimal("100.5"));
    when(rs.getBigDecimal("COST")).thenReturn(new BigDecimal("2500"));
    when(rs.getString("ITEM_DESCRIPTION")).thenReturn("Logging");

    DetailRow row = new DetailRowMapper().mapRow(rs, 1);

    assertEquals(7, row.costItemCode());
    assertEquals(new BigDecimal("100.5"), row.volume());
    assertEquals(2500, row.cost());
    assertEquals("Logging", row.itemDescription());
  }

  @Test
  void otherCostDetailRowMapper_mapsColumns_andNullCost() throws Exception {
    when(rs.getBigDecimal("ILCR_COST_REPORT_DETAIL_ID")).thenReturn(new BigDecimal("19"));
    when(rs.getString("ITEM_DESCRIPTION")).thenReturn("Haul");
    when(rs.getBigDecimal("COST")).thenReturn(null); // exercises the null branch
    when(rs.getBigDecimal("VOLUME")).thenReturn(new BigDecimal("12"));

    OtherCostDetailRow row = new OtherCostDetailRowMapper().mapRow(rs, 1);

    assertEquals(19, row.id());
    assertEquals("Haul", row.description());
    assertNull(row.cost());
    assertEquals(new BigDecimal("12"), row.volume());
  }
}
