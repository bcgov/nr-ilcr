package ca.bc.gov.nrs.ilcr.schedule3.api;

import ca.bc.gov.nrs.ilcr.schedule3.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Request;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 3 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule3Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4).
 *
 * <p>Story 4.1 realized the GET; Story 4.2 adds the PUT/DELETE writes, the BR-09 Crown Timber push,
 * and the check-status validation. Sub-page write endpoints (items 124/38) arrive with Story 4.4.
 */
@RequestMapping("/api/v1/schedule3")
public interface Schedule3Api {

  /**
   * Get the Schedule 3 aggregate document for a mill and reporting year (S03). Context guards
   * (400/404/409/403) come from {@code MillContextService}; this returns the 200 document with all
   * derived values (crown, subtotals, totals, timber costs, overhead) computed server-side.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule3Response> getSchedule3(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Save (create-or-update) the Schedule 3 entered fields for a mill/year and return the recomputed
   * document (Story 4.2, S01). Range validation on the body returns 400; a non-Draft track → 409; a
   * stale {@code revisionCount} → 409; missing {@code EDIT_SCHEDULE} → 403. A changed Crown Timber
   * volume propagates into Schedule 1 (BR-09) and rides WRN-001/002 on the response.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the entered fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed aggregate document
   */
  @PutMapping
  ResponseEntity<Schedule3Response> saveSchedule3(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule3Request request,
      Authentication authentication);

  /**
   * Delete the whole Schedule 3 (summary + all detail rows) for a mill/year (Story 4.2, S08).
   * Non-Draft track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the success {@code message} (SUC-002, AD-8)
   */
  @DeleteMapping
  ResponseEntity<MessageResponse> deleteSchedule3(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Check Status (BR-11/BR-03, Story 4.2): validate whether Schedule 3 meets all requirements.
   * Read-only — no status transition, no persistence. Missing {@code VIEW_SCHEDULE} → 403; unknown
   * mill/year or no summary → 404; closed mill → 409.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller
   * @return 200 with the check-status result (errors + requirements-met + success message)
   */
  @PostMapping("/check-status")
  ResponseEntity<CheckStatusResponse> checkStatus(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
