package ca.bc.gov.nrs.ilcr.schedule10.api;

import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 10 (Report New Road Construction Costs) API contract. The interface owns the request
 * mapping and parameter contract; {@code Schedule10Controller} implements it and adds
 * authorization.
 *
 * <p>This story is the read only. Stories 11.2 (writes + check-status) and 11.3 (frontend) build on
 * the document shape pinned here and must not re-pin it.
 *
 * <p>{@code millId}/{@code year} arrive as OPTIONAL raw Strings, not typed required params, because
 * the contract pins the verbatim legacy ERR-001 message for missing, blank AND non-numeric values —
 * which a typed required {@code @RequestParam} cannot produce (it yields the generic
 * missing-parameter and type-mismatch 400s instead). Parsing and the guard chain live in
 * {@code MillContextService} (AD-4).
 */
@RequestMapping("/api/v1/schedule10")
public interface Schedule10Api {

  /**
   * Get the Schedule 10 road-construction document for a mill and reporting year.
   *
   * <p>Each page carries a system-derived read-only Road Group (BR-04); an unmapped
   * TSA/supply-block
   * or TFL combination serves a blank Road Group without error (S12). Each page also carries the
   * road-detail count backing the legacy {@code Enter Road Data ({count})} link text (CNT-001).
   *
   * <p>Guards: missing/blank/non-numeric params → 400 ERR-001; mill not active for the year → 409
   * ERR-002; no {@code ILCR_MILL_REPORT_STATUS} row → 404 ERR-003; no {@code VIEW_SCHEDULE} → 403.
   * A valid, active mill/year with zero construction pages is a 200 with an empty page list, not a
   * 404.
   *
   * @param millId the raw mill id param (validated by millcontext; may be absent or malformed)
   * @param year the raw reporting year param (validated by millcontext; may be absent or malformed)
   * @param authentication the caller, which drives the read-only {@code editable} flag
   * @return 200 with the Schedule 10 document
   */
  @GetMapping
  ResponseEntity<Schedule10Response> getSchedule10(
      @RequestParam(name = "millId", required = false) String millId,
      @RequestParam(name = "year", required = false) String year,
      Authentication authentication);
}
