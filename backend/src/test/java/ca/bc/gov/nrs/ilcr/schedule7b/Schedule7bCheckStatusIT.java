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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries every culvert row. The original
 * cases post a body that MIRRORS the stored seed, so their verdicts are unchanged; the {@code #359}
 * cases post a body that DISAGREES with Oracle and prove the body wins.
 */
@DisplayName("POST /api/v1/schedule7b/check-status — type-conditional matrix (Story 13.2)")
class Schedule7bCheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7b/check-status";

  /** One on-screen culvert row as JSON; a null renders as JSON null (a blank field), never 0. */
  private static String culvert(
      String type,
      Integer span,
      String length,
      Integer pieces,
      Integer material,
      Integer install,
      String comments) {
    return "{\"culvertTypeCode\":"
        + quoted(type)
        + ",\"spanSize\":"
        + span
        + ",\"length\":"
        + length
        + ",\"culvertPieceCount\":"
        + pieces
        + ",\"materialCost\":"
        + material
        + ",\"installCost\":"
        + install
        + ",\"comments\":"
        + quoted(comments)
        + "}";
  }

  private static String quoted(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static String body(String... culverts) {
    return "{\"culverts\":[" + String.join(",", culverts) + "]}";
  }

  private static final String ROW_7801 =
      culvert("R", 1200, "12.5", 3, 4000, 1500, "Main haul road");
  private static final String ROW_7802 =
      culvert("O", null, "8.0", 2, 2500, 700, "Custom box culvert, fabricated on site");
  private static final String ROW_7803 = culvert("R", null, null, 1, 900, null, null);

  /** 514/2021 as served (V20260811): 7801, 7802, 7803 in id order. */
  private static final String BODY_514 = body(ROW_7801, ROW_7802, ROW_7803);

  /** 517/2021 as served: 7851, Pipe Arch with neither span nor comments. */
  private static final String BODY_517 = body(culvert("PA", null, "6.5", 4, 1800, 300, null));

  /** 515/2021 stores no culverts. */
  private static final String BODY_515 = body();

  private static MockHttpServletRequestBuilder check(String millId, String json) {
    return post(ENDPOINT)
        .param("millId", millId)
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("514/2021: only the incomplete culvert is flagged, in the exact legacy field order")
  void flagsOnlyTheIncompleteCulvert() throws Exception {
    mockMvc
        .perform(check("514", BODY_514))
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
        .perform(check("514", BODY_514))
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
        .perform(check("514", BODY_514))
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
        .perform(check("514", BODY_514))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.errors[*].text", everyItem(not(Matchers.containsString("Comments")))))
        .andExpect(jsonPath("$.errors.length()", is(3)));
  }

  @Test
  @DisplayName("S26+S27: 517/7851 is Pipe Arch with neither span nor comments and passes all-met")
  void thirdTypeWithNeitherConditionalValuePasses() throws Exception {
    mockMvc
        .perform(check("517", BODY_517))
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
    mockMvc.perform(check("517", BODY_517)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("An empty schedule reports all-met (nothing is missing when nothing is reported)")
  void emptyScheduleIsAllMet() throws Exception {
    mockMvc
        .perform(check("515", BODY_515))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", is(empty())));
  }

  @Test
  @DisplayName("Check Status mutates NOTHING — the culverts and their costs are untouched")
  void checkStatusMutatesNothing() throws Exception {
    String before = snapshotOf514();

    mockMvc.perform(check("514", BODY_514)).andExpect(status().isOk());

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
        .perform(check("514", BODY_514))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors.length()", is(3)))
        .andExpect(jsonPath("$.errors[*].text", everyItem(Matchers.containsString(" 3 -"))));
  }

  @Test
  @DisplayName("#359 unsaved clear: 7801's length emptied on screen -> flagged, stored ignored")
  void unsavedClear_bodyWinsOverStoredValue() throws Exception {
    mockMvc
        .perform(
            check(
                "514",
                body(
                    culvert("R", 1200, null, 3, 4000, 1500, "Main haul road"), ROW_7802, ROW_7803)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    "Culvert Report Id: 1 - Length : Value Required",
                    "Culvert Report Id : 3 - Culvert Type Round - Span size: Value Required",
                    "Culvert Report Id: 3 - Length : Value Required",
                    "Culvert Report Id: 3 - Install Cost : Value Required")));
  }

  @Test
  @DisplayName("#359 unsaved fix + type switch: 7803 fixed, 7802 switched to Round with no span")
  void unsavedFixAndTypeSwitch_bodyWins() throws Exception {
    mockMvc
        .perform(
            check(
                "514",
                body(
                    ROW_7801,
                    culvert("R", null, "8.0", 2, 2500, 700, "Custom box culvert"),
                    culvert("R", 600, "4.0", 1, 900, 0, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    "Culvert Report Id : 2 - Culvert Type Round - Span size: Value Required")));
  }

  @Test
  @DisplayName("#359 a disagreeing body that passes -> MET, and nothing is persisted")
  void disagreeingBody_metAndPersistsNothing() throws Exception {
    String before = snapshotOf514();

    mockMvc
        .perform(
            check("514", body(ROW_7801, ROW_7802, culvert("R", 600, "4.0", 1, 900, 250, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")));

    Assertions.assertThat(snapshotOf514()).isEqualTo(before);
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "514").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 a body without its culverts list, or with a null row, is a clean 400")
  void checkStatusRequiresTheCulvertsList() throws Exception {
    mockMvc.perform(check("514", "{}")).andExpect(status().isBadRequest());
    mockMvc.perform(check("514", "{\"culverts\":[null]}")).andExpect(status().isBadRequest());
  }
}
