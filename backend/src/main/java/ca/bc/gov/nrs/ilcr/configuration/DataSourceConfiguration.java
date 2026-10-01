package ca.bc.gov.nrs.ilcr.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.StringUtils;

/** Data source configuration for the application. */
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class DataSourceConfiguration {

  /**
   * Configures the primary data source.
   *
   * @param url the database url
   * @param username the database username
   * @param password the database password
   * @param driverClassName the database driver class name
   * @param poolName the connection pool name
   * @param maximumPoolSize the maximum pool size
   * @param minimumIdle the minimum idle connections
   * @param connectionTimeout the connection timeout in ms
   * @param idleTimeout the idle timeout in ms
   * @param maxLifetime the maximum connection lifetime in ms
   * @param keepaliveTime the keepalive time in ms
   * @param leakDetectionThreshold the leak detection threshold in ms
   * @return the configured data source
   */
  @Bean
  @Primary
  public DataSource dataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name:oracle.jdbc.OracleDriver}")
          String driverClassName,
      @Value("${spring.datasource.hikari.pool-name:ILCROraclePool}") String poolName,
      @Value("${spring.datasource.hikari.maximum-pool-size:5}") int maximumPoolSize,
      @Value("${spring.datasource.hikari.minimum-idle:1}") int minimumIdle,
      @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeout,
      @Value("${spring.datasource.hikari.idle-timeout:60000}") long idleTimeout,
      @Value("${spring.datasource.hikari.max-lifetime:180000}") long maxLifetime,
      @Value("${spring.datasource.hikari.keepalive-time:60000}") long keepaliveTime,
      @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
          long leakDetectionThreshold,
      @Value("${ilcr.datasource.validation-query:SELECT 1 FROM DUAL}") String validationQuery,
      ObjectProvider<MeterRegistry> meterRegistry) {
    requireProperty("spring.datasource.url", url);
    requireProperty("spring.datasource.username", username);
    requireProperty("spring.datasource.password", password);

    HikariConfig config = new HikariConfig();
    applyCommonHikari(
        config,
        url,
        username,
        password,
        driverClassName,
        idleTimeout,
        maxLifetime,
        keepaliveTime,
        validationQuery);
    config.setPoolName(poolName);
    config.setMaximumPoolSize(maximumPoolSize);
    config.setMinimumIdle(minimumIdle);
    config.setConnectionTimeout(connectionTimeout);
    config.setLeakDetectionThreshold(leakDetectionThreshold);
    // Expose hikaricp_connections_* so pool saturation (this story's whole concern) is alertable,
    // not inferred from latency. The hand-built pool + DelegatingDataSource wrapper means Boot's
    // Hikari metrics binder doesn't pick it up, so bind it here.
    meterRegistry.ifAvailable(config::setMetricRegistry);

    return new ValidatingDataSource(new HikariDataSource(config), validationQuery);
  }

  /**
   * A SEPARATE, small Hikari pool dedicated to the Jasper report-fill path (Story 29.1). The
   * Schedule 9 SQL-in-template fill borrows a JDBC connection for the ENTIRE render (embedded SQL +
   * PDF formatting) — the longest-held connection in the app. Drawing it from the {@link Primary}
   * transactional pool (default max 5) means a handful of concurrent reports can starve ordinary
   * schedule requests. This bean isolates report fills so they can only exhaust their own small
   * pool, with a short connection-timeout so the Nth queued render fast-fails instead of parking a
   * Tomcat thread for the full 30s.
   *
   * <p>Deliberately NOT {@link Primary}: the {@code jdbcTemplate}/{@code
   * namedParameterJdbcTemplate}/ {@code transactionManager} beans keep binding to the transactional
   * pool. Only {@code ReportService} (via {@code @Qualifier("reportingDataSource")}) draws from
   * here.
   */
  @Bean("reportingDataSource")
  public DataSource reportingDataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name:oracle.jdbc.OracleDriver}")
          String driverClassName,
      @Value("${spring.datasource.reporting.hikari.pool-name:ILCRReportingPool}") String poolName,
      @Value("${spring.datasource.reporting.hikari.maximum-pool-size:3}") int maximumPoolSize,
      @Value("${spring.datasource.reporting.hikari.minimum-idle:0}") int minimumIdle,
      @Value("${spring.datasource.reporting.hikari.connection-timeout:5000}")
          long connectionTimeout,
      @Value("${spring.datasource.hikari.idle-timeout:60000}") long idleTimeout,
      @Value("${spring.datasource.hikari.max-lifetime:180000}") long maxLifetime,
      @Value("${spring.datasource.hikari.keepalive-time:60000}") long keepaliveTime,
      @Value("${ilcr.datasource.validation-query:SELECT 1 FROM DUAL}") String validationQuery,
      ObjectProvider<MeterRegistry> meterRegistry) {
    requireProperty("spring.datasource.url", url);
    requireProperty("spring.datasource.username", username);
    requireProperty("spring.datasource.password", password);

    HikariConfig config =
        reportingHikariConfig(
            url,
            username,
            password,
            driverClassName,
            poolName,
            maximumPoolSize,
            minimumIdle,
            connectionTimeout,
            idleTimeout,
            maxLifetime,
            keepaliveTime,
            validationQuery);
    meterRegistry.ifAvailable(config::setMetricRegistry);
    return new ValidatingDataSource(new HikariDataSource(config), validationQuery);
  }

  /**
   * Build the reporting {@link HikariConfig}: the connection settings shared with the primary pool
   * (via {@link #applyCommonHikari}) plus the reporting-specific overrides — its own pool name +
   * small ceiling, a short connection-timeout (fast-fail rather than parking a Tomcat thread on the
   * Nth queued render), NO leak-detection (a render legitimately holds its connection for the whole
   * fill), and the read-only HINT. Extracted (package-private) so those invariants are
   * unit-testable without opening a database connection.
   *
   * <p>NOTE: {@code setReadOnly(true)} is a Hikari/JDBC <em>hint</em>, not an enforced privilege —
   * on Oracle it does not by itself prevent writes. It is intent-signalling + defence-in-depth
   * only; true write-prevention would be a read-only database account for the reporting connection.
   */
  static HikariConfig reportingHikariConfig(
      String url,
      String username,
      String password,
      String driverClassName,
      String poolName,
      int maximumPoolSize,
      int minimumIdle,
      long connectionTimeout,
      long idleTimeout,
      long maxLifetime,
      long keepaliveTime,
      String validationQuery) {
    HikariConfig config = new HikariConfig();
    applyCommonHikari(
        config,
        url,
        username,
        password,
        driverClassName,
        idleTimeout,
        maxLifetime,
        keepaliveTime,
        validationQuery);
    config.setPoolName(poolName);
    config.setMaximumPoolSize(maximumPoolSize);
    config.setMinimumIdle(minimumIdle);
    config.setConnectionTimeout(connectionTimeout);
    config.setReadOnly(true);
    return config;
  }

  /**
   * The connection settings both pools share (URL / credentials / driver / lifetimes / validation),
   * factored out so a change to one pool's shared setting can't silently drift from the other.
   * Pool-specific settings (name, sizes, timeout, leak-detection, read-only) are applied by the
   * caller.
   */
  private static void applyCommonHikari(
      HikariConfig config,
      String url,
      String username,
      String password,
      String driverClassName,
      long idleTimeout,
      long maxLifetime,
      long keepaliveTime,
      String validationQuery) {
    config.setJdbcUrl(url);
    config.setUsername(username);
    config.setPassword(password);
    config.setDriverClassName(driverClassName);
    config.setIdleTimeout(idleTimeout);
    config.setMaxLifetime(maxLifetime);
    config.setKeepaliveTime(keepaliveTime);
    config.setConnectionTestQuery(validationQuery);
  }

  @Bean
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  /**
   * Named-parameter JDBC operations over the same {@link Primary} {@code DataSource}. Because this
   * app hand-defines its datasource wiring, Boot's default {@code NamedParameterJdbcTemplate} is
   * not auto-created; declaring it here satisfies the
   * {@code @ConditionalOnBean(NamedParameterJdbcOperations)} guard that gates Spring Data JDBC's
   * {@code JdbcRepositoriesAutoConfiguration} (AD-3), so the Spring Data repository proxies (e.g.
   * {@code Schedule1Repository}) are created.
   */
  @Bean
  public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  /**
   * Transaction manager bound to the same {@link Primary} {@code DataSource} the Spring Data JDBC
   * repositories use, so {@code @Transactional} service methods (Story 2.1 write path, AR11) roll
   * back the whole unit of work on failure. Defined explicitly (rather than relying on
   * auto-configuration) so the manager and the repositories provably share one DataSource instance
   * — otherwise writes autocommit and never roll back.
   *
   * @param dataSource the primary ILCR datasource
   * @return the JDBC transaction manager
   */
  @Bean
  public PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
  }

  private static void requireProperty(String propertyName, String value) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(
          propertyName + " is required when ILCR datasource is enabled");
    }
  }
}
