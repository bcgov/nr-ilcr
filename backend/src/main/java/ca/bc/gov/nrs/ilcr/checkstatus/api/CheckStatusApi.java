package ca.bc.gov.nrs.ilcr.checkstatus.api;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Check Status API contract (Story 15.1; controller + api-interface split, the established idiom).
 * The interface owns the request mapping and parameter contract; {@code CheckStatusController}
 * implements it and adds authorization.
 *
 * <p><strong>Two recorded departures.</strong> This is a {@code GET} where the twelve per-schedule
 * siblings are all {@code POST /check-status}: the sweep is a pure read with no body, so the verb
 * is right, but it departs from the "actions are POST sub-resources" convention. And {@code
 * /api/v1/check-status} is a new top-level resource rather than a schedule sub-resource — the root
 * is spoken for by the epic family, which adds {@code /submit}, {@code /verify}, {@code
 * /set-to-draft} and {@code /set-to-submit} beneath it (Stories 15.3, 17, 18).
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings, and this is forced, not stylistic:
 * the legacy ERR-001 text ("Please Select Mill and Reporting Year in the Home Page. ", trailing
 * space verbatim) is raised by exactly one production call site, the String overload of {@code
 * MillContextService.validateMillYearActive}, which collapses missing, blank AND non-numeric values
 * into it. A typed required {@code @RequestParam} yields Spring's generic "Required parameter …"
 * 400 instead and can never emit the legacy message (the Schedule 6/11 idiom, not Schedule 1's).
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
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @return 200 with the twelve verdicts partitioned by track plus both track status codes
   */
  @GetMapping
  ResponseEntity<CheckStatusSweepResponse> checkStatus(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year);
}
