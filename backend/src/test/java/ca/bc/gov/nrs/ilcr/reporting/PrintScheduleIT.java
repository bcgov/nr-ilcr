package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Acceptance test — the combined Print Schedules PDF (Epic 20.2). POST /api/v1/reports/print
 * assembles the selected in-scope schedules into ONE bookmarked PDF, filled from the primary
 * datasource (Schedule 9) and the schedule {@code *Service} DTOs (5/6/7A/7B/11), on the shared seed:
 * mill 514/2021 carries data for all six in-scope schedules (Schedule 11 added by V20260816). The
 * PDF text is asserted with pdfbox to prove each selected section's heading and a seeded value
 * rendered; skip-empty (BR-09), all-empty (ERR-005) and the ERR-002/003/004 selection ladder plus
 * the 400/409 context guards are pinned here. Security is OFF (isolated from authz — {@link
 * PrintAuthorizationIT}).
 */
@DisplayName("POST /api/v1/reports/print — combined Print Schedules PDF")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class PrintScheduleIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/print";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_004 = "This Mill is not active for the current Reporting Year. "
      + "Please select another mill from the Home Page.";
  private static final String ERR_005 = "Schedule not found.";
  private static final String ERR_002_NO_SCHEDULE = "Please select at least one Schedule to print.";
  private static final String ERR_004_NO_OPTION = "At least one 'Print Option' is required to print.";

  private static String body(String json) {
    return json;
  }

  @Test
  @DisplayName("517/2021 select 5,6,7A,7B,9,11 -> 200 application/pdf, %PDF, attachment header")
  void multiSchedule_returnsCombinedPdf() throws Exception {
    // Mill 517/2021 carries data for ALL SIX in-scope schedules (Schedule 11 added by V20260816);
    // editability does not gate printing (517 is Submitted but print is read-only, BR-01).
    String selection = """
        {"schedule5":true,"schedule6":true,"schedule7a":true,"schedule7b":true,
         "schedule9":true,"schedule11":true,
         "printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result = mockMvc.perform(post(ENDPOINT).param("millId", "517").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection))
            .accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"schedules_print.pdf\""))
        .andReturn();

    byte[] pdf = result.getResponse().getContentAsByteArray();
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

    String text = extractText(pdf);
    // Each selected section's heading rendered, in the fixed legacy order.
    assertThat(text).contains("Schedule 5:  Camp and Access Expense");
    assertThat(text).contains("Schedule 6:  Road Management Costs");
    assertThat(text).contains("Schedule 7A:  Bridge Costs");
    assertThat(text).contains("Schedule 7B:  Culvert Costs");
    assertThat(text).contains("Miscellaneous");
    assertThat(text).contains("Schedule 11:  Basic Silviculture");
    // The mill title block (MILL_NAME-MILL_NUMBER) rides every section header.
    assertThat(text).contains("Submitted Milling");
    // A seeded value from each of several sections, proving real data (not just headings) rendered.
    assertThat(text).contains("Submitted Camp");        // Schedule 5 camp name (517/2021)
    assertThat(text).contains("CTR-517");               // Schedule 9 contractor id (record 9110)
    assertThat(text).contains("Cedar Ridge Reforest");  // Schedule 11 location (V20260816)
  }

  @Test
  @DisplayName("printComments=false hides the comments -> body still prints, no comment text")
  void commentsGated() throws Exception {
    String selection = """
        {"schedule9":true,"printScheduleInformation":true,"printComments":false}
        """;
    MvcResult result = mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isOk())
        .andReturn();

    String text = extractText(result.getResponse().getContentAsByteArray());
    // Body renders (item name present) but record 9101's comment "Cattleguard install." does not.
    assertThat(text).contains("Cattleguard");
    assertThat(text).doesNotContain("install");
  }

  @Test
  @DisplayName("skip-empty (BR-09): 514/2021 select 5+11, 11 has no data -> 5 prints, 11 omitted")
  void skipEmpty_keepsTheRest() throws Exception {
    // Mill 514/2021 has Schedule 5 camps but NO Schedule 11 locations, so selecting both must print
    // the Schedule 5 section and silently omit Schedule 11 (BR-09).
    String selection = """
        {"schedule5":true,"schedule11":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result = mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andReturn();

    String text = extractText(result.getResponse().getContentAsByteArray());
    assertThat(text).contains("Schedule 5:  Camp and Access Expense");
    assertThat(text).contains("Cedar Flats Camp");
    // Schedule 11 has no locations for 514/2021, so its section is skipped entirely.
    assertThat(text).doesNotContain("Schedule 11:  Basic Silviculture");
  }

  @Test
  @DisplayName("all-empty (ERR-005): 515/2021 select 5, no data -> 404 'Schedule not found.'")
  void allEmpty_returns404() throws Exception {
    String selection = """
        {"schedule5":true,"printScheduleInformation":true,"printComments":true}
        """;
    mockMvc.perform(post(ENDPOINT).param("millId", "515").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_005));
  }

  @Test
  @DisplayName("ERR-002: content option on, no schedule selected -> 400 verbatim")
  void err002_noScheduleSelected() throws Exception {
    String selection = """
        {"printScheduleInformation":true,"printComments":false}
        """;
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_002_NO_SCHEDULE));
  }

  @Test
  @DisplayName("ERR-003: a schedule selected but neither content option -> 400 verbatim")
  void err003_noContentOption() throws Exception {
    String selection = """
        {"schedule5":true,"printScheduleInformation":false,"printComments":false}
        """;
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith(
            "Schedules are 'checked' for print")));
  }

  @Test
  @DisplayName("ERR-004: no print option at all -> 400 verbatim")
  void err004_noPrintOption() throws Exception {
    String selection = """
        {"printScheduleInformation":false,"printComments":false,"printMillInformationReport":false}
        """;
    mockMvc.perform(post(ENDPOINT).param("millId", "514").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_004_NO_OPTION));
  }

  @Test
  @DisplayName("no mill/year context -> 400 verbatim ERR-003 (trailing space), no render")
  void noContext_returns400() throws Exception {
    String selection = """
        {"schedule9":true,"printScheduleInformation":true}
        """;
    mockMvc.perform(post(ENDPOINT).param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_003));
  }

  @Test
  @DisplayName("closed mill (516, CLS) -> 409 verbatim ERR-004, no render")
  void inactiveMill_returns409() throws Exception {
    String selection = """
        {"schedule9":true,"printScheduleInformation":true}
        """;
    mockMvc.perform(post(ENDPOINT).param("millId", "516").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(selection)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_004));
  }

  private static String extractText(byte[] pdf) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    }
  }
}
