package ca.bc.gov.nrs.ilcr.assignment;

import ca.bc.gov.nrs.ilcr.assignment.api.MillAssignmentApi;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentOutcome;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submitter↔mill assignment endpoints (Story 2.2). ILCR_ADMIN-gated (AD-7): a live role-switcher is an
 * admin-only capability. Never touches repositories directly (AD-1 layering); all message text is
 * server-resolved verbatim from the bundle (AD-8). The audit user is the acting admin's
 * {@code custom:idp_username} (Story 1.0), which fits the {@code VARCHAR2(30)} audit columns — NOT the
 * 36-char JWT {@code sub}.
 */
@RestController
@RequiredArgsConstructor
public class MillAssignmentController implements MillAssignmentApi {

  private final MillAssignmentService assignmentService;
  private final MessageSource messageSource;

  @Override
  @PreAuthorize("hasAuthority('ILCR_ADMIN')")
  public ResponseEntity<List<MillSubmitter>> list(long millId, Authentication authentication) {
    return ResponseEntity.ok(assignmentService.listByMill(millId));
  }

  @Override
  @PreAuthorize("hasAuthority('ILCR_ADMIN')")
  public ResponseEntity<AssignmentResponse> assign(
      long millId, AssignSubmitterRequest request, Authentication authentication) {
    AssignmentOutcome outcome =
        assignmentService.assign(millId, request.userGuid(), actingUser(authentication));
    return ResponseEntity.ok(new AssignmentResponse(outcome.submitter(), resolve(outcome)));
  }

  @Override
  @PreAuthorize("hasAuthority('ILCR_ADMIN')")
  public ResponseEntity<AssignmentResponse> end(
      long millId, String userGuid, EndAssignmentRequest request, Authentication authentication) {
    AssignmentOutcome outcome = assignmentService.end(
        millId, userGuid, request.revisionCount(), actingUser(authentication));
    return ResponseEntity.ok(new AssignmentResponse(outcome.submitter(), resolve(outcome)));
  }

  /** The acting admin's audit id: the FAM {@code custom:idp_username} (JWT) or the mock principal name. */
  private static String actingUser(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwt) {
      String id = JwtPrincipalUtil.getUserId(jwt);
      if (id != null && !id.isBlank()) {
        return id;
      }
    }
    return authentication.getName();
  }

  /** Resolve the outcome's bundle key to verbatim text (AD-8), filling the message's positional args. */
  private MessageInfo resolve(AssignmentOutcome outcome) {
    MillSubmitter s = outcome.submitter();
    String userLabel = s.displayName() != null ? s.displayName() : s.userGuid();
    Object[] args = switch (outcome.messageKey()) {
      // user.not.associated.to.mill = "User {0} is already associated to mill {1}. ..."
      case "user.not.associated.to.mill" ->
          new Object[] {userLabel, s.millName() != null ? s.millName() : s.millNumber()};
      // user.activate.mill / user.deactivate.mill = "Mill {0} - {1} has been ... for user {2}."
      default -> new Object[] {s.millNumber(), s.millName(), userLabel};
    };
    String text = messageSource.getMessage(
        outcome.messageKey(), args, outcome.messageKey(), LocaleContextHolder.getLocale());
    return new MessageInfo(outcome.messageKey(), text);
  }
}
