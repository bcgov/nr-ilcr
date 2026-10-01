package ca.bc.gov.nrs.ilcr.millcontext.api;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millcontext.dto.WorkingContext;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Mill-context API contract (controller + api-interface split, CSP idiom; mirrors {@code
 * Schedule1Api}). The interface owns the request mapping; {@code MillContextController} implements
 * it and adds {@code @Override} only.
 *
 * <p>Story 1.1 owns the two Home-page option-list endpoints below. Both are pre-selection reads
 * with no action gate: no {@code @PreAuthorize} and no per-user filter (roles/authorization are out
 * of scope for the whole Home epic; the per-user mill-association filter arrives with the FAM auth
 * story, AR4). Success payloads are plain {@code application/json} (Jackson {@code non_null}).
 *
 * <p>Story 1.2 will add {@code GET /mill-context?millId&year} returning {@code WorkingContext}
 * (pinned in the story's wire contract) to this same interface — NOT implemented here.
 */
@RequestMapping("/api/v1")
public interface MillContextApi {

  /**
   * List the mills offered for selection, ordered by mill number ascending (BR-02), SCOPED to the
   * authenticated caller (Story 5.5, AR4 now delivered). An {@code ILCR_ADMIN} sees every enrolled
   * mill including closed (legacy admin {@code getMills()} semantics: status xref AND at least one
   * {@code ILCR_MILL_REPORT_STATUS} row; no status filter). An {@code ILCR_SUBMITTER} sees only
   * mills they are actively associated to (legacy {@code getMills(userGuid)}); closed associated
   * mills are still included. The list is therefore no longer identical for every caller.
   *
   * @return 200 with the caller-scoped mill list as {@link MillSummary} items
   */
  @GetMapping("/mills")
  ResponseEntity<List<MillSummary>> listMills();

  /**
   * List the opened reporting years offered for selection, ordered by year descending (BR-03,
   * legacy {@code getReportingPeriods()}).
   *
   * @return 200 with the opened years as {@link ReportingYear} items
   */
  @GetMapping("/reporting-years")
  ResponseEntity<List<ReportingYear>> listReportingYears();

  /**
   * Resolve the working context for a selected (mill, year) pair (Story 1.2; UC-SEC-001
   * S01/S06/S07): the pinned {@code WorkingContext} with both independent track statuses (AR6), the
   * closed-mill {@code millViewable} flag, and null statuses when no report-status row exists.
   *
   * <p>Params are deliberately raw Strings: {@code MillContextService} owns the validation
   * (AR4/NFR6), so missing/blank/non-numeric values return the verbatim legacy required-field
   * messages — BOTH together when both are absent (S08) — rather than a first-error framework 400.
   *
   * <p>Story 1.3 amendment (AD-12, AC7): the 200 {@link WorkingContext} now carries a {@code
   * message} (SUC-001, {@code dataSavedSuccesfullyInfoMsg} → "Data saved successfully"),
   * server-resolved (AD-8), on EVERY 200. This endpoint is used both to CONFIRM a Home save (1.3)
   * and to LOAD the banner (1.4); the message is always present, but callers must only DISPLAY it
   * after an explicit Save — Story 1.4's banner load must IGNORE {@code message}. The 400/404 error
   * paths are unchanged.
   *
   * @param millId the selected mill id (raw request param; validated by the service)
   * @param year the selected reporting year (raw request param; validated by the service)
   * @return 200 with the resolved {@link WorkingContext} (incl. {@code message}); 400 (S04/S05/S08)
   *     / 404 via ProblemDetail
   */
  @GetMapping("/mill-context")
  ResponseEntity<WorkingContext> getMillContext(
      @RequestParam(required = false) String millId, @RequestParam(required = false) String year);
}
