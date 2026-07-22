package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;

/**
 * S23/S24 — persistence failure rolls back completely (500 / ERR-004) and an identical retry succeeds
 * once the fault clears (Story 2.1, AC5). Isolated in its own class because the {@code @MockitoSpyBean}
 * fault seam replaces the repository for the whole context — keeping it out of {@link Schedule1WriteIT}
 * so that class's persistence assertions run against the real repository.
 *
 * <p>Uses the dedicated mill 520 (summary 1020) so the rollback assertion (revision unchanged) is not
 * perturbed by other classes. Security OFF (mock {@code ILCR_SUBMITTER}).
 */
@DisplayName("PUT /api/v1/schedule1 — persistence failure rollback + retry (Story 2.1, S23/S24)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule1WriteFailureIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule1";
  private static final long SUMMARY_ID = 1020L;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean
  private Schedule1Repository repository;

  private int revision() {
    return jdbcTemplate.queryForObject(
        "SELECT REVISION_COUNT FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = ?",
        Integer.class, SUMMARY_ID);
  }

  private static String body(int revisionCount) {
    return """
        { "revisionCount": %d,
          "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": 50000 } ],
          "otherCostsVolume": 0 }
        """.formatted(revisionCount);
  }

  @Test
  @DisplayName("save failure -> full rollback + 500 ERR-004; identical retry after clear -> 200")
  void saveFailure_rollsBackAnd500_thenRetrySucceeds() throws Exception {
    int before = revision();

    // Inject a deterministic persistence fault on the first fixed-detail write (code 12).
    doThrow(new DataIntegrityViolationException("boom"))
        .when(repository).upsertFixedDetail(anyInt(), eq(12), any(), any(), anyString());

    mockMvc.perform(put(ENDPOINT).param("millId", "520").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(before))
            .with(csrf()))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", is("Schedule could not be saved.")));

    // Full rollback: the REVISION_COUNT bump was undone with the failed detail write (S23).
    assertEquals(before, revision(), "a failed save must roll back completely (no partial write)");

    // Fault clears; the identical retry with the same (still-current) token succeeds (S24).
    reset(repository);
    mockMvc.perform(put(ENDPOINT).param("millId", "520").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(body(before))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionCount", is(before + 1)));
  }
}
