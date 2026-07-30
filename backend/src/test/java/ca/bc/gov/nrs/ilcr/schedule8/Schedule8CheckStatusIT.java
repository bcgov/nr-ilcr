package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Acceptance test — Story 14.6. POST /api/v1/schedule8/check-status (all-pages sweep) and
 * POST .../pages/{pageId}/check-status (single page). Read-only (AD-5). Security OFF (mock
 * ILCR_SUBMITTER holds VIEW_SCHEDULE); the POST carries {@code .with(csrf())}. V15 mills 600–603:
 * 600 all-met, 601 issues (page + sample flags + zero-harvested), 602 no-samples, 603 single-vs-all.
 */
@DisplayName("POST /api/v1/schedule8/check-status — Check Status (Story 14.6)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8CheckStatusIT extends AbstractOracleIT {

  private static final String ALL = "/api/v1/schedule8/check-status";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private org.springframework.test.web.servlet.ResultActions checkAll(int millId) throws Exception {
    return mockMvc.perform(post(ALL).param("millId", String.valueOf(millId)).param("year", "2021")
        .with(csrf()).accept(MediaType.APPLICATION_JSON));
  }

  @Test
  @DisplayName("600 all fields present -> MET (SUC-003 banner)")
  void allMet_returnsMet() throws Exception {
    checkAll(600)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages[0].text",
            is("All requirements for this schedule have been met")))
        .andExpect(jsonPath("$.pages[0].met", is(true)))
        .andExpect(jsonPath("$.pages[0].samples[0].met", is(true)));
  }

  @Test
  @DisplayName("check-status mutates nothing (AD-5) — revisions + row counts unchanged")
  void checkStatus_mutatesNothing() throws Exception {
    Integer pageRev = jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM THE.TREE_TO_TRUCK_REPORT WHERE TREE_TO_TRUCK_REPORT_ID = 8970",
        Integer.class);
    int samplesBefore = JdbcTestUtils.countRowsInTableWhere(jdbcTemplate,
        "THE.TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_REPORT_ID = 8970");
    checkAll(600).andExpect(status().isOk());
    assertEquals(pageRev, jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM THE.TREE_TO_TRUCK_REPORT WHERE TREE_TO_TRUCK_REPORT_ID = 8970",
        Integer.class));
    assertEquals(samplesBefore, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate,
        "THE.TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_REPORT_ID = 8970"));
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
        .andExpect(jsonPath("$.pages[0].issues[0].message.text",
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
        .andExpect(jsonPath("$.pages[0].samples[0].issues[?(@.field=='Original TtT Rate')]").isNotEmpty())
        .andExpect(jsonPath("$.pages[0].samples[0].issues[?(@.field=='Slope Distance')]").isNotEmpty())
        .andExpect(jsonPath("$.pages[0].samples[0].issues[?(@.field=='Support Number')]").isNotEmpty())
        .andExpect(jsonPath("$.pages[0].samples[0].issues[?(@.field=='Skidding/Yarding')]").isNotEmpty());
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
        .andExpect(jsonPath("$.pages[0].samples[1].issues[0].message.text",
            is("Total value must be greater than 0.")));
  }

  @Test
  @DisplayName("603 single-page scope: the all-met page -> MET; the all-pages sweep -> ISSUES (S14)")
  void singlePageScope_vs_allSweep() throws Exception {
    // Single-page check of the all-met page 8976 -> MET (ignores the no-samples page 8978).
    mockMvc.perform(post("/api/v1/schedule8/pages/8976/check-status")
            .param("millId", "603").param("year", "2021").with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.pages.length()", is(1)))
        .andExpect(jsonPath("$.pages[0].id", is(8976)));
    // All-pages sweep -> ISSUES because page 8978 has no samples.
    checkAll(603).andExpect(jsonPath("$.outcome", is("ISSUES")));
  }
}
