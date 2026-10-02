package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Acceptance test — Story 14.6. POST /api/v1/schedule8/check-status (all-pages sweep) and POST
 * .../pages/{pageId}/check-status (single page). Read-only (AD-5). Security OFF (mock
 * ILCR_SUBMITTER holds VIEW_SCHEDULE); the POST carries {@code .with(csrf())}. V15 mills 600–603:
 * 600 all-met, 601 issues (page + sample flags + zero-harvested), 602 no-samples, 603
 * single-vs-all.
 *
 * <p>Since #359 (group C) both endpoints take a required body carrying the open panel: the page
 * panel for the all-pages check, the sample panel for the single-page check. The Story 14.6 cases
 * post no panel ({@code null}) and so keep judging the stored record; the #359 cases post a panel
 * that disagrees with Oracle and assert the verdict follows the panel while nothing is persisted.
 */
@DisplayName("POST /api/v1/schedule8/check-status — Check Status (Story 14.6)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8CheckStatusIT extends AbstractOracleIT {

  private static final String ALL = "/api/v1/schedule8/check-status";

  /** No page panel open: the stored verdict (#359). */
  private static final String NO_PAGE_PANEL = "{\"page\":null}";

  /** No sample panel open: the stored verdict (#359). */
  private static final String NO_SAMPLE_PANEL = "{\"sample\":null}";

  @Autowired private JdbcTemplate jdbcTemplate;

  private ResultActions checkAll(int millId) throws Exception {
    return mockMvc.perform(check(ALL, millId, NO_PAGE_PANEL));
  }

  /** A POST to {@code path} for the 2021 schedule of {@code millId}, carrying {@code body}. */
  private static MockHttpServletRequestBuilder check(String path, int millId, String body) {
    return post(path)
        .param("millId", String.valueOf(millId))
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(csrf())
        .accept(MediaType.APPLICATION_JSON);
  }

  private static String pagePath(int pageId) {
    return "/api/v1/schedule8/pages/" + pageId + "/check-status";
  }

  /**
   * Every page and sample row of {@code millId}, with its revision and audit columns and the fields
   * the #359 bodies edit — so "nothing is persisted" is asserted, not assumed.
   */
  private List<String> fingerprint(int millId) {
    List<String> rows =
        new ArrayList<>(
            jdbcTemplate.query(
                "SELECT r.TREE_TO_TRUCK_REPORT_ID || '|' || r.REVISION_COUNT || '|' || r.ENTRY_USERID"
                    + " || '|' || r.ENTRY_TIMESTAMP || '|' || r.UPDATE_USERID || '|' || r.UPDATE_TIMESTAMP"
                    + " || '|' || r.CONTACT_NAME || '|' || r.TSA_NUMBER || '|' || r.TFL_NUMBER_CODE"
                    + " FROM THE.TREE_TO_TRUCK_REPORT r WHERE r.ILCR_MILL_ID = ?"
                    + " ORDER BY r.TREE_TO_TRUCK_REPORT_ID",
                (rs, i) -> rs.getString(1),
                millId));
    rows.addAll(
        jdbcTemplate.query(
            "SELECT d.TREE_TO_TRUCK_DETAIL_REPORT_ID || '|' || d.REVISION_COUNT || '|' || d.ENTRY_USERID"
                + " || '|' || d.ENTRY_TIMESTAMP || '|' || d.UPDATE_USERID || '|' || d.UPDATE_TIMESTAMP"
                + " || '|' || d.CUT_BLOCK || '|' || d.CONIFEROUS_VOLUME"
                + " FROM THE.TREE_TO_TRUCK_DETAIL_REPORT d JOIN THE.TREE_TO_TRUCK_REPORT r"
                + " ON r.TREE_TO_TRUCK_REPORT_ID = d.TREE_TO_TRUCK_REPORT_ID"
                + " WHERE r.ILCR_MILL_ID = ? ORDER BY d.TREE_TO_TRUCK_DETAIL_REPORT_ID",
            (rs, i) -> rs.getString(1),
            millId));
    return rows;
  }

  @Test
  @DisplayName("600 all fields present -> MET (SUC-003 banner)")
  void allMet_returnsMet() throws Exception {
    checkAll(600)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")))
        .andExpect(jsonPath("$.pages[0].met", is(true)))
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(true)));
  }

  @Test
  @DisplayName("check-status mutates nothing (AD-5) — revisions + row counts unchanged")
  void checkStatus_mutatesNothing() throws Exception {
    Integer pageRev =
        jdbcTemplate.queryForObject(
            "SELECT REVISION_COUNT FROM THE.TREE_TO_TRUCK_REPORT WHERE TREE_TO_TRUCK_REPORT_ID = 8970",
            Integer.class);
    int samplesBefore =
        JdbcTestUtils.countRowsInTableWhere(
            jdbcTemplate, "THE.TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_REPORT_ID = 8970");
    checkAll(600).andExpect(status().isOk());
    assertEquals(
        pageRev,
        jdbcTemplate.queryForObject(
            "SELECT REVISION_COUNT FROM THE.TREE_TO_TRUCK_REPORT WHERE TREE_TO_TRUCK_REPORT_ID = 8970",
            Integer.class));
    assertEquals(
        samplesBefore,
        JdbcTestUtils.countRowsInTableWhere(
            jdbcTemplate, "THE.TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_REPORT_ID = 8970"));
  }

  @Test
  @DisplayName("602 page with no samples -> ISSUES with treeToTruckReportAtleastOneSample (S18)")
  void pageWithNoSamples_flagsAtLeastOneSample() throws Exception {
    checkAll(602)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.pages[0].met", is(false)))
        .andExpect(jsonPath("$.pages[0].issues.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].issues[0].field", is("Sample")))
        .andExpect(
            jsonPath(
                "$.pages[0].issues[0].message.text",
                is("Please create a TtT sample data record for this page")));
  }

  @Test
  @DisplayName("601 missing Contact/Phone/Supply Block -> page-level flags (S28)")
  void missingPageFields_flagged() throws Exception {
    checkAll(601)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.pages[0].issues[?(@.field=='Contact')]").isNotEmpty())
        .andExpect(jsonPath("$.pages[0].issues[?(@.field=='Phone')]").isNotEmpty())
        .andExpect(jsonPath("$.pages[0].issues[?(@.field=='Supply Block')]").isNotEmpty());
  }

  @Test
  @DisplayName("601 sample 8973: missing Cut Block/Original Rate, skyline supports, percent != 100")
  void sampleFieldFlags() throws Exception {
    checkAll(601)
        .andExpect(status().isOk())
        // Sample 8973 (index 0 under page 8972) carries the rich sample-level flag set.
        .andExpect(jsonPath("$.pages[0].samples[0].id", is(8973)))
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(false)))
        .andExpect(jsonPath("$.pages[0].samples[0].issues[?(@.field=='Cut Block')]").isNotEmpty())
        .andExpect(
            jsonPath("$.pages[0].samples[0].issues[?(@.field=='Original TtT Rate')]").isNotEmpty())
        .andExpect(
            jsonPath("$.pages[0].samples[0].issues[?(@.field=='Slope Distance')]").isNotEmpty())
        .andExpect(
            jsonPath("$.pages[0].samples[0].issues[?(@.field=='Support Number')]").isNotEmpty())
        .andExpect(
            jsonPath("$.pages[0].samples[0].issues[?(@.field=='Skidding/Yarding')]").isNotEmpty());
  }

  @Test
  @DisplayName("601 sample 8974 zero harvested -> Actual Harvested flag (S29, FLD-007)")
  void zeroHarvested_flagged() throws Exception {
    checkAll(601)
        .andExpect(status().isOk())
        // Sample 8974 (index 1): only the Actual-Harvested>0 flag (all else present, percent 100).
        .andExpect(jsonPath("$.pages[0].samples[1].id", is(8974)))
        .andExpect(jsonPath("$.pages[0].samples[1].issues.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[1].issues[0].field", is("Actual Harvested")))
        .andExpect(
            jsonPath(
                "$.pages[0].samples[1].issues[0].message.text",
                is("Total value must be greater than 0.")));
  }

  @Test
  @DisplayName(
      "603 single-page scope: the all-met page -> MET; the all-pages sweep -> ISSUES (S14)")
  void singlePageScope_vs_allSweep() throws Exception {
    // Single-page check of the all-met page 8976 -> MET (ignores the no-samples page 8978).
    mockMvc
        .perform(check(pagePath(8976), 603, NO_SAMPLE_PANEL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.pages.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].id", is(8976)));
    // All-pages sweep -> ISSUES because page 8978 has no samples.
    checkAll(603).andExpect(jsonPath("$.outcome", is("ISSUES")));
  }

  @Test
  @DisplayName("#359: a bodiless POST is a clean 400 on both endpoints, never a 500")
  void bodilessPostIsBadRequest() throws Exception {
    mockMvc
        .perform(post(ALL).param("millId", "600").param("year", "2021").with(csrf()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(post(pagePath(8970)).param("millId", "600").param("year", "2021").with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 all pages: an unsaved Contact clear on a MET schedule is reported")
  void pagePanelDisagreeingWithOracleWins() throws Exception {
    List<String> before = fingerprint(600);
    String body =
        """
        {"page":{"id":8970,"division":"North Div","contact":null,"phone":"2505551212",
                 "tsaNumber":"TSA6","tflNumber":null,"supplyBlock":"B","cuttingPermit":"cp9"}}
        """;

    mockMvc
        .perform(check(ALL, 600, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        // The page label carries the ON-SCREEN TSA and Cutting Permit, not the stored TSA5 / none.
        .andExpect(jsonPath("$.pages[0].pageLabel", is("Page # 1  -TSA: TSA6 -CP: cp9")))
        .andExpect(jsonPath("$.pages[0].issues.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].issues[0].field", is("Contact")))
        .andExpect(jsonPath("$.pages[0].issues[0].message.text", is("Value Required")))
        // Samples are always the stored ones.
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(true)));

    assertEquals(before, fingerprint(600), "check status must persist nothing");
  }

  @Test
  @DisplayName("#359 all pages: stored-missing Contact/Phone/Supply Block typed on screen clear")
  void pagePanelUnsavedFixClearsTheLines() throws Exception {
    List<String> before = fingerprint(601);
    String body =
        """
        {"page":{"id":8972,"division":"Typed Div","contact":"Pat","phone":"250",
                 "tsaNumber":"TSA5","tflNumber":null,"supplyBlock":"B","cuttingPermit":null}}
        """;

    mockMvc
        .perform(check(ALL, 601, body))
        .andExpect(status().isOk())
        // The samples' stored issues remain; the page's own lines are gone.
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.pages[0].issues.length()", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(false)));

    assertEquals(before, fingerprint(601), "check status must persist nothing");
  }

  @Test
  @DisplayName("#359 all pages: an unsaved TSA-to-TFL switch with TFL # blank reports TFL #")
  void pagePanelSwitchToTflReportsTflNumber() throws Exception {
    String body =
        """
        {"page":{"id":8970,"division":"North Div","contact":"Pat","phone":"2505551212",
                 "tsaNumber":"TFL","tflNumber":null,"supplyBlock":null,"cuttingPermit":null}}
        """;

    mockMvc
        .perform(check(ALL, 600, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].issues.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].issues[0].field", is("TFL #")));
  }

  @Test
  @DisplayName("#359 all pages: a new page (no id) is not evaluated")
  void newPageIsNotEvaluated() throws Exception {
    String body =
        """
        {"page":{"id":null,"division":null,"contact":null,"phone":null,
                 "tsaNumber":null,"tflNumber":null,"supplyBlock":null,"cuttingPermit":null}}
        """;

    mockMvc
        .perform(check(ALL, 600, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.pages.length()", is(1)));
  }

  @Test
  @DisplayName("#359 one page: an unsaved Coniferous clear on a MET page is reported")
  void samplePanelDisagreeingWithOracleWins() throws Exception {
    List<String> before = fingerprint(600);
    String body =
        """
        {"sample":{"id":8971,"contractId":"CMET","cutBlock":"CBMET","groundBasePct":100,
                   "grapplePct":null,"skylinePct":0,"highleadPct":null,"helicopterPct":null,
                   "otherSkiddingPct":null,"skylineSlopeDistance":null,"skylineSupportNumber":null,
                   "supportAvgDistance":null,"coniferousVolume":null,"deciduousVolume":0,
                   "originalRate":20.00}}
        """;

    mockMvc
        .perform(check(pagePath(8970), 600, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        .andExpect(jsonPath("$.pages[0].issues.length()", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[0].id", is(8971)))
        .andExpect(jsonPath("$.pages[0].samples[0].issues.length()", is(2)))
        .andExpect(jsonPath("$.pages[0].samples[0].issues[0].field", is("Coniferous")))
        .andExpect(jsonPath("$.pages[0].samples[0].issues[1].field", is("Actual Harvested")));

    assertEquals(before, fingerprint(600), "check status must persist nothing");
  }

  @Test
  @DisplayName("#359 one page: a stored zero-harvest sample fixed on screen is MET")
  void samplePanelUnsavedFixClearsTheLine() throws Exception {
    List<String> before = fingerprint(601);
    String body =
        """
        {"sample":{"id":8974,"contractId":"CZERO","cutBlock":"CBZ","groundBasePct":100,
                   "grapplePct":null,"skylinePct":0,"highleadPct":null,"helicopterPct":null,
                   "otherSkiddingPct":null,"skylineSlopeDistance":null,"skylineSupportNumber":null,
                   "supportAvgDistance":null,"coniferousVolume":250,"deciduousVolume":0,
                   "originalRate":20.00}}
        """;

    mockMvc
        .perform(check(pagePath(8972), 601, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].samples[1].id", is(8974)))
        .andExpect(jsonPath("$.pages[0].samples[1].met", is(true)))
        // The other sample keeps its stored verdict.
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(false)));

    assertEquals(before, fingerprint(601), "check status must persist nothing");
  }

  @Test
  @DisplayName("#359 one page: a new sample with Cut Block blank is evaluated as the next sample")
  void newSampleIsEvaluatedAsTheNextSample() throws Exception {
    List<String> before = fingerprint(602);
    String body =
        """
        {"sample":{"id":null,"contractId":"CNEW","cutBlock":null,"groundBasePct":60,
                   "grapplePct":40,"skylinePct":null,"highleadPct":null,"helicopterPct":null,
                   "otherSkiddingPct":null,"skylineSlopeDistance":null,"skylineSupportNumber":null,
                   "supportAvgDistance":null,"coniferousVolume":300,"deciduousVolume":0,
                   "originalRate":18.5}}
        """;

    mockMvc
        .perform(check(pagePath(8975), 602, body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        // Page 8975 stores no sample; the new one on screen satisfies "at least one sample".
        .andExpect(jsonPath("$.pages[0].issues.length()", is(0)))
        .andExpect(jsonPath("$.pages[0].samples.length()", is(1)))
        // An unsaved sample has no stored id, so none is reported (null, omitted on the wire).
        .andExpect(jsonPath("$.pages[0].samples[0].id").doesNotExist())
        .andExpect(jsonPath("$.pages[0].samples[0].sampleNumber", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].sampleLabel", is("Sample # 1 - CNEW")))
        .andExpect(jsonPath("$.pages[0].samples[0].issues.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].issues[0].field", is("Cut Block")));

    assertEquals(before, fingerprint(602), "check status must persist nothing");
  }
}
