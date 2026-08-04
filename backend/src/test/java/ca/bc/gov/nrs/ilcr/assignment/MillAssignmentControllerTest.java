package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentOutcome;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Unit test for {@link MillAssignmentController} — audit-user resolution + message resolution. */
@ExtendWith(MockitoExtension.class)
class MillAssignmentControllerTest {

  private static final long MILL = 514L;
  private static final String GUID = "B29C746A6BAF45B9844EE2E2984CA472";

  @Mock private MillAssignmentService assignmentService;
  @Mock private MessageSource messageSource;
  @InjectMocks private MillAssignmentController controller;

  private static MillSubmitter submitter(String status) {
    return new MillSubmitter(GUID, "Pat Submitter", "PSUBMIT", MILL, "514", "Test Mill",
        status, LocalDate.of(2026, 8, 4), status.equals("ENDED") ? LocalDate.of(2026, 8, 5) : null, 0);
  }

  @Test
  void assign_resolvesActivateMessage_andUsesMockPrincipalNameForAudit() {
    when(assignmentService.assign(eq(MILL), eq(GUID), anyString()))
        .thenReturn(new AssignmentOutcome(submitter("ACTIVE"), "user.activate.mill"));
    when(messageSource.getMessage(eq("user.activate.mill"), any(), anyString(), any()))
        .thenReturn("Mill 514 - Test Mill has been activated for user Pat Submitter.");

    Authentication mock = new UsernamePasswordAuthenticationToken(
        "dev-ilcr_admin", null, List.of(new SimpleGrantedAuthority("ILCR_ADMIN")));

    AssignmentResponse body = controller.assign(MILL, new AssignSubmitterRequest(GUID), mock).getBody();

    assertEquals("user.activate.mill", body.message().key());
    assertEquals("Mill 514 - Test Mill has been activated for user Pat Submitter.", body.message().text());

    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(assignmentService).assign(eq(MILL), eq(GUID), user.capture());
    assertEquals("dev-ilcr_admin", user.getValue()); // mock path → getName()
  }

  @Test
  void assign_duplicate_resolvesNotAssociatedMessage() {
    when(assignmentService.assign(eq(MILL), eq(GUID), anyString()))
        .thenReturn(new AssignmentOutcome(submitter("ACTIVE"), "user.not.associated.to.mill"));
    when(messageSource.getMessage(eq("user.not.associated.to.mill"), any(), anyString(), any()))
        .thenReturn("User Pat Submitter is already associated to mill Test Mill. Please verify.");

    Authentication mock = new UsernamePasswordAuthenticationToken(
        "dev-ilcr_admin", null, List.of(new SimpleGrantedAuthority("ILCR_ADMIN")));

    AssignmentResponse body = controller.assign(MILL, new AssignSubmitterRequest(GUID), mock).getBody();

    assertEquals("user.not.associated.to.mill", body.message().key());
  }

  @Test
  void end_usesIdpUsernameFromJwtForAudit() {
    when(assignmentService.end(eq(MILL), eq(GUID), eq(0), anyString()))
        .thenReturn(new AssignmentOutcome(submitter("ENDED"), "user.deactivate.mill"));
    when(messageSource.getMessage(eq("user.deactivate.mill"), any(), anyString(), any()))
        .thenReturn("Mill 514 - Test Mill has been deactivated for user Pat Submitter.");

    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
        .claim("custom:idp_username", "GRPASCUC").build();
    Authentication auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ILCR_ADMIN")));

    AssignmentResponse body =
        controller.end(MILL, GUID, new EndAssignmentRequest(0), auth).getBody();

    assertEquals("user.deactivate.mill", body.message().key());
    ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(assignmentService).end(eq(MILL), eq(GUID), eq(0), user.capture());
    // jwt path -> JwtPrincipalUtil.getUserId = provider-then-username (nr-csp currentUsername pattern);
    // provider is empty in this test JWT, so assert the username is carried rather than the exact format.
    org.junit.jupiter.api.Assertions.assertTrue(user.getValue().contains("GRPASCUC"));
  }
}
