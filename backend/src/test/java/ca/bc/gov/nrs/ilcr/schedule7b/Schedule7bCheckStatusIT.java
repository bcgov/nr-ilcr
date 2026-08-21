package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Story 13.2 acceptance — {@code POST /api/v1/schedule7b/check-status} and the type-conditional
 * matrix (AC9, BR-07; slices S15-S20/S24/S26-S28). Runs against the V20260811 seed, which was built
 * so each culvert exercises a different branch:
 *
 * <ul>
 *   <li>7801 Round, complete — passes.
 *   <li>7802 Others, comments present, NO span — passes, because span is conditional on type {@code
 *       R}.
 *   <li>7803 Round, span/length/install missing and rise blank — flags exactly three lines, and
 *       NEVER a rise line.
 *   <li>7851 (mill 517) Pipe Arch with neither span nor comments — passes, proving both conditional
 *       rules are inert for a third type.
 * </ul>
 *
 * <p><strong>Check Status is read-only and deliberately NOT Draft-gated</strong>, which is why the
 * 517 (Submitted) case is expected to run rather than 409. Recorded deviation: legacy DISABLED both
 * Check Status buttons whenever the report was not editable ({@code
 * schedule7B.xhtml:264-265,558-559}, {@code disabled="#{schedule7bMB.disableReportEdits()}"}).
 * Gating the endpoint the same way would break the report-level check — UC-CHK-001 requires calling
 * each schedule's own validation method during a status transition, i.e. precisely when the report
 * is Submitted or Verified, and the epic notes legacy's transition gate always evaluated 7B
 * correctly. So the button-disable is reproduced in the frontend from the document's {@code
 * editable} flag (Story 13.3), and the endpoint stays open to any {@code VIEW_SCHEDULE} holder.
 * Security OFF.
 */
@DisplayName("POST /api/v1/schedule7b/check-status — type-conditional matrix (Story 13.2)")
class Schedule7bCheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7b/check-status";

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("514/2021: only the incomplete culvert is flagged, in the exact legacy field order")
  void flagsOnlyTheIncompleteCulvert() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        // 7801 (rowCounter 1) and 7802 (rowCounter 2) pass; every line below belongs to 7803 (3).
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    "Culvert Report Id : 3 - Culvert Type Round - Span size: Value Required",
                    "Culvert Report Id: 3 - Length : Value Required",
                    "Culvert Report Id: 3 - Install Cost : Value Required")))
        .andExpect(jsonPath("$.errors[*].key", everyItem(is("missingRequiredFieldMsg"))))
        .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist());
  }

  @Test
  @DisplayName("S28: rise is NEVER flagged — 7803 has a blank rise and no rise line appears")
  void riseIsNeverFlagged() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors[*].text", everyItem(not(Matchers.containsString("Rise")))))
        .andExpect(jsonPath("$.errors[*].text", everyItem(not(Matchers.containsString("rise")))));
  }

  @Test
  @DisplayName("S26: 7802 is Others with NO span and still passes — span is conditional on type R")
  void nonRoundWithoutSpanPasses() throws Exception {
    // 7802 (rowCounter 2) is type 'O' with no span at all. Asserting the FULL error list rather
    // than
    // the absence of an "Id : 2 -" prefix: that prefix spelling is produced ONLY by the two
    // type-conditional labels, so an absence assertion on it could not fail even if a regression
    // started flagging every culvert's length or costs.
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    "Culvert Report Id : 3 - Culvert Type Round - Span size: Value Required",
                    "Culvert Report Id: 3 - Length : Value Required",
                    "Culvert Report Id: 3 - Install Cost : Value Required")))
        .andExpect(jsonPath("$.errors[*].text", everyItem(not(Matchers.containsString(" 2 -")))));
  }

  @Test
  @DisplayName("S27: 7803 is Round with NULL comments and raises NO comments line")
  void nonOthersWithoutCommentsPasses() throws Exception {
    // Retargeted from 7801, which HAS comments — so the old assertion held whether or not the
    // comments
    // check was type-conditional, and could not fail. 7803 is type 'R' with COMMENTS NULL, so it is
    // directly observable: deleting the TYPE_OTHERS guard adds a Comments line for rowCounter 3.
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.errors[*].text", everyItem(not(Matchers.containsString("Comments")))))
        .andExpect(jsonPath("$.errors.length()", is(3)));
  }

  @Test
  @DisplayName("S26+S27: 517/7851 is Pipe Arch with neither span nor comments and passes all-met")
  void thirdTypeWithNeitherConditionalValuePasses() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", is(empty())))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")))
        .andExpect(
            jsonPath(
                "$.requirementsMetMessage.text",
                is("All requirements for this schedule have been met")));
  }

  @Test
  @DisplayName("Check Status is not Draft-gated — it runs for a Submitted report (517/S)")
  void checkStatusIsNotDraftGated() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "517")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("An empty schedule reports all-met (nothing is missing when nothing is reported)")
  void emptyScheduleIsAllMet() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "515")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", is(empty())));
  }

  @Test
  @DisplayName("Check Status mutates NOTHING — the culverts and their costs are untouched")
  void checkStatusMutatesNothing() throws Exception {
    String before = snapshotOf514();

    mockMvc
        .perform(post(ENDPOINT).param("millId", "514").param("year", "2021"))
        .andExpect(status().isOk());

    Assertions.assertThat(snapshotOf514()).isEqualTo(before);
  }

  /** A stable text snapshot of 514/2021's culverts + costs, for the no-mutation assertion. */
  private String snapshotOf514() {
    return jdbc.queryForList(
            "SELECT c.CULVERT_REPORT_ID, c.ILCR_CULVERT_TYPE_CODE, c.SPAN_SIZE, c.RISE_SIZE, "
                + "c.LENGTH, c.CULVERT_PIECE_COUNT, c.COMMENTS, c.REVISION_COUNT, "
                + "d.ILCR_REPORT_COST_ITEM_ID, d.COST "
                + "FROM THE.CULVERT_REPORT c "
                + "LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d "
                + "  ON d.CULVERT_REPORT_ID = c.CULVERT_REPORT_ID "
                + "WHERE c.ILCR_MILL_ID = 514 AND c.REPORT_YEAR = 2021 "
                + "  AND c.ILCR_CATEGORY_ID = '7' "
                + "ORDER BY c.CULVERT_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID")
        .toString();
  }

  @Test
  @DisplayName("S24: multiple gaps on one culvert compose into multiple lines for that culvert")
  void multipleGapsComposeForOneCulvert() throws Exception {
    // 7803 alone contributes three lines (span, length, install cost) — the composition case. Tied
    // to
    // rowCounter 3 explicitly: a bare count of 3 would also pass if the three lines belonged to
    // three
    // DIFFERENT culverts, which is a different (and broken) behaviour.
    mockMvc
        .perform(
            post(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors.length()", is(3)))
        .andExpect(jsonPath("$.errors[*].text", everyItem(Matchers.containsString(" 3 -"))));
  }
}
