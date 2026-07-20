package ca.bc.gov.nrs.ilcr.configuration;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = "ilcr.security.enabled=true")
class SecurityConfigurationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @MockitoBean
  private JdbcClient jdbcClient;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUpMockMvc() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  void securedHealthProbePaths_arePublicWithoutJwt() throws Exception {
    mockMvc.perform(get("/api/health/readiness")).andExpect(status().isOk());
    mockMvc.perform(get("/api/health/liveness")).andExpect(status().isOk());
  }

  @Test
  void securedApiPath_requiresJwt() throws Exception {
    mockMvc.perform(get("/api/v1/schedule1")
            .param("millId", "518")
            .param("year", "2021"))
        .andExpect(status().isUnauthorized());
  }
}
