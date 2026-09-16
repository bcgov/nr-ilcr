package ca.bc.gov.nrs.ilcr.checkstatus.api;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.dto.base.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Check Status API contract (Story 15.1; controller + api-interface split, the established idiom).
 * The interface owns the request mapping and parameter contract; {@code CheckStatusController}
 * implements it and adds authorization.
 *
 * <p><strong>Two recorded departures.</strong> The sweep is a {@code GET} where the twelve
 * per-schedule siblings are all {@code POST /check-status}: the sweep is a pure read with no body,
 * so the verb is right, but it departs from the "actions are POST sub-resources" convention. And
 * {@code /api/v1/check-status} is a new top-level resource rather than a schedule sub-resource —
 * the root is spoken for by the epic family: {@code /submit} arrived with Story 15.3, and {@code
 * /verify}, {@code /set-to-draft} and {@code /set-to-submit} follow (Stories 17, 18).
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings on both endpoints, and this is
 * forced, not stylistic: the legacy ERR-001 text ("Please Select Mill and Reporting Year in the
 * Home Page. ", trailing space verbatim) is raised by exactly one production call site, the String
 * overload of {@code MillContextService.validateMillYearActive}, which collapses missing, blank AND
 * non-numeric values into it. A typed required {@code @RequestParam} yields Spring's generic
 * "Required parameter …" 400 instead and can never emit the legacy message (the Schedule 6/11
 * idiom, not Schedule 1's).
 */
@RequestMapping("/api/v1/check-status")
public interface CheckStatusApi {

  /**
   * Re-run every schedule's validation for a mill and reporting year, on both tracks, changing
   * nothing (UC-CHK-001 BR-02/BR-03/BR-04/BR-05, FR5). Method authorization runs first: no {@code
   * VIEW_SCHEDULE} → 403. For an authorized caller, missing/blank/non-numeric params → 400 ERR-001;
   * a mill outside the caller's scope → 403; no {@code ILCR_MILL_REPORT_STATUS} row → 404 {@code
   * checkStatusScheduleNotFoundErrorMsg} (the Check Status page's own text, UC-CHK-001 S06); mill
   * closed for the year → 409 ERR-002.
   *
   * <p>{@code schedules1To10.canSubmit} (Story 15.3) is whether Submit is OFFERED to this caller:
   * the caller holds {@code SUBMIT_REPORT}, remains within that action's SUBMITTER mill scope, and
   * the track is at Draft — the legacy button rule, with validity ignored, so a Licensee whose
   * schedules still fail is offered the button and learns from the click (409 {@code
   * reportNotSubmittedErrorMsg}) exactly as in legacy. {@code schedule11.canSubmit} is absent until
   * Epic 26.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller, for the offer flag
   * @return 200 with the twelve verdicts partitioned by track plus both track status codes
   */
  @GetMapping
  ResponseEntity<CheckStatusSweepResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Submit the Schedules 1–10 track for ministry review — Draft → Submitted (UC-CHK-002
   * S01/S03/S08, FR5, Story 15.3). No body: the client confirms in its own dialog; the server
   * enforces everything the dialog protected. Method authorization runs first: no {@code
   * SUBMIT_REPORT} → 403 (ADMIN-only callers do not hold it). A caller holding both ADMIN and
   * SUBMITTER retains the action under Epic 16, but only within the SUBMITTER role's active mill
   * assignment. Then, in order: missing/blank/non-numeric params → 400 ERR-001; a mill outside the
   * caller's scope → 403; no {@code ILCR_MILL_REPORT_STATUS} row → 404 {@code
   * checkStatusScheduleNotFoundErrorMsg}; mill closed for the year → 409 ERR-002. Inside ONE write
   * transaction that locks the status row first: track not at Draft → 409 {@code
   * reportSubmissionErrorMsg} (legacy's guard text); any of the eleven Schedule 1–10 checks failing
   * → 409 {@code reportNotSubmittedErrorMsg}, nothing written; a persistence failure after the
   * guard → 500 {@code reportSubmissionErrorMsg}, rolled back. Success → 200 {@code {"message":
   * {"key": "sch1-10SubmittedMsg", "text": …}}} issued only after commit, with the status row at
   * {@code S}, the submitting user's assignment recorded as the report's licensee, every Schedule
   * 1–10 row's audit columns touched and the ten category rows at {@code A}. Schedule 11's track is
   * never touched.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller, for the status guard, the audit name and the licensee
   * @return 200 with the legacy success message
   */
  @PostMapping("/submit")
  ResponseEntity<MessageResponse> submit(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
