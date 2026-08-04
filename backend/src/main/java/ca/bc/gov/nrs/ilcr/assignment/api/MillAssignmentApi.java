package ca.bc.gov.nrs.ilcr.assignment.api;

import ca.bc.gov.nrs.ilcr.assignment.dto.AssignSubmitterRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.AssignmentResponse;
import ca.bc.gov.nrs.ilcr.assignment.dto.EndAssignmentRequest;
import ca.bc.gov.nrs.ilcr.assignment.dto.MillSubmitter;
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

/**
 * Submitter↔mill assignment API (Story 2.2; controller + api-interface split, CSP idiom). The
 * interface owns the request mapping; {@code MillAssignmentController} implements it and enforces the
 * {@code ILCR_ADMIN} gate. Mill-centric (Q2): a mill owns its submitter assignments.
 */
@RequestMapping("/api/v1/mills/{millId}/submitters")
public interface MillAssignmentApi {

  /**
   * List a mill's submitter assignments — active and ended — newest first.
   *
   * @param millId the mill id (path)
   * @param authentication the caller (ILCR_ADMIN)
   * @return 200 with the assignment rows
   */
  @GetMapping
  ResponseEntity<List<MillSubmitter>> list(
      @PathVariable long millId, Authentication authentication);

  /**
   * Assign a submitter to the mill. An already-active pair is a no-op {@code user.not.associated.to.mill}
   * warning; otherwise a new active assignment is created ({@code user.activate.mill}).
   *
   * @param millId the mill id (path)
   * @param request the submitter's FAM user GUID
   * @param authentication the caller (ILCR_ADMIN; audit user)
   * @return 200 with the affected assignment + resolved message
   */
  @PostMapping
  ResponseEntity<AssignmentResponse> assign(
      @PathVariable long millId,
      @Valid @RequestBody AssignSubmitterRequest request,
      Authentication authentication);

  /**
   * Soft-end the submitter's active assignment for the mill (sets END_DATE; never deletes — AD-11).
   * 404 when none active; 409 when {@code revisionCount} is stale.
   *
   * @param millId the mill id (path)
   * @param userGuid the submitter's FAM user GUID (path)
   * @param request the optimistic-lock token
   * @param authentication the caller (ILCR_ADMIN; audit user)
   * @return 200 with the ended assignment + {@code user.deactivate.mill} message
   */
  @PatchMapping("/{userGuid}")
  ResponseEntity<AssignmentResponse> end(
      @PathVariable long millId,
      @PathVariable String userGuid,
      @Valid @RequestBody EndAssignmentRequest request,
      Authentication authentication);
}
