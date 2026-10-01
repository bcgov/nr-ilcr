package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — copying a Schedule 10 construction page.
 *
 * <p>The source page is seeded WITH road details on purpose: a copy that duplicated them would be
 * invisible against a childless source.
 */
@DisplayName("Schedule 10 — copy a construction page")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10CopyIT extends AbstractOracleIT {

  private static final String PAGES = "/api/v1/schedule10/pages";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("copies the page header only — the copy carries NO road details")
  void copyCarriesHeaderOnly() throws Exception {
    String body =
        mockMvc
            .perform(
                post(PAGES + "/8953/copy")
                    .param("millId", "721")
                    .param("year", "2021")
                    .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode source = null;
    JsonNode copy = null;
    for (JsonNode page : MAPPER.readTree(body).get("pages")) {
      if (page.get("pageId").asInt() == 8953) {
        source = page;
      } else if ("Copy Me".equals(page.path("divisionName").asText())) {
        copy = page;
      }
    }
    assertThat(source).isNotNull();
    assertThat(copy).as("a second page named 'Copy Me' should now exist").isNotNull();

    int copyId = copy.get("pageId").asInt();
    assertThat(copyId).isNotEqualTo(8953);
    // Legacy's copy constructor nulls both detail collections and saves without cascading.
    assertThat(copy.get("roadDetailCount").asInt()).isZero();
    assertThat(copy.get("roadDetails")).isEmpty();
    // The source keeps its own children.
    assertThat(source.get("roadDetailCount").asInt()).isEqualTo(2);

    // Header fields carried across, and the Road Group re-derived rather than copied.
    assertThat(copy.get("forestRegionCode").asText()).isEqualTo("RNI");
    assertThat(copy.get("tsaNumber").asText()).isEqualTo("01");
    assertThat(copy.get("constructionPeriod").asText()).isEqualTo("2021-08");
    assertThat(copy.get("roadGroup").asText()).isEqualTo("11");
    // A fresh row, not a clone of the source's audit state: the source sits at revision 3.
    assertThat(copy.get("revisionCount").asInt()).isZero();

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
                Integer.class,
                copyId))
        .isZero();
    // BOTH audit pairs on the copy, not just ENTRY_USERID: the copy goes through the same
    // insertPage
    // statement, so a missing stamp there would show up here first (code review 2026-08-18).
    var stored =
        jdbc.queryForMap(
            "SELECT ENTRY_USERID, UPDATE_USERID, TSB_NUMBER_CODE, TFL_NUMBER_CODE, REVISION_COUNT"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
            copyId);
    assertThat(stored)
        .containsEntry("ENTRY_USERID", "dev-submitter")
        .containsEntry("UPDATE_USERID", "dev-submitter");
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
    // The location legs are carried across verbatim — the supply block was asserted nowhere before.
    assertThat(stored).containsEntry("TSB_NUMBER_CODE", "01A");
    assertThat(stored.get("TFL_NUMBER_CODE")).isNull();
  }

  @Test
  @DisplayName("copying an unknown or foreign page is a 404")
  void unknownPageCopyIsNotFound() throws Exception {
    mockMvc
        .perform(
            post(PAGES + "/999999/copy").param("millId", "721").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());

    // Page 8955 belongs to mill 723.
    mockMvc
        .perform(
            post(PAGES + "/8955/copy").param("millId", "721").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());
  }
}
