package ca.bc.gov.nrs.ilcr.millmaintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millmaintenance.dto.AdminMill;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.AdminMillResponse;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ChangeMillStatusRequest;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ContactOption;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ImportableMill;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.MillSearchResponse;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.SaveMillContactsRequest;
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
 * Unit coverage for the two things {@link MillMaintenanceController} owns and no other test can
 * prove: the zero-match search branch, and the message/audit-identity resolution the service never
 * sees.
 *
 * <p>{@code MillMaintenanceIT} drives the same endpoints over HTTP against Oracle, but it runs in
 * the Integration job rather than the Sonar analysis job, so it also cannot stand in for these.
 * More importantly it cannot reach the identity failures at all: the IT's tokens always carry a
 * usable {@code custom:idp_username}, so the refusal below is unreachable from there.
 *
 * <p>Mocked service and message source, no Spring container — the controller holds no business
 * rules, so what is worth asserting is which arguments reach each collaborator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MillMaintenanceController")
class MillMaintenanceControllerTest {

  private static final String ACTING_USER = "IDIR\\JSMITH";

  @Mock private MillMaintenanceService service;
  @Mock private MessageSource messageSource;
  @InjectMocks private MillMaintenanceController controller;

  // ---------------------------------------------------------------- reads

  @Test
  @DisplayName("a search with matches answers 200 with the rows and NO message")
  void searchWithMatchesCarriesNoMessage() {
    when(service.search("0670", "CEDAR", AdminMill.ACTIVE)).thenReturn(List.of(mill(), mill()));

    ResponseEntity<MillSearchResponse> response =
        controller.search("0670", "CEDAR", AdminMill.ACTIVE, authentication());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().results()).hasSize(2);
    assertThat(response.getBody().messageKey()).isNull();
    assertThat(response.getBody().message()).isNull();
    // Nothing is resolved when there is nothing to say.
    verifyNoInteractions(messageSource);
  }

  @Test
  @DisplayName("a zero-match search answers 200 with the not-found message, never an error status")
  void zeroMatchSearchIsAnOkWithAMessage() {
    // S11: the criteria stay on screen for another attempt, so a 4xx here would make a perfectly
    // valid search render as a failure.
    when(service.search(null, "NO SUCH MILL", null)).thenReturn(List.of());
    resolves(MillMaintenanceService.MSG_NOT_FOUND, "No mills were found.");

    ResponseEntity<MillSearchResponse> response =
        controller.search(null, "NO SUCH MILL", null, authentication());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().results()).isEmpty();
    assertThat(response.getBody().messageKey()).isEqualTo(MillMaintenanceService.MSG_NOT_FOUND);
    assertThat(response.getBody().message()).isEqualTo("No mills were found.");
  }

  @Test
  @DisplayName("findImportable, findById and contactOptions pass through untouched")
  void readsPassThrough() {
    ImportableMill importable = new ImportableMill(671L, "0671", "SPRUCE MILL");
    ContactOption option = new ContactOption(44L, "J. SMITH");
    when(service.findImportable("0671", "SPRUCE")).thenReturn(List.of(importable));
    when(service.findById(670L)).thenReturn(mill());
    when(service.contactOptions(670L)).thenReturn(List.of(option));

    assertThat(controller.findImportable("0671", "SPRUCE", authentication()).getBody())
        .containsExactly(importable);
    assertThat(controller.findById(670L, authentication()).getBody()).isEqualTo(mill());
    assertThat(controller.contactOptions(670L, authentication()).getBody()).containsExactly(option);
    // A read carries no confirmation sentence.
    verifyNoInteractions(messageSource);
  }

  // --------------------------------------------------------------- writes

  @Test
  @DisplayName("import answers the mill with a NULL message — it has no legacy confirmation")
  void importCarriesNoMessage() {
    // The legacy screen confirmed an import by displaying the mill's details, so there is no
    // sentence to resolve and the key must stay null on the wire rather than becoming "".
    when(service.importMill(670L, ACTING_USER))
        .thenReturn(new MillMaintenanceService.Outcome(mill(), null));

    ResponseEntity<AdminMillResponse> response = controller.importMill(670L, jwtAuthentication());

    assertThat(response.getBody().mill()).isEqualTo(mill());
    assertThat(response.getBody().messageKey()).isNull();
    assertThat(response.getBody().message()).isNull();
    verifyNoInteractions(messageSource);
  }

  @Test
  @DisplayName("activate passes the read revision and the acting user, and resolves its message")
  void activateResolvesItsMessage() {
    when(service.activate(670L, 3, ACTING_USER))
        .thenReturn(
            new MillMaintenanceService.Outcome(mill(), MillMaintenanceService.MSG_ACTIVATED));
    resolves(MillMaintenanceService.MSG_ACTIVATED, "Mill 0670 CEDAR MILL activated.");

    ResponseEntity<AdminMillResponse> response =
        controller.activate(670L, new ChangeMillStatusRequest(3), jwtAuthentication());

    assertThat(response.getBody().messageKey()).isEqualTo(MillMaintenanceService.MSG_ACTIVATED);
    assertThat(response.getBody().message()).isEqualTo("Mill 0670 CEDAR MILL activated.");
    assertThat(resolvedArguments()).containsExactly("0670", "CEDAR MILL");
  }

  @Test
  @DisplayName("deactivate passes the read revision and the acting user, and resolves its message")
  void deactivateResolvesItsMessage() {
    when(service.deactivate(670L, 3, ACTING_USER))
        .thenReturn(
            new MillMaintenanceService.Outcome(mill(), MillMaintenanceService.MSG_DEACTIVATED));
    resolves(MillMaintenanceService.MSG_DEACTIVATED, "Mill 0670 CEDAR MILL expired.");

    ResponseEntity<AdminMillResponse> response =
        controller.deactivate(670L, new ChangeMillStatusRequest(3), jwtAuthentication());

    assertThat(response.getBody().messageKey()).isEqualTo(MillMaintenanceService.MSG_DEACTIVATED);
    assertThat(response.getBody().message()).isEqualTo("Mill 0670 CEDAR MILL expired.");
  }

  @Test
  @DisplayName("saveContacts unpacks all four body fields in order — a swap would be invisible")
  void saveContactsUnpacksTheWholeBody() {
    // The three contact fields are same-shaped and adjacent; nulls mean "cleared", not "unchanged",
    // so a transposed pair would silently write the wrong column rather than fail.
    when(service.saveContacts(670L, "Y", 11L, null, 3, ACTING_USER))
        .thenReturn(new MillMaintenanceService.Outcome(mill(), MillMaintenanceService.MSG_SAVED));
    resolves(MillMaintenanceService.MSG_SAVED, "Mill 0670 CEDAR MILL updated.");

    ResponseEntity<AdminMillResponse> response =
        controller.saveContacts(
            670L, new SaveMillContactsRequest("Y", 11L, null, 3), jwtAuthentication());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().messageKey()).isEqualTo(MillMaintenanceService.MSG_SAVED);
    assertThat(resolvedArguments()).containsExactly("0670", "CEDAR MILL");
  }

  // ----------------------------------------------------- acting identity

  @Test
  @DisplayName("a token without custom:idp_username is refused HERE, not as an ORA-12899")
  void tokenWithoutUsernameIsRefusedAtTheBoundary() {
    for (String claim : new String[] {null, "", "   "}) {
      Authentication authentication = jwtAuthentication(claim);

      assertThatThrownBy(() -> controller.importMill(670L, authentication))
          .as("custom:idp_username=%s", claim)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("custom:idp_username");
    }
    // The write must not have been attempted with a substitute identity.
    verify(service, never()).importMill(any(Long.class), any());
  }

  @Test
  @DisplayName("with security off the dev mock principal's name stamps the audit columns")
  void nonJwtPrincipalFallsBackToItsName() {
    when(service.importMill(670L, "dev-admin"))
        .thenReturn(new MillMaintenanceService.Outcome(mill(), null));

    controller.importMill(670L, authentication());

    verify(service).importMill(670L, "dev-admin");
  }

  // -------------------------------------------------------------- helpers

  private static AdminMill mill() {
    return new AdminMill(670L, "0670", "CEDAR MILL", AdminMill.ACTIVE, "Active", "Y", 11L, 22L, 3);
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
