package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * Explicitly enables Spring Data JDBC (AD-3). This app hand-defines its datasource wiring in
 * {@link DataSourceConfiguration} rather than using Boot's defaults, so Boot's
 * {@code JdbcRepositoriesAutoConfiguration} does not activate; declaring the Spring Data JDBC
 * infrastructure here (via {@link AbstractJdbcConfiguration}: mapping context, converter, dialect,
 * aggregate template) plus {@link EnableJdbcRepositories} makes it deterministic. The Oracle dialect
 * is resolved from the live JDBC connection.
 *
 * <p>Scoped to the repository packages so scanning only picks up the Spring Data repository
 * interfaces ({@link Schedule1Repository}, {@link MillContextRepository}). Gated on the same
 * {@code ilcr.datasource.enabled} flag as the datasource so contexts without a datasource are
 * unaffected.
 */
@Configuration
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
@EnableJdbcRepositories(
    basePackageClasses = {Schedule1Repository.class, MillContextRepository.class})
public class SpringDataJdbcConfiguration extends AbstractJdbcConfiguration {
}
