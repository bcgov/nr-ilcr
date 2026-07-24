package ca.bc.gov.nrs.ilcr.schedule1.api;

import ca.bc.gov.nrs.ilcr.schedule1.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Request;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
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
 * Schedule 1 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule1Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4).
 */
@RequestMapping("/api/v1/schedule1")
public interface Schedule1Api {

  /**
   * Get the Schedule 1 aggregate document for a mill and reporting year. Context guards
   * (400/404/409/403) come from Story 1.1; this returns the 200 document (Story 1.2).
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule1Response> getSchedule1(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Save (create-or-update) the Schedule 1 entered fields for a mill/year and return the recomputed
   * document (Story 2.1, S01). Range validation on the request body returns 400; a non-Draft track
   * returns 409; a stale {@code revisionCount} returns 409; missing {@code EDIT_SCHEDULE} returns 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the entered fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed aggregate document
   */
  @PutMapping
  ResponseEntity<Schedule1Response> saveSchedule1(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule1Request request,
      Authentication authentication);

  /**
   * Delete the whole Schedule 1 (summary + all detail rows) for a mill/year (Story 2.1, S13/BR-08).
   * Non-Draft track → 409; missing {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the success {@code message} (SUC-002, AD-8) — supersedes the Story 2.1 pinned 204
   */
  @DeleteMapping
  ResponseEntity<MessageResponse> deleteSchedule1(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Check Status (BR-07, Story 2.6): validate whether Schedule 1 meets all requirements. Read-only —
   * no status transition, no persistence. Missing {@code VIEW_SCHEDULE} → 403; unknown mill/year or
   * no summary → 404; closed mill → 409.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller
   * @return 200 with the check-status result (errors, warnings, requirements-met + success message)
   */
  @PostMapping("/check-status")
  ResponseEntity<CheckStatusResponse> checkStatus(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
