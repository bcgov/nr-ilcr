package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Acceptance test — Story 14.3. PUT/DELETE /api/v1/schedule8/pages/{pageId}/samples. Security OFF
 * (mock ILCR_SUBMITTER holds EDIT_SCHEDULE); mutating requests carry {@code .with(csrf())}. Dedicated
 * V13 mills/pages: 590/8900 create + validation, 591/8910/8911 edit/stale, 592/8920/8921(+rate 8922)
 * delete cascade, 593/8930 (non-Draft) gate. Explicitly proves the S15-vs-S16 asymmetry: sum &gt; 100
 * is rejected at Save, sum &lt; 100 SAVES.
 */
@DisplayName("PUT/DELETE /api/v1/schedule8/pages/{pageId}/samples — sample write (Story 14.3)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8SampleWriteIT extends AbstractOracleIT {

  private static final String SAMPLE = "THE.TREE_TO_TRUCK_DETAIL_REPORT";
  private static final String RATE = "THE.TREE_TO_TRUCK_RATE_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private static String url(int pageId) {
    return "/api/v1/schedule8/pages/" + pageId + "/samples";
  }

  private int samples(String where) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SAMPLE, where);
  }

  private Integer revisionOf(int sampleId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SAMPLE + " WHERE TREE_TO_TRUCK_DETAIL_REPORT_ID = ?",
        Integer.class, sampleId);
  }

  private org.springframework.test.web.servlet.ResultActions putSample(int millId, int pageId,
      String body) throws Exception {
    return mockMvc.perform(put(url(pageId)).param("millId", String.valueOf(millId))
        .param("year", "2021").with(csrf())
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  @Test
  @DisplayName("create sample (sum == 100) -> 200 saved, new row at revision 1")
  void create_persistsSampleAtRevisionOne() throws Exception {
    String body = """
        {"contractId": "CNEW1", "cutBlock": "CB", "groundBasePct": 60, "grapplePct": 40,
         "coniferousVolume": 500, "originalRate": 22.50}""";
    putSample(590, 8900, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals(1, samples("TREE_TO_TRUCK_REPORT_ID = 8900 AND CONTRACTOR_ID = 'CNEW1'"));
    assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + SAMPLE
            + " WHERE TREE_TO_TRUCK_REPORT_ID = 8900 AND CONTRACTOR_ID = 'CNEW1'", Integer.class));
  }

  @Test
  @DisplayName("skidding sum < 100 SAVES (S16 — no Save-time error, the asymmetry)")
  void skiddingSumBelow100_saves() throws Exception {
    String body = """
        {"contractId": "CLT99", "groundBasePct": 50}""";
    putSample(590, 8900, body).andExpect(status().isOk());
    assertEquals(1, samples("TREE_TO_TRUCK_REPORT_ID = 8900 AND CONTRACTOR_ID = 'CLT99'"));
  }

  @Test
  @DisplayName("skidding sum > 100 -> 400 (S15), nothing persists")
  void skiddingSumAbove100_returns400() throws Exception {
    int before = samples("TREE_TO_TRUCK_REPORT_ID = 8900");
    String body = """
        {"contractId": "CGT100", "groundBasePct": 60, "grapplePct": 60}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
    assertEquals(before, samples("TREE_TO_TRUCK_REPORT_ID = 8900"));
  }

  @Test
  @DisplayName("individual % out of 0-100 -> 400 (S17)")
  void individualPercentOutOfRange_returns400() throws Exception {
    String body = """
        {"contractId": "CBAD", "groundBasePct": 150}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Contract ID missing -> 400 (S20)")
  void contractIdMissing_returns400() throws Exception {
    String body = """
        {"groundBasePct": 100}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Helicopter % != 0 without Distance/Cycle/Direction/Dump -> 400 (S23)")
  void helicopterConditionalMissing_returns400() throws Exception {
    String body = """
        {"contractId": "CHELI", "groundBasePct": 50, "helicopterPct": 50}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Helicopter % != 0 WITH the required fields -> 200")
  void helicopterConditionalComplete_saves() throws Exception {
    String body = """
        {"contractId": "CHELOK", "groundBasePct": 50, "helicopterPct": 50,
         "distance": 12.5, "cycleTime": 3.0, "uphillDirection": true, "waterDumpDestination": false}""";
    putSample(590, 8900, body).andExpect(status().isOk());
    assertEquals(1, samples("TREE_TO_TRUCK_REPORT_ID = 8900 AND CONTRACTOR_ID = 'CHELOK'"));
  }

  @Test
  @DisplayName("Other % != 0 with skid type 'NA' -> 400 (S24)")
  void otherConditionalNaSkidType_returns400() throws Exception {
    String body = """
        {"contractId": "COTHER", "groundBasePct": 50, "otherSkiddingPct": 50, "skidTypeCode": "NA"}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("coniferous volume out of range -> 400 (S25)")
  void volumeOutOfRange_returns400() throws Exception {
    String body = """
        {"contractId": "CVOL", "groundBasePct": 100, "coniferousVolume": 10000000}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("original rate out of range -> 400 (S26)")
  void originalRateOutOfRange_returns400() throws Exception {
    String body = """
        {"contractId": "CRATE", "groundBasePct": 100, "originalRate": 1000000.00}""";
    putSample(590, 8900, body).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("unknown page -> 404")
  void unknownPage_returns404() throws Exception {
    String body = """
        {"contractId": "CNOPAGE", "groundBasePct": 100}""";
    putSample(590, 99999, body).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("edit sample (current revision) -> 200, field updated, revision bumped")
  void edit_updatesAndBumpsRevision() throws Exception {
    int rev = revisionOf(8911);
    String body = """
        {"id": 8911, "revisionCount": %d, "contractId": "CEDIT", "groundBasePct": 70}"""
        .formatted(rev);
    putSample(591, 8910, body).andExpect(status().isOk());
    assertEquals("CEDIT", jdbcTemplate.queryForObject(
        "SELECT CONTRACTOR_ID FROM " + SAMPLE + " WHERE TREE_TO_TRUCK_DETAIL_REPORT_ID = 8911",
        String.class));
    assertEquals(Integer.valueOf(rev + 1), revisionOf(8911));
  }

  @Test
  @DisplayName("stale revisionCount -> 409")
  void staleRevision_returns409() throws Exception {
    String body = """
        {"id": 8911, "revisionCount": 999, "contractId": "CSTALE", "groundBasePct": 70}""";
    putSample(591, 8910, body).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("non-Draft mill -> 409 (Draft gate before persist)")
  void nonDraft_returns409() throws Exception {
    String body = """
        {"contractId": "CSUB", "groundBasePct": 100}""";
    putSample(593, 8930, body).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("delete sample -> 200, sample + its rate details cascade-removed")
  void delete_cascadesRateDetails() throws Exception {
    mockMvc.perform(delete(url(8920) + "/8921").param("millId", "592").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
    assertEquals(0, samples("TREE_TO_TRUCK_DETAIL_REPORT_ID = 8921"));
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, RATE,
        "TREE_TO_TRUCK_DETAIL_REPORT_ID = 8921"));
  }

  @Test
  @DisplayName("delete unknown sample -> 200 (idempotent)")
  void deleteUnknown_isIdempotent() throws Exception {
    mockMvc.perform(delete(url(8920) + "/99999").param("millId", "592").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
