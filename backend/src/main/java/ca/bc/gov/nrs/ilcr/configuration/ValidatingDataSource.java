package ca.bc.gov.nrs.ilcr.configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class ValidatingDataSource extends DelegatingDataSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidatingDataSource.class);

    private final String validationQuery;

    public ValidatingDataSource(DataSource targetDataSource, String validationQuery) {
        super(targetDataSource);
        this.validationQuery = validationQuery;
        validate();
    }

    private void validate() {
        try (Connection connection = getTargetDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(validationQuery);
            LOGGER.info("Oracle datasource validation successful");
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Oracle datasource validation failed. Check SPRING_DATASOURCE_URL, "
                            + "SPRING_DATASOURCE_USERNAME, and SPRING_DATASOURCE_PASSWORD.",
                    exception
            );
        }
    }
}
