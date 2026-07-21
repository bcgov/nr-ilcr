package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * Story 2.4 acceptance (AD-4/5/6/8/9/10): the Subtotal Other Costs sub-resource
 * ({@code GET/POST/PUT/DELETE /api/v1/schedule1/other-costs}) against a real Oracle dialect.
 *
 * <p>Security is OFF (mock {@code ILCR_SUBMITTER} holds VIEW/EDIT_SCHEDULE) so this isolates the
 * document/persistence/validation/gate behaviour from authz (403 lives in
 * {@link Schedule1OtherCostsAuthorizationIT}). Dedicated mills per mutating op (V6) keep the shared
 * container order-independent: 523 read, 524 add, 525 update, 526 delete; 517 = non-Draft gate.
 */
@DisplayName("/api/v1/schedule1/other-costs — Subtotal Other Costs (Story 2.4)")
class Schedule1OtherCostsIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule1/other-costs";
  private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private int itemizedRowCount(long summaryId) {
    return JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = " + summaryId + " AND ILCR_REPORT_COST_ITEM_ID = 19"
            + " AND ITEM_DESCRIPTION IS NOT NULL");
  }

  @Test
  @DisplayName("GET 523 — lists itemized rows + shared volume + server-computed totals")
  void getList_returnsRowsAndTotals() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "523").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.volume", is(5000)))
        .andExpect(jsonPath("$.count", is(2)))
        .andExpect(jsonPath("$.costSubtotal", is(3000)))
        .andExpect(jsonPath("$.editable", is(true)))
        // Row 5051: cost 3000, perUnit = 3000 / shared 5000 = 0.6 (server-computed).
        .andExpect(jsonPath("$.rows[?(@.id == 5051)].cost", contains(3000)))
        .andExpect(jsonPath("$.rows[?(@.id == 5051)].perUnit", contains(0.6)))
        // Row 5052 (null cost) is still listed (EQ-M2).
        .andExpect(jsonPath("$.rows[?(@.description == 'Existing Row B')]", hasSize(1)));
  }

  @Test
  @DisplayName("POST 524 — add inherits shared volume, recomputes subtotal, null cost allowed")
  void add_persistsInheritsVolume_nullCostAllowed() throws Exception {
    // Add a row with a cost: it inherits the shared volume 6000 (BR-06); perUnit = 1200/6000 = 0.2.
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"New Row\", \"cost\": 1200 }")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.count", is(1)))
        .andExpect(jsonPath("$.costSubtotal", is(1200)))
        .andExpect(jsonPath("$.rows[?(@.description == 'New Row')].perUnit", contains(0.2)));
    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1026 AND ITEM_DESCRIPTION = 'New Row'"
            + " AND COST = 1200 AND VOLUME = 6000"), "row persisted with inherited volume");

    // Add a row with a null cost: accepted (EQ-M2); subtotal unchanged (still 1200), count 2.
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"No Cost Row\", \"cost\": null }")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count", is(2)))
        .andExpect(jsonPath("$.costSubtotal", is(1200)));
    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1026 AND ITEM_DESCRIPTION = 'No Cost Row' AND COST IS NULL"),
        "null-cost row persisted");
  }

  @Test
  @DisplayName("POST blank description -> 400 verbatim, nothing written")
  void add_blankDescription_returns400() throws Exception {
    int before = itemizedRowCount(1026);
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"\", \"cost\": 5 }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("Description: Value is required.")));
    assertEquals(before, itemizedRowCount(1026), "a rejected add must not persist");
  }

  @Test
  @DisplayName("POST cost > 99,999,999 -> 400 verbatim FLD-001, nothing written")
  void add_overRangeCost_returns400() throws Exception {
    int before = itemizedRowCount(1026);
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Too big\", \"cost\": 100000000 }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Entered cost must be between -99,999,999 and 99,999,999.")));
    assertEquals(before, itemizedRowCount(1026), "a rejected add must not persist");
  }

  @Test
  @DisplayName("POST non-numeric cost -> 400 verbatim FLD-004, nothing written")
  void add_nonNumericCost_returns400() throws Exception {
    int before = itemizedRowCount(1026);
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Bad cost\", \"cost\": \"abc\" }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("Entered cost is invalid.")));
    assertEquals(before, itemizedRowCount(1026), "a rejected add must not persist");
  }

  @Test
  @DisplayName("POST description > 30 chars -> 400 verbatim, nothing written")
  void add_overLengthDescription_returns400() throws Exception {
    int before = itemizedRowCount(1026);
    String tooLong = "X".repeat(31);
    mockMvc.perform(post(ENDPOINT).param("millId", "524").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"" + tooLong + "\", \"cost\": 5 }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("Description must be 30 characters or fewer.")));
    assertEquals(before, itemizedRowCount(1026), "a rejected add must not persist");
  }

  @Test
  @DisplayName("PUT 525/{id} — edits description + cost, recomputes")
  void update_editsRow() throws Exception {
    mockMvc.perform(put(ENDPOINT + "/5071").param("millId", "525").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Updated Row\", \"cost\": 1500 }")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
        .andExpect(jsonPath("$.rows[?(@.id == 5071)].cost", contains(1500)));
    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_COST_REPORT_DETAIL_ID = 5071 AND ITEM_DESCRIPTION = 'Updated Row' AND COST = 1500"),
        "row updated in place");
  }

  @Test
  @DisplayName("PUT unknown id -> 404 verbatim")
  void update_unknownId_returns404() throws Exception {
    mockMvc.perform(put(ENDPOINT + "/999999").param("millId", "525").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"x\", \"cost\": 1 }"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("Other cost not found.")));
  }

  @Test
  @DisplayName("DELETE 526/{id} — removes the row, shared-volume row survives")
  void delete_removesRow() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/5081").param("millId", "526").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")))
        .andExpect(jsonPath("$.count", is(0)));
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_COST_REPORT_DETAIL_ID = 5081"), "itemized row removed");
    assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_COST_REPORT_DETAIL_ID = 5080"), "shared-volume row must survive (sole-writer invariant)");
  }

  @Test
  @DisplayName("DELETE unknown id -> 404 verbatim")
  void delete_unknownId_returns404() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/999999").param("millId", "526").param("year", "2021"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Other cost not found.")));
  }

  @Test
  @DisplayName("POST against non-Draft (mill 517, track S) -> 409, nothing written")
  void add_nonDraft_returns409() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "517").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"nope\", \"cost\": 1 }"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
        "ILCR_REPORT_SUMMARY_ID = 1017 AND ITEM_DESCRIPTION = 'nope'"), "gated add must not persist");
  }
}
