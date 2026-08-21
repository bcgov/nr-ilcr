package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acceptance test — the combined Print Schedules PDF (Epic 20.2). POST /api/v1/reports/print
 * assembles the selected in-scope schedules into ONE bookmarked PDF, filled from the primary
 * datasource (Schedule 9) and the schedule {@code *Service} DTOs (5/6/7A/7B/11), on the shared
 * seed: mill 517/2021 carries data for all six in-scope schedules (Schedule 11 added by V20260816).
 * The PDF text is asserted with pdfbox to prove each selected section's heading and a seeded value
 * rendered, and the PDF outline (top-level bookmarks) is asserted to be exactly the rendered
 * schedules' titles in order (BR-08/AC9); skip-empty (BR-09), all-empty (ERR-005), the deferred
 * mill-information-report and the ERR-002/003/004 selection ladder plus the 400/409 context guards
 * are pinned here. Security is OFF (isolated from authz — {@link PrintAuthorizationIT}).
 */
@DisplayName("POST /api/v1/reports/print — combined Print Schedules PDF")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class PrintScheduleIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/print";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String ERR_003 = "Please Select Mill and Reporting Year in the Home Page. ";
  private static final String ERR_004 =
      "This Mill is not active for the current Reporting Year. "
          + "Please select another mill from the Home Page.";
  private static final String ERR_005 = "Schedule not found.";
  private static final String ERR_002_NO_SCHEDULE = "Please select at least one Schedule to print.";
  private static final String ERR_004_NO_OPTION =
      "At least one 'Print Option' is required to print.";
  // ERR-003, verbatim = printOptionsErrorMsg in messages.properties (asserted in full, not a
  // prefix).
  private static final String ERR_003_NO_CONTENT =
      "Schedules are 'checked' for print but neither the print options 'Print Schedule Information' "
          + "or 'Print Comments' are selected. Please select at least one of these print options before "
          + "attempting to print. Alternatively, if you are only wanting to print the 'Mill Information "
          + "Report' ensure all schedules are unchecked before attempting to print.";
  // The deferred Mill Information Report (millInformationReportUnavailableMsg), verbatim.
  private static final String MILL_INFO_UNAVAILABLE =
      "The Mill Information Report is not yet available.";

  // Spy the real factory so the fill still virtualizes normally, but we can prove the render
  // obtained a
  // virtualizer and wired it into the Jasper fill (Story 29.2 — large fills spill to disk, not the
  // heap).
  @MockitoSpyBean private ReportVirtualizerFactory virtualizerFactory;

  private static String body(String json) {
    return json;
  }

  @Test
  @DisplayName("517/2021 select 5,6,7A,7B,9,11 -> 200 application/pdf, %PDF, attachment header")
  void multiSchedule_returnsCombinedPdf() throws Exception {
    // Mill 517/2021 carries data for ALL SIX in-scope schedules (Schedule 11 added by V20260816);
    // editability does not gate printing (517 is Submitted but print is read-only, BR-01).
    String selection =
        """
        {"schedule5":true,"schedule6":true,"schedule7a":true,"schedule7b":true,
         "schedule9":true,"schedule11":true,
         "printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection))
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
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
    // A distinct seeded value from EACH in-scope section, proving real data (not just headings)
    // rendered. These are ungated body values (present regardless of the comment flag).
    assertThat(text).contains("Submitted Camp"); // Schedule 5 camp name (517/2021)
    assertThat(text).contains("03B"); // Schedule 6 supply block (RMR 8303)
    assertThat(text).contains("Harbour Overpass"); // Schedule 7A bridge location (7651)
    assertThat(text).contains("Pipe Arch"); // Schedule 7B culvert type desc (7851 'PA')
    assertThat(text).contains("CTR-517"); // Schedule 9 contractor id (record 9110)
    assertThat(text).contains("Cedar Ridge Reforest"); // Schedule 11 location (V20260816)
    // printComments=true, so a comment-gated value IS present (Schedule 6 detail 8313 comment). The
    // sibling commentsGated test pins the absent-when-false half; this pins the present-when-true
    // half.
    assertThat(text).contains("Bulkley haul road");

    // BR-08/AC9: the PDF's top-level bookmarks are EXACTLY the rendered schedules' titles, in the
    // fixed legacy order. Fails if batch bookmarks or the per-section print name regress.
    assertThat(topLevelBookmarks(pdf))
        .containsExactly(
            ScheduleKey.SCHEDULE_5.bookmarkTitle(),
            ScheduleKey.SCHEDULE_6.bookmarkTitle(),
            ScheduleKey.SCHEDULE_7A.bookmarkTitle(),
            ScheduleKey.SCHEDULE_7B.bookmarkTitle(),
            ScheduleKey.SCHEDULE_9.bookmarkTitle(),
            ScheduleKey.SCHEDULE_11.bookmarkTitle());
  }

  @Test
  @DisplayName("single in-scope schedule -> PDF outline has EXACTLY ONE bookmark (BR-08)")
  void singleSchedule_hasExactlyOneBookmark() throws Exception {
    // BR-08 holds for a single-schedule print too: a lone selected schedule (Schedule 6, which
    // 517/2021
    // has data for) must still carry its one top-level bookmark, not an empty outline. Guards the
    // caller-gated batch-bookmark decision against the earlier "sectionCount > 1" regression.
    String selection =
        """
        {"schedule6":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    assertThat(topLevelBookmarks(result.getResponse().getContentAsByteArray()))
        .containsExactly(ScheduleKey.SCHEDULE_6.bookmarkTitle());
  }

  @Test
  @DisplayName("comments-only (info=false, comments=true) -> row identifier still prints")
  void commentsOnly_keepsRowIdentifier() throws Exception {
    // With p_do_print_body off, the body columns are suppressed but the primary row identifier is
    // not
    // (it renders regardless, like Schedule 5's header) so a comments-only render still names each
    // row.
    // 517/2021 Schedule 7B has one culvert whose type identifier resolves to "Pipe Arch".
    String selection =
        """
        {"schedule7b":true,"printScheduleInformation":false,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    String text = extractText(result.getResponse().getContentAsByteArray());
    // The row identifier (culvert Type) is present even though the schedule-information body is
    // off.
    assertThat(text).contains("Pipe Arch");
  }

  @Test
  @DisplayName("printComments=false hides the comments -> body still prints, no comment text")
  void commentsGated() throws Exception {
    String selection =
        """
        {"schedule9":true,"printScheduleInformation":true,"printComments":false}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "514")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
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
    String selection =
        """
        {"schedule5":true,"schedule11":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "514")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
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
    String selection =
        """
        {"schedule5":true,"printScheduleInformation":true,"printComments":true}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "515")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_005));
  }

  @Test
  @DisplayName("ERR-002: content option on, no schedule selected -> 400 verbatim")
  void err002_noScheduleSelected() throws Exception {
    String selection =
        """
        {"printScheduleInformation":true,"printComments":false}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_002_NO_SCHEDULE));
  }

  @Test
  @DisplayName("ERR-003: a schedule selected but neither content option -> 400 verbatim")
  void err003_noContentOption() throws Exception {
    String selection =
        """
        {"schedule5":true,"printScheduleInformation":false,"printComments":false}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_003_NO_CONTENT));
  }

  @Test
  @DisplayName("ERR-004: no print option at all -> 400 verbatim")
  void err004_noPrintOption() throws Exception {
    String selection =
        """
        {"printScheduleInformation":false,"printComments":false,"printMillInformationReport":false}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_004_NO_OPTION));
  }

  @Test
  @DisplayName("no mill/year context -> 400 verbatim ERR-003 (trailing space), no render")
  void noContext_returns400() throws Exception {
    String selection =
        """
        {"schedule9":true,"printScheduleInformation":true}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_003));
  }

  @Test
  @DisplayName("closed mill (516, CLS) -> 409 verbatim ERR-004, no render")
  void inactiveMill_returns409() throws Exception {
    String selection =
        """
        {"schedule9":true,"printScheduleInformation":true}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "516")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_004));
  }

  @Test
  @DisplayName("allSchedules=true -> 200 combined PDF with every in-scope section (BR-07)")
  void allSchedules_rendersEveryInScopeSection() throws Exception {
    // BR-07: "all" expands to every schedule; only the six in-scope ones render. Mill 517/2021 has
    // data in all six, so the combined PDF must carry all six section headings and bookmarks.
    String selection =
        """
        {"allSchedules":true,"printScheduleInformation":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "517")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    byte[] pdf = result.getResponse().getContentAsByteArray();
    String text = extractText(pdf);
    assertThat(text).contains("Schedule 5:  Camp and Access Expense");
    assertThat(text).contains("Schedule 6:  Road Management Costs");
    assertThat(text).contains("Schedule 7A:  Bridge Costs");
    assertThat(text).contains("Schedule 7B:  Culvert Costs");
    assertThat(text).contains("Miscellaneous");
    assertThat(text).contains("Schedule 11:  Basic Silviculture");
    assertThat(topLevelBookmarks(pdf))
        .containsExactly(
            ScheduleKey.SCHEDULE_5.bookmarkTitle(),
            ScheduleKey.SCHEDULE_6.bookmarkTitle(),
            ScheduleKey.SCHEDULE_7A.bookmarkTitle(),
            ScheduleKey.SCHEDULE_7B.bookmarkTitle(),
            ScheduleKey.SCHEDULE_9.bookmarkTitle(),
            ScheduleKey.SCHEDULE_11.bookmarkTitle());
  }

  @Test
  @DisplayName("only an unimplemented schedule (8) -> 404 'Schedule not found.'")
  void onlyUnimplementedSchedule_returns404() throws Exception {
    // Schedule 8 is accepted but has no enum constant (not rendered in 20.2), so it produces no
    // section — leaving nothing selected in scope, which is all-empty (ERR-005), not
    // mill-info-only.
    String selection =
        """
        {"schedule8":true,"printScheduleInformation":true}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_005));
  }

  @Test
  @DisplayName(
      "mill-info-report only -> 404 'not yet available' (distinct from 'Schedule not found.')")
  void millInformationReportOnly_returnsDistinct404() throws Exception {
    // The ONLY requested content is the deferred Mill Information report: no schedule, no content
    // option. This must return the honest "not yet available" message, NOT the misleading ERR-005.
    String selection =
        """
        {"printMillInformationReport":true,"printScheduleInformation":false,"printComments":false}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(MILL_INFO_UNAVAILABLE));
  }

  @Test
  @DisplayName(
      "combined fill uses the swap-file virtualizer (Story 29.2 — spilled, not heap-pinned)")
  void combinedFill_usesVirtualizer() throws Exception {
    // BR-07 "all" → the biggest fill in scope. The render must obtain a virtualizer from the
    // factory
    // and pass it as the Jasper fill parameter, so page objects spill to disk under a large fill.
    String selection =
        """
        {"allSchedules":true,"printScheduleInformation":true}
        """;
    streamPdf(
            post(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF));

    verify(virtualizerFactory, atLeastOnce()).create();
  }

  @Test
  @DisplayName("Schedule 10 (Story 20.4): 710/2021 renders the section + exactly one bookmark")
  void schedule10_rendersWithOneBookmark() throws Exception {
    // Mill 710/2021 carries the rich Schedule 10 fixture (V20260817): 2 construction pages with
    // road
    // details, region RNI resolved to "Northern Interior". Selecting Schedule 10 alone must render
    // its section (heading + real seeded values) and carry exactly its one top-level bookmark
    // (BR-08).
    String selection =
        """
        {"schedule10":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "710")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection))
                    .accept(MediaType.APPLICATION_PDF))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    byte[] pdf = result.getResponse().getContentAsByteArray();
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

    String text = extractText(pdf);
    assertThat(text).contains("Schedule 10:  New Road Construction Costs");
    assertThat(text).contains("Sch10 Rich Construction"); // mill title block (name-number)
    assertThat(text).contains("North Division"); // page 8900 division
    assertThat(text).contains("Northern Interior"); // region RNI resolved code -> label
    assertThat(text).contains("Mainline A"); // road name (detail 8910)

    // BR-08: a single-schedule print still carries exactly one top-level bookmark.
    assertThat(topLevelBookmarks(pdf)).containsExactly(ScheduleKey.SCHEDULE_10.bookmarkTitle());
  }

  @Test
  @DisplayName("skip-empty (BR-09): 514/2021 select 5+10, 10 has no data -> 5 prints, 10 omitted")
  void schedule10_skipEmpty_keepsTheRest() throws Exception {
    // Mill 514/2021 has Schedule 5 camps but NO Schedule 10 construction pages (the Schedule 10
    // fixtures are mills 710-716), so selecting both must print Schedule 5 and silently omit
    // Schedule 10 (BR-09) — leaving exactly the Schedule 5 bookmark.
    String selection =
        """
        {"schedule5":true,"schedule10":true,"printScheduleInformation":true,"printComments":true}
        """;
    MvcResult result =
        streamPdf(
                post(ENDPOINT)
                    .param("millId", "514")
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(selection)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andReturn();

    byte[] pdf = result.getResponse().getContentAsByteArray();
    String text = extractText(pdf);
    assertThat(text).contains("Schedule 5:  Camp and Access Expense");
    assertThat(text).contains("Cedar Flats Camp");
    assertThat(text).doesNotContain("Schedule 10:  New Road Construction Costs");
    assertThat(topLevelBookmarks(pdf)).containsExactly(ScheduleKey.SCHEDULE_5.bookmarkTitle());
  }

  @Test
  @DisplayName("all-empty (ERR-005): 715/2021 select 10, no pages -> 404 'Schedule not found.'")
  void schedule10Only_noData_returns404() throws Exception {
    // Mill 715/2021 is a valid active context with ZERO Schedule 10 pages; a Schedule-10-only print
    // is then all-empty, the legacy single-schedule outcome (ERR-005), not a blank PDF.
    String selection =
        """
        {"schedule10":true,"printScheduleInformation":true,"printComments":true}
        """;
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "715")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(selection)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(ERR_005));
  }

  private static String extractText(byte[] pdf) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    }
  }

  /**
   * The ordered top-level bookmark (outline) titles of the PDF, or an empty list when it has none.
   */
  private static List<String> topLevelBookmarks(byte[] pdf) throws Exception {
    try (PDDocument document = Loader.loadPDF(pdf)) {
      PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
      List<String> titles = new ArrayList<>();
      if (outline != null) {
        for (PDOutlineItem item : outline.children()) {
          titles.add(item.getTitle());
        }
      }
      return titles;
    }
  }
}
