package ca.bc.gov.nrs.ilcr.fam;

import ca.bc.gov.nrs.ilcr.fam.api.FamDirectoryApi;
import ca.bc.gov.nrs.ilcr.fam.dto.FamSubmitter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAM directory endpoint for the admin assignment picker (Story 2.3). ILCR_ADMIN-gated (AD-7). Never
 * touches the DB — the candidate list comes from the {@link FamDirectoryClient} seam. A directory
 * failure surfaces as a 503 {@code ProblemDetail} (from {@link FamDirectoryUnavailableException}) so
 * the admin screen can show it without breaking the local assignments view.
 */
@RestController
@RequiredArgsConstructor
public class FamDirectoryController implements FamDirectoryApi {

  private final FamDirectoryClient directoryClient;

  @Override
  @PreAuthorize("hasAuthority('ILCR_ADMIN')")
  public ResponseEntity<List<FamSubmitter>> searchSubmitters(String q, Authentication authentication) {
    return ResponseEntity.ok(directoryClient.searchSubmitters(q));
  }
}
