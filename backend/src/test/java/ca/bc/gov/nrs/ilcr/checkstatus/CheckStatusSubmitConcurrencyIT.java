package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acceptance test — Story 15.3 AC 6: two concurrent submits for the same mill/year serialize on the
 * status row's {@code FOR UPDATE} lock. Exactly one commits {@code S}; the other blocks, then reads
 * {@code S} and answers the guard's 409 ({@code reportSubmissionErrorMsg}), having written nothing.
 * Whichever thread wins, the database ends in the same state — one submission, one revision bump,
 * every category advanced once — which is what makes the assertion order-independent.
 *
 * <p>Owns mill 761 ({@code R__55}); security OFF. Records each request's wall time (the lock is
 * held for the whole ten-schedule gate, D10) so the Completion Notes can carry a measured number.
 */
@DisplayName(
    "POST /api/v1/check-status/submit — two racing submits serialize on the row lock (15.3)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class CheckStatusSubmitConcurrencyIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/check-status/submit";
  private static final String SUBMISSION_ERROR =
      "An error has been found submitting schedules. The error details have been logged. Please"
          + " contact ILCR application support.";

  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  private record Outcome(int status, JsonNode body, long millis) {}

  @Test
  @DisplayName("761/2021: exactly one 200, one 409 reportSubmissionErrorMsg; S committed once")
  void twoSubmits_exactlyOneCommits() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    Callable<Outcome> racer =
        () -> {
          start.await(10, TimeUnit.SECONDS);
          long began = System.nanoTime();
          MvcResult result =
              mockMvc
                  .perform(
                      post(ENDPOINT)
                          .param("millId", "761")
                          .param("year", "2021")
                          .accept(MediaType.APPLICATION_JSON))
                  .andReturn();
          long millis = (System.nanoTime() - began) / 1_000_000;
          String content = result.getResponse().getContentAsString();
          return new Outcome(
              result.getResponse().getStatus(),
              content.isBlank() ? null : json.readTree(content),
              millis);
        };

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Outcome> outcomes = new ArrayList<>();
    try {
      Future<Outcome> first = pool.submit(racer);
      Future<Outcome> second = pool.submit(racer);
      start.countDown();
      outcomes.add(first.get(90, TimeUnit.SECONDS));
      outcomes.add(second.get(90, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    List<Integer> statuses = outcomes.stream().map(Outcome::status).sorted().toList();
    assertThat(statuses).as(outcomes.toString()).containsExactly(200, 409);
    Outcome winner = outcomes.stream().filter(o -> o.status() == 200).findFirst().orElseThrow();
    Outcome loser = outcomes.stream().filter(o -> o.status() == 409).findFirst().orElseThrow();
    assertThat(winner.body().path("message").path("key").asText()).isEqualTo("sch1-10SubmittedMsg");
    assertThat(loser.body().path("detail").asText()).isEqualTo(SUBMISSION_ERROR);
    System.out.printf(
        "15.3 AC 6 timing (D10): winning submit held the lock ~%d ms; blocked submit answered in"
            + " %d ms%n",
        winner.millis(), loser.millis());

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT ILCR_MILL_REPORT_STATUS_CODE, REVISION_COUNT, LICENSEE_USER_GUID"
                + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = 761 AND REPORT_YEAR = 2021");
    assertThat(row.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("S");
    assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isEqualTo(1);
    assertThat(row.get("LICENSEE_USER_GUID")).isEqualTo(CANONICAL_SUBMITTER_GUID);

    Map<String, Object> categories =
        jdbc.queryForMap(
            "SELECT SUM(CASE WHEN CATEGORY_STATE_CODE = 'A' THEN 1 ELSE 0 END) ADVANCED,"
                + " SUM(REVISION_COUNT) REVISIONS FROM THE.ILCR_REPORT_CATEGORY"
                + " WHERE ILCR_MILL_ID = 761 AND REPORT_YEAR = 2021");
    assertThat(((Number) categories.get("ADVANCED")).intValue()).isEqualTo(10);
    assertThat(((Number) categories.get("REVISIONS")).intValue()).isEqualTo(10);
  }
}
