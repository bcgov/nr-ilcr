package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 10 deletes.
 *
 * <p>The page cascade is the reason {@code ILCR_LCRD_RCR_DTL_FK} is declared in test scope: that
 * constraint is ENABLED in delivery with no cascade, so removing a parent before its grandchildren
 * raises {@code ORA-02292}. Without the constraint here, a wrong delete order would pass every test
 * and fail in production — these tests are only meaningful because the FK exists.
 */
@DisplayName("Schedule 10 — deletes")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10DeleteIT extends AbstractOracleIT {

  private static final String PAGES = "/api/v1/schedule10/pages";
  private static final String MILL = "722";

  @Autowired
  private JdbcTemplate jdbc;

  private int countPages(int pageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
        Integer.class, pageId);
  }

  private int countDetails(int pageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
        Integer.class, pageId);
  }

  private int countCosts(int detailId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        Integer.class, detailId);
  }

  @Test
  @DisplayName("deleting a page removes its road details and their cost lines, in the right order")
  void pageDeleteCascadesToGrandchildren() throws Exception {
    // Page 8954 owns details 8966 and 8967; 8966 carries two cost lines, 8967 carries two (one of
    // them a stored NULL cost).
    assertThat(countDetails(8954)).isEqualTo(2);
    assertThat(countCosts(8966)).isEqualTo(2);
    assertThat(countCosts(8967)).isEqualTo(2);

    mockMvc.perform(delete(PAGES + "/8954").param("millId", MILL).param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));

    assertThat(countPages(8954)).isZero();
    assertThat(countDetails(8954)).isZero();
    assertThat(countCosts(8966)).isZero();
    assertThat(countCosts(8967)).isZero();
  }

  @Test
  @DisplayName("deleting one road detail leaves the page and its siblings untouched")
  void roadDetailDeleteIsSurgical() throws Exception {
    // Mill 721's page 8953 owns details 8964 and 8965. Removing one must not disturb the other.
    mockMvc.perform(delete(PAGES + "/8953/road-details/8964")
            .param("millId", "721").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")));

    assertThat(countPages(8953)).isEqualTo(1);
    assertThat(countDetails(8953)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8965", String.class))
        .isEqualTo("Copy Child Two");
  }

  @Test
  @DisplayName("a delete aimed at another mill's page is a 404 and touches nothing")
  void foreignPageDeleteIsNotFound() throws Exception {
    // Page 8955 belongs to mill 723.
    mockMvc.perform(delete(PAGES + "/8955").param("millId", MILL).param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(countPages(8955)).isEqualTo(1);
    assertThat(countDetails(8955)).isEqualTo(1);
  }

  @Test
  @DisplayName("a delete of an unknown road detail is a 404")
  void unknownRoadDetailDeleteIsNotFound() throws Exception {
    mockMvc.perform(delete(PAGES + "/8955/road-details/999999")
            .param("millId", "723").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());
  }
}
