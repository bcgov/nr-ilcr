package ca.bc.gov.nrs.ilcr.schedule9;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Story 9.2 acceptance — {@code POST /api/v1/schedule9/check-status} (AC7; slice S09). Reproduces
 * {@code Schedule9CheckStatus.validateSchedule}: eight fields per record, the 1-based row number in
 * the title, the preserved Save-vs-Check asymmetry (blank units/cost and a side slope of exactly
 * 100 SAVE but are flagged here), and the base validator's {@code invalidRangeErrorMsg} for a range
 * failure. Read-only — it mutates nothing.
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries every row. The original cases post
 * a body that MIRRORS the stored fixture (V20260815), so their verdicts are unchanged; the {@code
 * #359} cases post a body that DISAGREES with Oracle and prove the body wins.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule9/check-status — Schedule 9 Check Status (Story 9.2)")
class Schedule9CheckStatusIT extends AbstractOracleIT {

  private static final String CHECK_STATUS = "/api/v1/schedule9/check-status";

  /** One on-screen row as JSON; a null renders as JSON null (a blank field), never 0. */
  private static String row(
      String contractor,
      Integer item,
      Integer slope,
      String units,
      String unit,
      String bec,
      Integer cost,
      String source) {
    return "{\"contractorId\":"
        + quoted(contractor)
        + ",\"contractualItemCode\":"
        + item
        + ",\"sideSlopePct\":"
        + slope
        + ",\"numberOfUnits\":"
        + units
        + ",\"unitCode\":"
        + quoted(unit)
        + ",\"biogeoclimaticZone\":"
        + quoted(bec)
        + ",\"cost\":"
        + cost
        + ",\"sourceCode\":"
        + quoted(source)
        + "}";
  }

  private static String quoted(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static String body(String... rows) {
    return "{\"records\":[" + String.join(",", rows) + "]}";
  }

  /** 702 as served: 9140 (item 108) and 9141 (item 111, slope 50), both complete. */
  private static final String BODY_702 =
      body(
          row("CTR-OK1", 108, null, "15.0", "M3", "BZ1", 5000, "A"),
          row("CTR-OK2", 111, 50, "25.0", "M3", "BZ1", 6000, "A"));

  /** 703 as served: 9142 (blank units + cost), 9143 (item 112, slope 100), 9144 (no company). */
  private static final String BODY_703 =
      body(
          row("CTR-BLK", 108, null, null, "M3", "BZ1", null, "A"),
          row("CTR-SS", 112, 100, "12.0", "M3", "BZ1", 7000, "A"),
          row(null, 108, null, "18.0", "M3", "BZ1", 9000, "A"));

  private static final String BODY_704 = body();

  private static MockHttpServletRequestBuilder check(String millId, String json) {
    return post(CHECK_STATUS)
        .with(csrf())
        .param("millId", millId)
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  @Test
  @DisplayName("all records satisfied -> requirementsMet, the SUC-002 banner, no errors")
  void allMet() throws Exception {
    mockMvc
        .perform(check("702", BODY_702))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")))
        .andExpect(
            jsonPath(
                "$.requirementsMetMessage.text",
                is("All requirements for this schedule have been met")));
  }

  @Test
  @DisplayName("zero records -> vacuously met (banner, no errors), not a 404")
  void emptyIsVacuouslyMet() throws Exception {
    mockMvc
        .perform(check("704", BODY_704))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")));
  }

  @Test
  @DisplayName(
      "mixed record set -> the per-field lines in record then legacy field order, no banner")
  void issuesComposedVerbatim() throws Exception {
    mockMvc
        .perform(check("703", BODY_703))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        // requirementsMetMessage is omitted (Jackson NON_NULL) when there are issues.
        .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist())
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    // record 9142 (row 1): blank units + blank cost SAVE but are flagged here.
                    "Contractual Work Report Id : 1 Number of Units: Value Required",
                    "Contractual Work Report Id : 1 Cost$: Value Required",
                    // record 9143 (row 2): side slope 100 SAVES (<=100) but Check flags it (>99),
                    // and the
                    // range message is the base validator's invalidRangeErrorMsg, not the save-time
                    // FLD-003.
                    "Contractual Work Report Id : 2 Side Slope %: Entered value must be between 0 and 99.",
                    // record 9144 (row 3): a required-select omission.
                    "Contractual Work Report Id : 3 Company ID: Value Required")));
  }

  @Test
  @DisplayName("check-status mutates nothing")
  void mutatesNothing() throws Exception {
    long before =
        jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE ILCR_MILL_ID = 703",
                Long.class);
    Object slopeBefore =
        jdbc()
            .queryForObject(
                "SELECT SIDE_SLOPE_PCT FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = 9143",
                Integer.class);

    mockMvc.perform(check("703", BODY_703)).andExpect(status().isOk());

    assertEquals(
        before,
        jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE ILCR_MILL_ID = 703",
                Long.class));
    assertEquals(
        slopeBefore,
        jdbc()
            .queryForObject(
                "SELECT SIDE_SLOPE_PCT FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = 9143",
                Integer.class));
  }

  @Test
  @DisplayName("#359 unsaved clear: 702 stores a cost, the screen empties it -> flagged")
  void unsavedClear_bodyWinsOverStoredValue() throws Exception {
    mockMvc
        .perform(
            check(
                "702",
                body(
                    row("CTR-OK1", 108, null, "15.0", "M3", "BZ1", null, "A"),
                    row("CTR-OK2", 111, 50, "25.0", "M3", "BZ1", 6000, "A"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains("Contractual Work Report Id : 1 Cost$: Value Required")));
  }

  @Test
  @DisplayName("#359 item switch: 702 row 1 switched to 111 with side slope blank -> flagged")
  void itemSwitch_bodyItemDrivesSideSlope() throws Exception {
    mockMvc
        .perform(
            check(
                "702",
                body(
                    row("CTR-OK1", 111, null, "15.0", "M3", "BZ1", 5000, "A"),
                    row("CTR-OK2", 111, 50, "25.0", "M3", "BZ1", 6000, "A"))))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains("Contractual Work Report Id : 1 Side Slope %: Value Required")));
  }

  @Test
  @DisplayName("#359 unsaved fix: the screen fixes all three 703 rows -> MET, nothing persisted")
  void unsavedFix_bodyWins_persistsNothing() throws Exception {
    String before = snapshotOf703();

    mockMvc
        .perform(
            check(
                "703",
                body(
                    row("CTR-BLK", 108, null, "1.0", "M3", "BZ1", 0, "A"),
                    row("CTR-SS", 112, 99, "12.0", "M3", "BZ1", 7000, "A"),
                    row("CTR-NEW", 108, null, "18.0", "M3", "BZ1", 9000, "A"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")));

    assertEquals(before, snapshotOf703(), "check-status must not write a row or move a token");
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "703").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 a body without its records list is a clean 400, never a 500")
  void checkStatusRequiresTheRecordsList() throws Exception {
    mockMvc.perform(check("703", "{}")).andExpect(status().isBadRequest());
    mockMvc.perform(check("703", "{\"records\":[null]}")).andExpect(status().isBadRequest());
  }

  /** Every 703 record and cost line, REVISION_COUNT included, as one comparable string. */
  private String snapshotOf703() {
    return jdbc()
        .queryForList(
            "SELECT r.CONTRACTUAL_WORK_REPORT_ID, r.CONTRACTOR_ID, r.SIDE_SLOPE_PCT,"
                + " r.PERFORMED_UNIT, r.REVISION_COUNT, d.ILCR_REPORT_COST_ITEM_ID, d.COST,"
                + " d.REVISION_COUNT AS DETAIL_REVISION"
                + " FROM THE.CONTRACTUAL_WORK_REPORT r"
                + " LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d"
                + "   ON d.CONTRACTUAL_WORK_REPORT_ID = r.CONTRACTUAL_WORK_REPORT_ID"
                + " WHERE r.ILCR_MILL_ID = 703 ORDER BY 1, 6")
        .toString();
  }
}
