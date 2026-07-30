package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Acceptance test — Story 14.4. POST/PUT/DELETE /api/v1/schedule8/samples/{sampleId}/rates. Security
 * OFF (mock ILCR_SUBMITTER holds EDIT_SCHEDULE); mutating requests carry {@code .with(csrf())}. Proves
 * the addition/deduction classification (by cost item subcategory) + the sample totals/finalRate/count
 * recompute (CNT-003) + required/range 400s + delete-recompute. V14 mills 594/595/596.
 */
@DisplayName("POST/PUT/DELETE /api/v1/schedule8/samples/{sampleId}/rates — rate write (Story 14.4)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8RateWriteIT extends AbstractOracleIT {

  private static final String RATE = "THE.TREE_TO_TRUCK_RATE_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private static String rates(int sampleId) {
    return "/api/v1/schedule8/samples/" + sampleId + "/rates";
  }

  private int rateRows(String where) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, RATE, where);
  }

  private org.springframework.test.web.servlet.ResultActions postRate(int millId, int sampleId,
      String body) throws Exception {
    return mockMvc.perform(post(rates(sampleId)).param("millId", String.valueOf(millId))
        .param("year", "2021").with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("add addition (item 82, subcat '1') -> 200, finalRate = original + addition, counts up")
  void addAddition_recomputesFinalRate() throws Exception {
    String body = """
        {"costItemCode": 82, "costingRate": 5.00, "costTypeCode": "CT1", "itemDescription": "A"}""";
    postRate(594, 8941, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        // Sample 8941 (page 8940, index 0): originalRate 20 + additionsTotal 5 = finalRate 25.
        .andExpect(jsonPath("$.pages[0].samples[0].id", is(8941)))
        .andExpect(jsonPath("$.pages[0].samples[0].additionCount", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[0].additionsTotal", is(5)))
        .andExpect(jsonPath("$.pages[0].samples[0].finalRate", is(25)));
    assertEquals(1, rateRows("TREE_TO_TRUCK_DETAIL_REPORT_ID = 8941 AND ILCR_REPORT_COST_ITEM_ID = 82"));
  }

  @Test
  @DisplayName("add deduction (item 101, subcat '3') -> 200, finalRate = original - deduction")
  void addDeduction_recomputesFinalRate() throws Exception {
    String body = """
        {"costItemCode": 101, "costingRate": 2.00, "costTypeCode": "CT2"}""";
    postRate(594, 8942, body)
        .andExpect(status().isOk())
        // Sample 8942 (page 8940, index 1): originalRate 20 - deductionsTotal 2 = finalRate 18.
        .andExpect(jsonPath("$.pages[0].samples[1].id", is(8942)))
        .andExpect(jsonPath("$.pages[0].samples[1].deductionCount", is(1)))
        .andExpect(jsonPath("$.pages[0].samples[1].deductionsTotal", is(2)))
        .andExpect(jsonPath("$.pages[0].samples[1].finalRate", is(18)));
    assertEquals(1,
        rateRows("TREE_TO_TRUCK_DETAIL_REPORT_ID = 8942 AND ILCR_REPORT_COST_ITEM_ID = 101"));
  }

  @Test
  @DisplayName("cost item missing -> 400")
  void costItemMissing_returns400() throws Exception {
    postRate(594, 8941, """
        {"costingRate": 5.00, "costTypeCode": "CT1"}""").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("costing rate missing -> 400")
  void costingRateMissing_returns400() throws Exception {
    postRate(594, 8941, """
        {"costItemCode": 82, "costTypeCode": "CT1"}""").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("cost type missing -> 400")
  void costTypeMissing_returns400() throws Exception {
    postRate(594, 8941, """
        {"costItemCode": 82, "costingRate": 5.00}""").andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("costing rate out of range -> 400 (S27)")
  void costingRateOutOfRange_returns400() throws Exception {
    postRate(594, 8941, """
        {"costItemCode": 82, "costingRate": 10000000.00, "costTypeCode": "CT1"}""")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("unknown sample -> 404")
  void unknownSample_returns404() throws Exception {
    postRate(594, 99999, """
        {"costItemCode": 82, "costingRate": 5.00, "costTypeCode": "CT1"}""")
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("edit rate inline (current revision) -> 200, costing rate updated, revision bumped")
  void edit_updatesCostingRateAndBumpsRevision() throws Exception {
    Integer rev = jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + RATE + " WHERE TREE_TO_TRUCK_RATE_DETAIL_ID = 8952",
        Integer.class);
    String body = """
        {"revisionCount": %d, "costItemCode": 82, "costingRate": 7.00, "costTypeCode": "CT1"}"""
        .formatted(rev);
    mockMvc.perform(put(rates(8951) + "/8952").param("millId", "595").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    assertEquals(0, new java.math.BigDecimal("7.00").compareTo(jdbcTemplate.queryForObject(
        "SELECT COSTING_RATE FROM " + RATE + " WHERE TREE_TO_TRUCK_RATE_DETAIL_ID = 8952",
        java.math.BigDecimal.class)));
    assertEquals(Integer.valueOf(rev + 1), jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + RATE + " WHERE TREE_TO_TRUCK_RATE_DETAIL_ID = 8952",
        Integer.class));
  }

  @Test
  @DisplayName("stale revisionCount -> 409")
  void staleRevision_returns409() throws Exception {
    mockMvc.perform(put(rates(8951) + "/8952").param("millId", "595").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"revisionCount": 999, "costItemCode": 82, "costingRate": 9.00, "costTypeCode": "CT1"}"""))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("non-Draft mill -> 409 (Draft gate)")
  void nonDraft_returns409() throws Exception {
    postRate(596, 8961, """
        {"costItemCode": 82, "costingRate": 5.00, "costTypeCode": "CT1"}""")
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("delete rate row -> 200, removed, sample totals recompute")
  void delete_removesRowAndRecomputes() throws Exception {
    mockMvc.perform(delete(rates(8953) + "/8954").param("millId", "595").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")))
        // Sample 8953 (page 8950, index 1) loses its only deduction -> count 0, finalRate = original 30.
        .andExpect(jsonPath("$.pages[0].samples[1].id", is(8953)))
        .andExpect(jsonPath("$.pages[0].samples[1].deductionCount", is(0)))
        .andExpect(jsonPath("$.pages[0].samples[1].finalRate", is(30)));
    assertEquals(0, rateRows("TREE_TO_TRUCK_RATE_DETAIL_ID = 8954"));
  }

  @Test
  @DisplayName("delete unknown rate row -> 200 (idempotent)")
  void deleteUnknown_isIdempotent() throws Exception {
    mockMvc.perform(delete(rates(8951) + "/99999").param("millId", "595").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
