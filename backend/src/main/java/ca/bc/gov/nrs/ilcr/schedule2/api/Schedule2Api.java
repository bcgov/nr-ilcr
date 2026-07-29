package ca.bc.gov.nrs.ilcr.schedule2.api;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageResponse;
import ca.bc.gov.nrs.ilcr.schedule2.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
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
 * Schedule 2 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule2Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4). Covers GET (read),
 * PUT (save), DELETE, and POST /check-status (Story 3.2).
 */
@RequestMapping("/api/v1/schedule2")
public interface Schedule2Api {

  /**
   * Get the Schedule 2 aggregate document for a mill and reporting year. Context guards
   * (400/404/409/403) are enforced by {@code MillContextService} + method security. Unlike
   * Schedule 1, a valid, active mill/year with no saved Schedule 2 returns a 200 empty editable
   * document — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule2Response> getSchedule2(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Save (create-or-update) the two entered Schedule 2 line items for a mill/year and return the
   * recomputed document (Story 3.2, S12). Unlike Schedule 1, a mill/year with no saved Schedule 2 is
   * created on save (never 404). Range validation on the request body returns 400; a non-Draft track
   * returns 409; a stale {@code revisionCount} returns 409; missing {@code EDIT_SCHEDULE} returns 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param request the entered fields + optimistic-lock token (validated)
   * @param authentication the caller (drives EDIT_SCHEDULE + audit user)
   * @return 200 with the recomputed aggregate document carrying the success {@code message} (AD-8)
   */
  @PutMapping
  ResponseEntity<Schedule2Response> saveSchedule2(
      @RequestParam long millId,
      @RequestParam int year,
      @Valid @RequestBody Schedule2Request request,
      Authentication authentication);

  /**
   * Delete the whole Schedule 2 (summary + items 25/26) for a mill/year (Story 3.2). Idempotent: a
   * Draft mill with no summary returns 200 (never 404). Non-Draft track → 409; missing
   * {@code EDIT_SCHEDULE} → 403.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (drives EDIT_SCHEDULE)
   * @return 200 with the success {@code message} (SUC-002, AD-8)
   */
  @DeleteMapping
  ResponseEntity<MessageResponse> deleteSchedule2(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);

  /**
   * Evaluate the Schedule 2 completion requirement (BR-07, Check Status) for a mill/year — read-only
   * (AD-5), mutates nothing, no request body. Returns 200 {@code {outcome:"MET", ...}} when the
   * server-assembled {@code purchasedLogCost.cost} (item 25) is present, else
   * {@code {outcome:"ISSUES", ...}} (including an unsaved schedule with no summary — never 404). Same
   * no-summary-required context guards as read/write: 400 (bad param) / 404 (unknown mill) / 409
   * (closed) / 403 (no VIEW_SCHEDULE).
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (authorized for VIEW_SCHEDULE)
   * @return 200 with the {@link CheckStatusResponse} (outcome + resolved message)
   */
  @PostMapping("/check-status")
  ResponseEntity<CheckStatusResponse> checkStatus(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
