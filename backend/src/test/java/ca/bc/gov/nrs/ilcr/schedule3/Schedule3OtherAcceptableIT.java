package ca.bc.gov.nrs.ilcr.schedule3;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Acceptance test — Story 4.4 Other Acceptable Costs sub-resource (item-124 TOT+PO&P groups, AD-5).
 * Security OFF (mock ILCR_SUBMITTER). Read mill 550 (2 seeded groups), write mill 551 (empty; each
 * lifecycle test adds AND removes its own rows so the shared container stays order-independent), and
 * non-Draft mill 552.
 */
@DisplayName("/api/v1/schedule3/other-acceptable-costs — Other Acceptable sub-resource (Story 4.4)")
class Schedule3OtherAcceptableIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3/other-acceptable-costs";

  @Test
  @DisplayName("GET returns the seeded groups with derived crown + subtotal")
  void get_returnsGroups() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "550").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.count", is(2)))
        // Group "Consulting": 800 − 300 = 500 crown.
        .andExpect(jsonPath("$.rows[?(@.description == 'Consulting')].total", contains(800)))
        .andExpect(jsonPath("$.rows[?(@.description == 'Consulting')].pop", contains(300)))
        .andExpect(jsonPath("$.rows[?(@.description == 'Consulting')].crown", contains(500)))
        // Subtotal: harvest 1400, pop 500, crown 900.
        .andExpect(jsonPath("$.subtotal.harvest", is(1400)))
        .andExpect(jsonPath("$.subtotal.pop", is(500)))
        .andExpect(jsonPath("$.subtotal.crown", is(900)));
  }

  @Test
  @DisplayName("POST → PUT → DELETE lifecycle recomputes the group and cleans up")
  void addUpdateDelete_lifecycle() throws Exception {
    // Add a group on the empty write mill.
    String added = mockMvc.perform(post(ENDPOINT).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"New Cost\", \"total\": 900, \"pop\": 100 }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.rows[?(@.description == 'New Cost')].total", contains(900)))
        .andExpect(jsonPath("$.rows[?(@.description == 'New Cost')].crown", contains(800)))
        .andReturn().getResponse().getContentAsString();
    int id = JsonPath.read(added, "$.rows[0].id");

    // Update it (crown recomputes 1000 − 400 = 600).
    mockMvc.perform(put(ENDPOINT + "/" + id).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Updated\", \"total\": 1000, \"pop\": 400 }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[?(@.description == 'Updated')].total", contains(1000)))
        .andExpect(jsonPath("$.rows[?(@.description == 'Updated')].pop", contains(400)))
        .andExpect(jsonPath("$.rows[?(@.description == 'Updated')].crown", contains(600)));

    // Delete it (back to empty).
    mockMvc.perform(delete(ENDPOINT + "/" + id).param("millId", "551").param("year", "2021"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.count", is(0)));
  }

  @Test
  @DisplayName("POST blank description → 400 FLD-003")
  void addBlankDescription_returns400() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"\", \"total\": 100, \"pop\": 50 }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("POST out-of-range total → 400 FLD-001")
  void addOutOfRange_returns400() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"X\", \"total\": 100000000, \"pop\": 0 }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("PUT/DELETE unknown id → 404")
  void unknownId_returns404() throws Exception {
    mockMvc.perform(put(ENDPOINT + "/999999").param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"X\", \"total\": 1, \"pop\": 0 }"))
        .andExpect(status().isNotFound());
    mockMvc.perform(delete(ENDPOINT + "/999999").param("millId", "551").param("year", "2021"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST on a non-Draft schedule → 409")
  void addNonDraft_returns409() throws Exception {
    mockMvc.perform(post(ENDPOINT).param("millId", "552").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"X\", \"total\": 1, \"pop\": 0 }"))
        .andExpect(status().isConflict());
  }
}
