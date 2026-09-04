package ca.bc.gov.nrs.ilcr.reporting.api;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Print Schedule PDF API contract (Epic 20). The interface owns the request mapping; {@code
 * ReportController} implements it and adds authorization — the established controller/api-interface
 * idiom (mirrors {@code Schedule9Api}).
 *
 * <p>The PDF is STREAMED to the servlet output stream: {@code ResponseEntity<Resource>} with {@code
 * application/pdf} (Story 29.2), so a big "all schedules" print is never buffered whole as a {@code
 * byte[]} on the heap. Later Epic 20 report stories copy this shape.
 *
 * <p><b>Every one of these endpoints either produces a whole PDF or produces no file at all.</b>
 * That is a guarantee, not a best effort, and it is what the ordering in {@code ReportController}
 * buys: the guards, the fill AND the export all complete on the synchronous path, before a status
 * code is chosen, so any failure among them is an ordinary {@code problem+json} response. The
 * export writes to a temp file rather than to the response (see {@code PdfSpooler}), which is what
 * lets it fail that way and what keeps the heap flat at the same time.
 *
 * <p>Only the finished file's bytes are streamed, under a real {@code Content-Length}. A transfer
 * interrupted after the commit is therefore a short read against a declared length, which the
 * browser fails outright — so it cannot be mistaken for a complete download either. There is no
 * remaining path on which a caller receives a partial PDF as a success.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like the Schedule read endpoints
 * — so the shared {@code MillContextService} guard can emit the verbatim ERR-003 for missing,
 * blank, AND non-numeric values, which a typed required {@code @RequestParam} cannot produce.
 */
@RequestMapping("/api/v1/reports")
public interface ReportApi {

  /**
   * Download the Schedule 9 (Miscellaneous/Unique Logging Costs) report as a PDF for a mill and
   * reporting year.
   *
   * <p>Guards (identical to the Schedule 9 read, verbatim legacy text): missing/blank/non-numeric
   * params → 400 (ERR-003); mill not active for the year → 409 (ERR-004); no report-status context
   * → 404 (ERR-005); no {@code VIEW_SCHEDULE} → 403. A valid mill/year whose Schedule 9 has zero
   * records yields no PDF → 404 {@code Schedule not found.} (legacy single-schedule semantics).
   *
   * @param millId the mill id (optional raw String)
   * @param year the reporting year (optional raw String)
   * @param authentication the caller (authorized for VIEW_SCHEDULE — print is read-only, BR-01)
   * @return 200 streaming the PDF ({@code application/pdf} + attachment Content-Disposition)
   */
  @GetMapping("/schedule9")
  ResponseEntity<Resource> getSchedule9Pdf(
      @RequestParam(required = false) String millId,
      @RequestParam(required = false) String year,
      Authentication authentication);

  /**
   * Download the selected schedules assembled into ONE bookmarked PDF for a mill and reporting year
   * (Epic 20.2, combined Print Schedules). The {@link PrintRequest} body carries the twelve
   * schedule flags + "all" + the three print options; the backend renders only the in-scope
   * sections (5/6/7A/7B/9/11) in the fixed legacy order, one bookmark per rendered schedule
   * (BR-08), skipping a selected schedule with no data (BR-09).
   *
   * <p>Guards run before any fill: missing/blank/non-numeric params → 400 (ERR-001); mill not
   * active → 409 (ERR-002); no report-status context → 404 (ERR-003); then selection validation in
   * the legacy order → 400 (ERR-002/003/004 verbatim). A selection where NO chosen schedule has
   * data yields no PDF → 404 {@code Schedule not found.} (ERR-005). No {@code VIEW_SCHEDULE} → 403;
   * anonymous → 401.
   *
   * @param millId the mill id (optional raw String)
   * @param year the reporting year (optional raw String)
   * @param request the print selection
   * @param authentication the caller (authorized for VIEW_SCHEDULE — print is read-only, BR-01)
   * @return 200 streaming the combined PDF ({@code application/pdf} + attachment
   *     Content-Disposition)
   */
  // @Valid here is a forward-looking safeguard (Story 29.13): PrintRequest is today an all-Boolean
  // record whose compact constructor defaults every omitted flag to false, so there is nothing to
  // constrain and @Valid is a no-op. It is declared so that if a non-Boolean field is ever added to
  // PrintRequest, adding a constraint to that field is all it takes to have it enforced — the
  // wiring
  // is already here. Do NOT add constraints to the Boolean flags.
  @PostMapping("/print")
  ResponseEntity<Resource> printSchedules(
      @RequestParam(required = false) String millId,
      @RequestParam(required = false) String year,
      @Valid @RequestBody PrintRequest request,
      Authentication authentication);

