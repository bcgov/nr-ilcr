package ca.bc.gov.nrs.ilcr.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class OracleHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public OracleHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", Integer.class);
            return Health.up()
                    .withDetail("database", "oracle")
                    .withDetail("validationQueryResult", result)
                    .build();
        } catch (Exception exception) {
            return Health.down(exception)
                    .withDetail("database", "oracle")
                    .build();
        }
    }
}
