package ca.bc.gov.nrs.ilcr.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
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

@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class DataSourceConfiguration {

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:oracle.jdbc.OracleDriver}") String driverClassName,
            @Value("${spring.datasource.hikari.pool-name:ILCROraclePool}") String poolName,
            @Value("${spring.datasource.hikari.maximum-pool-size:5}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:1}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeout,
            @Value("${spring.datasource.hikari.idle-timeout:60000}") long idleTimeout,
            @Value("${spring.datasource.hikari.max-lifetime:180000}") long maxLifetime,
            @Value("${spring.datasource.hikari.keepalive-time:60000}") long keepaliveTime,
            @Value("${spring.datasource.hikari.leak-detection-threshold:60000}") long leakDetectionThreshold,
            @Value("${ilcr.datasource.validation-query:SELECT 1 FROM DUAL}") String validationQuery
    ) {
        requireProperty("spring.datasource.url", url);
        requireProperty("spring.datasource.username", username);
        requireProperty("spring.datasource.password", password);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setKeepaliveTime(keepaliveTime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);
        config.setConnectionTestQuery(validationQuery);

        return new ValidatingDataSource(new HikariDataSource(config), validationQuery);
    }

    /**
     * A SEPARATE, read-only, small Hikari pool dedicated to the Jasper report-fill path (Story 29.1).
     * The Schedule 9 SQL-in-template fill borrows a JDBC connection for the ENTIRE render (embedded SQL
     * + PDF formatting) — the longest-held connection in the app. Drawing it from the {@link Primary}
     * transactional pool (default max 5) means a handful of concurrent reports can starve ordinary
     * schedule requests. This bean isolates report fills so they can only exhaust their own small pool.
     *
     * <p>Deliberately NOT {@link Primary}: the {@code jdbcTemplate}/{@code namedParameterJdbcTemplate}/
     * {@code transactionManager} beans keep binding to the transactional pool. Only {@code ReportService}
     * (via {@code @Qualifier("reportingDataSource")}) draws from here.
     */
    @Bean("reportingDataSource")
    public DataSource reportingDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name:oracle.jdbc.OracleDriver}") String driverClassName,
            @Value("${spring.datasource.reporting.hikari.pool-name:ILCRReportingPool}") String poolName,
            @Value("${spring.datasource.reporting.hikari.maximum-pool-size:3}") int maximumPoolSize,
            @Value("${spring.datasource.reporting.hikari.minimum-idle:0}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:30000}") long connectionTimeout,
            @Value("${spring.datasource.hikari.idle-timeout:60000}") long idleTimeout,
            @Value("${spring.datasource.hikari.max-lifetime:180000}") long maxLifetime,
            @Value("${spring.datasource.hikari.keepalive-time:60000}") long keepaliveTime,
            @Value("${ilcr.datasource.validation-query:SELECT 1 FROM DUAL}") String validationQuery
    ) {
        requireProperty("spring.datasource.url", url);
        requireProperty("spring.datasource.username", username);
        requireProperty("spring.datasource.password", password);

        HikariConfig config = reportingHikariConfig(url, username, password, driverClassName, poolName,
                maximumPoolSize, minimumIdle, connectionTimeout, idleTimeout, maxLifetime, keepaliveTime,
                validationQuery);
        return new ValidatingDataSource(new HikariDataSource(config), validationQuery);
    }

    /**
     * Build the read-only reporting {@link HikariConfig}. Extracted (package-private) so its
     * isolation-critical invariants — read-only, its own pool name, a small dedicated ceiling, and NO
     * leak-detection (a render legitimately holds its connection for the whole fill) — are unit-testable
     * without opening a real database connection.
     */
    static HikariConfig reportingHikariConfig(
            String url, String username, String password, String driverClassName, String poolName,
            int maximumPoolSize, int minimumIdle, long connectionTimeout, long idleTimeout,
            long maxLifetime, long keepaliveTime, String validationQuery) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setPoolName(poolName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setKeepaliveTime(keepaliveTime);
        config.setConnectionTestQuery(validationQuery);
        // Read-only: report fills never write. Leak-detection intentionally left unset (0/off): a Jasper
        // render holds its connection for the full fill+format, which would trip a leak warning; the
        // dedicated small pool bounds the exposure instead.
        config.setReadOnly(true);
        return config;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Named-parameter JDBC operations over the same {@link Primary} {@code DataSource}. Because this
     * app hand-defines its datasource wiring, Boot's default {@code NamedParameterJdbcTemplate} is not
     * auto-created; declaring it here satisfies the {@code @ConditionalOnBean(NamedParameterJdbcOperations)}
     * guard that gates Spring Data JDBC's {@code JdbcRepositoriesAutoConfiguration} (AD-3), so the
     * Spring Data repository proxies (e.g. {@code Schedule1Repository}) are created.
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
            throw new IllegalStateException(propertyName + " is required when ILCR datasource is enabled");
        }
    }
}
