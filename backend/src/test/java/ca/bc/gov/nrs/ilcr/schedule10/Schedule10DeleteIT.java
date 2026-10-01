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

  @Autowired private JdbcTemplate jdbc;

  private int countPages(int pageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
        Integer.class,
        pageId);
  }

  private int countDetails(int pageId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
        Integer.class,
        pageId);
  }

  private int countCosts(int detailId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        Integer.class,
        detailId);
  }

  @Test
  @DisplayName("deleting a page removes its road details and their cost lines, in the right order")
  void pageDeleteCascadesToGrandchildren() throws Exception {
    // Page 8954 owns details 8966 and 8967; 8966 carries two cost lines, 8967 carries two (one of
    // them a stored NULL cost).
    assertThat(countDetails(8954)).isEqualTo(2);
    assertThat(countCosts(8966)).isEqualTo(2);
    assertThat(countCosts(8967)).isEqualTo(2);

    mockMvc
        .perform(delete(PAGES + "/8954").param("millId", MILL).param("year", "2021").with(csrf()))
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
    // Page 8958 (mill 721, year 2022) owns details 8971 and 8972. Removing one must not disturb the
    // other. This owns its own (mill, YEAR): it previously deleted from page 8953, which
    // Schedule10CopyIT asserts still has TWO details, so the two classes shared (721, 2021) and
    // CopyIT passed only because "Copy" sorts before "Delete" (code review 2026-08-18).
    mockMvc
        .perform(
            delete(PAGES + "/8958/road-details/8971")
                .param("millId", "721")
                .param("year", "2022")
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")));

    assertThat(countPages(8958)).isEqualTo(1);
    assertThat(countDetails(8958)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8972",
                String.class))
        .isEqualTo("Surgical Sibling");
  }

  @Test
  @DisplayName("a delete aimed at another mill's page is a 404 and touches nothing")
  void foreignPageDeleteIsNotFound() throws Exception {
    // Page 8955 belongs to mill 723.
    mockMvc
        .perform(delete(PAGES + "/8955").param("millId", MILL).param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(countPages(8955)).isEqualTo(1);
    assertThat(countDetails(8955)).isEqualTo(1);
  }

  @Test
  @DisplayName("the YEAR leg of the delete predicate is enforced, not just the mill")
  void wrongYearDeleteIsNotFound() throws Exception {
    // Page 8908 is mill 710 / year 2020 (seeded by the 11.1 read fixtures, unused on the write path
    // until now). Addressed under the right mill but year 2021 it must miss on the year leg alone.
    // 2021 is deliberate: mill 710 is Draft in both 2020 and 2021, so requireDraft passes and the
    // 404 can only come from the page predicate. Aiming at a year with no status row would have
    // produced a 404 for an unrelated reason and proved nothing.
    // Code review 2026-08-18: every foreign-context test varied only the mill, so removing
    // "AND REPORT_YEAR = :year" from countPage/deletePage broke no assertion.
    mockMvc
        .perform(delete(PAGES + "/8908").param("millId", "710").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(countPages(8908)).isEqualTo(1);
  }

  @Test
  @DisplayName("the CATEGORY leg is enforced — a non-Schedule-10 page is invisible to this API")
  void wrongCategoryDeleteIsNotFound() throws Exception {
    // Page 8909 IS mill 710 / year 2021 — correct on both legs — but carries ILCR_CATEGORY_ID =
    // '99'.
    // It must still be a 404, or Schedule 10 could delete another schedule's row.
    mockMvc
        .perform(delete(PAGES + "/8909").param("millId", "710").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(countPages(8909)).isEqualTo(1);
  }

  @Test
  @DisplayName("a road-detail delete from a FOREIGN mill is a 404 and touches nothing")
  void foreignMillRoadDetailDeleteIsNotFound() throws Exception {
    // Detail 8968 under page 8955 belongs to mill 723. Addressed with the correct page/detail pair
    // but a foreign mill, it must miss. The delete SQL carries no mill predicate of its own — the
    // whole guard is countRoadDetail's — and code review 2026-08-18 found no test exercising it, so
    // deleting "AND r.ILCR_MILL_ID = :millId" from that probe left the suite green while enabling
    // cross-mill deletion.
    mockMvc
        .perform(
            delete(PAGES + "/8955/road-details/8968")
                .param("millId", "722")
                .param("year", "2021")
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertThat(countDetails(8955)).isEqualTo(1);
  }

  @Test
  @DisplayName("a delete of an unknown road detail is a 404 and touches nothing")
  void unknownRoadDetailDeleteIsNotFound() throws Exception {
    mockMvc
        .perform(
            delete(PAGES + "/8955/road-details/999999")
                .param("millId", "723")
                .param("year", "2021")
                .with(csrf()))
        .andExpect(status().isNotFound());

    // Asserted, not assumed: this test previously checked only the status code.
    assertThat(countDetails(8955)).isEqualTo(1);
  }
}
