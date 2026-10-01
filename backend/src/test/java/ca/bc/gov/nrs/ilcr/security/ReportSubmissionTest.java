package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit test for {@link ReportSubmission} — the legacy Submit-button rule ({@code
 * UserSessionMB.canUserSubmitReport():502-509}) as the one server-side "is Submit offered"
 * authority (Story 15.3 AC 9/11, D1/D2). Role × status truth table; validity is deliberately not an
 * input, so there is nothing to vary on that axis — the click reveals a failing gate, exactly as
 * legacy.
 */
@ExtendWith(MockitoExtension.class)
class ReportSubmissionTest {

  private static final long MILL = 760L;
  private static final String GUID = "CANONSUBMITTERBBBBCCCCDDDD000001";

  @Mock private MillContextRepository millContextRepository;
  private ReportSubmission rule;

  @BeforeEach
  void setUp() {
    rule = new ReportSubmission(new SchedulePermissions(), millContextRepository);
  }

  private Authentication auth(String... authorities) {
    return new UsernamePasswordAuthenticationToken(
        "u", "p", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
  }

  @ParameterizedTest(name = "{0} at {1} -> {2}")
  @DisplayName("SUBMITTER at Draft only; ADMIN-only never; S/V/O/null never")
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
  @DisplayName("dual-role Submit keeps the SUBMITTER mill-assignment scope")
  void bothRoles_scopeIsTheSubmitterAssignment() {
    Authentication both = dualRoleJwt();
    when(millContextRepository.userHasActiveAssignment(MILL, GUID)).thenReturn(true, false);

    assertTrue(rule.canSubmit(both, "D", MILL));
    assertFalse(rule.canSubmit(both, "D", MILL));
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

  @Test
  @DisplayName(
      "validateSubmitterMillAccess: a single-role submitter passes without a repository read")
  void validateScope_singleRoleSubmitter_passesWithoutARead() {
    rule.validateSubmitterMillAccess(auth("ILCR_SUBMITTER"), MILL);
    verifyNoInteractions(millContextRepository);
  }

  @Test
  @DisplayName("validateSubmitterMillAccess: a dual-role caller with no active assignment is 403'd")
  void validateScope_dualRoleUnassigned_isDenied() {
    when(millContextRepository.userHasActiveAssignment(MILL, GUID)).thenReturn(false);

    assertThrows(
        AccessDeniedException.class, () -> rule.validateSubmitterMillAccess(dualRoleJwt(), MILL));
  }

  @Test
  @DisplayName("the dual-role scope reads the GUID from the typed dev/e2e mock principal too")
  void dualRole_mockPrincipal_guidComesFromTheRecord() {
    Authentication both =
        new UsernamePasswordAuthenticationToken(
            new MockUserPrincipal("dev-admin-submitter", GUID),
            "N/A",
            List.of(new SimpleGrantedAuthority("ADMIN"), new SimpleGrantedAuthority("SUBMITTER")));
    when(millContextRepository.userHasActiveAssignment(MILL, GUID)).thenReturn(true);

    assertTrue(rule.canSubmit(both, "D", MILL));
  }

  @Test
  @DisplayName("a dual-role caller whose principal carries no directory GUID is never in scope")
  void dualRole_opaquePrincipal_isDenied() {
    Authentication both = auth("ILCR_ADMIN", "ILCR_SUBMITTER");

    assertFalse(rule.canSubmit(both, "D", MILL));
    assertThrows(AccessDeniedException.class, () -> rule.validateSubmitterMillAccess(both, MILL));
    verifyNoInteractions(millContextRepository);
  }

  private static Authentication dualRoleJwt() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("custom:idp_user_id", GUID)
            .claim("cognito:groups", List.of("ILCR_ADMIN", "ILCR_SUBMITTER"))
            .build();
    return new UsernamePasswordAuthenticationToken(
        jwt,
        "N/A",
        List.of(
            new SimpleGrantedAuthority("ILCR_ADMIN"),
            new SimpleGrantedAuthority("ILCR_SUBMITTER")));
  }
}
