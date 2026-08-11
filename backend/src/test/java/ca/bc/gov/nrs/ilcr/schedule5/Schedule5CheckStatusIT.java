package ca.bc.gov.nrs.ilcr.schedule5;

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
 * Story 7.2 acceptance — {@code POST /api/v1/schedule5/check-status} (AC9; slices S06/S20; BR-08).
 *
 * <p>Security is pinned OFF explicitly (not relied on by default — that cost Story 8.1 a review patch)
 * so the mock {@code ILCR_SUBMITTER} applies; authorization is {@link Schedule5WriteAuthorizationIT}'s.
 *
 * <p>Every expected line below is composed by hand from the legacy source
 * ({@code Schedule5MB.addMessageCheckStatus} :337-339 → {@code FacesUtil} :131-139) rather than copied
 * from a run — and note the shape: {@code "Camp Report Name : {name} - {label}: Value Required"}, with
 * NO space before the final colon. Schedule 6 renders a space on both sides. All eight segments are
 * additionally pinned in {@link Schedule5CheckStatusCompositionTest}, which can reach the two this
 * fixture cannot.
 *
 * <p>Mills 672/673/674 are READ-ONLY by contract — the endpoint mutates nothing — so they share one
 * year safely. The final test proves that claim with a per-row fingerprint rather than asserting it.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule5/check-status — Schedule 5 readiness (AC9)")
class Schedule5CheckStatusIT extends AbstractOracleIT {

  private static final String CHECK_STATUS = "/api/v1/schedule5/check-status";

  @Autowired
  private DataSource dataSource;

