package ca.bc.gov.nrs.ilcr.dataextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import ca.bc.gov.nrs.ilcr.reporting.FileSpooler;
import ca.bc.gov.nrs.ilcr.reporting.SpoolShape;
import ca.bc.gov.nrs.ilcr.reporting.SpooledFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit test for the Data Extract download response (Story 21.2). Plain Mockito, no Spring: the
 * controller holds no rule but the three headers, and the ONE thing in them a slice test cannot see
 * is the clock — the integration tests run against the pod's UTC clock on whatever day they run, so
 * a file named for the wrong calendar day would pass every regex they can write.
 *
 * <p>The instant is chosen to straddle midnight: 02:30 UTC on 2020-03-06 is 18:30 Pacific on the
 * evening of 2020-03-05, but already the next day in UTC. Legacy's server and its readers sat in
 * Pacific time, so the download must be named for the Pacific date.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataExtractController — the download response")
class DataExtractControllerTest {

  /** 2020-03-05 18:30 Pacific (PST, UTC-8) — already 02:30 on 2020-03-06 in UTC. */
  private static final Instant STRADDLES_MIDNIGHT = Instant.parse("2020-03-06T02:30:00Z");

  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  private static final DataExtractRequest REQUEST =
      new DataExtractRequest("2020", "2020", List.of(514L), List.of("Schedule 6"));

  @Mock private DataExtractService service;

  /**
   * A REAL spooled file. {@code SpooledFile}'s constructor is package-private to {@code reporting}
   * on purpose (only the spooler may mint one), so the honest way to hold one here is to spool a
   * few bytes through the public {@code FileSpooler} into a temp directory. Its measured size is
   * then the figure the controller must echo as {@code Content-Length}.
   */
  @TempDir Path spoolDir;

  private SpooledFile spooled(byte[] body) {
    return new FileSpooler(spoolDir.toString())
        .spool(SpoolShape.DATA_EXTRACT, out -> out.write(body));
  }

  @Test
  @DisplayName("names the file for the PACIFIC date, with legacy's content type and a real length")
  void generate_namesFileByPacificDate_withLegacyContentTypeAndMeasuredLength() throws IOException {
    // A BOM-led one-cell row: the smallest thing that is shaped like a real extract.
    byte[] body = "\uFEFF\"x\"\n".getBytes(StandardCharsets.UTF_8);
    try (SpooledFile csv = spooled(body)) {
      when(service.generate(REQUEST)).thenReturn(csv);
      DataExtractController controller =
          new DataExtractController(service, Clock.fixed(STRADDLES_MIDNIGHT, PACIFIC));

      // Authentication is only consumed by @PreAuthorize, which does not run outside Spring.
      ResponseEntity<Resource> response = controller.generate(REQUEST, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      // Exactly legacy's name shape (less the createTempFile suffix, which was an artefact), and
      // 0305 not 0306: the day is the Pacific one, although the instant is past midnight in UTC.
      assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
          .isEqualTo("attachment; filename=\"dataExtract20200305.csv\"");
      // Legacy's literal application/csv, not the IANA text/csv. Asserted as the wire string
      // rather than a MediaType, since a MediaType equality would accept parameter reordering.
      assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
          .isEqualTo("application/csv;charset=UTF-8");
      // Content-Length is the MEASURED size of the finished file (SpooledFile.size), never a
      // prediction — it is what turns a truncated transfer into a browser-visible failure.
      assertThat(response.getHeaders().getContentLength()).isEqualTo(body.length);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().contentLength()).isEqualTo(body.length);
    }
  }

  @Test
  @DisplayName("negative control: the same instant on a UTC clock would name the NEXT day")
  void generate_sameInstantOnUtcClock_namesNextDay_soTheZoneIsLoadBearing() {
    try (SpooledFile csv = spooled(new byte[] {'x'})) {
      when(service.generate(REQUEST)).thenReturn(csv);
      DataExtractController controller =
          new DataExtractController(service, Clock.fixed(STRADDLES_MIDNIGHT, ZoneOffset.UTC));

      ResponseEntity<Resource> response = controller.generate(REQUEST, null);

      // Proves the first test is not passing by accident of the instant: only the zone differs.
      assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
          .isEqualTo("attachment; filename=\"dataExtract20200306.csv\"");
    }
  }
}
