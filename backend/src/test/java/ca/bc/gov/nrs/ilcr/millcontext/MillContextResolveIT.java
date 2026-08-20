package ca.bc.gov.nrs.ilcr.millcontext;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance tests — Story 1.2 (AD-10, AD-12 amended contract). {@code GET /api/v1/mill-context}:
 * resolve a chosen mill/year into the pinned {@code WorkingContext} with both independent track
 * statuses (AR6), the S06 closed-mill flag, S07 null-status tolerance, the S04/S05/S08 verbatim
 * required-field 400s, and 404 for unknown mill/year.
 *
 * <p>Runs with security ON ({@code ilcr.security.enabled=true}, app default). Since O4
 * (fam-auth-1-1) Home renders after sign-in, so this endpoint is no longer public — every request
 * carries an authenticated principal ({@code AUTH}), and an unauthenticated request is 401
 * (asserted in {@code millContextRequiresAuthentication}). {@code @MockitoBean JwtDecoder}
 * satisfies {@code oauth2ResourceServer().jwt()} construction (never invoked — the {@code jwt()}
 * post-processor injects the principal directly).
 *
 * <p>Fixtures (V2/V4/V8/V9 — the Home fixtures were renumbered V5/V6→V8/V9 in Story 1.3 after the
 * schedule-track seeds claimed V5-V7; fixture-robust assertions only — no positional/exact-count
 * coupling): 514/2020 both tracks (S + silvi D, dates); 514/2021 1-10 D only (silvi NULL, draft
 * date); 516/2021 closed (CLS, no view row → date null); (515, 2020) selectable mill + opened year
 * with NO status row (S07); 540 never-enrolled (not selectable → 404); 2019 not opened.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Home working context — GET /api/v1/mill-context (Story 1.2)")
class MillContextResolveIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/mill-context";
  private static final String MILL_REQUIRED = "Mill: Value is required.";
  private static final String YEAR_REQUIRED = "Reporting Year: Value is required.";
  // Home is post-login (O4): the endpoint needs an authenticated caller, not a specific role
  // (no @PreAuthorize, no per-user filter yet — that arrives with Epic 5).
  private static final RequestPostProcessor AUTH = jwt();

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  @DisplayName("514/2020 — 200 with BOTH independent track statuses and their dates")
  void bothTracks_returnsFullContext() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "514")
                .param("year", "2020")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(514)))
        .andExpect(jsonPath("$.millNumber", is("514")))
        .andExpect(jsonPath("$.millName", is("AAA Milling")))
        .andExpect(jsonPath("$.reportYear", is(2020)))
        .andExpect(jsonPath("$.millViewable", is(true)))
        .andExpect(jsonPath("$.schedules1To10Status.code", is("S")))
        .andExpect(jsonPath("$.schedules1To10Status.description", is("Submitted")))
        // Submitted -> MILL_STATUS_SUBMIT_DATE '02 2020-11-30' with 3-char prefix stripped.
        .andExpect(jsonPath("$.schedules1To10Status.date", is("2020-11-30")))
        .andExpect(jsonPath("$.schedule11Status.code", is("D")))
        .andExpect(jsonPath("$.schedule11Status.description", is("Draft")))
        // Silvi Draft -> SILVI_STATUS_DRAFT_DATE (each track uses its OWN code — the legacy
        // cross-track bug is deliberately not reproduced).
        .andExpect(jsonPath("$.schedule11Status.date", is("2020-08-01")))
        // Story 1.3 amendment (AC7): every 200 carries the SUC-001 message (reused legacy
        // bundle key + server-resolved verbatim text). The frontend only DISPLAYS it after an
        // explicit Save (the 1.4 banner load must ignore it).
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
  }

  @Test
  @DisplayName("Story 1.3 (AC7) — every 200 carries the SUC-001 message from the legacy bundle")
  void successResponse_carriesSuc001Message() throws Exception {
    // Even a minimal 200 (S07: selectable mill + opened year, no status row) carries message.
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "515")
                .param("year", "2020")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is("Data saved successfully")));
  }

  @Test
  @DisplayName("514/2021 — 200, 1-10 Draft with date; NULL silvi code -> schedule11Status absent")
  void nullSilviCode_omitsSchedule11Status() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "514")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.schedules1To10Status.code", is("D")))
        .andExpect(jsonPath("$.schedules1To10Status.description", is("Draft")))
        .andExpect(jsonPath("$.schedules1To10Status.date", is("2021-03-15")))
        .andExpect(jsonPath("$.schedule11Status").doesNotExist())
        .andExpect(jsonPath("$.millViewable", is(true)));
  }

  @Test
  @DisplayName("516/2021 closed mill — 200 with millViewable:false, date absent (S06)")
  void closedMill_isFlagNotError() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "516")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.millId", is(516)))
        .andExpect(jsonPath("$.millViewable", is(false)))
        .andExpect(jsonPath("$.schedules1To10Status.code", is("D")))
        // No ILCR_MILL_REPORT_STATUS_RPT_VW row for 516/2021 -> date null -> omitted
        // (frontend renders "Not Initiated", Story 1.4).
        .andExpect(jsonPath("$.schedules1To10Status.date").doesNotExist());
  }

  @Test
  @DisplayName("(515, 2020) no status row — 200 with both statuses absent (S07)")
  void noStatusRow_returnsContextWithNullStatuses() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "515")
                .param("year", "2020")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.millId", is(515)))
        .andExpect(jsonPath("$.reportYear", is(2020)))
        .andExpect(jsonPath("$.millViewable", is(true)))
        .andExpect(jsonPath("$.schedules1To10Status").doesNotExist())
        .andExpect(jsonPath("$.schedule11Status").doesNotExist());
  }

  @Test
  @DisplayName("missing millId — 400 problem+json with verbatim FLD-001 text (S04)")
  void missingMill_returns400WithVerbatimText() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).with(AUTH).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString(MILL_REQUIRED)))
        .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED)));
  }

  @Test
  @DisplayName("missing year — 400 with verbatim FLD-002 text (S05)")
  void missingYear_returns400WithVerbatimText() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).with(AUTH).param("millId", "514").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString(YEAR_REQUIRED)))
        .andExpect(jsonPath("$.messages[*].text", contains(YEAR_REQUIRED)));
  }

  @Test
  @DisplayName("both missing — 400 carries BOTH verbatim texts together, Mill first (S08)")
  void bothMissing_returns400WithBothMessages() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).with(AUTH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString(MILL_REQUIRED)))
        .andExpect(jsonPath("$.detail", containsString(YEAR_REQUIRED)))
        // Field order mirrors home.xhtml: Mill, then Reporting Year.
        .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED, YEAR_REQUIRED)));
  }

  @Test
  @DisplayName(
      "non-numeric millId — 400 with the Mill required text (legacy: only valid options exist)")
  void nonNumericMill_returns400WithVerbatimText() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "abc")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.messages[*].text", contains(MILL_REQUIRED)));
  }

  @Test
  @DisplayName("unknown mill 999 / never-enrolled 540 / unopened year 2019 — 404 problem+json")
  void unknownMillOrYear_returns404() throws Exception {
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "999")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

    // Mill 540 has an xref but NO report-status row for any year -> not selectable (legacy
    // getMills() parity, Story 1.1 review decision) -> 404, same as an unknown id. (522 is now
    // the schedule fixtures' enrolled mill, so Home's never-enrolled probe uses 540.)
    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "540")
                .param("year", "2021")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get(ENDPOINT)
                .with(AUTH)
                .param("millId", "514")
                .param("year", "2019")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "auth boundary (O4) — mill-context requires authentication; GET and POST 401 unauthenticated")
  void millContextRequiresAuthentication() throws Exception {
    // Since O4 (fam-auth-1-1), Home is post-login: an unauthenticated GET is no longer permitted
    // (the authenticated cases above all pass AUTH). POST was never permitted.
    mockMvc
        .perform(get(ENDPOINT).param("millId", "514").param("year", "2020"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post(ENDPOINT).with(csrf())).andExpect(status().isUnauthorized());
  }
}
