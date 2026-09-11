package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.dataextract.api.DataExtractApi;
import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for the Data Extract (UC-EXT-001). Adds authorization to {@link DataExtractApi} and
 * holds no business rules — the selection gate belongs to {@link DataExtractService}.
 *
 * <p>Gated on the extract's OWN capability rather than the reports area's. Legacy derived this
 * page's WebADE action from its view id, giving it an {@code extractData} action distinct from the
 * {@code generateReports} action that rendered the submenu, so the rebuild gives it its own action
 * too. Authorization names the capability, never the control or the menu (AD-7).
 *
 * <p>No {@code MessageSource} is wired in: every response this endpoint produces today is an error,
 * and {@code GlobalExceptionHandler} resolves those keys itself. The story that adds the CSV adds
 * the bundle lookup with it, if its response needs one.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class DataExtractController implements DataExtractApi {

  private static final String GENERATE_DATA_EXTRACT =
      "@permissions.hasPermission(authentication, 'GENERATE_DATA_EXTRACT')";

  private final DataExtractService service;

  /**
   * Constructs the controller.
   *
   * @param service the extract selection gate
   */
  public DataExtractController(DataExtractService service) {
    this.service = service;
  }

  @Override
  @PreAuthorize(GENERATE_DATA_EXTRACT)
  public ResponseEntity<Void> generate(DataExtractRequest request, Authentication authentication) {
    service.generate(request);
    // Unreachable: the generator seam always throws today. Kept so the signature carries the shape
    // the CSV response will take rather than declaring a return type that has to change twice.
    return ResponseEntity.noContent().build();
  }
}
