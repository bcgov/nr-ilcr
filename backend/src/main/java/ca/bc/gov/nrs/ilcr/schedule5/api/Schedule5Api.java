package ca.bc.gov.nrs.ilcr.schedule5.api;

import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 5 (Camp and Access Expenses) API contract (controller + api-interface split, the
 * established idiom). The interface owns the request mapping and parameter contract;
 * {@code Schedule5Controller} implements it and adds authorization.
 *
 * <p>Story 7.1 is the READ side only. The write/copy/delete endpoints and {@code POST
 * /check-status} belong to 7.2, and the two expense sub-pages to 7.4; they realize the document
 * pinned by {@link Schedule5Response} rather than re-pinning it.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedules 6 and 11, unlike
 * Schedule 1's typed params — because AC4 pins the verbatim legacy ERR-003 message for missing,
 * blank, AND non-numeric values, which a typed required {@code @RequestParam} cannot produce (it
 * yields the generic missing-parameter / type-mismatch 400s). Parsing and the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule5")
public interface Schedule5Api {

  /**
   * Get the Schedule 5 camp-and-access-expenses document for a mill and reporting year.
   *
   * <p>Guards (UC-SCH5-001, verbatim legacy text resolved from the message bundle):
   * missing/blank/non-numeric params → 400 ERR-003; mill not {@code ACT} for the year → 409
   * ERR-004; no {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-005; no {@code VIEW_SCHEDULE} → 403.
   * A mill/year with zero camps is a valid 200 with {@code camps: []}, never a 404.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the camps document
   */
  @GetMapping
  ResponseEntity<Schedule5Response> getSchedule5(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
