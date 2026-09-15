package ca.bc.gov.nrs.ilcr.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit coverage for what {@link MillAssociationController} owns: the two message argument orders,
 * and the acting identity it reads off the token.
 *
 * <p>The argument orders are the reason this test exists. The already-associated warning names the
 * user then the mill; the three confirmations name the mill then the user. Both orders are legal
 * {@code MessageFormat} input, so transposing them produces a grammatical sentence with the wrong
 * nouns in it — nothing downstream would fail, and {@code MillAssociationIT} asserts the resolved
 * text only for the happy path.
 *
 * <p>Companion to {@link MillAssociationControllerAuthorizationTest}, which pins the {@code
 * MAINTAIN_MILLS} gate by reflection but never invokes an endpoint.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MillAssociationController")
class MillAssociationControllerTest {

  private static final String ACTING_USER = "IDIR\\JSMITH";
  private static final String USER_GUID = "0123456789ABCDEF0123456789ABCDEF";

  @Mock private MillAssociationService service;
  @Mock private MessageSource messageSource;
  @InjectMocks private MillAssociationController controller;

  @Test
  @DisplayName("list passes includeEnded straight through and resolves nothing")
  void listPassesThrough() {
    when(service.listByMill(670L, true)).thenReturn(List.of(assignment(MillSubmitter.ENDED)));

    ResponseEntity<List<MillSubmitter>> response = controller.list(670L, true, authentication());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).extracting(MillSubmitter::status).containsExactly("ENDED");
    verifyNoInteractions(messageSource);
  }

  @Test
  @DisplayName("add resolves the confirmation as mill number, mill name, user, then two blanks")
  void addResolvesTheConfirmationInMillFirstOrder() {
    // The two trailing blanks stand in for the target user's first and last name, which no
    // association row carries. They must stay empty rather than repeat the GUID.
    when(service.add(670L, USER_GUID, ACTING_USER))
        .thenReturn(
            new AssignmentService.Outcome(
                assignment(MillSubmitter.ACTIVE), AssignmentService.MSG_ASSIGNED));
    resolves(AssignmentService.MSG_ASSIGNED, "User associated to mill 0670 CEDAR MILL.");

    ResponseEntity<AssignmentResponse> response =
        controller.add(670L, new AssignSubmitterRequest(USER_GUID), jwtAuthentication());

    assertThat(response.getBody().messageKey()).isEqualTo(AssignmentService.MSG_ASSIGNED);
    assertThat(response.getBody().message()).isEqualTo("User associated to mill 0670 CEDAR MILL.");
    assertThat(resolvedArguments()).containsExactly("0670", "CEDAR MILL", USER_GUID, "", "");
  }

  @Test
  @DisplayName("the already-associated warning uses the OTHER order — user first, then mill name")
  void alreadyAssociatedWarningUsesTheUserFirstOrder() {
    when(service.add(670L, USER_GUID, ACTING_USER))
        .thenReturn(
            new AssignmentService.Outcome(
                assignment(MillSubmitter.ACTIVE), AssignmentService.MSG_ALREADY_ASSIGNED));
    resolves(AssignmentService.MSG_ALREADY_ASSIGNED, "User is already associated to CEDAR MILL.");

    ResponseEntity<AssignmentResponse> response =
        controller.add(670L, new AssignSubmitterRequest(USER_GUID), jwtAuthentication());

    // A refusal, not a failure: the pair is unchanged and the screen shows a warning, so this is
    // still a 200 and the key is what tells the two apart.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().messageKey()).isEqualTo(AssignmentService.MSG_ALREADY_ASSIGNED);
    assertThat(resolvedArguments()).containsExactly(USER_GUID, "CEDAR MILL");
  }

  @Test
  @DisplayName("activate forwards mill, user, read revision and acting user, in that order")
  void activateForwardsEveryArgument() {
    when(service.activate(670L, USER_GUID, 4, ACTING_USER))
        .thenReturn(
            new AssignmentService.Outcome(
                assignment(MillSubmitter.ACTIVE), AssignmentService.MSG_ASSIGNED));
    resolves(AssignmentService.MSG_ASSIGNED, "activated");

    ResponseEntity<AssignmentResponse> response =
        controller.activate(670L, USER_GUID, new EndAssignmentRequest(4), jwtAuthentication());

    assertThat(response.getBody().message()).isEqualTo("activated");
    verify(service).activate(670L, USER_GUID, 4, ACTING_USER);
  }

  @Test
  @DisplayName("deactivate forwards mill, user, read revision and acting user, in that order")
  void deactivateForwardsEveryArgument() {
    when(service.deactivate(670L, USER_GUID, 4, ACTING_USER))
        .thenReturn(
            new AssignmentService.Outcome(
                assignment(MillSubmitter.ENDED), AssignmentService.MSG_ENDED));
    resolves(AssignmentService.MSG_ENDED, "ended");

    ResponseEntity<AssignmentResponse> response =
        controller.deactivate(670L, USER_GUID, new EndAssignmentRequest(4), jwtAuthentication());

    assertThat(response.getBody().messageKey()).isEqualTo(AssignmentService.MSG_ENDED);
    assertThat(response.getBody().message()).isEqualTo("ended");
    verify(service).deactivate(670L, USER_GUID, 4, ACTING_USER);
  }

  @Test
  @DisplayName("a token without custom:idp_username is refused HERE, not as an ORA-12899")
  void tokenWithoutUsernameIsRefusedAtTheBoundary() {
    for (String claim : new String[] {null, "", "   "}) {
      Authentication authentication = jwtAuthentication(claim);

      assertThatThrownBy(
              () -> controller.add(670L, new AssignSubmitterRequest(USER_GUID), authentication))
          .as("custom:idp_username=%s", claim)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("custom:idp_username");
    }
    verify(service, never()).add(anyLong(), any(), any());
  }

  @Test
  @DisplayName("with security off the dev mock principal's name stamps the audit columns")
  void nonJwtPrincipalFallsBackToItsName() {
    when(service.add(670L, USER_GUID, "dev-admin"))
        .thenReturn(
            new AssignmentService.Outcome(
                assignment(MillSubmitter.ACTIVE), AssignmentService.MSG_ASSIGNED));

    controller.add(670L, new AssignSubmitterRequest(USER_GUID), authentication());

    verify(service).add(670L, USER_GUID, "dev-admin");
  }

  // -------------------------------------------------------------- helpers

  private static MillSubmitter assignment(String status) {
    boolean active = MillSubmitter.ACTIVE.equals(status);
    return new MillSubmitter(
        USER_GUID,
        null,
        670L,
        "0670",
        "CEDAR MILL",
        status,
        active ? LocalDate.of(2026, 1, 5) : null,
        active ? null : LocalDate.of(2026, 3, 9),
        4);
  }

  private void resolves(String key, String text) {
    when(messageSource.getMessage(eq(key), any(), eq(key), any(Locale.class))).thenReturn(text);
  }

  /** The arguments the controller substituted into the last resolved message. */
  private Object[] resolvedArguments() {
    ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
    verify(messageSource).getMessage(any(), args.capture(), any(), any(Locale.class));
    return args.getValue();
  }

  /** The dev mock principal: no token, so the audit identity is its name. */
  private static Authentication authentication() {
    return new UsernamePasswordAuthenticationToken("dev-admin", "N/A", List.of());
  }

  private static Authentication jwtAuthentication() {
    return jwtAuthentication(ACTING_USER);
  }

  private static Authentication jwtAuthentication(String idpUsername) {
    Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "irrelevant");
    if (idpUsername != null) {
      jwt.claim("custom:idp_username", idpUsername);
    }
    return new JwtAuthenticationToken(jwt.build());
  }
}
