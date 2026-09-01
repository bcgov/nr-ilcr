package ca.bc.gov.nrs.ilcr.reporting.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Print Schedule PDF API contract (Epic 20). The interface owns the request mapping; {@code
 * ReportController} implements it and adds authorization — the established controller/api-interface
 * idiom (mirrors {@code Schedule9Api}).
 *
 * <p>The PDF is STREAMED to the servlet output stream: {@code
 * ResponseEntity<StreamingResponseBody>} with {@code application/pdf} (Story 29.2), so a big "all
 * schedules" print is written straight to the response instead of buffered whole as a {@code
 * byte[]} on the heap. The mill/year/selection guards (400/404/409) still run — and may reject —
 * BEFORE the response is committed, because the fill happens synchronously in the controller and
 * only the export streams. Later Epic 20 report stories copy this shape.
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
  ResponseEntity<StreamingResponseBody> getSchedule9Pdf(
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
  ResponseEntity<StreamingResponseBody> printSchedules(
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
  ResponseEntity<StreamingResponseBody> getMillInformationPdf(
      @RequestParam(required = false) String year, Authentication authentication);
}
