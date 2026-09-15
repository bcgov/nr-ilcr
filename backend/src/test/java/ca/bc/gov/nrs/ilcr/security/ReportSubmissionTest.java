package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit test for {@link ReportSubmission} — the legacy Submit-button rule ({@code
 * UserSessionMB.canUserSubmitReport():502-509}) as the one server-side "is Submit offered"
 * authority (Story 15.3 AC 9/11, D1/D2). Role × status truth table; validity is deliberately not an
 * input, so there is nothing to vary on that axis — the click reveals a failing gate, exactly as
 * legacy.
 */
class ReportSubmissionTest {

  private final ReportSubmission rule = new ReportSubmission(new SchedulePermissions());

  private Authentication auth(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
        "u", "p", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
  }

  @ParameterizedTest(name = "{0} at {1} -> {2}")
  @DisplayName("SUBMITTER at Draft only; ADMIN never; S/V/O/null never")
  @CsvSource({
    "ILCR_SUBMITTER, D, true",
    "ILCR_SUBMITTER, S, false",
    "ILCR_SUBMITTER, V, false",
    "ILCR_SUBMITTER, O, false",
    "ILCR_ADMIN, D, false",
    "ILCR_ADMIN, S, false",
    "ILCR_ADMIN, V, false",
    "ILCR_ADMIN, O, false",
    "SUBMITTER, D, true",
    "ADMIN, D, false",
    "SCOPE_write, D, false",
  })
  void roleByStatus(String authority, String status, boolean offered) {
    assertTrue(offered == rule.canSubmit(auth(authority), status));
  }

  @Test
  @DisplayName("a null status (no status row) is never Draft")
  void nullStatus_notOffered() {
    assertFalse(rule.canSubmit(auth("ILCR_SUBMITTER"), null));
  }

  @Test
  @DisplayName("holding ADMIN alongside SUBMITTER does not take the offer away")
  void bothRoles_offeredAtDraft() {
    assertTrue(rule.canSubmit(auth("ILCR_ADMIN", "ILCR_SUBMITTER"), "D"));
  }

  @Test
  @DisplayName("no authentication, or an unauthenticated one, is never offered Submit")
  void nullOrAnonymous_notOffered() {
    assertFalse(rule.canSubmit(null, "D"));
    UsernamePasswordAuthenticationToken anonymous =
        new UsernamePasswordAuthenticationToken("u", "p");
    assertFalse(anonymous.isAuthenticated());
    assertFalse(rule.canSubmit(anonymous, "D"));
  }
}
