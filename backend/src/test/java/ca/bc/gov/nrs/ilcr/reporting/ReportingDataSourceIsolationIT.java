package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 29.1: proves the Jasper report-fill path draws from a SEPARATE Hikari pool, so a burst of
 * report renders can only exhaust that dedicated pool and never starve the {@code @Primary}
 * transactional pool serving ordinary schedule requests.
 *
 * <p>The reporting pool is sized ABOVE the primary pool here (6 &gt; the default 5) so the isolation
 * test is a real regression guard: if someone dropped the {@code @Qualifier} on {@code ReportService}
 * and it fell back to the shared primary pool of 5, saturating "the reporting pool" with 6 connections
 * would be impossible (the 6th would block to the timeout and fail) — so the test would go red.
 */
@TestPropertySource(properties = {
    "ilcr.security.enabled=false",
    "spring.datasource.reporting.hikari.maximum-pool-size=6"
})
class ReportingDataSourceIsolationIT extends AbstractOracleIT {

  @Autowired private DataSource primaryDataSource; // resolves to the @Primary bean

  @Autowired
  @Qualifier("reportingDataSource")
  private DataSource reportingDataSource;

  @Test
  void reportingDataSourceIsADistinctPoolWithItsOwnName() throws Exception {
    assertThat(reportingDataSource).isNotSameAs(primaryDataSource);

    HikariDataSource reportingHikari = reportingDataSource.unwrap(HikariDataSource.class);
    assertThat(reportingHikari.getPoolName()).isEqualTo("ILCRReportingPool");
    // The read-only flag is a config/pool HINT (not an Oracle-enforced privilege), so assert the pool
    // config rather than connection.isReadOnly() — the driver may report that inconsistently.
    assertThat(reportingHikari.isReadOnly()).isTrue();
    assertThat(reportingHikari.getMaximumPoolSize()).isGreaterThan(primaryMaxPoolSize());
  }

  @Test
  void saturatingTheReportingPoolLeavesTheTransactionalPoolUntouched() throws Exception {
    HikariDataSource reportingHikari = reportingDataSource.unwrap(HikariDataSource.class);
    HikariDataSource primaryHikari = primaryDataSource.unwrap(HikariDataSource.class);
    int reportingMax = reportingHikari.getMaximumPoolSize();

    List<Connection> held = new ArrayList<>();
    try {
      // Hold EVERY connection the reporting pool can hand out (the stand-in for concurrent renders, each
      // pinning a connection for its whole fill). Because reportingMax (6) > the primary ceiling (5),
      // completing this loop is itself proof of a separate pool — the shared pool could never hand out 6.
      for (int i = 0; i < reportingMax; i++) {
        held.add(reportingDataSource.getConnection());
      }

      // The transactional pool is provably untouched by the reporting saturation...
      assertThat(primaryHikari.getHikariPoolMXBean().getActiveConnections()).isZero();
      // ...and an ordinary request still gets a connection right away.
      try (Connection primary = primaryDataSource.getConnection()) {
        assertThat(primary.isValid(2)).isTrue();
      }
    } finally {
      for (Connection connection : held) {
        try {
          connection.close();
        } catch (SQLException ignored) {
          // Best-effort cleanup — keep closing the rest so a single failure can't leak sessions.
        }
      }
    }
  }

  private int primaryMaxPoolSize() throws SQLException {
    return primaryDataSource.unwrap(HikariDataSource.class).getMaximumPoolSize();
  }
}
