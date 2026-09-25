package ca.bc.gov.nrs.ilcr.schedule11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Acceptance test — Story 26.2 AC 2 (CHK-006 S22): an unclassified persistence failure part-way
 * through a page-level save answers 500 {@code scheduleNotSavedErrorMsg} (ERR-004) and rolls the
 * WHOLE save back — including the location delete that had already run. A class of its own because
 * the repository spy forks the Spring context, as {@code Schedule1WriteFailureIT} does; the spy
 * precedent for a Spring Data repository is {@code ReversalRollbackIT}.
 *
 * <p>Owns mill 805's row 9428 for the failure: the save deletes it, the cost cascade that follows
 * is made to fail, and the row must still be there afterwards.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("Schedule 11 — a failing page-level save rolls back whole (26.2, ERR-004)")
class Schedule11SaveFailureIT extends AbstractOracleIT {

  @MockitoSpyBean private Schedule11Repository repository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("S22: the cost cascade fails after the location delete -> 500 ERR-004, row restored")
  void failureMidSave_rollsBackEverything() throws Exception {
    doThrow(new DataAccessResourceFailureException("ORA-00060: deadlock detected"))
        .when(repository)
        .deleteCostsForLocation(eq(9428L));

    mockMvc
        .perform(
            put("/api/v1/schedule11/locations")
                .param("millId", "805")
                .param("year", "2021")
                // Security OFF: the mock principal defaults to SUBMITTER, who may not write at the
                // silviculture 'S' of 805 — the gate would refuse 409 before the save ever ran.
                .header("X-Mock-Groups", "ILCR_ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"locations\":[],\"deletedIds\":[9428]}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail", is("Schedule could not be saved.")));

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.BASIC_SILVICULTURE_REPORT"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9428",
                Integer.class))
        .as("the location delete that ran before the failure is rolled back with it")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9428",
                Integer.class))
        .isEqualTo(2);
  }
}
