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
import org.springframework.jdbc.core.simple.JdbcClient;
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

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * JdbcClient is retained for legacy Oracle access patterns that do not map cleanly to Spring Data
     * JDBC repositories, such as projection-heavy queries or future PL/SQL / REF-CURSOR calls.
     */
    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    /**
     * Transaction manager bound to the same {@link Primary} {@code DataSource} the {@link JdbcClient}
     * uses, so {@code @Transactional} service methods (Story 2.1 write path, AR11) roll back the whole
     * unit of work on failure. Defined explicitly (rather than relying on auto-configuration) so the
     * manager and the JdbcClient provably share one DataSource instance — otherwise writes autocommit
     * and never roll back.
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
