package ca.bc.gov.nrs.ilcr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * No-DB context smoke test. The Oracle datasource is disabled by default (AD-2), so the JdbcClient
 * bean that repositories depend on is absent here; a mock stands in so the wiring loads. The real
 * datasource + JdbcClient path is proven by the Testcontainers acceptance tests (*IT).
 */
@SpringBootTest
class IlcrBackendApplicationTests {

  @MockitoBean
  private JdbcClient jdbcClient;

  @Test
  void contextLoads() {
  }
}
