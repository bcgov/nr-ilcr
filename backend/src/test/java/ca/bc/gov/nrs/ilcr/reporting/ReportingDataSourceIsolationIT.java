package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 29.1: proves the Jasper report-fill path draws from a SEPARATE, read-only Hikari pool, so a
 * burst of report renders can only exhaust that dedicated pool and never starve the {@code @Primary}
 * transactional pool serving ordinary schedule requests.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
class ReportingDataSourceIsolationIT extends AbstractOracleIT {

  @Autowired private DataSource primaryDataSource; // resolves to the @Primary bean

  @Autowired
  @Qualifier("reportingDataSource")
  private DataSource reportingDataSource;

  @Test
  void reportingDataSourceIsADistinctReadOnlyPool() throws Exception {
    assertThat(reportingDataSource).isNotSameAs(primaryDataSource);

    HikariDataSource reportingHikari = reportingDataSource.unwrap(HikariDataSource.class);
    assertThat(reportingHikari.getPoolName()).isEqualTo("ILCRReportingPool");
    assertThat(reportingHikari.isReadOnly()).isTrue();

    try (Connection connection = reportingDataSource.getConnection()) {
      assertThat(connection.isReadOnly()).isTrue();
    }
  }

  @Test
  void saturatingTheReportingPoolLeavesTheTransactionalPoolAcquirable() throws Exception {
    int reportingMax = reportingDataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();

    List<Connection> held = new ArrayList<>();
    try {
      // Hold every connection the reporting pool can hand out — the stand-in for concurrent renders,
      // each of which pins a connection for its whole fill.
      for (int i = 0; i < reportingMax; i++) {
        held.add(reportingDataSource.getConnection());
      }
      // The transactional pool is untouched: an ordinary request still gets a connection right away.
      try (Connection primary = primaryDataSource.getConnection()) {
        assertThat(primary.isValid(2)).isTrue();
      }
    } finally {
      for (Connection connection : held) {
        connection.close();
      }
    }
  }
}
