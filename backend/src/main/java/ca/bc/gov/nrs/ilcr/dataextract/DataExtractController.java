package ca.bc.gov.nrs.ilcr.dataextract;

import ca.bc.gov.nrs.ilcr.dataextract.api.DataExtractApi;
import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import ca.bc.gov.nrs.ilcr.reporting.SpooledFile;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for the Data Extract (UC-EXT-001). Adds authorization to {@link DataExtractApi} and
 * holds no business rules — the selection gate and the build belong to {@link DataExtractService}.
 *
 * <p>Gated on the extract's OWN capability rather than the reports area's. Legacy derived this
 * page's WebADE action from its view id (the postback key was {@code extractData/Generate Report}),
 * distinct from the {@code generateReports} action that rendered the submenu — the menu item itself
 * carried no action of its own — so the rebuild gives the page its own action too. Authorization
 * names the capability, never the control or the menu (AD-7).
 *
 * <p>The response is built AFTER the file is complete on disk, the same whole-file-or-no-file
 * contract the report endpoints keep: a throw from the service is still a normal error here, and
 * the body is a {@link Resource} written on the request thread with a real {@code Content-Length}.
 *
 * <p>The content type is legacy's literal {@code application/csv; charset=UTF-8}, not the IANA
 * {@code text/csv}; nothing but a browser download reads it, and legacy's literal costs nothing.
 * The file name is {@code dataExtract<yyyyMMdd>.csv} — legacy's name less the random temp-file
 * suffix {@code createTempFile} appended, which was an artefact rather than a name. The frontend
 * builds the same name from the same rule rather than parsing this header, as the other download
 * pages do.
 */
@RestController
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class DataExtractController implements DataExtractApi {

  private static final Logger log = LoggerFactory.getLogger(DataExtractController.class);

  private static final String GENERATE_DATA_EXTRACT =
      "@permissions.hasPermission(authentication, 'GENERATE_DATA_EXTRACT')";

  static final MediaType APPLICATION_CSV_UTF8 =
      MediaType.parseMediaType("application/csv; charset=UTF-8");

  private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final DataExtractService service;
  private final Clock clock;

  /**
   * Constructs the controller.
   *
   * @param service the extract selection gate and generator seam
   */
  @Autowired
  public DataExtractController(DataExtractService service) {
    this(service, Clock.system(ZoneId.of("America/Vancouver")));
  }

  DataExtractController(DataExtractService service, Clock clock) {
    this.service = service;
    this.clock = clock;
  }

  @Override
  @PreAuthorize(GENERATE_DATA_EXTRACT)
  public ResponseEntity<Resource> generate(
      DataExtractRequest request, Authentication authentication) {
    // Before the ResponseEntity exists, deliberately: a throw here is still a normal error.
    SpooledFile csv = service.generate(request);
    String filename = "dataExtract" + LocalDate.now(clock).format(FILE_DATE) + ".csv";
    log.debug("Sending {} ({} bytes)", filename, csv.size());
    return ResponseEntity.ok()
        .contentType(APPLICATION_CSV_UTF8)
        .contentLength(csv.size())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(csv.asResource());
  }
}
