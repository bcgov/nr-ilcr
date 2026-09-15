package ca.bc.gov.nrs.ilcr.assignment.api;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The mill record's association panel (UC-MILL-001 S05-S09, S13) — the wire contract for listing,
 * adding, activating and deactivating a mill's user associations from the admin Mills surface.
 *
 * <p>Rooted under {@code /api/v1/admin/mills/{millId}/users} so the whole Mills page runs behind
 * one action ({@code MAINTAIN_MILLS}), alongside the mill lifecycle endpoints it sits with. The
 * users-screen endpoints over the same cross-reference ({@code /api/v1/mills/{millId}/submitters},
 * {@code MAINTAIN_USERS}) are a different surface with deliberately different add semantics — an
 * add here creates the association inactive and never revives an ended pair.
 *
 * <p>Actions are POST sub-resources per the house endpoint convention. Errors are RFC 7807 {@code
 * ProblemDetail}. Pinned statuses: a malformed {@code userGuid} is 400; an unknown or untracked
 * mill is 404; a missing association on a toggle is 404; a closed-mill activation, a stale
 * revision, and a toggle already in its target state are all 409. A duplicate add is a <strong>200
 * warning</strong>, never an error — the legacy dialog closed and the panels stood, so callers read
 * {@code messageKey}, not just the status.
 *
 * <p>The {@code userGuid} path variable carries the same exact-32 constraint the add body enforces,
 * so a truncated or padded identifier is refused at the boundary rather than reaching the database
 * and coming back as an indistinguishable "no such assignment" 404. The implementing controller is
 * {@code @Validated} so the violation surfaces as a {@code ConstraintViolationException}, which the
 * global handler already renders as a 400 {@code ProblemDetail}.
 */
@RequestMapping("/api/v1/admin/mills/{millId}/users")
public interface MillAssociationApi {

  /**
   * The mill's associations. Both states are included by default, because the legacy panel listed
   * inactive associations beside active ones, each with its own toggle.
   *
   * @param millId the mill id
   * @param includeEnded false to narrow the list to active associations only
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the associations; 404 when the mill is unknown or not yet imported
   */
  @GetMapping
  ResponseEntity<List<MillSubmitter>> list(
      @PathVariable long millId,
      @RequestParam(defaultValue = "true") boolean includeEnded,
      Authentication authentication);

  /**
   * Add a user to the mill (S05). The association is created INACTIVE — legacy hard-coded it — and
   * the confirmation is the verbatim legacy "has been activated" sentence, kept as a recorded
   * quirk; the separate activate action is what makes it effective. A user with no ILCR account row
   * is provisioned first.
   *
   * <p>A user already associated to the mill — in any state — is answered 200 with the verbatim
   * duplicate warning and no write at all (S07): an ended association is NOT revived by re-adding.
   *
   * @param millId the mill id
   * @param request the directory GUID of the user to associate
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the association (or the unchanged existing one under the duplicate warning)
   */
  @PostMapping
  ResponseEntity<AssignmentResponse> add(
      @PathVariable long millId,
      @Valid @RequestBody AssignSubmitterRequest request,
      Authentication authentication);

  /**
   * Activate an association (S09) — refused 409 with the legacy mill-inactive message while the
   * mill is not active (S13, BR-02), leaving the association unchanged.
   *
   * <p>Outcomes: 400 malformed {@code userGuid}; 404 unknown mill or no association; 409 closed
   * mill, stale revision, or already active.
   *
   * <p><strong>Precedence among the 409s, which is deliberate and not incidental:</strong> the
   * already-active check runs <em>before</em> the mill-status guard. Activating a row that is
   * already active is answered as a stale revision even on a Closed mill — nothing is being
   * activated, so BR-02 is not the operative refusal, and the caller's real problem is that their
   * view no longer matches the row. `MillAssociationServiceTest` pins the order with a {@code
   * verify(accounts, never()).requireMillActive(...)}.
   *
   * @param millId the mill id
   * @param userGuid the associated user's directory GUID
   * @param request the revision last read for this association
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the activated association with the legacy confirmation
   */
  @PostMapping("/{userGuid}/activate")
  ResponseEntity<AssignmentResponse> activate(
      @PathVariable long millId,
      @PathVariable @Size(min = 32, max = 32) String userGuid,
      @Valid @RequestBody EndAssignmentRequest request,
      Authentication authentication);

  /**
   * Deactivate an association (S08). No business rule governs this toggle — legacy checked nothing
   * — so the only refusals are mechanical.
   *
   * <p>Outcomes: 400 malformed {@code userGuid}; 404 unknown mill or no association; 409 stale
   * revision or already inactive.
   *
   * @param millId the mill id
   * @param userGuid the associated user's directory GUID
   * @param request the revision last read for this association
   * @param authentication the caller, who must hold {@code MAINTAIN_MILLS}
   * @return the deactivated association with the legacy confirmation
   */
  @PostMapping("/{userGuid}/deactivate")
  ResponseEntity<AssignmentResponse> deactivate(
      @PathVariable long millId,
      @PathVariable @Size(min = 32, max = 32) String userGuid,
      @Valid @RequestBody EndAssignmentRequest request,
      Authentication authentication);
}
