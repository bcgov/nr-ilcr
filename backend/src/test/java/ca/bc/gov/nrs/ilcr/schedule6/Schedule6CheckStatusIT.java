package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 8.2 acceptance — {@code POST /api/v1/schedule6/check-status} (AC7; slices S09/S11/S20/S21 +
 * the pinned quirks). The composed lines are asserted BYTE-FOR-BYTE, including the legacy cost
 * mislabel ("TSA or TFL (Cost $)" checking Cost) and the space on both sides of the final colon
 * ({@code FacesUtil} composition). The S10 TFL-missing line is unreachable from persisted rows
 * (legacy view-state-only — story Completion Notes); its branch is pinned at the service seam in
 * {@link Schedule6CheckStatusServiceTest}, and the composition path it shares with these lines is
 * proven here.
 *
 * <p>Fixture mill 664 (id order): placeholder 8324 (EXCLUDED, deviation (d) — counters start at 1
 * with 8325), 8325 complete, 8326 missing cost, 8327 missing supply block, 8328 missing both, 8329
 * cost 0 (MET — the D2 quirk). Mill 663 is all-met; 515 zero-records; 660 lone-comment. The
 * endpoint mutates nothing — every test here is read-only by contract, proven by the row-count +
 * every-REVISION_COUNT fingerprint (GET-body equality could miss a column the document doesn't
 * expose — the 25.2 rationale).
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule6/check-status — composed readiness lines (Story 8.2)")
class Schedule6CheckStatusIT extends AbstractOracleIT {

