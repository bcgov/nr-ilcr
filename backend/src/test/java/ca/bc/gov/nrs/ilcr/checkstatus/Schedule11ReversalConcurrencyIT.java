package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Acceptance test — two concurrent transitions out of the same Schedule 11 status give exactly one
 * 200, and the loser is refused with legacy's {@code reportSubmissionErrorMsg}.
 *
 * <p>The reversals take no row lock (deviation (U)), so there is no lock to park a thread on. The
 * barrier sits on the one read every racer makes: each unlocked {@code findTrackStatusCodes}
 * returns only once BOTH have read, so both see the same status, both pass the gate, and both reach
 * the status UPDATE. That UPDATE's {@code expectedCode} predicate is then the only thing deciding
 * the outcome: the first commits, the second waits on Oracle's row lock, re-evaluates the predicate
 * against the moved code and matches zero rows. No fixed sleep anywhere; the latch is released by
 * events, never by time.
 *
 * <p>Owns mills 830, 831 and 832 ({@code R__62}). 832 races Set to Draft against the Schedule 11
 * Verify, the two exits of {@code S}: before this story only another verify could move a
 * silviculture {@code S}.
 */
@DisplayName("Schedule 11 reversals — racing transitions, exactly one commits")
class Schedule11ReversalConcurrencyIT extends Schedule11ReversalSupport {

  private static final String VERIFY_11 = "/api/v1/check-status/schedule11/verify";

  @MockitoSpyBean private MillContextService millContextService;
  private final ObjectMapper json = new ObjectMapper();

  private record Outcome(int status, JsonNode body) {}

  @Test
  @DisplayName("830: two Set to Drafts both read S -> one 200, one 409; D committed once")
  void twoSetToDrafts_exactlyOneCommits() throws Exception {
    Map<String, Object> before = statusRow(830);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(830);
    Map<String, Object> pairsBefore = identityPairs(830);

    List<Outcome> outcomes = race(830, SET_TO_DRAFT_11, SET_TO_DRAFT_11);

    assertExactlyOneWinner(outcomes, "sch11DraftMsg");
    assertEndedAt(830, before, "D", "D", 1);
    assertThat(oneToTenCategories(830)).isEqualTo(oneToTenBefore);
    assertThat(identityPairs(830)).isEqualTo(pairsBefore);
  }

  @Test
  @DisplayName("831: two Set to Submits both read V -> one 200, one 409; S committed once")
  void twoSetToSubmits_exactlyOneCommits() throws Exception {
    Map<String, Object> before = statusRow(831);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(831);
    Map<String, Object> pairsBefore = identityPairs(831);

    List<Outcome> outcomes = race(831, SET_TO_SUBMIT_11, SET_TO_SUBMIT_11);

    assertExactlyOneWinner(outcomes, "sch11SubmittedMsg");
    assertEndedAt(831, before, "S", "A", 1);
    assertThat(oneToTenCategories(831)).isEqualTo(oneToTenBefore);
    assertThat(identityPairs(831)).isEqualTo(pairsBefore);
  }

  @Test
  @DisplayName(
      "832: Set to Draft races the Schedule 11 Verify on the same S -> one 200, one 409; the row"
          + " ends at exactly the winner's code with the winner's category")
  void setToDraftRacingVerify_exactlyOneCommits() throws Exception {
    Map<String, Object> before = statusRow(832);
    List<Map<String, Object>> oneToTenBefore = oneToTenCategories(832);

    List<Outcome> outcomes = race(832, SET_TO_DRAFT_11, VERIFY_11);

    List<Integer> statuses = outcomes.stream().map(Outcome::status).sorted().toList();
    assertThat(statuses).as(outcomes.toString()).containsExactly(200, 409);
    Outcome loser = outcomes.stream().filter(o -> o.status() == 409).findFirst().orElseThrow();
    assertThat(loser.body().path("detail").asText()).isEqualTo(SUBMISSION_ERROR);

    // The winner is whichever UPDATE reached the row first; the row must be exactly that one's.
    int winnerIndex = outcomes.get(0).status() == 200 ? 0 : 1;
    boolean draftWon = winnerIndex == 0;
    String winnerKey = outcomes.get(winnerIndex).body().path("message").path("key").asText();
    assertThat(winnerKey).isEqualTo(draftWon ? "sch11DraftMsg" : "sch11VerifiedMsg");
    assertEndedAt(832, before, draftWon ? "D" : "V", draftWon ? "D" : "V", 1);
    assertThat(oneToTenCategories(832)).isEqualTo(oneToTenBefore);
  }

  /**
   * Both requests are released only once both have read the status row; outcomes in submit order.
   */
  private List<Outcome> race(long mill, String first, String second) throws Exception {
    CountDownLatch bothHaveRead = new CountDownLatch(2);
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              bothHaveRead.countDown();
              if (!bothHaveRead.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other racer's read");
              }
              return result;
            })
        .when(millContextService)
        .findTrackStatusCodes(mill, 2021);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Outcome> outcomes = new ArrayList<>();
    try {
      Future<Outcome> a = pool.submit(racer(mill, first));
      Future<Outcome> b = pool.submit(racer(mill, second));
      outcomes.add(a.get(90, TimeUnit.SECONDS));
      outcomes.add(b.get(90, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    // Both racers really did read the unmoved status before either wrote; without this the test
    // could pass on two sequential requests, which prove only the ordinary refusal.
    assertThat(bothHaveRead.getCount()).isZero();
    return outcomes;
  }

  private Callable<Outcome> racer(long mill, String endpoint) {
    return () -> {
      MvcResult result = mockMvc.perform(reverse(endpoint, mill, 2021)).andReturn();
      String content = result.getResponse().getContentAsString();
      return new Outcome(
          result.getResponse().getStatus(), content.isBlank() ? null : json.readTree(content));
    };
  }

  private static void assertExactlyOneWinner(List<Outcome> outcomes, String successKey) {
    List<Integer> statuses = outcomes.stream().map(Outcome::status).sorted().toList();
    assertThat(statuses).as(outcomes.toString()).containsExactly(200, 409);
    Outcome winner = outcomes.stream().filter(o -> o.status() == 200).findFirst().orElseThrow();
    Outcome loser = outcomes.stream().filter(o -> o.status() == 409).findFirst().orElseThrow();
    assertThat(winner.body().path("message").path("key").asText()).isEqualTo(successKey);
    assertThat(loser.body().path("detail").asText()).isEqualTo(SUBMISSION_ERROR);
  }

  /**
   * The loser's transaction rolled back whole: the category moved exactly once, 1-10 not at all.
   */
  private void assertEndedAt(
      long mill, Map<String, Object> before, String code, String category, int categoryRevision) {
    Map<String, Object> row = statusRow(mill);
    assertThat(row.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo(code);
    assertThat(row.get("ILCR_MILL_REPORT_STATUS_CODE"))
        .isEqualTo(before.get("ILCR_MILL_REPORT_STATUS_CODE"));
    assertThat(((Number) row.get("REVISION_COUNT")).intValue())
        .isEqualTo(((Number) before.get("REVISION_COUNT")).intValue());
    Map<String, Object> eleven = categoryEleven(mill);
    assertThat(eleven.get("CATEGORY_STATE_CODE")).isEqualTo(category);
    assertThat(((Number) eleven.get("REVISION_COUNT")).intValue()).isEqualTo(categoryRevision);
  }
}