  @Test
  @DisplayName("672/2021 all met -> MET, the schedule banner ALONE, and camps: [] (deviation (C))")
  void allMet_emitsBannerAloneWithNoPerCampResults() throws Exception {
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "672").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages", hasSize(1)))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        // Verbatim legacy :38 — and note NO trailing period, unlike campRequirementsMetMsg.
        .andExpect(jsonPath("$.messages[0].text",
            is("All requirements for this schedule have been met")))
        // THE deviation: both the epics AC and UC-SCH5-001-detailed.md:151 describe an all-met PAIR
        // (banner + per-camp SUC-005). Legacy's pass branch returns before the per-camp loop is ever
        // entered (Schedule5MB.java:324-326), so there are NO per-camp results. Mill 672's two camps
        // both pass — including their complete item-62/68 rows — and neither appears here.
        .andExpect(jsonPath("$.camps", hasSize(0)));
  }

  @Test
  @DisplayName("674/2021 with ZERO camps -> vacuously MET, not ISSUES and not 404")
  void zeroCamps_isVacuouslyMet() throws Exception {
    // isSchedule5Valid ANDs over the camps and returns true before its loop runs
    // (Schedule5CheckStatus.java:89-97).
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "674").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")))
        .andExpect(jsonPath("$.messages[0].key", is("scheduleRequirementsMetMsg")))
        .andExpect(jsonPath("$.camps", hasSize(0)));
  }

  @Test
  @DisplayName("673/2021 mixed -> ISSUES, no banner, all four camps reported in CAMP_REPORT_ID order")
  void mixed_reportsEveryCampInIdOrder() throws Exception {
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("ISSUES")))
        // On ISSUES the schedule-level banner is empty — the two are mutually exclusive.
        .andExpect(jsonPath("$.messages", hasSize(0)))
        .andExpect(jsonPath("$.camps", hasSize(4)))
        // Addressed BY INDEX so the ascending CAMP_REPORT_ID order is pinned, not merely membership
        // (7.1 deviation (c): legacy iterates a HashMap and has no ORDER BY on this path).
        .andExpect(jsonPath("$.camps[0].campId", is(8211)))
        .andExpect(jsonPath("$.camps[1].campId", is(8212)))
        .andExpect(jsonPath("$.camps[2].campId", is(8213)))
        .andExpect(jsonPath("$.camps[3].campId", is(8214)));
  }

  @Test
  @DisplayName("a camp with a stored ZERO distance/volume and a SINGLE-SPACE row description is MET")
  void zeroAndWhitespaceArePresent() throws Exception {
    // Camp 8211 carries distance 0, volume 0, and an item-62 row whose description is " ". Two
    // separate legacy rules make it MET, and each has an obvious "tidier" implementation that breaks
    // it:
    //   * the three numeric descriptors are PURE null tests (:18-20), so 0 is PRESENT — the D2
    //     precedent. A `> 0` test would flag a legitimately zero camp.
    //   * the sub-list description test is `== null || "".equals(...)` and does NOT trim
    //     (CheckStatusUtil.java:134), so a single space is a description. isBlank would flag it.
    // Because the schedule outcome is ISSUES, this camp is also the one carrying SUC-005.
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[0].requirementsMet", is(true)))
        .andExpect(jsonPath("$.camps[0].messages", hasSize(1)))
        .andExpect(jsonPath("$.camps[0].messages[0].key", is("campRequirementsMetMsg")))
        // Verbatim legacy :40, with the camp NAME interpolated as {0} — not an ordinal, unlike
        // Schedule 6's rowCounter — and WITH a trailing period.
        .andExpect(jsonPath("$.camps[0].messages[0].text",
            is("All requirements for Zero Descriptor Camp have been met.")))
        // A met message names no field, so `field` is omitted from the JSON entirely.
        .andExpect(jsonPath("$.camps[0].messages[0].field").doesNotExist());
  }

  @Test
  @DisplayName("the three numeric descriptor lines compose byte-for-byte, in legacy emission order")
  void missingDescriptors_composeVerbatimInOrder() throws Exception {
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[1].campName", is("Bare Descriptor Camp")))
        .andExpect(jsonPath("$.camps[1].requirementsMet", is(false)))
        .andExpect(jsonPath("$.camps[1].messages", hasSize(3)))
        // Emission order is distance, size, volume (Schedule5MB.java:351-359) — deliberately DIFFERENT
        // from the order the flags are computed in (Schedule5CheckStatus.java:18-20).
        .andExpect(jsonPath("$.camps[1].messages[*].field", contains(
            "roadDistanceToOperatingArea", "sizeOfCamp", "associatedCampVolume")))
        .andExpect(jsonPath("$.camps[1].messages[*].key", contains(
            "missingRequiredFieldMsg", "missingRequiredFieldMsg", "missingRequiredFieldMsg")))
        // NO space before the final colon. This is where copying Schedule 6's FIELD_SEGMENTS map
        // would produce "… - Size of Camp : Value Required" and ship a one-byte parity break.
        .andExpect(jsonPath("$.camps[1].messages[*].text", contains(
            "Camp Report Name : Bare Descriptor Camp - Road Distance to Operating Area: "
                + "Value Required",
            "Camp Report Name : Bare Descriptor Camp - Size of Camp: Value Required",
            "Camp Report Name : Bare Descriptor Camp - Associated Camp Volume: Value Required")));
  }

  @Test
  @DisplayName("a whitespace-only stored camp name flags Camp name, with the raw name in the line")
  void whitespaceCampName_flagsAndEmbedsTheRawName() throws Exception {
    // CAMP_NAME is NOT NULL in delivery, so legacy's null branch is unreachable from stored data and
    // a whitespace-only name is the ONLY way this condition fires — while the test itself IS trimmed
    // (CoreUtil.isNullOrEmptyString(name, true) at :17). Camp 8213's name is three spaces, and they
    // go into the composed line untouched: one space from the prefix, three from the name, one
    // leading the segment.
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[2].campId", is(8213)))
        .andExpect(jsonPath("$.camps[2].messages", hasSize(1)))
        .andExpect(jsonPath("$.camps[2].messages[0].field", is("campName")))
        .andExpect(jsonPath("$.camps[2].messages[0].text",
            is("Camp Report Name :     - Camp name: Value Required")));
  }

  @Test
  @DisplayName("all four sub-list conditions fire together and compose verbatim (S20)")
  void subListConditions_composeVerbatim() throws Exception {
    // Camp 8214's descriptors are all present; its four sub-page rows supply exactly one problem each:
    // an item-62 row with no description, an item-62 row with no cost, and the same pair for item 68.
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.camps[3].campName", is("Sub Page Issue Camp")))
        .andExpect(jsonPath("$.camps[3].messages", hasSize(4)))
        .andExpect(jsonPath("$.camps[3].messages[*].field", contains(
            "otherCampExpenseDescription", "otherCampExpenseCost",
            "otherAccessExpenseDescription", "otherAccessExpenseCost")))
        .andExpect(jsonPath("$.camps[3].messages[*].text", contains(
            "Camp Report Name : Sub Page Issue Camp - Other Camp Expense List (Description): "
                + "Value Required",
            "Camp Report Name : Sub Page Issue Camp - Other Camp Expense List (Cost $): "
                + "Value Required",
            "Camp Report Name : Sub Page Issue Camp - Other Access Expense List (Description): "
                + "Value Required",
            "Camp Report Name : Sub Page Issue Camp - Other Access Expense List (Cost $): "
                + "Value Required")));
  }

  @Test
  @DisplayName("671/2021 non-Draft -> 200, because check status is VIEW-gated, not Draft-gated")
  void nonDraftMillIsStillCheckable() throws Exception {
    // The 2.6 precedent (deferred-work.md:23). A licensee must be able to review a submitted schedule;
    // gating this endpoint on Draft by copy-paste from the writes would take that away.
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "671").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
  }

  @Test
  @DisplayName("the context guards still apply — a closed mill 409s and an unknown mill 404s")
  void contextGuardsApply() throws Exception {
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "516").param("year", "2021"))
        .andExpect(status().isConflict());
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "999").param("year", "2021"))
        .andExpect(status().isNotFound());
    // ERR-003: missing params must stay the verbatim legacy message, which is why millId/year are
    // optional raw Strings rather than typed required params.
    mockMvc.perform(post(CHECK_STATUS).with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("check status MUTATES NOTHING — proven per row, not asserted")
  void mutatesNothing() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<Map<String, Object>> before = fingerprint(jdbc);

    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "673").param("year", "2021"))
        .andExpect(status().isOk());
    mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "672").param("year", "2021"))
        .andExpect(status().isOk());

    // A per-row snapshot including BOTH audit pairs and REVISION_COUNT. Count- and sum-based
    // fingerprints have passed while real writes slipped through (8-2-…md:112), and a stray
    // UPDATE_TIMESTAMP is exactly the kind of write a row count cannot see.
    assertEquals(before, fingerprint(jdbc));
  }

  private static List<Map<String, Object>> fingerprint(JdbcTemplate jdbc) {
    return jdbc.queryForList("""
        SELECT c.CAMP_REPORT_ID, c.CAMP_NAME, c.DISTANCE_TO_OPERATING_AREA, c.CAMP_SIZE_CAPACITY,
               c.ASSOCIATED_CAMP_VOLUME, c.ISOLATED_CAMP_IND, c.COMMENTS, c.REVISION_COUNT,
               c.ENTRY_USERID, c.ENTRY_TIMESTAMP, c.UPDATE_USERID, c.UPDATE_TIMESTAMP,
               d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST,
               d.ITEM_DESCRIPTION, d.REVISION_COUNT AS DETAIL_REVISION,
               d.ENTRY_USERID AS DETAIL_ENTRY_USER, d.ENTRY_TIMESTAMP AS DETAIL_ENTRY_TS,
               d.UPDATE_USERID AS DETAIL_UPDATE_USER, d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TS
          FROM THE.CAMP_REPORT c
          LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.CAMP_REPORT_ID = c.CAMP_REPORT_ID
         WHERE c.ILCR_MILL_ID IN (671, 672, 673, 674)
         ORDER BY c.CAMP_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
        """);
  }
}