  private static final String CHECK_STATUS = "/api/v1/schedule6/check-status";

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName(
      "S09/S11/S20/S21: mixed results — byte-for-byte lines, per-record met banner, "
          + "ordinals skip the excluded placeholder, cost==0 is MET; and it mutates NOTHING")
  void mixedResults_composedVerbatim_mutatesNothing() throws Exception {
    List<Map<String, Object>> before = stateFingerprint();

    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "664").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        // ISSUES: no schedule-level banner (legacy shows it only on the pass branch).
        .andExpect(jsonPath("$.messages", hasSize(0)))
        // The placeholder 8324 is excluded (deviation (d)): five visible records, 1-based.
        .andExpect(jsonPath("$.records", hasSize(5)))
        .andExpect(jsonPath("$.records[?(@.recordId==8325)].rowCounter", contains(1)))
        // S20: the complete record reports its own met banner on a mixed result.
        .andExpect(jsonPath("$.records[?(@.recordId==8325)].met", contains(true)))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8325)].metMessage.key",
                contains("roadRequirementsMetMsg")))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8325)].metMessage.text",
                contains("All requirements for 1 have been met.")))
        // S09: missing cost — the legacy mislabel, byte-for-byte.
        .andExpect(jsonPath("$.records[?(@.recordId==8326)].issues[0].field", contains("cost")))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8326)].issues[0].message.key",
                contains("missingRequiredFieldMsg")))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8326)].issues[0].message.text",
                contains("Road : 2 - TSA or TFL (Cost $) : Value Required")))
        // S11: missing supply block.
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8327)].issues[0].message.text",
                contains("Road : 3 - Supply Block : Value Required")))
        // S21: one record, BOTH lines, legacy order (supply block before cost).
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8328)].issues[0].message.text",
                contains("Road : 4 - Supply Block : Value Required")))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8328)].issues[1].message.text",
                contains("Road : 4 - TSA or TFL (Cost $) : Value Required")))
        .andExpect(jsonPath("$.records[?(@.recordId==8328)].met", contains(false)))
        // D2 quirk: cost == 0 is PRESENT (null-only check) — the TFL record is met.
        .andExpect(jsonPath("$.records[?(@.recordId==8329)].met", contains(true)))
        .andExpect(
            jsonPath(
                "$.records[?(@.recordId==8329)].metMessage.text",
                contains("All requirements for 5 have been met.")));

    assertEquals(before, stateFingerprint());
  }

  @Test
  @DisplayName(
      "SUC-003: all records complete -> MET, the single schedule banner, NO per-record results")
  void allMet_singleBannerOnly() throws Exception {
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "663").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages", hasSize(1)))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        .andExpect(
            jsonPath("$.messages[0].text", is("All requirements for this schedule have been met")))
        // The legacy pass branch never enters the per-record loop: records is EMPTY.
        .andExpect(jsonPath("$.records", hasSize(0)));
  }

  @Test
  @DisplayName("Zero records -> vacuous MET (the legacy loop never runs)")
  void zeroRecords_vacuousPass() throws Exception {
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "515").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.records", hasSize(0)));
  }

  @Test
  @DisplayName(
      "Lone-comment placeholder -> vacuous MET (deviation (d): the hidden row is not flagged)")
  void loneComment_vacuousPass() throws Exception {
    // Mill 660/2021 (8.1 read fixture, used READ-ONLY): a single placeholder row, no records.
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "660").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        .andExpect(jsonPath("$.records", hasSize(0)));
  }

  @Test
  @DisplayName(
      "VIEW-gated, not Draft-gated: check-status runs on a non-Draft ('S') mill (2.6 precedent)")
  void nonDraftTrack_stillChecks() throws Exception {
    // Mill 662/2021 is 'S' with one complete record — the endpoint must NOT 409.
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("millId", "662").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
  }

  @Test
  @DisplayName("Context guard reused: missing millId -> 400 verbatim ERR-001 (trailing space)")
  void missingContext_returns400Err001() throws Exception {
    mockMvc
        .perform(post(CHECK_STATUS).with(csrf()).param("year", "2021"))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")));
  }

  /**
   * Every mutable Schedule 6 column of mill 664/2021 that a buggy check-status could touch: the
   * per-row revision + classification + comments, the detail rows' values, and BOTH audit pairs on
   * both tables.
   *
   * <p>The audit columns were missing, which contradicted this method's own "every mutable column"
   * claim in the worst way: a check-status that stamped {@code UPDATE_USERID}/{@code
   * UPDATE_TIMESTAMP} — the single most likely way for a read to accidentally mutate, since every
   * write statement in this story sets exactly those — compared equal and passed (code review
   * 2026-08-04). The detail's own {@code REVISION_COUNT} is included for the same reason: {@code
   * updateCostDetail} deliberately never bumps it, so it too was invisible.
   */
  private List<Map<String, Object>> stateFingerprint() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    return jdbc.queryForList(
        """
            SELECT r.ROAD_MAINTENANCE_REPORT_ID, r.TSA_NUMBER, r.TSB_NUMBER_CODE, r.TFL_NUMBER_CODE,
                   r.COMMENTS, r.REVISION_COUNT,
                   r.ENTRY_USERID, r.ENTRY_TIMESTAMP, r.UPDATE_USERID, r.UPDATE_TIMESTAMP,
                   d.ILCR_COST_REPORT_DETAIL_ID, d.VOLUME, d.COST,
                   d.COMMENTS AS DETAIL_COMMENTS, d.REVISION_COUNT AS DETAIL_REVISION,
                   d.ENTRY_USERID AS DETAIL_ENTRY_USERID,
                   d.ENTRY_TIMESTAMP AS DETAIL_ENTRY_TIMESTAMP,
                   d.UPDATE_USERID AS DETAIL_UPDATE_USERID,
                   d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TIMESTAMP
              FROM THE.ROAD_MAINTENANCE_REPORT r
              LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d
                ON d.ROAD_MAINTENANCE_REPORT_ID = r.ROAD_MAINTENANCE_REPORT_ID
             WHERE r.ILCR_MILL_ID = 664 AND r.REPORT_YEAR = 2021 AND r.ILCR_CATEGORY_ID = '6'
             ORDER BY r.ROAD_MAINTENANCE_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
            """);
  }
}
