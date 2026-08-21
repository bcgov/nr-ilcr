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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 9.2 acceptance — {@code POST /api/v1/schedule9/check-status} (AC7; slice S09). Reproduces
 * {@code Schedule9CheckStatus.validateSchedule}: eight fields per record, the 1-based row number in
 * the title, the preserved Save-vs-Check asymmetry (blank units/cost and a side slope of exactly
 * 100 SAVE but are flagged here), and the base validator's {@code invalidRangeErrorMsg} for a range
 * failure. Read-only — it mutates nothing.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule9/check-status — Schedule 9 Check Status (Story 9.2)")
class Schedule9CheckStatusIT extends AbstractOracleIT {

  private static final String CHECK_STATUS = "/api/v1/schedule9/check-status";

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  @Test
  @DisplayName("all records satisfied -> requirementsMet, the SUC-002 banner, no errors")
  void allMet() throws Exception {
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "702").param("year", "2021"))
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
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "704").param("year", "2021"))
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
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "703").param("year", "2021"))
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

    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "703").param("year", "2021"))
        .andExpect(status().isOk());

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
}
