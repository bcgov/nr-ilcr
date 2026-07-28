package ca.bc.gov.nrs.ilcr;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No-DB context smoke test. Forces the Oracle datasource OFF (AD-2 intent): the merged
 * {@code application.yml} now defaults {@code ilcr.datasource.enabled} to {@code true}, so this
 * smoke test must set it {@code false} explicitly or the Spring Data JDBC config would try to build
 * the JDBC infrastructure against a (nonexistent) live connection. With the datasource off the
 * Spring Data JDBC repositories are absent; mocks stand in so the wiring loads. The real datasource +
 * Spring Data JDBC path is proven by the Testcontainers acceptance tests (*IT).
 */
@SpringBootTest
@TestPropertySource(properties = "ilcr.datasource.enabled=false")
class IlcrBackendApplicationTests {

  @MockitoBean
  private Schedule1Repository schedule1Repository;

  @MockitoBean
  private MillContextRepository millContextRepository;

  @MockitoBean
  private Schedule2Repository schedule2Repository;

  @Test
  void contextLoads() {
  }
}
