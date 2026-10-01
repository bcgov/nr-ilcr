package ca.bc.gov.nrs.ilcr.assignment.api;

import ca.bc.gov.nrs.ilcr.assignment.dto.AccountResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import ca.bc.gov.nrs.ilcr.assignment.dto.SetAccountActiveRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Licensee account and mill-assignment API contract (UC-USR-001/002; controller + api-interface
 * split). The interface owns the request mappings; {@code AssignmentController} implements it and
 * adds the ADMIN-only {@code MAINTAIN_USERS} authorization, so a submitter is denied 403 on every
 * operation rather than merely being hidden from the menu.
 *
 * <p>Mapped from {@code /api/v1} rather than a single resource root because the surface is
 * addressed from both directions — a mill's submitters and a submitter's mills — mirroring the two
 * legacy screens that reached the same cross-reference.
 */
@RequestMapping("/api/v1")
public interface AssignmentApi {

  /**
   * One mill's submitter assignments, active first then most recently dated.
   *
   * @param millId the mill
   * @param includeEnded true to include assignments that have been ended
   * @param authentication the caller (must hold {@code MAINTAIN_USERS})
   * @return 200 with the mill's assignments; 404 when the mill does not exist
   */
  @GetMapping("/mills/{millId}/submitters")
  ResponseEntity<List<MillSubmitter>> listByMill(
      @PathVariable long millId,
      @RequestParam(defaultValue = "false") boolean includeEnded,
      Authentication authentication);

  /**
   * One submitter's mill assignments, by ascending mill id.
   *
   * @param userGuid the submitter's directory GUID ({@code custom:idp_user_id})
   * @param includeEnded true to include assignments that have been ended
   * @param authentication the caller (must hold {@code MAINTAIN_USERS})
   * @return 200 with the submitter's assignments, empty when they hold none
   */
  @GetMapping("/submitters/{userGuid}/mills")
  ResponseEntity<List<MillSubmitter>> listByUser(
      @PathVariable String userGuid,
      @RequestParam(defaultValue = "false") boolean includeEnded,
      Authentication authentication);

  /**
   * Assign a submitter to a mill, provisioning their ILCR account when they have never held one and
   * reviving their assignment when it had been ended.
   *
   * <p>Answers 200 in three cases that differ only by message: newly assigned, revived, and already
   * assigned — the last changing nothing. Callers must read {@code messageKey} to tell them apart.
   *
   * @param millId the mill
   * @param request the submitter's directory GUID
   * @param authentication the caller (must hold {@code MAINTAIN_USERS}; drives the audit user)
   * @return 200 with the assignment and its message; 404 unknown mill; 409 closed mill or lost
   *     concurrent update on a revive
   */
  @PostMapping("/mills/{millId}/submitters")
  ResponseEntity<AssignmentResponse> assign(
      @PathVariable long millId,
      @Valid @RequestBody AssignSubmitterRequest request,
      Authentication authentication);

  /**
   * End a submitter's mill assignment, keeping the row and clearing its active date.
   *
   * @param millId the mill
   * @param userGuid the submitter's directory GUID
   * @param request the optimistic-lock token the caller read
   * @param authentication the caller (must hold {@code MAINTAIN_USERS}; drives the audit user)
   * @return 200 with the ended assignment; 404 when the pair has no assignment row (including an
   *     unknown mill); 409 when stale or already ended
   */
  @PatchMapping("/mills/{millId}/submitters/{userGuid}")
  ResponseEntity<AssignmentResponse> end(
      @PathVariable long millId,
      @PathVariable String userGuid,
      @Valid @RequestBody EndAssignmentRequest request,
      Authentication authentication);

  /**
   * Flag a licensee's ILCR account active or inactive.
   *
   * <p>The flag is administrative state only and gates nothing, so this neither grants nor removes
   * access. Deactivation is refused while the user holds any active assignment.
   *
   * @param userGuid the submitter's directory GUID
   * @param request the desired state
   * @param authentication the caller (must hold {@code MAINTAIN_USERS}; drives the audit user)
   * @return 200 with the account and its message; 409 when active assignments remain; 404 when
   *     deactivating a user who has no account row (activation provisions one instead)
   */
  @PatchMapping("/submitters/{userGuid}")
  ResponseEntity<AccountResponse> setAccountActive(
      @PathVariable String userGuid,
      @Valid @RequestBody SetAccountActiveRequest request,
      Authentication authentication);
}
