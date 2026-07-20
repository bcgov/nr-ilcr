package ca.bc.gov.nrs.ilcr.schedule4.api;

import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 4 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule4Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4).
 *
 * <p>This first Schedule 4 story is READ only — the sub-page lists (Towing/Truck Rehaul/Other) and
 * all write paths are deferred.
 */
@RequestMapping("/api/v1/schedule4")
public interface Schedule4Api {

  /**
   * Get the Schedule 4 (Special Log Transportation Costs) read document for a mill and reporting
   * year: the list of dump locations, each with its in-scope transportation-category amounts (9 fixed
   * + 3 distance-based). Context guards (400/404/409/403) are enforced by {@code MillContextService} +
   * method security. A valid, active mill/year with no {@code TRANSPORTATION_REPORT} rows for category
   * {@code "4"} returns 200 with {@code locations: []} — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the Schedule 4 read document
   */
  @GetMapping
  ResponseEntity<Schedule4Response> getSchedule4(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
