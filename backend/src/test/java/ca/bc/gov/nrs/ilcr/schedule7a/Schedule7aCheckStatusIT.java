package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Story 12.2 acceptance — {@code POST /api/v1/schedule7a/check-status} (AC7, slice S29; BR-08).
 * Read-only; mutates nothing; NOT Draft-gated (runs on the Submitted mill 517). Security OFF.
 *
 * <p>Since #359 the endpoint judges the SCREEN: the body carries every bridge row. The original
 * cases post a body that MIRRORS the stored seed (V27), so their verdicts are unchanged; the {@code
 * #359} cases post a body that DISAGREES with Oracle and prove the body wins.
 */
@DisplayName("POST /api/v1/schedule7a/check-status — per-bridge readiness (Story 12.2)")
class Schedule7aCheckStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule7a/check-status";

  @Autowired private JdbcTemplate jdbc;

  /**
   * One on-screen bridge row as JSON. {@code costs} are in the order sitePlan, superstructure
   * material/deliver/install, abutment material/deliver/install, approach, afterInstall, other; a
   * null renders as JSON null (a blank field), never 0.
   */
  private static String bridge(
      String name,
      String built,
      Integer lifeSpan,
      String height,
      String length,
      String width,
      Integer distance,
      Integer... costs) {
    String[] names = {
      "sitePlanCost",
      "superstructureMaterialCost",
      "superstructureDeliverCost",
      "superstructureInstallCost",
      "abutmentMaterialCost",
      "abutmentDeliverCost",
      "abutmentInstallCost",
      "approachCost",
      "afterInstallCost",
      "otherCost"
    };
    StringBuilder json =
        new StringBuilder("{\"locationName\":")
            .append(quoted(name))
            .append(",\"builtDate\":")
            .append(quoted(built))
            .append(",\"lifeSpan\":")
            .append(lifeSpan)
            .append(",\"abutmentHeight\":")
            .append(height)
            .append(",\"length\":")
            .append(length)
            .append(",\"width\":")
            .append(width)
            .append(",\"distance\":")
            .append(distance);
    for (int i = 0; i < names.length; i++) {
      json.append(",\"").append(names[i]).append("\":").append(costs[i]);
    }
    return json.append('}').toString();
  }

  private static String quoted(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  private static String body(String... bridges) {
    return "{\"bridges\":[" + String.join(",", bridges) + "]}";
  }

  private static final String ROW_7601 =
      bridge(
          "North Fork Bridge",
          "2020-06",
          50,
          "5.0",
          "20.0",
          "4.0",
          12,
          1000,
          5000,
          500,
          800,
          3000,
          300,
          400,
          700,
          200,
          100);
  private static final String ROW_7602 =
      bridge(
          "South Creek Bridge",
          "2019-11",
          40,
          "3.5",
          "15.5",
          "3.0",
          8,
          2000,
          4000,
          400,
          600,
          1000,
          100,
          200,
          300,
          150,
          50);

  /** 7603 as stored: afterInstall (72) and other (73) have no detail row. */
  private static final String ROW_7603 =
      bridge(
          "Old Mill Crossing",
          "2018-03",
          30,
          "2.5",
          "10.0",
          "2.5",
          5,
          500,
          3000,
          300,
          500,
          800,
          80,
          120,
          100,
          null,
          null);

  /** 514/2021 as served: 7601, 7602, 7603 in id order. */
  private static final String BODY_514 = body(ROW_7601, ROW_7602, ROW_7603);

  /** 517/2021 as served: 7651, complete. */
  private static final String BODY_517 =
      body(
          bridge(
              "Harbour Overpass",
              "2020-09",
              60,
              "6.0",
              "25.0",
              "5.0",
              15,
              1500,
              2500,
              250,
              350,
              900,
              90,
              110,
              250,
              100,
              75));

  private static MockHttpServletRequestBuilder check(String millId, String json) {
    return post(ENDPOINT)
        .param("millId", millId)
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }

  @Test
  @DisplayName(
      "514/2021 — bridge 3 missing two costs -> flagged; complete bridges reported met (S29)")
  void incompleteBridge_flagsMissingCosts() throws Exception {
    mockMvc
        .perform(check("514", BODY_514))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        // 7603 (rowCounter 3) is missing afterInstall(72) + other(73) — two flags, legacy order.
        .andExpect(jsonPath("$.errors", hasSize(2)))
        .andExpect(jsonPath("$.errors[0].key", is("missingRequiredFieldMsg")))
        .andExpect(jsonPath("$.errors[0].text", containsString("Bridge Report Id : 3")))
        // The verbatim legacy line, ": " separator included (util/FacesUtil.java:134).
        .andExpect(
            jsonPath(
                "$.errors[0].text",
                is("Bridge Report Id : 3 - Certification After install Cost : Value Required")))
        .andExpect(
            jsonPath("$.errors[1].text", is("Bridge Report Id : 3 - Other Costs : Value Required")))
        // 7601 + 7602 pass -> two per-bridge all-met messages; no schedule-wide all-met.
        .andExpect(jsonPath("$.bridgeMessages", hasSize(2)))
        .andExpect(jsonPath("$.bridgeMessages[0].key", is("bridgeRequirementsMetMsg")))
        .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist());
  }

  @Test
  @DisplayName("517/2021 — single complete bridge -> all met (schedule-wide, not Draft-gated)")
  void completeSchedule_allMet() throws Exception {
    mockMvc
        .perform(check("517", BODY_517))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        // No per-bridge lines when the whole schedule passes — legacy showed the one schedule-wide
        // message and ran its per-bridge loop only in the failed branch
        // (Schedule7aMB.java:197-296).
        .andExpect(jsonPath("$.bridgeMessages", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")))
        .andExpect(
            jsonPath(
                "$.requirementsMetMessage.text",
                is("All requirements for this schedule have been met")));
  }

  @Test
  @DisplayName("#359 unsaved clear: 7601's distance emptied on screen -> flagged, stored ignored")
  void unsavedClear_bodyWinsOverStoredValue() throws Exception {
    String cleared =
        bridge(
            "North Fork Bridge",
            "2020-06",
            50,
            "5.0",
            "20.0",
            "4.0",
            null,
            1000,
            5000,
            500,
            800,
            3000,
            300,
            400,
            700,
            200,
            100);
    mockMvc
        .perform(check("514", body(cleared, ROW_7602, ROW_7603)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                contains(
                    "Bridge Report Id : 1 - Distance (km) : Value Required",
                    "Bridge Report Id : 3 - Certification After install Cost : Value Required",
                    "Bridge Report Id : 3 - Other Costs : Value Required")))
        // Only 7602 (row 2) still passes.
        .andExpect(jsonPath("$.bridgeMessages", hasSize(1)));
  }

  @Test
  @DisplayName("#359 unsaved fix: 7603's two missing costs typed on screen -> MET, nothing written")
  void unsavedFix_bodyWins_persistsNothing() throws Exception {
    String before = snapshotOf514();
    String fixed =
        bridge(
            "Old Mill Crossing",
            "2018-03",
            30,
            "2.5",
            "10.0",
            "2.5",
            5,
            500,
            3000,
            300,
            500,
            800,
            80,
            120,
            100,
            0,
            1);

    mockMvc
        .perform(check("514", body(ROW_7601, ROW_7602, fixed)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.bridgeMessages", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")));

    assertEquals(before, snapshotOf514(), "check-status must not write a row or move a token");
  }

  @Test
  @DisplayName("#359 an ABSENT body is a clean 400, not a silent stored-only verdict")
  void checkStatusRequiresABody() throws Exception {
    mockMvc
        .perform(post(ENDPOINT).param("millId", "514").param("year", "2021"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("#359 a body without its bridges list, or with a null row, is a clean 400")
  void checkStatusRequiresTheBridgesList() throws Exception {
    mockMvc.perform(check("514", "{}")).andExpect(status().isBadRequest());
    mockMvc.perform(check("514", "{\"bridges\":[null]}")).andExpect(status().isBadRequest());
  }

  /** Every 514/2021 bridge and cost row, REVISION_COUNT included, as one comparable string. */
  private String snapshotOf514() {
    return jdbc.queryForList(
            "SELECT b.BRIDGE_REPORT_ID, b.LOCATION_NAME, b.DISTANCE_FROM_STORAGE,"
                + " b.REVISION_COUNT, d.ILCR_REPORT_COST_ITEM_ID, d.COST"
                + " FROM THE.BRIDGE_REPORT b"
                + " LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.BRIDGE_REPORT_ID = b.BRIDGE_REPORT_ID"
                + " WHERE b.ILCR_MILL_ID = 514 AND b.REPORT_YEAR = 2021"
                + " ORDER BY b.BRIDGE_REPORT_ID, d.ILCR_REPORT_COST_ITEM_ID")
        .toString();
  }
}
