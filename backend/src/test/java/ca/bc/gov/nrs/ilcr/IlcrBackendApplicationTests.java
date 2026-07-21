package ca.bc.gov.nrs.ilcr;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No-DB context smoke test. The local profile disables Oracle by default, so the JdbcClient
 * bean that repositories depend on is absent here; a mock stands in so the wiring loads. The real
 * datasource + JdbcClient path is proven by the Testcontainers acceptance tests (*IT).
 */
@SpringBootTest
@ActiveProfiles("local")
class IlcrBackendApplicationTests {

  @MockitoBean
  private JdbcClient jdbcClient;

  @MockitoBean
  private Schedule1Repository schedule1Repository;

  @Test
  void contextLoads() {
  }
}
