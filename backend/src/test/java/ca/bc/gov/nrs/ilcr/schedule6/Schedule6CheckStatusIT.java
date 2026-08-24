package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 8.2/Task 6/Task 8 acceptance — {@code POST /api/v1/schedule6/check-status}: the body is now
 * REQUIRED (Task 8 removed the transitional stored-rows fallback), so every case here is
 * payload-driven. The byte-for-byte composed lines (the legacy cost mislabel, the space on both
 * sides of the final colon, the D2 zero-cost-is-MET quirk, issue ordering, the vacuous MET pass)
 * are pinned source-agnostically at the service/controller seam by {@link
 * Schedule6CheckStatusServiceTest} and {@link Schedule6CheckStatusCompositionTest} — those apply
 * regardless of whether a candidate came from a stored row or a payload row, so they needed no
 * changes when the stored-rows source was retired. This class is left proving only what is specific
 * to the HTTP/payload seam: the verdict follows the submitted values, the endpoint persists
 * nothing, and the context guard is reused.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule6/check-status — payload-driven readiness (Task 6/Task 8)")
class Schedule6CheckStatusIT extends AbstractOracleIT {

  private static final String CHECK_STATUS = "/api/v1/schedule6/check-status";

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("the verdict reflects the SUBMITTED values, not the stored ones")
  void evaluatesSubmittedValues() throws Exception {
    // The stored row 8399 (mill 726/2020) has a cost. Submit the same row with cost cleared:
    // legacy's ajax="false" postback applied the screen to the model before evaluating
    // (Schedule6MB.checkStatus :139-140), so the verdict must follow the screen.
    String body =
        """
        {"generalComments":null,
         "records":[{"areaType":"Y9","supplyBlock":"Y9A","volume":10,"cost":null,
                     "comments":null}]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "726")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("ISSUES"))
        .andExpect(
            jsonPath("$.records[0].issues[?(@.field=='cost')].message.text")
                .value("Road : 1 - TSA or TFL (Cost $) : Value Required"));
  }

  @Test
  @DisplayName("check status persists nothing")
  void persistsNothing() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String before =
        jdbc.queryForObject(
            "SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8399",
            String.class);
    String body =
        """
        {"generalComments":"typed but never saved",
         "records":[{"areaType":"Y9","supplyBlock":"Y9A","volume":10,"cost":5,"comments":null}]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "726")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals(
        before,
        jdbc.queryForObject(
            "SELECT COMMENTS FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8399",
            String.class));
  }

  @Test
  @DisplayName(
      "VIEW-gated, not Draft-gated: check-status runs on a non-Draft ('S') mill (2.6 precedent)")
  void nonDraftTrack_stillChecks() throws Exception {
    // Mill 662/2021 is 'S' (non-Draft, shared read-only fixture) -- the endpoint must NOT 409.
    // Removing requireDraft would only be caught indirectly today (by a stubbed-repository unit
    // test noticing an extra call); this is the HTTP-level proof of the 2.6 precedent itself.
    String body =
        """
        {"generalComments":null,
         "records":[{"areaType":"01","supplyBlock":"01B","cost":1}]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "662")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome", is("MET")));
  }

  @Test
  @DisplayName("a null entry in records[] is a clean 400, never an NPE-to-500")
  void nullEntryInRecords_returns400() throws Exception {
    String body =
        """
        {"generalComments":null,"records":[null]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("millId", "726")
                .param("year", "2020")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isBadRequest())
        // Bean Validation's element-type @NotNull now carries a bundle key (final-review M6) rather
        // than falling back to its English default -- every sibling in Schedule6SaveDocumentIT
        // asserts $.detail verbatim, not just the status.
        .andExpect(jsonPath("$.detail", is("Value Required")));
  }

  @Test
  @DisplayName(
      "Context guard reused: missing millId -> 400 verbatim ERR-001 (trailing space), even with "
          + "a well-formed body")
  void missingContext_returns400Err001() throws Exception {
    String body =
        """
        {"generalComments":null,"records":[]}
        """;
    mockMvc
        .perform(
            post(CHECK_STATUS)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")));
  }
}
