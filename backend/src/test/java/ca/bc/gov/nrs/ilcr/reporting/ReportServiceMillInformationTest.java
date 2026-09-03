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
import java.util.regex.Pattern;
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
 *
 * <p>Both render entry points are here: the all-mills report for a year (Story 19.1) and the
 * per-mill drill-down (Story 19.3). They are deliberately the same renderer over a list of one, and
 * {@code drillDownSectionMatchesTheAllMillsSection} is where that sameness stops being an argument
 * and becomes an assertion.
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

  @Test
  @DisplayName("a mill with no row for the year is its own 404, distinct from the no-mills one")
  void absentMillRaisesMillNotFound() {
    // Not MillInformationNoMillsException: that one says the YEAR is empty, and saying it here
    // would be false on its face — the administrator is reading a table of the year's mills while
    // the message claims none exist. And not the catch-all either: an open year with no row for one
    // mill is a data condition, not a fault, so it must not raise the 5xx rate.
    when(millInformationService.findSection(999, 2021)).thenReturn(java.util.Optional.empty());
    ReportService service = service();

    assertThatThrownBy(() -> service.renderMillInformation(999L, 2021))
        .isInstanceOf(MillInformationMillNotFoundException.class)
        .extracting(e -> ((MillInformationMillNotFoundException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("the drill-down renders exactly ONE section — the mill asked for, and no other")
  void drillDownRendersOnlyTheRequestedMill() throws Exception {
    when(millInformationService.findSection(731, 2021))
        .thenReturn(java.util.Optional.of(section(731, "SECOND MILL", "7310")));

    ReportService.MillDrillDown drillDown = service().renderMillInformation(731L, 2021);
    // The mill NUMBER travels back for the parity filename; the endpoint is keyed by the id.
    assertThat(drillDown.millNumber()).isEqualTo("7310");

    byte[] pdf;
    try (RenderedReport report = drillDown.report()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      report.writeTo(out);
      pdf = out.toByteArray();
    }

    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    try (PDDocument document = Loader.loadPDF(pdf)) {
      // ONE fill, so one page — where the all-mills report of two mills produces two.
      assertThat(document.getNumberOfPages()).isEqualTo(1);
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("SECOND MILL - 7310");
      // No other mill leaks in. The read is what scopes this, and a predicate that silently stopped
      // binding would show up here as a second section rather than as a passing test.
      assertThat(text).doesNotContain("FIRST MILL");
    }
  }

  @Test
  @DisplayName("a drilled mill still at Opened/Draft renders blank milestones, never a crash (S08)")
  void drillDownWithNoMilestonesStillRenders() throws Exception {
    // The recorded fix for legacy's latent NPE. MillReportStatusReport.java:96-99 called
    // .substring(2) on all four milestone strings with no null guard, so drilling into a mill whose
    // milestones are NULL in the view — fixture mill 732 — threw before a byte was written. Here
    // every milestone routes through LegacyDateText.stripPrefix and then the mapper's blank
    // substitution, so the crash is unreachable BY CONSTRUCTION. That is exactly why it is
    // asserted:
    // 19.1's own tests all render mill 730, which is fully dated, so nothing proved this until now.
    MillInformationSection openedOnly =
        new MillInformationSection(
            732,
            "7320",
            "MILL INFO NO CLIENT",
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
    when(millInformationService.findSection(732, 2021))
        .thenReturn(java.util.Optional.of(openedOnly));

    String text;
    try (RenderedReport report = service().renderMillInformation(732L, 2021).report()) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      report.writeTo(out);
      try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
        text = new PDFTextStripper().getText(document);
      }
    }

    // A PDF, with the section's chrome intact...
    assertThat(text)
        .contains("MILL INFO NO CLIENT - 7320")
        .contains("Schedule Status")
        // ...and the milestone lines BLANK: never the word "null", never a raw prefix, and never
        // the "-" the address/contact fields use (legacy's null sweep left these empty).
        .doesNotContain("null")
        .doesNotContain("D: ")
        .doesNotContain("Draft: -")
        .doesNotContain("Submitted: -")
        .doesNotContain("Verified: -");
  }

  @Test
  @DisplayName("the drill-down and the all-mills report render that mill's section IDENTICALLY")
  void drillDownSectionMatchesTheAllMillsSection() throws Exception {
    // The story's parity acceptance criterion, proven rather than argued: same template, same
    // mapper, same projection, so the same mill's page must come out the same. Compared as
    // extracted
    // TEXT, which is what a reader would compare — byte equality would fail on the PDF's own
    // creation metadata, not on content.
    MillInformationSection first = section(730, "FIRST MILL", "7300");
    MillInformationSection second = section(731, "SECOND MILL", "7310");
    when(millInformationService.findSections(2021)).thenReturn(List.of(first, second));
    when(millInformationService.findSection(731, 2021)).thenReturn(java.util.Optional.of(second));
    ReportService service = service();

    String allMills;
    try (RenderedReport report = service.renderMillInformation(2021)) {
      allMills = textOf(report);
    }
    String drillDown;
    try (RenderedReport report = service.renderMillInformation(731L, 2021).report()) {
      drillDown = textOf(report);
    }

    // The all-mills text holds both pages; the drill-down holds exactly the second mill's page.
    assertThat(allMills).contains(drillDown);
    assertThat(drillDown).contains("SECOND MILL - 7310").doesNotContain("FIRST MILL");
  }

  /**
   * The extracted text of a rendered report, exported through the real PDF exporter, with the
   * template's generation timestamp normalised away.
   *
   * <p><b>Why the timestamp has to go, and why removing it does not weaken the parity
   * assertion.</b> Every section stamps the moment it was FILLED ({@code September 02, 2026
   * 14:18:59}). The comparison above renders the same mill twice, a fraction of a second apart, so
   * whenever those two fills straddle a clock second the texts differ by one digit and a
   * containment check fails — for a reason that has nothing to do with the mill. This was a real
   * flake in this test, not a hypothetical: it passed twice and then failed on the third full run.
   *
   * <p>The timestamp is document chrome — when the PDF was produced — not mill content, and it is
   * the ONLY thing normalised. Everything the story actually claims is identical between the two
   * outputs (the heading, all nineteen fields, the milestone lines, the section chrome, the
   * associated-user frame, the page footer) is still compared verbatim. Loosening the assertion to
   * a few `contains` calls on individual fields, which was the alternative, would have been the
   * weakening.
   */
  private static String textOf(RenderedReport report) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    report.writeTo(out);
    try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
      return PRINT_TIMESTAMP
          .matcher(new PDFTextStripper().getText(document))
          .replaceAll("<generated>");
    }
  }

  /** The template's {@code MMMM dd, yyyy HH:mm:ss} generation stamp, one per rendered section. */
  private static final Pattern PRINT_TIMESTAMP =
      Pattern.compile("[A-Z][a-z]+ \\d{2}, \\d{4} \\d{2}:\\d{2}:\\d{2}");

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
