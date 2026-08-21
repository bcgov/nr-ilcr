package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spill-and-read-back acceptance test for the Jasper virtualizer (Story 29.2). {@code
 * ilcr.reporting.virtualizer.max-size=1} forces the fill to keep just ONE page in memory and page
 * all the rest out to the swap file — so a real "all schedules" print (mill 517/2021 has data for
 * all six in-scope schedules) genuinely exercises the riskiest new path: reading virtualized pages
 * BACK off the swap file during export, which runs on the async dispatch thread AFTER the request
 * thread that filled them has returned. The other reporting ITs run at the default max-size (300)
 * and never page out, so this is the only test that would catch a devirtualization regression.
 *
 * <p>The assertion is that the extracted PDF text is still COMPLETE — every section's heading and a
 * seeded body value from each — proving no page was lost or corrupted across the spill/read-back.
 * Security is OFF (isolated from authz).
 */
@DisplayName("POST /api/v1/reports/print — virtualizer spills to swap and reads back (max-size=1)")
@TestPropertySource(
    properties = {"ilcr.security.enabled=false", "ilcr.reporting.virtualizer.max-size=1"})
class ReportVirtualizerSpillIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/print";

  @Test
  @DisplayName(
      "allSchedules print with max-size=1 -> complete PDF (every section survives the spill)")
  void allSchedules_withAggressiveSpill_producesCompletePdf() throws Exception {
    // max-size=1 guarantees pages page out to the swap file mid-fill; a complete PDF proves they
    // are
    // read back correctly on the async export thread.
    String selection =
        """
        {"allSchedules":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(selection)
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    byte[] pdf = result.getResponse().getContentAsByteArray();
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

    String text;
    try (PDDocument document = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(document);
    }
    // Every in-scope section's heading is present despite the aggressive spill...
    assertThat(text).contains("Schedule 5:  Camp and Access Expense");
    assertThat(text).contains("Schedule 6:  Road Management Costs");
    assertThat(text).contains("Schedule 7A:  Bridge Costs");
    assertThat(text).contains("Schedule 7B:  Culvert Costs");
    assertThat(text).contains("Miscellaneous");
    assertThat(text).contains("Schedule 11:  Basic Silviculture");
    // ...and a seeded body value from each, proving real page content survived the swap round-trip.
    assertThat(text).contains("Submitted Camp"); // Schedule 5 camp name (517/2021)
    assertThat(text).contains("03B"); // Schedule 6 supply block (RMR 8303)
    assertThat(text).contains("Harbour Overpass"); // Schedule 7A bridge location (7651)
    assertThat(text).contains("Pipe Arch"); // Schedule 7B culvert type desc (7851 'PA')
    assertThat(text).contains("CTR-517"); // Schedule 9 contractor id (record 9110)
    assertThat(text).contains("Cedar Ridge Reforest"); // Schedule 11 location (V20260816)
  }
}
