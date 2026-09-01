package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
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
  private static final String YEAR_NOT_OPEN = "Report Year is not an open reporting period.";

  /** Every mill R__40 and V9 put in the 2021 report, in mill-id order. */
  private static final List<String> SECTION_TITLES =
      List.of(
          "AAA Milling - 514",
          "MILL INFO FULL - 7300",
          "MILL INFO SPARSE - 7310",
          "MILL INFO NO CLIENT - 7320",
          "MILL INFO CLOSED SINCE - 7330");

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
  @DisplayName("the PDF carries one section per mill, in mill-id order, with no mill missing")
  void pdfCarriesEverySectionInOrder() throws Exception {
    String text = pdfText(2021);

    // Order matters and is legacy's (ORDER BY ILCR_MILL_ID), so this asserts the sequence rather
    // than mere presence — dropping the ORDER BY, or narrowing a join so a mill disappears, fails.
    assertThat(text).containsSubsequence(SECTION_TITLES.toArray(String[]::new));
    // A count, so adding an unrelated seeded mill to 2021 is a deliberate act rather than a silent
    // one. "Schedule Status" appears exactly once per rendered section.
    assertThat(text.split("Schedule Status", -1)).hasSize(SECTION_TITLES.size() + 1);
  }

  @Test
  @DisplayName("each mill's heading is stamped once, not duplicated by a hidden anchor field")
  void headingIsNotDuplicated() throws Exception {
    // The outline anchor rides the visible heading. If it is ever moved back onto a hidden white
    // textField, every title lands in the page twice — invisible on screen, but picked up by text
    // extraction, copy/paste and screen readers.
    String text = pdfText(2021);

    for (String title : SECTION_TITLES) {
      assertThat(text.split(java.util.regex.Pattern.quote(title), -1))
          .as("occurrences of %s", title)
          .hasSize(2);
    }
  }

  @Test
  @DisplayName("static section chrome survives the port")
  void sectionChromeIsPreserved() throws Exception {
    assertThat(pdfText(2021))
        .contains("Government of British Columbia")
        .contains("2021 Annual Interior Logging Cost Report")
        .contains("Mill Information")
        .contains("Timber Pricing Branch")
        .contains("Schedule Status")
        .contains("Contacts")
        .contains("Head Office")
        .contains("Division");
  }

  @Test
  @DisplayName("a populated mill shows its address, region, ownership and formatted phone")
  void populatedMillRendersItsContent() throws Exception {
    assertThat(pdfText(2021))
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
  @DisplayName("Active reflects the REPORTING YEAR's status, not the mill's status today")
  void activeFlagIsPerReportingYear() throws Exception {
    // Mill 733 is ACT for 2021 in the report view but CLS on its status xref (closed since).
    // Reading
    // the xref would print "No" and would silently rewrite history on every reprint.
    String text = pdfText(2021);

    assertThat(sectionFor(text, "MILL INFO CLOSED SINCE")).contains("Active:").contains("Yes");
    // Mill 732 is CLS for the year itself, so it must read No — proving the flag is read at all.
    assertThat(sectionFor(text, "MILL INFO NO CLIENT")).contains("Active:").contains("No");
  }

  @Test
  @DisplayName("one PDF outline bookmark per mill, titled and ordered like the sections")
  void outlineCarriesOneBookmarkPerMill() throws Exception {
    try (PDDocument document = Loader.loadPDF(pdfBytes(2021))) {
      PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
      assertThat(outline).isNotNull();
      List<String> titles = new java.util.ArrayList<>();
      for (PDOutlineItem item : outline.children()) {
        titles.add(item.getTitle());
      }
      assertThat(titles).containsExactlyElementsOf(SECTION_TITLES);
    }
  }

  @Test
  @DisplayName("an unreached milestone renders blank — not the raw prefix, and not a dash")
  void prefixOnlyMilestoneRendersBlank() throws Exception {
    // Mill 731's draft/submit/verify are seeded as "D: " / "S: " / "V: ", the shape 80 of the 118
    // delivery rows carry. The prefix must never reach the page, and legacy left these EMPTY rather
    // than substituting "-" (its null sweep mapped absent to ""), so a dash here is a parity break.
    String sparse = sectionFor(pdfText(2021), "MILL INFO SPARSE");
    assertThat(sparse)
        .doesNotContain("D: ")
        .doesNotContain("S: ")
        .doesNotContain("V: ")
        .doesNotContain("Draft: -")
        .doesNotContain("Submitted: -")
        .doesNotContain("Verified: -");
  }

  @Test
  @DisplayName("the associated-user sections keep their legacy frame but carry no rows")
  void associatedUserSectionsAreFramedButEmpty() throws Exception {
    String text = pdfText(2021);

    // The legacy frame is kept — both headings and their separating rules — so the document holds
    // its shape and the gap is visible rather than a silently missing section.
    assertThat(text).contains("Submitter/Licensee's Delegate").contains("Associated Auditor(s)");

    // But no user ROW is rendered. "Email:" and "Phone Number:" exist only inside those two tables
    // (the mill block's own label is "Name:"), so their absence is what proves the tables are
    // empty.
    // And no raw GUID reaches the page, which is the outcome the descope exists to prevent.
    assertThat(text)
        .doesNotContain("Email:")
        .doesNotContain("Phone Number:")
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
  @DisplayName("a year that is not an open reporting period -> 400, not a 500 system fault")
  void yearNotOpen_rejects() throws Exception {
    // 1999 parses fine but was never opened. Before this guard it reached the report, found no
    // mills, and surfaced as undefinedError — telling the administrator the system had broken.
    mockMvc
        .perform(get(ENDPOINT).param("year", "1999").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(YEAR_NOT_OPEN));
  }

  @Test
  @DisplayName("a nonsense year -> 400, never a 500")
  void nonsenseYear_rejects() throws Exception {
    for (String year : new String[] {"0", "-1", "99999"}) {
      mockMvc
          .perform(get(ENDPOINT).param("year", year).accept(MediaType.APPLICATION_PDF))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(YEAR_NOT_OPEN));
    }
  }

  private byte[] pdfBytes(int year) throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT).param("year", String.valueOf(year)).accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andReturn();
    return result.getResponse().getContentAsByteArray();
  }

  private String pdfText(int year) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdfBytes(year))) {
      return new PDFTextStripper().getText(document);
    }
  }

  /**
   * The extracted text of the one section naming {@code millTitle}, so an assertion can be scoped
   * to a single mill rather than the whole document.
   *
   * <p>A plain first-match split is enough because the outline anchor now rides the visible
   * heading: each mill title appears exactly ONCE in the extracted text. It previously appeared
   * twice — the hidden anchor field duplicated it — and this method had to take the last match to
   * compensate.
   */
  private static String sectionFor(String text, String millTitle) {
    return java.util.Arrays.stream(text.split("Government of British Columbia"))
        .filter(section -> section.contains(millTitle))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no section found for " + millTitle));
  }
}
