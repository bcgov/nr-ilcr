package ca.bc.gov.nrs.ilcr.schedule2.api;

import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Schedule 2 API contract (controller + api-interface split, CSP idiom). The interface owns the
 * request mapping and parameter contract; {@code Schedule2Controller} implements it and adds
 * authorization. {@code millId} and {@code year} are required query params (AD-4). Read-only slice —
 * no PUT/DELETE/check-status (deferred to Story 3.2).
 */
@RequestMapping("/api/v1/schedule2")
public interface Schedule2Api {

  /**
   * Get the Schedule 2 aggregate document for a mill and reporting year. Context guards
   * (400/404/409/403) are enforced by {@code MillContextService} + method security. Unlike
   * Schedule 1, a valid, active mill/year with no saved Schedule 2 returns a 200 empty editable
   * document — never a 404.
   *
   * @param millId the mill id (required)
   * @param year the reporting year (required)
   * @param authentication the caller (used to derive the read-only {@code editable} flag)
   * @return 200 with the aggregate document
   */
  @GetMapping
  ResponseEntity<Schedule2Response> getSchedule2(
      @RequestParam long millId, @RequestParam int year, Authentication authentication);
}
