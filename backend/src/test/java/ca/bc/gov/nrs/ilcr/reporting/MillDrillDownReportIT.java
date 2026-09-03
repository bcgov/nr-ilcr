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
 * Acceptance test — the per-mill drill-down PDF (UC-MRPT-002 S02 / UC-MRPT-004 S02). GET
 * /api/v1/reports/mill-information/{millId}?year= renders ONE mill's Mill Information section into
 * {@code mill_<millNumber>_print.pdf}, with no working-context guard and no closed-mill rejection.
 *
 * <p>Seed is R__40, the same fixtures {@link MillInformationReportIT} renders as one document:
 * mills 730 (fully dated), 731 (prefix-only milestones), 732 (all four milestone columns NULL) and
 * 733 (opened plus prefix-only, ACT in the year but CLS today), all with 2021 report-status rows
 * alongside the pre-existing mill 514.
 *
 * <p>Mills 732 and 733 are the point of this suite as much as 730 is. Legacy crashed on exactly
 * those shapes — {@code MillReportStatusReport.java:96-99} called {@code .substring(2)} on all four
 * milestone strings with no null guard, so drilling into a mill still at Opened/Draft threw before
 * a byte was written. Here the strip null-guards, so the crash is unreachable by construction;
 * 19.1's tests never proved it because they render mill 730, which is fully dated.
 *
 * <p>Security is pinned OFF so these isolate rendering from authorization, which {@link
 * MillDrillDownAuthorizationIT} covers.
 */
@DisplayName("GET /api/v1/reports/mill-information/{millId} — per-mill drill-down PDF")
// Security OFF isolates rendering from authorization. The mock principal defaults to
// ILCR_SUBMITTER, which this ADMIN-only endpoint would 403, so the mock role is raised to
// ILCR_ADMIN — these tests exercise the report, not the gate that owns it.
@TestPropertySource(
    properties = {"ilcr.security.enabled=false", "ilcr.security.mock-role=ILCR_ADMIN"})
class MillDrillDownReportIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/mill-information/{millId}";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String YEAR_REQUIRED = "Report Year: Value is required.";
  private static final String YEAR_NOT_OPEN = "Report Year is not an open reporting period.";
  private static final String MILL_NOT_FOUND =
      "The selected mill has no report status for the selected Report Year.";

  @Test
  @DisplayName("mill 730, 2021 -> 200 application/pdf, %PDF body, mill_7300_print.pdf attachment")
  void drillDownReturnsPdfWithTheParityFilename() throws Exception {
    // Mill ID 730 carries mill NUMBER 7300. Legacy named the file from the number
    // (PrintSchedulesMB.java:332), so the two differing here is what makes this assertion able to
    // catch a filename built from the path variable.
    MvcResult result =
        streamPdf(get(ENDPOINT, 730).param("year", "2021").accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mill_7300_print.pdf\""))
            .andReturn();

    assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
  }

  @Test
  @DisplayName("the PDF carries the clicked mill's section and NO other mill's")
  void pdfCarriesOnlyTheRequestedMill() throws Exception {
    String text = pdfText(731, 2021);

    assertThat(text).contains("MILL INFO SPARSE - 7310");
    // "Schedule Status" appears exactly once per rendered section, so this is a section COUNT: the
    // all-mills report of the same year renders five. A predicate that silently stopped binding
    // would show up as five sections here rather than as a passing test.
    assertThat(text.split("Schedule Status", -1)).hasSize(2);
    // And by name, so a section for another mill cannot hide inside a miscount.
    assertThat(text)
        .doesNotContain("AAA Milling")
        .doesNotContain("MILL INFO FULL")
        .doesNotContain("MILL INFO NO CLIENT")
        .doesNotContain("MILL INFO CLOSED SINCE");
  }

  @Test
  @DisplayName("the drilled section carries the same content the all-mills report renders for it")
  void sectionContentMatchesTheAllMillsReport() throws Exception {
    // The story's parity requirement: one projection, one template, one mapper. Mill 730 is the
    // fully-populated fixture, so every field the section has is exercised here — address, zone
    // description, ownership name, the formatted phone and both dated milestones — and these are
    // the SAME assertions MillInformationReportIT makes against the all-mills document.
    String text = pdfText(730, 2021);

    assertThat(text)
        .contains("100 MAIN STREET")
        .contains("CRANBROOK")
        .contains("V1C1A1")
        .contains("Kootenay Selling Price Zone")
        .contains("FULL OWNERSHIP HOLDINGS LTD")
        .contains("(250) 555-1212")
        .contains("2021-01-05")
        .contains("2021-07-01")
        // Section chrome survives, so the drill-down is the same document type, not a stripped one.
        .contains("Government of British Columbia")
        .contains("2021 Annual Interior Logging Cost Report")
        .contains("Mill Information")
        .contains("Contacts");
  }

  @Test
  @DisplayName("a mill with all four milestone columns NULL still returns a PDF (the S08 fix)")
  void openedOrDraftOnlyMillReturnsPdfWithBlankMilestones() throws Exception {
    // Mill 732: MILL_STATUS_OPEN/DRAFT/SUBMIT/VERIFY_DATE are all seeded NULL. This is the exact
    // shape legacy's unguarded .substring(2) threw on, and the reason this test exists is that the
    // fix is invisible — it lives in LegacyDateText.stripPrefix's null guard, which no 19.1 test
    // reaches because mill 730 is fully dated.
    streamPdf(get(ENDPOINT, 732).param("year", "2021").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"mill_7320_print.pdf\""));

    String text = pdfText(732, 2021);
    assertThat(text)
        .contains("MILL INFO NO CLIENT - 7320")
        .contains("Schedule Status")
        // Blank, not the word "null" and not the "-" the address/contact fields fall back to:
        // legacy's null sweep left milestone dates empty, and this PDF has no legend to decode a
        // raw prefix.
        .doesNotContain("null")
        .doesNotContain("Draft: -")
        .doesNotContain("Submitted: -")
        .doesNotContain("Verified: -");
  }

  @Test
  @DisplayName("mill 731's prefix-only milestones render BLANK — not the prefix, not a dash")
  void prefixOnlyMilestonesRenderBlank() throws Exception {
    // Mill 731's draft/submit/verify are seeded "D: " / "S: " / "V: " — the shape 80 of the 118
    // delivery rows carry. The prefix must never reach the page, and legacy left these EMPTY rather
    // than substituting "-" (its null sweep mapped absent to ""), so a dash here is a parity break.
    //
    // This PDF differs from the 19.2 status TABLE deliberately: that table renders the raw prefixed
    // strings BECAUSE it carries the O/D/S/V legend to decode them. This document has no legend, so
    // it must strip, exactly as the all-mills report does.
    String text = pdfText(731, 2021);

    assertThat(text)
        .contains("MILL INFO SPARSE - 7310")
        .contains("2021-01-05")
        .doesNotContain("D: ")
        .doesNotContain("S: ")
        .doesNotContain("V: ")
        .doesNotContain("null")
        .doesNotContain("Draft: -")
        .doesNotContain("Submitted: -")
        .doesNotContain("Verified: -");
  }

  @Test
  @DisplayName("mill 733 — opened, everything else prefix-only — also returns a PDF")
  void openedWithPrefixOnlyMilestonesReturnsPdf() throws Exception {
    // The other half of the S08 evidence, and the more common delivery shape: the mill HAS reached
    // Opened, and the remaining three columns hold "D: " / "S: " / "V: " with no date. 80 of the
    // 118
    // real rows look like this.
    String text = pdfText(733, 2021);

    assertThat(text)
        .contains("MILL INFO CLOSED SINCE - 7330")
        .contains("2021-01-05")
        .doesNotContain("D: ")
        .doesNotContain("S: ")
        .doesNotContain("V: ")
        // ACT for 2021 in the report view but CLS on the xref: the per-year value is what prints,
        // and a reprint of 2021 must not be rewritten by the mill's closure since.
        .contains("Active:")
        .contains("Yes");
  }

  @Test
  @DisplayName("a CLOSED-in-the-year mill is still drillable — no active-mill guard runs")
  void closedMillIsStillDrillable() throws Exception {
    // Mill 732 is CLS for 2021 itself. The status table lists closed mills (its Active column is
    // what says so), so they must stay drillable; MillContextService.validateMillYearActive would
    // reject this with a 409, which is why neither of its overloads is called on this path.
    String text = pdfText(732, 2021);

    assertThat(text).contains("MILL INFO NO CLIENT - 7320").contains("Active:").contains("No");
  }

  @Test
  @DisplayName("an existing mill with no row for the OPEN year -> 404 and no file")
  void millWithNoRowForTheYear_rejects() throws Exception {
    // 2020 is an opened reporting period (V8) whose only report-view row is mill 514, so mill 730
    // genuinely exists and genuinely has nothing for that year — the real shape of this rejection,
    // not a made-up id. The year guard therefore passes and the READ is what answers 404.
    mockMvc
        .perform(get(ENDPOINT, 730).param("year", "2020").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(MILL_NOT_FOUND));
  }

  @Test
  @DisplayName("an unknown mill id -> the same 404, never an empty PDF")
  void unknownMill_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT, 999_999).param("year", "2021").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value(MILL_NOT_FOUND));
  }

  @Test
  @DisplayName("mill 514 for 2020 DOES resolve — proving the 404 above is about the row, not 2020")
  void theSameYearStillWorksForAMillThatHasARow() throws Exception {
    // Without this, the 404 above would also pass if the endpoint had simply broken for 2020.
    streamPdf(get(ENDPOINT, 514).param("year", "2020").accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));
  }

  @Test
  @DisplayName("no year -> 400 with the verbatim required-field message and no file")
  void missingYear_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT, 730).accept(MediaType.APPLICATION_PDF))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
  }

  @Test
  @DisplayName("a blank or non-numeric year -> 400, the same rejection as an absent one")
  void unusableYear_rejects() throws Exception {
    // The empty string is the literal `?year=` an unset control submits, distinct from the absent
    // parameter above; both collapse to the same rejection, because the legacy control was a
    // dropdown of opened periods and any value that is not a year means no year was chosen.
    for (String year : new String[] {"", "  ", "not-a-year"}) {
      mockMvc
          .perform(get(ENDPOINT, 730).param("year", year).accept(MediaType.APPLICATION_PDF))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
    }
  }

  @Test
  @DisplayName("a year that is not an open reporting period -> 400, not a 404 and not a 500")
  void yearNotOpen_rejects() throws Exception {
    // The guard runs BEFORE the read, so an unopened year is a bad selection rather than a missing
    // mill. Getting these two the wrong way round would tell the administrator the mill is at
    // fault.
    for (String year : new String[] {"1899", "1999", "0", "99999"}) {
      mockMvc
          .perform(get(ENDPOINT, 730).param("year", year).accept(MediaType.APPLICATION_PDF))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(YEAR_NOT_OPEN));
    }
  }

  private String pdfText(long millId, int year) throws Exception {
    MvcResult result =
        streamPdf(
                get(ENDPOINT, millId)
                    .param("year", String.valueOf(year))
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andReturn();
    try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
      return new PDFTextStripper().getText(document);
    }
  }
}
