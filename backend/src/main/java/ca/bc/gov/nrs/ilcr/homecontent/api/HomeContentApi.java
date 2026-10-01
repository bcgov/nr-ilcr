package ca.bc.gov.nrs.ilcr.homecontent.api;

import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentEntry;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveRequest;
import ca.bc.gov.nrs.ilcr.homecontent.dto.HomeContentSaveResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Content Editing API contract (Story 24.2 / UC-CNT-001; controller + api-interface split). The
 * interface owns the request mapping; {@code HomeContentController} adds authorization. {@code
 * list} and {@code save} are ADMIN-only ({@code EDIT_HOME_CONTENT}, 403 for a submitter, S13);
 * {@code mine} is authenticated-only — any signed-in user reads their own role's message for the
 * Home render.
 */
@RequestMapping("/api/v1/home-content")
public interface HomeContentApi {

  /**
   * The three role messages for the Content Editing page.
   *
   * @param authentication the caller (must hold {@code EDIT_HOME_CONTENT})
   * @return 200 with the role messages
   */
  @GetMapping
  ResponseEntity<List<HomeContentEntry>> list(Authentication authentication);

  /**
   * The message for the CALLER's role — the Home page render (Licensee for a submitter,
   * Administrator for an admin). Authenticated but not admin-gated.
   *
   * @param authentication the caller
   * @return 200 with the caller-role message
   */
  @GetMapping("/mine")
  ResponseEntity<HomeContentEntry> mine(Authentication authentication);

  /**
   * Save all three role messages atomically. Any blank editor → 400 with all blanks reported
   * together (FLD-001) and nothing saved; a message over the column cap → 400. Missing role rows
   * are created by the save so an administrator can repair partial legacy data.
   *
   * @param request the three messages
   * @param authentication the caller (must hold {@code EDIT_HOME_CONTENT}; drives the audit user)
   * @return 200 with the verbatim success message + reloaded messages
   */
  @PutMapping
  ResponseEntity<HomeContentSaveResponse> save(
      @RequestBody HomeContentSaveRequest request, Authentication authentication);
}
