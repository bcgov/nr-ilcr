package ca.bc.gov.nrs.ilcr.checkstatus.api;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.SetTrackStatusResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.VerifyReportResponse;
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
 * the root is spoken for by the epic family: {@code /submit} arrived with Story 15.3, {@code
 * /verify} with Story 17.1, and {@code /set-to-draft} and {@code /set-to-submit} with Story 18.1.
 * All four sub-resources DO follow the POST-sub-resource convention; the GET's departure is the
 * sweep's alone.
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
   * submitNotDraftErrorMsg} ("Schedules 1-10 are no longer in Draft and cannot be submitted." — a
   * business-ruled departure from legacy's generic guard text); any of the eleven Schedule 1–10
   * checks failing → 409 {@code reportNotSubmittedErrorMsg}, nothing written; a persistence failure
   * after the guard → 500 {@code reportSubmissionErrorMsg}, rolled back. Success → 200 {@code
   * {"message": {"key": "sch1-10SubmittedMsg", "text": …}}} issued only after commit, with the
   * status row at {@code S}, the submitting user's assignment recorded as the report's licensee,
   * every Schedule 1–10 row's audit columns touched and the ten category rows at {@code A}.
   * Schedule 11's track is never touched.
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

  /**
   * Verify a submitted Schedules 1&ndash;10 track — the Submitted&rarr;Verified transition that is
   * the ministry's sign-off for rate setting (UC-CHK-007/012, FR5). Method authorization runs
   * first: no {@code SET_REPORT_STATUS} → 403, which is every Licensee. For an authorized caller,
   * missing/blank/non-numeric params → 400 ERR-001; no {@code ILCR_MILL_REPORT_STATUS} row → 404
   * {@code checkStatusScheduleNotFoundErrorMsg}; mill closed for the year → 409 ERR-002; one or
   * more schedules failing validation → 409 {@code reportNotSubmittedErrorMsg}; a track that is not
   * Submitted, which covers a no-op, both illegal Draft&harr;Verified jumps and a stored NULL
   * status code → 409 {@code reportSubmissionErrorMsg}; a write that cannot be persisted → 500
   * {@code reportSubmissionErrorMsg}, everything rolled back.
   *
   * <p><strong>A refused transition answers 409, and that IS legacy.</strong> The DAO returns
   * {@code false} ({@code SubmitReportDAO.isMillReportStatusValid:448}), and {@code
   * ILCRService.submitReport:718-723} — declared {@code void} — converts it into {@code
   * ILCSException(SCHEDULE_NOT_SUBMITTED)}, mapped to {@code reportSubmissionErrorMsg} at {@code
   * ILCSException:50}. The bean's {@code catch} at {@code CheckStatusMB.submitReport:289-292} then
   * renders the error, skipping {@code sch1-10VerifiedMsg} at {@code :284}. {@code UC-CHK-007-S07}
   * calls this a silent-success defect, but that record reads only the bean and the DAO — it never
   * opens the service — and hedges itself as unconfirmed.
   *
   * <p>There is no revision-conflict outcome: the status row carries no optimistic guard, because
   * legacy's {@code REVISION_COUNT} was a plain column rather than a {@code @Version} and no
   * transition ever bumped it.
   *
   * <p>The 409 and the 500 that share {@code reportSubmissionErrorMsg} are distinguishable only by
   * status code, never by response body — legacy reused one message for both conditions and AD-8
   * keeps its text verbatim.
   *
   * <p>The Schedule 11 track is untouched: it has its own status and its own workflow.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the acting principal, supplying both the audit actor and the directory
   *     GUID the auditor cross-reference is looked up by
   * @return 200 with the new track status and the verbatim success message
   */
  @PostMapping("/verify")
  ResponseEntity<VerifyReportResponse> verifySchedules1To10(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Send a submitted Schedules 1&ndash;10 track back to the Licensee for rework &mdash;
   * Submitted&rarr;Draft (UC-CHK-016, FR5, Story 18.1). No body: the client confirms in its own
   * dialog and the server enforces everything that dialog protected, including for a forged request
   * that never saw the button (UC-CHK-016 S07).
   *
   * <p>Method authorization runs FIRST, so a caller lacking {@code SET_REPORT_STATUS} &mdash; every
   * Licensee &mdash; gets 403 ahead of any 400 for malformed parameters. Then, in the house order:
   * missing/blank/non-numeric params → 400 ERR-001; no {@code ILCR_MILL_REPORT_STATUS} row → 404
   * {@code checkStatusScheduleNotFoundErrorMsg}; mill not {@code ACT} for the year → 409 ERR-002.
   * Then the gate, then legality: one or more of the eleven Schedule 1&ndash;10 checks failing →
   * 409 {@code reportNotSubmittedErrorMsg}; a track that is not Submitted, which covers the {@code
   * D}&rarr;{@code D} no-op, both {@code D}&harr;{@code V} jumps, Set to Draft at Verified and the
   * dead {@code O} code → 409 {@code setToDraftNotSubmittedErrorMsg}; a write that cannot be
   * persisted → 500 {@code reportSubmissionErrorMsg}, everything rolled back.
   *
   * <p><strong>The gate runs on the way back as well, and it has a known consequence</strong>
   * (Story 18.1 D5): a Submitted report that fails validation cannot be sent back to Draft to be
   * repaired. That is legacy &mdash; one {@code submitReport(String)} validated before every
   * transition &mdash; and is raised with the business as its own question, not fixed here.
   *
   * <p>Success → 200 with the track at {@code D}, every Schedule 1&ndash;10 row's audit columns
   * touched, the ten category rows at {@code D}, and {@code sch1-10DraftMsg} issued only after
   * commit. <strong>Neither identity pair is written</strong>: legacy skipped the whole association
   * block for a {@code 'D'} target ({@code SubmitReportDAO:401}), so {@code LICENSEE_*} and {@code
   * AUDITOR_*} are left exactly as the submit and verify that preceded them set them. Schedule 11's
   * track, its category row and {@code BASIC_SILVICULTURE_REPORT} are never touched (BR-07).
   *
   * <p>There is no revision-conflict outcome and no lost-update 500: the status UPDATE carries an
   * {@code expectedCode} predicate, so a track that moved between this request's read and its write
   * answers the same 409 {@code setToDraftNotSubmittedErrorMsg} as any other refusal.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the acting principal, supplying the audit name. No directory GUID is read
   *     — this transition records no identity pair
   * @return 200 with the new track status and the verbatim legacy success message
   */
  @PostMapping("/set-to-draft")
  ResponseEntity<SetTrackStatusResponse> setSchedules1To10ToDraft(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);

  /**
   * Withdraw a verification so the report can be re-reviewed &mdash; Verified&rarr;Submitted
   * (UC-CHK-018, FR5, Story 18.1). Identical in every respect to {@link #setSchedules1To10ToDraft}
   * above except the three values that differ, so only those are restated here.
   *
   * <p>The refusal for a track that is not Verified &mdash; the {@code S}&rarr;{@code S} no-op,
   * both {@code D}&harr;{@code V} jumps, Set to Submit at Submitted, the dead {@code O} code
   * &mdash; is 409 {@code setToSubmitNotVerifiedErrorMsg}. Note that a track at Draft is refused
   * here too: {@code D}&rarr;{@code S} is a legal pair, but it is a licensee's SUBMIT and belongs
   * to {@code POST /submit}, not to this endpoint.
   *
   * <p>Success → 200 with the track at {@code S} and the ten category rows at {@code A} (legacy
   * mapped both {@code DS} and {@code VS} to {@code A}, so a reversed verification is
   * indistinguishable from a fresh submission at the category level). The success message is {@code
   * sch1-10SubmittedMsg} &mdash; legacy reused the submit text and minted no "verification
   * reversed" message, so there is none to port.
   *
   * <p>Neither identity pair is written, and here that IS a recorded deviation (S). Legacy wrote
   * the LICENSEE pair from the acting ADMIN's {@code ILCR_MILL_USER_XREF} row ({@code
   * SubmitReportDAO:406-407}), which destroys the record of who actually submitted and stores NULL
   * whenever that admin has no assignment for the mill — the normal case, since ministry users are
   * not mill-assigned. Ratified 2026-09-21 (D1(a)); matches {@code epics.md:2110} and PRD FR5.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the acting principal, supplying the audit name
   * @return 200 with the new track status and the verbatim legacy success message
   */
  @PostMapping("/set-to-submit")
  ResponseEntity<SetTrackStatusResponse> setSchedules1To10ToSubmit(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
