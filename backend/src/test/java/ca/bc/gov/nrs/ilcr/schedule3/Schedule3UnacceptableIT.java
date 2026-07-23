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
 * Acceptance test — Story 4.4 Included Unacceptable Costs sub-resource (item-38 rows, AD-5). Security
 * OFF (mock ILCR_SUBMITTER). Read mill 550 (1 seeded row + Annual Rents S111), write mill 551 (empty).
 */
@DisplayName("/api/v1/schedule3/included-unacceptable-costs — Unacceptable sub-resource (Story 4.4)")
class Schedule3UnacceptableIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule3/included-unacceptable-costs";

  @Test
  @DisplayName("GET returns the seeded row, subtotal, and read-only Annual Rents (S111)")
  void get_returnsRows() throws Exception {
    mockMvc.perform(get(ENDPOINT).param("millId", "550").param("year", "2021")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.count", is(1)))
        .andExpect(jsonPath("$.rows[?(@.description == 'Penalty')].total", contains(250)))
        .andExpect(jsonPath("$.subtotalTotal", is(250)))
        // Annual Rents (Forest Act, S111) = the item-29 Harvest (read-only).
        .andExpect(jsonPath("$.annualRentsTotal", is(777)));
  }

  @Test
  @DisplayName("POST → PUT → DELETE lifecycle recomputes the subtotal and cleans up")
  void addUpdateDelete_lifecycle() throws Exception {
    String added = mockMvc.perform(post(ENDPOINT).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Fine\", \"total\": 500 }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.rows[?(@.description == 'Fine')].total", contains(500)))
        .andReturn().getResponse().getContentAsString();
    int id = JsonPath.read(added, "$.rows[0].id");

    mockMvc.perform(put(ENDPOINT + "/" + id).param("millId", "551").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{ \"description\": \"Fine (revised)\", \"total\": 650 }"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[?(@.description == 'Fine (revised)')].total", contains(650)))
        .andExpect(jsonPath("$.subtotalTotal", is(650)));

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
            .content("{ \"description\": \"\", \"total\": 100 }"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
  }

  @Test
  @DisplayName("DELETE unknown id → 404")
  void unknownId_returns404() throws Exception {
    mockMvc.perform(delete(ENDPOINT + "/999999").param("millId", "551").param("year", "2021"))
        .andExpect(status().isNotFound());
  }
}
