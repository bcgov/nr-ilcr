package ca.bc.gov.nrs.ilcr.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.oracle.OracleContainer;

/**
 * Shared Testcontainers base for Schedule 1 acceptance ({@code *IT.java}) tests (AD-10). Starts one
 * Oracle-Free container for the whole run, applies the shared test-scope THE snapshot with Flyway's
 * Java API, and exposes a MockMvc wired through the Spring Security filter chain.
 *
 * <p>The container is started and migrated in a static block (once per JVM) rather than via
 * {@code @Container}/{@code @Testcontainers}, and Spring Boot's Flyway auto-run is disabled — Boot 4
 * split Flyway auto-configuration into a separate module the app deliberately does not depend on
 * (AD-2: no runtime DDL). Connecting as user {@code THE} makes {@code THE.<table>} resolve as the
 * current schema without needing CREATE USER privileges.
 *
 * <p>Spring Boot 4 also moved the servlet {@code @AutoConfigureMockMvc} slice; MockMvc is built
 * explicitly from the {@link WebApplicationContext} with {@code springSecurity()} applied so the real
 * authorization path is exercised (AD-7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class AbstractOracleIT {

  static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart")
      .withUsername("THE")
      .withPassword("THE");

  static {
    ORACLE.start();
    Flyway.configure()
        .dataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword())
        .locations("classpath:db")
        .load()
        .migrate();
  }

  @Autowired
  private WebApplicationContext webApplicationContext;

  /** Shared MockMvc for subclasses, wired through the full Spring Security filter chain. */
  protected MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
    registry.add("ilcr.datasource.enabled", () -> "true");
    // Migrations applied manually in the static block above; keep Boot's Flyway off.
    registry.add("spring.flyway.enabled", () -> "false");
  }
}
