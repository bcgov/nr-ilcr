package ca.bc.gov.nrs.ilcr;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Repository;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Repository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No-DB context smoke test. Forces the Oracle datasource OFF (AD-2 intent): the merged
 * {@code application.yml} defaults {@code ilcr.datasource.enabled} to {@code false}; this smoke
 * test keeps that explicit. With the datasource off the
 * Spring Data JDBC repositories are absent; mocks stand in so the wiring loads. The real datasource +
 * Spring Data JDBC path is proven by the Testcontainers acceptance tests (*IT).
 */
@SpringBootTest
@TestPropertySource(properties = "ilcr.datasource.enabled=false")
class IlcrBackendApplicationTests {

  @MockitoBean
  private Schedule1Repository schedule1Repository;

  @MockitoBean
  private Schedule3Repository schedule3Repository;

  @MockitoBean
  private MillContextRepository millContextRepository;

  @MockitoBean
  private Schedule2Repository schedule2Repository;

  @MockitoBean
  private Schedule4Repository schedule4Repository;

  @Test
  void contextLoads() {
  }
}
