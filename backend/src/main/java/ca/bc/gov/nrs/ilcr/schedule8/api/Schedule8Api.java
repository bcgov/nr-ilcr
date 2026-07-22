package ca.bc.gov.nrs.ilcr.schedule8.api;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 8 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule8Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4). Story 14.1 is the
 * read only; the page/sample/rate write endpoints and Check Status are later stories (14.2–14.6).
 */
@RequestMapping("/api/v1/schedule8")
public interface Schedule8Api {

  /**
   * Get the Schedule 8 (Tree to Truck / Special Skidding Costs) read document for a mill and reporting
   * year: the three-level hierarchy of report pages, each with its samples, each with its
   * additions/deductions and server-computed rates and counts. Context guards (400/404/409/403) are
   * enforced by {@code MillContextService} + method security. A valid, active mill/year with no
   * category-{@code '8'} pages returns 200 with {@code pages: []} — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the Schedule 8 read document
   */
  @GetMapping
  ResponseEntity<Schedule8Response> getSchedule8(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
