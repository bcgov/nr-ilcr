package ca.bc.gov.nrs.ilcr.schedule6;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Task 3 acceptance — {@code DELETE /api/v1/schedule6/records/{recordId}}: one road record deleted,
 * including the BR-09 delete-side placeholder re-insert that keeps the schedule-level general
 * comment alive when the last road record goes ({@code Schedule6DAO.java:297-309}). Security-off is
 * pinned EXPLICITLY, matching {@link Schedule6WriteIT}; authz is proven in {@link
 * Schedule6AuthorizationIT} / {@link Schedule6WriteAuthorizationIT}.
 *
 * <p>Fixture mills 667-669 (V20260822 migration) are each dedicated to one destructive test, so
 * these tests are order-independent and never touch a mill/year another suite fingerprints (the
 * V32-established "one destructive test per context" contract).
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("DELETE /api/v1/schedule6/records/{recordId} — Schedule 6 delete (Task 3)")
class Schedule6DeleteIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule6/records";

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("deleting the sole record re-inserts a placeholder so the comment survives")
  void deletingSoleRecordPreservesGeneralComment() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/8370").param("millId", "667").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        // The road record is gone from the served list...
        .andExpect(jsonPath("$.roadRecords").isEmpty())
        // ...but the comment it was storing is still the schedule's general comment. This is the
        // whole point of the BR-09 delete-side re-insert (Schedule6DAO.java:297-309); without it a
        // naive delete silently destroys the comment.
        .andExpect(jsonPath("$.generalComments", is("Comment that must survive the delete")))
        .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));

    // The surviving row is a real placeholder: classification all NULL, revision 0, no cost detail,
    // and a DIFFERENT id from the deleted 8370 -- a fresh INSERT (repository.nextRoadReportId()),
    // never a mutated survivor of the deleted row (code review 2026-08-21).
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> placeholder =
        jdbc.queryForMap(
            """
            SELECT ROAD_MAINTENANCE_REPORT_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE,
                   COMMENTS, REVISION_COUNT
              FROM THE.ROAD_MAINTENANCE_REPORT
             WHERE ILCR_MILL_ID = 667 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '6'
            """);
    assertNotEquals(8370, ((Number) placeholder.get("ROAD_MAINTENANCE_REPORT_ID")).intValue());
    assertEquals(null, placeholder.get("TSA_NUMBER"));
    assertEquals(null, placeholder.get("TSB_NUMBER_CODE"));
    assertEquals(null, placeholder.get("TFL_NUMBER_CODE"));
    assertEquals("Comment that must survive the delete", placeholder.get("COMMENTS"));
    assertEquals(0, ((Number) placeholder.get("REVISION_COUNT")).intValue());

    // The deleted row's item-69 detail is gone, not orphaned.
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8370",
            Integer.class));
  }

  @Test
  @DisplayName("deleting the sole record with a blank comment leaves nothing behind")
  void deletingSoleRecordWithBlankCommentLeavesNoPlaceholder() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/8371").param("millId", "668").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords").isEmpty())
        .andExpect(jsonPath("$.generalComments").doesNotExist());

    // Legacy re-inserts ONLY when the deleted row's comment was non-EMPTY (:297, via
    // CoreUtil.isNullOrEmptyString). This row's COMMENTS is NULL, so the branch is off. A
    // placeholder here would resurrect an empty comment and strand a bare row forever.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 668 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '6'",
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8371",
            Integer.class));
  }

  @Test
  @DisplayName(
      "deleting one of TWO records leaves the survivor and its comment intact, no placeholder")
  void deletingNonSoleRecordLeavesSurvivorUntouched() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/8372").param("millId", "669").param("year", "2021").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roadRecords", hasSize(1)))
        .andExpect(jsonPath("$.roadRecords[0].recordId", is(8373)))
        .andExpect(jsonPath("$.generalComments", is("Replicated comment.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // Exactly one row remains under this mill/year -- the survivor, never a re-inserted placeholder
    // (the BR-09 re-insert branch is reserved for deleting the SOLE road record).
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ILCR_MILL_ID = 669 AND REPORT_YEAR = 2021 AND ILCR_CATEGORY_ID = '6'",
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8372",
            Integer.class));
  }

  @Test
  @DisplayName("an unknown record id is a 404")
  void unknownIdIsNotFound() throws Exception {
    mockMvc
        .perform(
            delete(RECORDS + "/99999999").param("millId", "667").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));
  }

  @Test
  @DisplayName("a record belonging to another mill is a 404 -- the IDOR scope guard")
  void foreignMillRecordIsNotFound() throws Exception {
    // 8373 belongs to mill 669/2021 -- addressing it via mill 667 is foreign. Deliberately NOT
    // 8372: deletingNonSoleRecordLeavesSurvivorUntouched deletes that row, so asserting its
    // survival here would be order-dependent on JUnit's method-execution order (code review
    // 2026-08-21). 8373 is never deleted by any test in this class, so this proof is genuinely
    // order-free.
    mockMvc
        .perform(
            delete(RECORDS + "/8373").param("millId", "667").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));

    // ...and it is still there afterwards: the guard rejected, it did not delete.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8373",
            Integer.class));
  }

  @Test
  @DisplayName("a placeholder id is a 404 -- it is not a served record")
  void placeholderIdIsNotFound() throws Exception {
    // 660/2021's record 8304 (V31) is a genuine placeholder: classification all NULL.
    mockMvc
        .perform(
            delete(RECORDS + "/8304").param("millId", "660").param("year", "2021").with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is("Road record not found.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8304",
            Integer.class));
  }

  @Test
  @DisplayName("a non-Draft (status 'S') mill/year rejects the delete with 409; nothing mutates")
  void nonDraftTrackReturns409() throws Exception {
    // 662/2021 (V32) is status 'S' with record 8321 -- a rejected delete mutates nothing, so this
    // context is safe to reuse (no fixture churn for other suites reading it).
    mockMvc
        .perform(
            delete(RECORDS + "/8321").param("millId", "662").param("year", "2021").with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ROAD_MAINTENANCE_REPORT "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8321",
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
                + "WHERE ROAD_MAINTENANCE_REPORT_ID = 8321",
            Integer.class));
  }
}
