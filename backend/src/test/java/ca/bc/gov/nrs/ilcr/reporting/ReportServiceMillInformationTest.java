package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millinformation.MillInformationService;
import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Service;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Service;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Service;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for the Mill Information render path.
 *
 * <p>These fill the REAL compiled template from the classpath — the bean-datasource path needs no
 * database connection, so the whole render is exercisable without Oracle. The integration tests
 * prove the endpoint end to end; these prove the rendering logic itself, which is otherwise only
 * reachable through a container the coverage analysis never runs.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService — Mill Information render")
class ReportServiceMillInformationTest {

  @Mock private DataSource dataSource;
  @Mock private Schedule1Service schedule1Service;
  @Mock private Schedule2Service schedule2Service;
  @Mock private Schedule3Service schedule3Service;
  @Mock private Schedule4Service schedule4Service;
  @Mock private Schedule5Service schedule5Service;
  @Mock private Schedule6Service schedule6Service;
  @Mock private Schedule7aService schedule7aService;
  @Mock private Schedule7bService schedule7bService;
  @Mock private Schedule8Service schedule8Service;
  @Mock private Schedule9Service schedule9Service;
  @Mock private Schedule10Service schedule10Service;
  @Mock private Schedule11Service schedule11Service;
  @Mock private MillInformationService millInformationService;

  private ReportService service() {
    return new ReportService(
        dataSource,
        schedule1Service,
        schedule2Service,
        schedule3Service,
        schedule4Service,
        schedule5Service,
        schedule6Service,
        schedule7aService,
        schedule7bService,
        schedule8Service,
        schedule9Service,
        schedule10Service,
        schedule11Service,
        millInformationService,
        new ReportVirtualizerFactory("", 300, 4096, 100));
  }

  @Test
  @DisplayName("an open year with no mills is its own 404, not the catch-all 500")
  void emptyYearRaisesNoMillsNotFound() {
    // The caller has already rejected any year that is not open, so reaching here means a year WAS
    // opened and no mill was initialised against it: a data condition, not a fault. Sharing
    // undefinedError would tell an administrator the system is broken and raise the 5xx rate for
    // something nobody needs to fix in code.
    when(millInformationService.findSections(1999)).thenReturn(List.of());
    ReportService service = service();

    assertThatThrownBy(() -> service.renderMillInformation(1999))
        .isInstanceOf(MillInformationNoMillsException.class)
        .extracting(e -> ((MillInformationNoMillsException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("each mill becomes its own section, and the exported PDF carries them all")
  void everyMillBecomesASection() throws Exception {
    when(millInformationService.findSections(2021))
        .thenReturn(
            List.of(section(730, "FIRST MILL", "7300"), section(731, "SECOND MILL", "7310")));

    byte[] pdf;
    try (RenderedReport report = service().renderMillInformation(2021)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      report.writeTo(out);
      pdf = out.toByteArray();
    }

    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    try (PDDocument document = Loader.loadPDF(pdf)) {
      // One fill per mill, so one page each — the legacy shape.
      assertThat(document.getNumberOfPages()).isEqualTo(2);
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("FIRST MILL - 7300").contains("SECOND MILL - 7310");
      assertThat(text).contains("2021 Annual Interior Logging Cost Report");
    }
  }

  @Test
  @DisplayName("absent values reach the page as legacy rendered them, not as the word null")
  void absentValuesRenderAsLegacyDid() throws Exception {
    MillInformationSection sparse =
        new MillInformationSection(
            732,
            "7320",
            "SPARSE MILL",
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    when(millInformationService.findSections(2021)).thenReturn(List.of(sparse));

    String text;
    try (RenderedReport report = service().renderMillInformation(2021)) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      report.writeTo(out);
      try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
        text = new PDFTextStripper().getText(document);
      }
    }

    assertThat(text)
        .contains("SPARSE MILL - 7320")
        .contains("Active:")
        .contains("No")
        .doesNotContain("null");
  }

  private static MillInformationSection section(long id, String name, String number) {
    return new MillInformationSection(
        id,
        number,
        name,
        true,
        "A Zone",
        "An Owner",
        "1 Main St",
        "Suite 2",
        "Cranbrook",
        "V1C1A1",
        "Y",
        "Head Office Contact",
        "2505551212",
        "Division Contact",
        "2505551313",
        "2021-01-05",
        "2021-03-10",
        "2021-05-20",
        "2021-07-01");
  }
}
