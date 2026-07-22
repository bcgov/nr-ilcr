package ca.bc.gov.nrs.ilcr.schedule4;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Story 4.3. Sub-page list rows: read extension + POST (add) / DELETE (remove row).
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}) so the mock {@code ILCR_SUBMITTER} principal
 * holds EDIT_SCHEDULE (403 case in {@link Schedule4WriteAuthorizationIT}); mutating requests send
 * {@code .with(csrf())} (the merged chain enforces {@code csrf.spa()}). Dedicated V9 mills: 550
 * "Rowed Dump" (primary 8050 + seeded Towing 8051 / Rehaul 8052 / Other 8053) for read/add/delete,
 * 551 (non-Draft) for the write gate. Cases read counts at runtime so they stay order-independent.
 */
@DisplayName("POST/DELETE /api/v1/schedule4/locations/{id}/rows — sub-page lists (Story 4.3)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule4SubPageIT extends AbstractOracleIT {

  private static final String ROWS_550 = "/api/v1/schedule4/locations/8050/rows";
  private static final String REPORT = "THE.TRANSPORTATION_REPORT";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  /** Count of sub-page rows (detail code 43/46/55) for a mill — order-independent delta anchor. */
  private int subPageRowCount(long mill) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + DETAIL + " d JOIN " + REPORT + " tr "
            + "ON tr.TRANSPORTATION_REPORT_ID = d.TRANSPORTATION_REPORT_ID "
            + "WHERE tr.ILCR_MILL_ID = ? AND d.ILCR_REPORT_COST_ITEM_ID IN (43, 46, 55)",
        Integer.class, mill);
  }

  private int rows(String where) {
    return org.springframework.test.jdbc.JdbcTestUtils.countRowsInTableWhere(
        jdbcTemplate, REPORT, where);
  }

  private static String num(Object v) {
    return v == null ? "null" : v.toString();
  }

  private static String rowBody(String type, String description, Object distance, Object volume,
      Object cost, Object cycle) {
    return """
        {
          "type": %s,
          "description": %s,
          "distance": %s,
          "volume": %s,
          "cost": %s,
          "cycle": %s
        }
        """.formatted(
            type == null ? "null" : ("\"" + type + "\""),
            description == null ? "null" : ("\"" + description + "\""),
            num(distance), num(volume), num(cost), num(cycle));
  }

  // ---- read extension: location carries its sub-page rows (43/46/55). ----------------------------

  @Test
  @DisplayName("read — 550 Rowed Dump carries its Towing/Rehaul/Other rows; Rehaul has its cycle")
  void get_includesSubPageRows() throws Exception {
    mockMvc.perform(get("/api/v1/schedule4").param("millId", "550").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // one location; assert the seeded rows by their unique descriptions (order/count independent).
        .andExpect(jsonPath("$.locations[0].name", is("Rowed Dump")))
        .andExpect(jsonPath("$.locations[0].subPageRows[?(@.description=='Existing Towing')].code",
            contains(43)))
        .andExpect(jsonPath("$.locations[0].subPageRows[?(@.description=='Existing Rehaul')].code",
            contains(46)))
        .andExpect(jsonPath("$.locations[0].subPageRows[?(@.description=='Existing Rehaul')].cycle",
            contains(7)))
        .andExpect(jsonPath("$.locations[0].subPageRows[?(@.description=='Existing Other')].code",
            contains(55)))
        // sub-page codes never leak into the fixed/distance category grid.
        .andExpect(jsonPath("$.locations[0].categories[?(@.code == 43)]").isEmpty());
  }

  // ---- add each row type. ------------------------------------------------------------------------

  @Test
  @DisplayName("add — Towing row (43) persists a new report + description detail; saved echo")
  void post_towing_persists() throws Exception {
    int before = subPageRowCount(550);
    mockMvc.perform(post(ROWS_550).with(csrf()).param("millId", "550").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(rowBody("TOWING", "Added Towing", "35.0", 120, 3600, null))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals(before + 1, subPageRowCount(550), "one sub-page row added");
    assertEquals(1, rows("ILCR_MILL_ID = 550 AND LOCATION_DESCRIPTION = 'Rowed Dump' "
        + "AND DISTANCE = 35.0"), "new row report carries its own distance");
    assertEquals(1, org.springframework.test.jdbc.JdbcTestUtils.countRowsInTableWhere(jdbcTemplate,
        DETAIL, "ILCR_REPORT_COST_ITEM_ID = 43 AND ITEM_DESCRIPTION = 'Added Towing'"),
        "detail carries item 43 + description");
  }

  @Test
  @DisplayName("add — Truck Rehaul row (46) persists the cycle on the report")
  void post_truckRehaul_persistsCycle() throws Exception {
    int before = subPageRowCount(550);
    mockMvc.perform(post(ROWS_550).with(csrf()).param("millId", "550").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(rowBody("TRUCK_REHAUL", "Added Rehaul", "45.0", 210, 6300, 12))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals(before + 1, subPageRowCount(550));
    assertEquals(1, rows("ILCR_MILL_ID = 550 AND LOCATION_DESCRIPTION = 'Rowed Dump' "
        + "AND DISTANCE = 45.0 AND TRANSPORTATION_CYCLE_TIME = 12"), "cycle persisted on the report");
  }

  @Test
  @DisplayName("add — Other row (55) persists")
  void post_other_persists() throws Exception {
    int before = subPageRowCount(550);
    mockMvc.perform(post(ROWS_550).with(csrf()).param("millId", "550").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(rowBody("OTHER", "Added Other", "25.0", 60, 1200, null))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    assertEquals(before + 1, subPageRowCount(550));
    assertEquals(1, org.springframework.test.jdbc.JdbcTestUtils.countRowsInTableWhere(jdbcTemplate,
        DETAIL, "ILCR_REPORT_COST_ITEM_ID = 55 AND ITEM_DESCRIPTION = 'Added Other'"));
  }

  // ---- row validation -> 400 verbatim, nothing persisted. ----------------------------------------

  @Test
  @DisplayName("volume > 999,999 -> 400 volume6DigitValidatorErrorMsg, nothing persisted")
  void post_volumeOver_returns400() throws Exception {
    expect400(rowBody("TOWING", "Bad Vol", "10.0", 1000000, 5000, null),
        "Entered volume must be between 0 and 999,999.");
  }

  @Test
  @DisplayName("cost > 9,999,999 -> 400 costSize7ValidatorErrorMsg, nothing persisted")
  void post_costOver_returns400() throws Exception {
    expect400(rowBody("TOWING", "Bad Cost", "10.0", 100, 10000000, null),
        "Entered cost must be between -9,999,999 and 9,999,999.");
  }

  @Test
  @DisplayName("Truck Rehaul cycle > 999,999 -> 400 cycleValidatorErrorMsg, nothing persisted")
  void post_cycleOver_returns400() throws Exception {
    expect400(rowBody("TRUCK_REHAUL", "Bad Cycle", "10.0", 100, 5000, 1000000),
        "Entered cycle time must be between 0 and 999,999.");
  }

  @Test
  @DisplayName("distance > 999,999.9 -> 400 distanceValidatorErrorMsg, nothing persisted")
  void post_distanceOver_returns400() throws Exception {
    expect400(rowBody("TOWING", "Bad Dist", "1000000", 100, 5000, null),
        "Entered distance must be between 0 and 999,999.");
  }

  @Test
  @DisplayName("blank description -> 400 Value Required, nothing persisted")
  void post_blankDescription_returns400() throws Exception {
    expect400(rowBody("TOWING", "   ", "10.0", 100, 5000, null), "Value Required");
  }

  private void expect400(String body, String verbatimDetailFragment) throws Exception {
    int before = subPageRowCount(550);
    mockMvc.perform(post(ROWS_550).with(csrf()).param("millId", "550").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString(verbatimDetailFragment)));
    assertEquals(before, subPageRowCount(550), "a rejected add must not persist anything");
  }

  // ---- delete row + guard + idempotency. ---------------------------------------------------------

  @Test
  @DisplayName("delete — removes the Towing row 8051 + detail; deleted echo; second delete idempotent")
  void delete_removesRow_idempotent() throws Exception {
    mockMvc.perform(delete("/api/v1/schedule4/locations/8050/rows/8051").with(csrf())
            .param("millId", "550").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
    assertEquals(0, rows("TRANSPORTATION_REPORT_ID = 8051"), "the row's report is gone");
    assertEquals(0, org.springframework.test.jdbc.JdbcTestUtils.countRowsInTableWhere(jdbcTemplate,
        DETAIL, "TRANSPORTATION_REPORT_ID = 8051"), "its cascaded detail is gone");
    // Idempotent: deleting the now-absent row still returns 200.
    mockMvc.perform(delete("/api/v1/schedule4/locations/8050/rows/8051").with(csrf())
            .param("millId", "550").param("year", "2021"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("delete guard — a primary/category report id via the row endpoint is a NO-OP (survives)")
  void delete_primaryReportId_isNoOp() throws Exception {
    // 8050 is Rowed Dump's PRIMARY (fixed-category) report, NOT a sub-page row.
    mockMvc.perform(delete("/api/v1/schedule4/locations/8050/rows/8050").with(csrf())
            .param("millId", "550").param("year", "2021"))
        .andExpect(status().isOk());
    assertEquals(1, rows("TRANSPORTATION_REPORT_ID = 8050"),
        "the location's primary report must NOT be deletable via the row endpoint");
  }

  // ---- Draft gate. -------------------------------------------------------------------------------

  @Test
  @DisplayName("add against non-Draft (mill 551, track S) -> 409, nothing persisted")
  void post_nonDraft_returns409() throws Exception {
    int before = subPageRowCount(551);
    mockMvc.perform(post("/api/v1/schedule4/locations/8060/rows").with(csrf())
            .param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(rowBody("TOWING", "Nope", "10.0", 100, 5000, null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(before, subPageRowCount(551), "a gated add must not persist");
  }

  @Test
  @DisplayName("delete against non-Draft (mill 551, track S) -> 409, row survives")
  void delete_nonDraft_returns409() throws Exception {
    mockMvc.perform(delete("/api/v1/schedule4/locations/8060/rows/8061").with(csrf())
            .param("millId", "551").param("year", "2021"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(1, rows("TRANSPORTATION_REPORT_ID = 8061"), "a gated delete keeps the row");
  }
}
