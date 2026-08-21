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
 * Acceptance test — Schedule 9 PDF (Epic 20, AD-16). GET /api/v1/reports/schedule9 renders the
 * embedded JasperReports 7 template to a PDF, filled from the primary datasource on the same
 * V20260813 seed the Schedule 9 read pins: 514/2021 (3 records) → 200 PDF; 515/2021 (Draft, empty)
 * → 404 {@code Schedule not found.}; 517/2021 (Submitted, 1 record) → 200 PDF (editability does not
 * gate printing). The mill/year context guards (400 no-context, 409 inactive) are shared with the
 * read endpoint and re-pinned here.
 *
 * <p>Security is pinned OFF explicitly (Story 8.1's lesson) so these isolate rendering from authz
 * (covered by {@link ReportAuthorizationIT}). The body is asserted to start with the {@code %PDF}
 * signature — proving a real PDF, not an error page — plus the {@code application/pdf} content type
 * and the attachment Content-Disposition (AC1).
 */
@DisplayName("GET /api/v1/reports/schedule9 — Schedule 9 PDF")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class ReportSchedule9IT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/schedule9";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_004 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";
  private static final String ERR_005 = "Schedule not found.";

  @Test
  @DisplayName("514/2021 Draft (3 records) -> 200 application/pdf, %PDF body, attachment header")
  void draftWithRecords_returnsPdf() throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT)
                    .param("millId", "514")
                    .param("year", "2021")
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule9_514_2021.pdf\""))
            .andReturn();

    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body).isNotEmpty();
    // The %PDF signature proves a real PDF was streamed, not an HTML/JSON error page.
    assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
  }

  @Test
  @DisplayName("517/2021 Submitted (1 record) -> 200 PDF (editability does not gate printing, AC9)")
  void nonDraftWithRecords_returnsPdf() throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"schedule9_517_2021.pdf\""))
            .andReturn();

    assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
  }

  @Test
  @DisplayName(
      "514/2021 PDF text carries the heading, the mill title block, record data, and comments")
  void pdfText_carriesHeadingMillRecordAndComments() throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT)
                    .param("millId", "514")
                    .param("year", "2021")
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andReturn();

    String text;
    try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
      text = new PDFTextStripper().getText(document);
    }

    // Header (page header static text) and the THE.MILL title block (MILL_NAME || '-' ||
    // MILL_NUMBER).
    assertThat(text).contains("Miscellaneous");
    assertThat(text).contains("AAA Milling");
    // Record 9101's contractual item (item 108 name) resolved through the embedded SQL joins.
    assertThat(text).contains("Cattleguard");
    // The comment renders because the endpoint passes p_do_print_comment=true — "install" appears
    // only in record 9101's comment "Cattleguard install.", so this proves the flag is honored.
    assertThat(text).contains("install");
  }

  @Test
  @DisplayName("515/2021 Draft but no records -> 404 verbatim 'Schedule not found.' (AC5), no PDF")
  void draftWithNoRecords_returns404() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "515")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_005));
  }

  @Test
  @DisplayName("no mill/year context -> 400 verbatim ERR-003 (trailing space), no render")
  void noContext_returns400() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_003));
  }

  @Test
  @DisplayName("closed mill (516, CLS) -> 409 verbatim ERR-004 (BR-10 blocks viewing), no render")
  void inactiveMill_returns409() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "516")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_004));
  }
}
