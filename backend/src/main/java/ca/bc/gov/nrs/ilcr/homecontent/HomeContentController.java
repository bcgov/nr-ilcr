package ca.bc.gov.nrs.ilcr.homecontent;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import ca.bc.gov.nrs.ilcr.homecontent.api.HomeContentApi;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveRequest;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveResponse;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RestController;

/**
 * Content Editing endpoints (Story 24.2 / UC-CNT-001). {@code list}/{@code save} are gated on the
 * ADMIN-only {@code EDIT_HOME_CONTENT} action (S13); {@code mine} is authenticated-only so the Home
 * page can render the viewer's role message. Resolves the verbatim success text here (AD-8).
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class HomeContentController implements HomeContentApi {

  private static final String MSG_SAVED = "dataSavedSuccesfullyInfoMsg";

  private final HomeContentService service;
  private final MessageSource messageSource;

  public HomeContentController(HomeContentService service, MessageSource messageSource) {
    this.service = service;
    this.messageSource = messageSource;
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_HOME_CONTENT')")
  public ResponseEntity<List<HomeContentEntry>> list(Authentication authentication) {
    return ResponseEntity.ok(service.readAll());
  }

  @Override
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<HomeContentEntry> mine(Authentication authentication) {
    return ResponseEntity.ok(service.readForRole(contentRoleOf(authentication)));
  }

  @Override
  @PreAuthorize("@permissions.hasPermission(authentication, 'EDIT_HOME_CONTENT')")
  public ResponseEntity<HomeContentSaveResponse> save(
      HomeContentSaveRequest request, Authentication authentication) {
    service.saveAll(request, authentication.getName());
    String message =
        messageSource.getMessage(MSG_SAVED, null, MSG_SAVED, LocaleContextHolder.getLocale());
    return ResponseEntity.ok(new HomeContentSaveResponse(MSG_SAVED, message, service.readAll()));
  }

  /**
   * ILCR_ADMIN → the Administrator message; everyone else (Licensee/submitter) → the Licensee one.
   */
  private static String contentRoleOf(Authentication authentication) {
    boolean admin =
        authentication != null
            && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(Role::fromValue)
                .anyMatch(role -> role == Role.ADMIN);
    return admin ? HomeContentService.ROLE_ADMIN : HomeContentService.ROLE_LICENSEE;
  }
}
