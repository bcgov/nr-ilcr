package ca.bc.gov.nrs.ilcr;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Cross-schedule regression pin for the APP-WIDE {@code
 * spring.jackson.deserialization.accept-float-as-int: false} in {@code application.yml}.
 *
 * <p><strong>Why this class exists.</strong> Story 7.2 needed fractional cost input REJECTED rather
 * than silently truncated (AC6), and the same latent truncation had already been recorded repo-wide
 * ({@code deferred-work.md}: "Fractional cost input is silently truncated to whole dollars …
 * Cross-cutting — identical behaviour in Schedule 1"). Turning the Jackson feature off in the
 * shared {@code application.yml} closed it for every schedule at once instead of leaving Schedule 5
 * the only safe one — but it is a compatibility change to every endpoint in the application, and
 * the only evidence offered for it was that the whole suite stayed green. Code review (PR #242)
 * asked for the blast radius to be MEASURED on schedules other than 5 rather than inferred. That is
 * this class: it asserts, on endpoints owned by other epics, that a fractional value posted into an
 * {@code Integer} field is a clean 400 and not a 200-with-a-different-number.
 *
 * <p><strong>What a failure here means.</strong> Not necessarily a bug — it means the app-wide
 * deserialization contract moved. Either the {@code application.yml} setting was removed (restoring
 * silent truncation everywhere, which is the defect) or a DTO's field type changed. Both are
 * decisions, so both should be made deliberately rather than discovered in production.
 *
 * <p><strong>Both requests are aimed at mill 671/2021, whose Schedules 1–10 track is {@code S}, not
 * Draft.</strong> That is a fail-safe, not an accident. Body binding happens during
 * handler-argument resolution, so a rejected value never reaches a controller, a service, or the
 * database — but if the pin ever regressed, a Draft mill/year would let these requests write real
 * rows before the assertion failed. Against a Submitted one the regression surfaces as a 409 from
 * the Draft gate and still touches nothing.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("accept-float-as-int: false holds across schedules, not just Schedule 5")
class JacksonIntegerCoercionIT extends AbstractOracleIT {

  /** Submitted (not Draft) — see the class javadoc on why that matters. */
  private static final String MILL = "671";

  private static final String YEAR = "2021";

  private static final String PROBLEM_JSON = "application/problem+json";

  /**
   * The verbatim legacy {@code costConverterErrorMsg} (FLD-004). A refused float reaches {@code
   * GlobalExceptionHandler.handleNotReadable}, whose cause message names {@code java.lang.Integer},
   * so it resolves the COST converter text rather than the generic "request body is invalid" —
   * asserted rather than assumed, because that branch is what makes the rejection legible to a
   * licensee instead of merely correct.
   */
  private static final String COST_CONVERTER_MSG = "Entered cost is invalid.";

  @Test
  @DisplayName("Schedule 1 other-costs: a fractional cost is 400, never truncated to 1234")
  void schedule1OtherCostRejectsFractionalCost() throws Exception {
    // ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRequest#cost is an Integer. With Jackson's default
    // ACCEPT_FLOAT_AS_INT this bound as 1234 and the licensee was told the save succeeded with a
    // number they never typed.
    mockMvc
        .perform(
            post("/api/v1/schedule1/other-costs")
                .with(csrf())
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Coercion probe\",\"cost\":1234.99}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(COST_CONVERTER_MSG)));
  }

  @Test
  @DisplayName("Schedule 6 road records: a fractional cost is 400, never truncated to 1234")
  void schedule6RoadRecordRejectsFractionalCost() throws Exception {
    // ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest#cost is an Integer; its sibling volume is
    // a
    // BigDecimal and legitimately fractional, so this endpoint proves the change bites ONLY the
    // integer-typed fields it was aimed at.
    mockMvc
        .perform(
            post("/api/v1/schedule6/records")
                .with(csrf())
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roadName\":\"Coercion probe\",\"volume\":10.55,\"cost\":1234.99}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(COST_CONVERTER_MSG)));
  }

  @Test
  @DisplayName(
      "a WHOLE number written as 1234.0 is rejected too — the rule is the type, not the value")
  void trailingZeroIsStillFractionalJson() throws Exception {
    // The sharp edge of the compatibility change, and the one most likely to surprise an existing
    // client: 1234.0 loses nothing on truncation, but it is a JSON float and is now refused all the
    // same. Pinned so the behaviour is a recorded decision rather than a support ticket.
    mockMvc
        .perform(
            post("/api/v1/schedule1/other-costs")
                .with(csrf())
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Coercion probe\",\"cost\":1234.0}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(COST_CONVERTER_MSG)));
  }
}
