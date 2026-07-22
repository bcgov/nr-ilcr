package ca.bc.gov.nrs.ilcr;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No-DB context smoke test. The Oracle datasource is disabled by default (AD-2), so the Spring Data
 * JDBC repositories (created only when a datasource is present) are absent here; mocks stand in so
 * the wiring loads. The real datasource + Spring Data JDBC path is proven by the Testcontainers
 * acceptance tests (*IT).
 */
@SpringBootTest
class IlcrBackendApplicationTests {

  @MockitoBean
  private Schedule1Repository schedule1Repository;

  @MockitoBean
  private MillContextRepository millContextRepository;

  @Test
  void contextLoads() {
  }
}
