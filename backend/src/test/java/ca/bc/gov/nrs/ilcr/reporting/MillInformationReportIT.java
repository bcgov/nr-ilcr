package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acceptance test — the Mill Information report (UC-MRPT-003). GET
 * /api/v1/reports/mill-information?year= renders one section per mill for the year into a single
 * {@code mills_print.pdf}, with no mill parameter and no working-context guard.
 *
 * <p>Seed is R__40: mills 730 (fully populated), 731 (sparse — no zone, no contacts, prefix-only
 * milestones) and 732 (no client linkage at all), all with 2021 report-status rows alongside the
 * pre-existing mill 514.
 *
 * <p>Security is pinned OFF so these isolate rendering from authorization, which {@link
 * MillInformationReportAuthorizationIT} covers.
 */
@DisplayName("GET /api/v1/reports/mill-information — Mill Information report")
// Security OFF isolates rendering from authorization. The mock principal defaults to
// ILCR_SUBMITTER, which this ADMIN-only endpoint would 403, so the mock role is raised to
// ILCR_ADMIN — these tests exercise the report, not the gate that owns it.
@TestPropertySource(
    properties = {"ilcr.security.enabled=false", "ilcr.security.mock-role=ILCR_ADMIN"})
class MillInformationReportIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/mill-information";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String YEAR_REQUIRED = "Report Year: Value is required.";
  private static final String UNDEFINED_ERROR =
      "ILCR has found an unhandled error/exception. Please refer to application log files.";

  @Test
  @DisplayName("2021 -> 200 application/pdf, %PDF body, mills_print.pdf attachment")
  void openYear_returnsPdf() throws Exception {
    MvcResult result =
        streamPdf(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mills_print.pdf\""))
            .andReturn();

    assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
  }

  @Test
  @DisplayName("the PDF carries one section per mill, with populated and \"-\" fallback content")
  void pdfCarriesEveryMillSection() throws Exception {
    String text = pdfText(2021);

    // Every seeded mill for the year gets its own section (BR-01/BR-05).
    assertThat(text)
        .contains("MILL INFO FULL - 7300")
        .contains("MILL INFO SPARSE - 7310")
        .contains("MILL INFO NO CLIENT - 7320");

    // Static section chrome, proving layout parity survived the port.
    assertThat(text)
        .contains("Government of British Columbia")
        .contains("2021 Annual Interior Logging Cost Report")
        .contains("Mill Information")
        .contains("Timber Pricing Branch")
        .contains("Schedule Status")
        .contains("Ownership Information")
        .contains("Contacts");

    // Mill 730: populated address, region, ownership, formatted head-office phone, all milestones.
    assertThat(text)
        .contains("100 MAIN STREET")
        .contains("CRANBROOK")
        .contains("V1C1A1")
        .contains("Kootenay Selling Price Zone")
        .contains("FULL OWNERSHIP HOLDINGS LTD")
        .contains("(250) 555-1212")
        .contains("2021-01-05")
        .contains("2021-07-01");
  }

  @Test
  @DisplayName("a milestone holding only its legacy prefix renders blank, never \"D: \"")
  void prefixOnlyMilestoneRendersBlank() throws Exception {
    // Mill 731's draft/submit/verify are seeded as "D: " / "S: " / "V: ", the shape 80 of the 118
    // delivery rows carry. The three-character prefix must never reach the page.
    assertThat(pdfText(2021)).doesNotContain("D: ").doesNotContain("S: ").doesNotContain("V: ");
  }

  @Test
  @DisplayName("the associated-user tables are absent — no GUID reaches the page")
  void associatedUserTablesAreNotRendered() throws Exception {
    String text = pdfText(2021);

    assertThat(text)
        .doesNotContain("Associated Auditor")
        .doesNotContain("Submitter/Licensee")
        .doesNotContain(CANONICAL_SUBMITTER_GUID);
  }

  @Test
  @DisplayName("no year -> 400 with the verbatim required-field message and no file")
  void missingYear_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
  }

  @Test
  @DisplayName("a blank year -> 400, the same rejection as an absent one")
  void blankYear_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "  ").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
  }

  @Test
  @DisplayName("a non-numeric year -> 400, not a framework parse error")
  void nonNumericYear_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "not-a-year").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
  }

  @Test
  @DisplayName("a year no mill reports on -> 500 undefinedError and no file")
  void yearWithNoMills_surfacesUndefinedError() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "1999").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value(UNDEFINED_ERROR));
  }

  private String pdfText(int year) throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT).param("year", String.valueOf(year)).accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andReturn();
    try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
      return new PDFTextStripper().getText(document);
    }
  }
}
