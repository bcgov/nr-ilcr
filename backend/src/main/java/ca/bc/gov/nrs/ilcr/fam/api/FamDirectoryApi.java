package ca.bc.gov.nrs.ilcr.fam.api;

import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * FAM directory API (Story 2.3; controller + api-interface split, CSP idiom). Backs the admin's
 * assignment picker. {@code MillAssignmentController} owns assign/end; this owns the candidate list.
 */
@RequestMapping("/api/v1/fam/submitters")
public interface FamDirectoryApi {

  /**
   * List submitter-eligible FAM users for the picker, optionally filtered by {@code q}.
   *
   * @param q optional free-text filter (matched against display name / username)
   * @param authentication the caller (ILCR_ADMIN)
   * @return 200 with the candidates; 503 {@code ProblemDetail} if the FAM directory is unreachable
   */
  @GetMapping
  ResponseEntity<List<FamSubmitter>> searchSubmitters(
      @RequestParam(name = "q", required = false) String q, Authentication authentication);
}
