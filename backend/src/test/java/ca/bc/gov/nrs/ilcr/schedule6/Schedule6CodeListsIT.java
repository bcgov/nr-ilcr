package ca.bc.gov.nrs.ilcr.schedule6;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 6 code lists (Task 1 correction). GET /api/v1/schedule6 now carries
 * {@code codeLists.tsaNumbers}/{@code codeLists.supplyBlocks} (code + description pairs) so the
 * page can show a name instead of a raw stored code (retires part of deviation (A)).
 *
 * <p>Security OFF ({@code ilcr.security.enabled=false}), matching {@link Schedule6DocumentIT} —
 * document assembly is isolated from authz. Uses the V20260822 fixture codes (Y9/Y9A in-window,
 * X9/X9A expired-before-2021) against the existing 514/2021 Draft context so assertions can
 * contain-check without depending on the exact seeded-image code universe.
 */
@DisplayName("GET /api/v1/schedule6 — TSA/Supply Block code lists (Schedule 6 corrections)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule6CodeListsIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule6";

  @Test
  @DisplayName("the document serves TSA numbers with their descriptions, year-filtered")
  void servesTsaNumbers() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        // In-window fixture is offered WITH its description -- the whole point of the correction.
        .andExpect(
            jsonPath("$.codeLists.tsaNumbers[?(@.code=='Y9')].description")
                .value("Fixture Timber Supply Area"))
        // Expired-before-2021 fixture is filtered out, proving the year window is real and not an
        // unfiltered SELECT that happens to look right.
        .andExpect(jsonPath("$.codeLists.tsaNumbers[?(@.code=='X9')]").isEmpty());
  }

  @Test
  @DisplayName("the document serves supply blocks with their descriptions, year-filtered")
  void servesSupplyBlocks() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.codeLists.supplyBlocks[?(@.code=='Y9A')].description")
                .value("Fixture Supply Block A"))
        .andExpect(jsonPath("$.codeLists.supplyBlocks[?(@.code=='X9A')]").isEmpty());
  }
}
