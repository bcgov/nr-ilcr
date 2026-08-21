package ca.bc.gov.nrs.ilcr.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the Story 29.1 reporting-datasource config. Exercises {@code
 * reportingHikariConfig} directly (no {@code HikariDataSource}, so no database connection is
 * opened) to pin the isolation-critical invariants: read-only, its own named pool, a small
 * dedicated ceiling, and leak-detection left disabled (a Jasper render legitimately holds its
 * connection for the whole fill).
 */
class DataSourceConfigurationTest {

  @Test
  void reportingHikariConfigIsReadOnlyWithItsOwnSmallFastFailingPoolAndNoLeakDetection() {
    HikariConfig config =
        DataSourceConfiguration.reportingHikariConfig(
            "jdbc:oracle:thin:@//db.example:1521/ILCR",
            "THE",
            "secret",
            "oracle.jdbc.OracleDriver",
            "ILCRReportingPool",
            3,
            0,
            5000L,
            60000L,
            180000L,
            60000L,
            "SELECT 1 FROM DUAL");

    // Read-only HINT (defence-in-depth; not Oracle-enforced write-prevention).
    assertThat(config.isReadOnly()).isTrue();
    // A distinct, small pool of its own — not the @Primary transactional pool.
    assertThat(config.getPoolName()).isEqualTo("ILCRReportingPool");
    assertThat(config.getMaximumPoolSize()).isEqualTo(3);
    assertThat(config.getMinimumIdle()).isEqualTo(0);
    // Short connection-timeout: the Nth queued render fast-fails rather than parking a Tomcat
    // thread 30s.
    assertThat(config.getConnectionTimeout()).isEqualTo(5000L);
    // Deliberately unset (0 = disabled): a render holds its connection for the full fill+format,
    // which
    // would otherwise trip a leak warning; the small dedicated pool bounds the exposure instead.
    assertThat(config.getLeakDetectionThreshold()).isZero();
    // Credentials/URL/validation still wired through the shared builder.
    assertThat(config.getJdbcUrl()).isEqualTo("jdbc:oracle:thin:@//db.example:1521/ILCR");
    assertThat(config.getConnectionTestQuery()).isEqualTo("SELECT 1 FROM DUAL");
  }
}
