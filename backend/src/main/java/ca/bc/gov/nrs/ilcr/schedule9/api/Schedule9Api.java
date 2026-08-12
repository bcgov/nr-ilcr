package ca.bc.gov.nrs.ilcr.schedule9.api;

import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 9 (Miscellaneous and Unique Logging Costs) API contract (controller + api-interface
 * split, the established idiom). The interface owns the request mapping; {@code Schedule9Controller}
 * implements it and adds authorization.
 *
 * <p>Story 9.1 is the READ side only; the write/delete endpoints and {@code POST /check-status}
 * belong to 9.2 and realize the document pinned by {@link Schedule9Response} rather than re-pinning
 * it.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedules 5/6/11 — because
 * the guards pin the verbatim ERR message for missing, blank, AND non-numeric values, which a typed
 * required {@code @RequestParam} cannot produce. Parsing + the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule9")
public interface Schedule9Api {

  /**
   * Get the Schedule 9 contractual-work document for a mill and reporting year.
   *
   * <p>Guards (verbatim legacy text from the message bundle): missing/blank/non-numeric params →
   * 400 (EF1); mill not active for the year → 409 (EF2); no report-status context → 404 (EF3); no
   * {@code VIEW_SCHEDULE} → 403. A mill/year with zero records is a valid 200 with
   * {@code records: []}, never a 404.
   *
   * @param millId the mill id (optional raw String)
   * @param year the reporting year (optional raw String)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule9Response> getSchedule9(
      @RequestParam(required = false) String millId,
      @RequestParam(required = false) String year,
      Authentication authentication);
}
