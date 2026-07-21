package ca.bc.gov.nrs.ilcr.millcontext.api;

import ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Mill-context API contract (controller + api-interface split, CSP idiom; mirrors
 * {@code Schedule1Api}). The interface owns the request mapping; {@code MillContextController}
 * implements it and adds {@code @Override} only.
 *
 * <p>Story 1.1 owns the two Home-page option-list endpoints below. Both are pre-selection reads with
 * no action gate: no {@code @PreAuthorize} and no per-user filter (roles/authorization are out of
 * scope for the whole Home epic; the per-user mill-association filter arrives with the FAM auth
 * story, AR4). Success payloads are plain {@code application/json} (Jackson {@code non_null}).
 *
 * <p>Story 1.2 will add {@code GET /mill-context?millId&year} returning {@code WorkingContext}
 * (pinned in the story's wire contract) to this same interface — NOT implemented here.
 */
@RequestMapping("/api/v1")
public interface MillContextApi {

  /**
   * List the mills offered for selection, ordered by mill number ascending (BR-02) — exact legacy
   * {@code getMills()} semantics: a mill is listed iff it has its status xref AND at least one
   * {@code ILCR_MILL_REPORT_STATUS} row (ever enrolled in reporting; 2026-07-21 review decision).
   * Closed mills are included — no status filter, no per-user association filter — so the list is
   * identical for every caller (AR4 deferral).
   *
   * @return 200 with the mill list as {@link MillSummary} items
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
}
