package ca.bc.gov.nrs.ilcr.controller;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.service.codes.DistrictService;
import ca.bc.gov.nrs.ilcr.util.JwtPrincipalUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes application code lists used by the frontend.
 *
 * <p>This controller returns small, read-only code sets such as districts, sampling codes and
 * assessment-area statuses. These endpoints are intended for populating dropdowns and other
 * UI widgets.</p>
 */
@RestController
@RequestMapping("/api/codes")
@RequiredArgsConstructor
@Slf4j
public class CodesController {

  private final DistrictService districtService;

  /**
   * Return the list of district code/description pairs.
   *
   * @return a list of {@link CodeDescriptionDto} representing districts
   */
  @GetMapping("/districts")
  public List<CodeDescriptionDto> getDistricts(@AuthenticationPrincipal Jwt jwt) {
    log.info("Fetching districts for user: {}", JwtPrincipalUtil.getUserId(jwt));
    return districtService.findAllOrgUnits();
  }

}
