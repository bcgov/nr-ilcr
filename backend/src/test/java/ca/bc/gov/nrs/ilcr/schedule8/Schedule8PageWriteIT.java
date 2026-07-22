package ca.bc.gov.nrs.ilcr.schedule8;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Acceptance test — Story 14.2. PUT (save create/edit) + DELETE (cascade) /api/v1/schedule8/pages.
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}, explicit because the merged runtime default is
 * fail-closed) so the mock {@code ILCR_SUBMITTER} principal holds EDIT_SCHEDULE — isolating the
 * write-gate / validation / concurrency / cascade behaviour from authz (the 403 case lives in
 * {@link Schedule8PageWriteAuthorizationIT}). Mutating requests carry {@code .with(csrf())} (the
 * merged-main {@code csrf.spa()} regression). Dedicated V12 mills keep cases order-independent:
 * 580 create, 581 (page 8800) edit/stale, 582 (page 8810 + sample + rate) delete, 583 (non-Draft) gate.
 */
@DisplayName("PUT/DELETE /api/v1/schedule8/pages — page write (Story 14.2)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule8PageWriteIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule8/pages";
  private static final String REPORT = "THE.TREE_TO_TRUCK_REPORT";
  private static final String SAMPLE = "THE.TREE_TO_TRUCK_DETAIL_REPORT";
  private static final String RATE = "THE.TREE_TO_TRUCK_RATE_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private int reports(String where) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, REPORT, where);
  }

  private Integer revisionOf(int pageId) {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM " + REPORT + " WHERE TREE_TO_TRUCK_REPORT_ID = ?",
        Integer.class, pageId);
  }

  private String stringCol(String col, String where) {
    return jdbcTemplate.queryForObject(
        "SELECT " + col + " FROM " + REPORT + " WHERE " + where, String.class);
  }

  @Test
  @DisplayName("create page (id null) -> 200 saved, new row at revision 1, editable")
  void create_persistsPageAtRevisionOne() throws Exception {
    int before = reports("ILCR_MILL_ID = 580");
    String body = """
        {"id": null, "revisionCount": null, "license": "LCRT1", "supportCentre": "SC1",
         "region": "R1", "becZone": "BZ1", "tsaNumber": "TSA5", "supplyBlock": "B",
         "division": "Created Div", "contact": "Sam"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals(before + 1, reports("ILCR_MILL_ID = 580"));
    assertEquals(Integer.valueOf(1),
        jdbcTemplate.queryForObject("SELECT REVISION_COUNT FROM " + REPORT
            + " WHERE ILCR_MILL_ID = 580 AND HARVEST_LICENSE_NUMBER = 'LCRT1'", Integer.class));
  }

  @Test
  @DisplayName("missing required field (license) -> 400, nothing persists")
  void missingLicense_returns400_noPersist() throws Exception {
    int before = reports("ILCR_MILL_ID = 580");
    String body = """
        {"supportCentre": "SC1", "region": "R1", "becZone": "BZ1", "tsaNumber": "TSA5"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    assertEquals(before, reports("ILCR_MILL_ID = 580"));
  }

  @Test
  @DisplayName("neither TSA nor TFL -> 400 (TSA-or-TFL required)")
  void neitherTsaNorTfl_returns400() throws Exception {
    String body = """
        {"license": "LREQ1", "supportCentre": "SC1", "region": "R1", "becZone": "BZ1"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("TFL selected -> supply block cleared server-side (BR-03 mutual exclusion)")
  void tflSelected_clearsSupplyBlock() throws Exception {
    String body = """
        {"license": "LTFL1", "supportCentre": "SC1", "region": "R1", "becZone": "BZ1",
         "tflNumber": "48", "supplyBlock": "B"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "580").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    String where = "ILCR_MILL_ID = 580 AND HARVEST_LICENSE_NUMBER = 'LTFL1'";
    assertEquals("48", stringCol("TFL_NUMBER_CODE", where));
    assertNull(stringCol("TSB_NUMBER_CODE", where)); // supply block cleared
  }

  @Test
  @DisplayName("edit page (id present, current revision) -> 200, field updated, revision bumped")
  void edit_updatesFieldsAndBumpsRevision() throws Exception {
    int rev = revisionOf(8800);
    String body = """
        {"id": 8800, "revisionCount": %d, "license": "L581", "supportCentre": "SC1",
         "region": "R1", "becZone": "BZ1", "tsaNumber": "TSA5", "division": "Renamed Div"}"""
        .formatted(rev);
    mockMvc.perform(put(ENDPOINT).param("millId", "581").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
    assertEquals("Renamed Div",
        stringCol("DIVISION_LOCATION", "TREE_TO_TRUCK_REPORT_ID = 8800"));
    assertEquals(Integer.valueOf(rev + 1), revisionOf(8800));
  }

  @Test
  @DisplayName("stale revisionCount -> 409, nothing changes")
  void staleRevision_returns409() throws Exception {
    String body = """
        {"id": 8800, "revisionCount": 999, "license": "L581", "supportCentre": "SC1",
         "region": "R1", "becZone": "BZ1", "tsaNumber": "TSA5"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "581").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("non-Draft mill -> 409 (Draft gate before persist)")
  void nonDraft_returns409() throws Exception {
    int before = reports("ILCR_MILL_ID = 583");
    String body = """
        {"license": "LSUB1", "supportCentre": "SC1", "region": "R1", "becZone": "BZ1",
         "tsaNumber": "TSA5"}""";
    mockMvc.perform(put(ENDPOINT).param("millId", "583").param("year", "2021")
            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict());
    assertEquals(before, reports("ILCR_MILL_ID = 583"));
  }

  @Test
  @DisplayName("delete page -> 200 deleted, page + sample + rate all cascade-removed")
  void delete_cascadesSamplesAndRateDetails() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/8810").param("millId", "582").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
    assertEquals(0, reports("TREE_TO_TRUCK_REPORT_ID = 8810"));
    assertEquals(0,
        JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SAMPLE, "TREE_TO_TRUCK_REPORT_ID = 8810"));
    assertEquals(0,
        JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, RATE,
            "TREE_TO_TRUCK_DETAIL_REPORT_ID = 8811"));
  }

  @Test
  @DisplayName("delete unknown page id -> 200 (idempotent)")
  void deleteUnknownId_isIdempotent() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/99999").param("millId", "582").param("year", "2021")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }
}