  /**
   * Download the Mill Information report for a reporting year as a PDF: every mill with a report
   * status for that year, one section each, in a single {@code mills_print.pdf}.
   *
   * <p>This endpoint deliberately takes NO {@code millId} and runs NO mill/year context guard. The
   * legacy page carried no Home working context — its {@code areMillYearSelected()} render guard is
   * commented out — and the report covers every mill by definition, so there is no context to
   * validate. Do not "align" it with the sibling endpoints above.
   *
   * <p>Guards, in the order they run: missing/blank/non-numeric {@code year} → <b>400</b> {@code
   * Report Year: Value is required.}; a parseable year that is not an OPEN reporting period →
   * <b>400</b> {@code Report Year is not an open reporting period.}; an open year no mill has a
   * report status for → <b>404</b> {@code No mill has a report status for the selected Report
   * Year.}; a genuine failure while building → <b>500</b> {@code undefinedError}. No file is
   * produced in any of those cases. Without {@code GENERATE_MILL_REPORTS} → 403; anonymous → 401.
   *
   * <p>The rejections are deliberately distinct: only the 500 means something is broken, so only it
   * belongs in the error rate.
   *
   * @param year the reporting year (optional raw String, so the guard owns the rejection text)
   * @param authentication the caller (authorized for GENERATE_MILL_REPORTS — administrators only)
   * @return 200 streaming the PDF ({@code application/pdf} + attachment Content-Disposition)
   */
  @GetMapping("/mill-information")
  ResponseEntity<Resource> getMillInformationPdf(
      @RequestParam(required = false) String year, Authentication authentication);

  /**
   * Download ONE mill's Mill Information section for a reporting year as a PDF — the per-mill
   * drill-down launched from the Mill Status Report table (UC-MRPT-002 S02 / UC-MRPT-004 S02).
   *
   * <p>Nested under {@code /mill-information} because that is what this IS: the same report scoped
   * to one mill — same template, same nineteen fields, same mapper, one section instead of many.
   * Legacy ran the identical renderer for both ({@code ILCRPrintService.java:213-220,230-249}), and
   * nesting keeps this interface's "every method streams a PDF" contract intact. The Story 19.2
   * JSON status table is a different resource and stays at {@code /mill-status}.
   *
   * <p>Like the all-mills endpoint above, this runs NO mill/year working-context guard and takes no
   * account of the Home selection — and, unlike the schedule endpoints, it deliberately does not
   * reject a CLOSED mill. Closed mills appear in the status table (its Active column is what says
   * so, per the reporting year), so they must stay drillable: a 2021 report on a mill closed in
   * 2024 is a legitimate thing for an administrator to print. {@code
   * MillContextService.validateMillYearActive} would refuse it, which is why neither of its
   * overloads is called here. Do not "align" this with the sibling schedule endpoints.
   *
   * <p>Guards, in the order they run: missing/blank/non-numeric {@code year} → <b>400</b> {@code
   * Report Year: Value is required.}; a parseable year that is not an OPEN reporting period →
   * <b>400</b> {@code Report Year is not an open reporting period.}; an open year in which THIS
   * mill has no report status → <b>404</b> {@code The selected mill has no report status for the
   * selected Report Year.}; a genuine failure while building → <b>500</b> {@code undefinedError}. A
   * non-numeric {@code millId} never reaches the method — the path variable's own type conversion
   * rejects it. No file is produced in any of those cases. Without {@code GENERATE_MILL_REPORTS} →
   * 403; anonymous → 401.
   *
   * <p>The attachment filename is {@code mill_<millNumber>_print.pdf}, parity-bound to legacy
   * ({@code PrintSchedulesMB.java:332}) — note MILL NUMBER, not the mill id in the path.
   *
   * @param millId the mill to report on — the mill id, NOT the mill number
   * @param year the reporting year (optional raw String, so the guard owns the rejection text)
   * @param authentication the caller (authorized for GENERATE_MILL_REPORTS — administrators only)
   * @return 200 streaming the PDF ({@code application/pdf} + attachment Content-Disposition)
   */
  @GetMapping("/mill-information/{millId}")
  ResponseEntity<Resource> getMillDrillDownPdf(
      @PathVariable long millId,
      @RequestParam(required = false) String year,
      Authentication authentication);
}
