package ca.bc.gov.nrs.ilcr.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 5.4 (AC6) — mock-parity: with {@code ilcr.security.enabled=false} the {@code
 * MockPrincipalFilter} seeds the principal and method security STILL evaluates
 * {@code @PreAuthorize} against the mock authority, so admin-action authorization is identical
 * on/off (AD-7). Business logic never branches on the toggle.
 *
 * <p>The dev {@code X-Mock-Groups} header drives the mock role (the SPA's role selector). An admin
 * mock passes the {@code MAINTAIN_CODE_TABLES} gate; a submitter mock is denied 403 — the same
 * outcomes {@code AdminActionAuthorizationIT} proves with a real JWT and security ON.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("Mock-parity authorization — @PreAuthorize evaluates the mock authority (Story 5.4)")
class MockParityAuthorizationIT extends AbstractOracleIT {

  private static final String CODE_TABLES = "/api/v1/code-tables";
  private static final String MOCK_GROUPS = "X-Mock-Groups";

  @Test
  @DisplayName("security OFF — a mock ILCR_SUBMITTER is denied the admin action (403)")
  void mockSubmitter_forbidden() throws Exception {
    mockMvc
        .perform(get(CODE_TABLES).header(MOCK_GROUPS, "ILCR_SUBMITTER"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("security OFF — a mock ILCR_ADMIN passes the admin action (200)")
  void mockAdmin_allowed() throws Exception {
    mockMvc.perform(get(CODE_TABLES).header(MOCK_GROUPS, "ILCR_ADMIN")).andExpect(status().isOk());
  }
}
