package ca.bc.gov.nrs.ilcr.schedule6.api;

import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 6 (Road Management Costs) API contract (controller + api-interface split, the
 * established idiom). The interface owns the request mapping and parameter contract;
 * {@code Schedule6Controller} implements it and adds authorization. Future actions are
 * PUT/DELETE/POST sub-resources (Story 8.2).
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings — like Schedule 11, unlike
 * Schedule 1's typed params — because AC4 pins the verbatim legacy ERR-001 message for missing,
 * blank, AND
 * non-numeric values, which a typed required {@code @RequestParam} cannot produce (it yields the
 * generic missing-parameter / type-mismatch 400s). Parsing + the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule6")
public interface Schedule6Api {

  /**
   * Get the Schedule 6 road-management-costs document for a mill and reporting year. Guards:
   * missing/malformed params → 400 ERR-001; mill not active → 409 ERR-002; no
   * {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-003 (zero road records is a valid 200); no
   * {@code VIEW_SCHEDULE} → 403.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent/malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent/malformed)
   * @param authentication the caller (drives the read-only {@code editable} flag)
   * @return 200 with the road-records document
   */
  @GetMapping
  ResponseEntity<Schedule6Response> getSchedule6(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
