package ca.bc.gov.nrs.ilcr.userlookup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for the outbound NR User Lookup calls against a mock HTTP server: the service-account
 * token exchange (its per-scope keying, its expiry arithmetic, and the retry that recovers a
 * rotated credential), the request shapes from Paulo's documented contract, the identity mapping
 * rules — {@code guid} is the association key, {@code businessGuid} never is — and the failure
 * translation the screen depends on.
 *
 * <p>Several tests here are deliberately negative controls: they assert what must NOT appear in the
 * outbound request, because the mapping bugs that matter most in this slice (a criterion sent under
 * the wrong name, a blank criterion sent as a filter, a scope-crossed token) all produce a
 * perfectly well-formed request that simply asks the wrong question.
 */
@DisplayName("UserLookupClient — NR User Lookup API calls (UC-USR-001, DL-27)")
class UserLookupClientTest {

  private static final String BASE = "https://lookup.example";
  private static final String TOKEN_URL = "https://sso.example/token";
  private static final String SEARCH_URL = BASE + "/api/v1/user-lookup/idir-users/search";
  private static final String BCEID_URL = BASE + "/api/v1/user-lookup/businessBceid";
  private static final String GUID = "AAAABBBBCCCCDDDDEEEEFFFF00001111";
  private static final String SCOPE_IDIR = "user-lookup:idir:search";
  private static final String SCOPE_BCEID = "user-lookup:business-bceid:read";

  private static final String EXPECTED_BASIC =
      "Basic "
          + Base64.getEncoder()
              .encodeToString("ilcr-service:vault-secret".getBytes(StandardCharsets.UTF_8));

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-08-25T12:00:00Z"));
  private final UserLookupClient client = clientOver(BASE);

  private UserLookupClient clientOver(String baseUrl) {
    return new UserLookupClient(
        builder.build(), baseUrl, TOKEN_URL, "ilcr-service", "vault-secret", clock);
  }

  @Test
  @DisplayName("an IDIR search authenticates with the service account and maps the candidates")
  void idirSearchUsesTheServiceTokenAndMaps() {
    expectToken(SCOPE_IDIR, "svc-token-1");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(method(HttpMethod.POST))
        // The SERVICE token — an end-user token here would violate the lookup API's contract.
        .andExpect(header("Authorization", "Bearer svc-token-1"))
        .andExpect(jsonPath("$.firstName").value("jane"))
        .andRespond(
            withSuccess(
                """
                [{"guid":"%s","userId":"JDOE","firstName":"Jane","lastName":"Doe",
                  "email":"jane@gov.bc.ca","unknownFutureField":true}]
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.searchIdir("jane", null, null);

    assertEquals(1, found.size());
    assertEquals(GUID, found.get(0).userGuid());
    assertEquals("Jane Doe", found.get(0).displayName());
    assertEquals("JDOE", found.get(0).idpUsername());
    assertEquals("IDIR", found.get(0).identityProvider());
    server.verify();
  }

  @Test
  @DisplayName("each criterion travels under its own name, and the documented page size is sent")
  void everyCriterionTravelsUnderItsOwnName() {
    // Without per-field assertions, transposing two of the three putIfNotBlank names — or the
    // controller's last two arguments — ships a picker that searches the wrong field entirely,
    // with every other test in this class still green.
    expectToken(SCOPE_IDIR, "svc-token-a");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(jsonPath("$.firstName").value("jane"))
        .andExpect(jsonPath("$.lastName").value("doe"))
        .andExpect(jsonPath("$.userId").value("JDOE"))
        .andExpect(jsonPath("$.pageSize").value(50))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertTrue(client.searchIdir("jane", "doe", "JDOE").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("blank criteria are omitted from the request rather than sent as empty filters")
  void blankCriteriaAreOmittedFromTheRequest() {
    // The negative control for putIfNotBlank: an unconditional put would still satisfy every
    // value-based assertion above while sending nulls the directory may treat as real filters.
    expectToken(SCOPE_IDIR, "svc-token-b");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(jsonPath("$.firstName").value("jane"))
        .andExpect(jsonPath("$.lastName").doesNotExist())
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertTrue(client.searchIdir("jane", "   ", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a criterion padded with whitespace is trimmed before it becomes a filter")
  void criteriaAreTrimmed() {
    expectToken(SCOPE_IDIR, "svc-token-c");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(jsonPath("$.firstName").value("jane"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertTrue(client.searchIdir("  jane\n", null, null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("the service token is fetched once and reused while fresh")
  void tokenIsReusedWhileFresh() {
    expectToken(SCOPE_IDIR, "svc-token-2");
    expectSearchWithBearer("svc-token-2");
    expectSearchWithBearer("svc-token-2");

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("the two operations hold separate tokens — a scope is never reused across them")
  void tokensAreKeyedByScope() {
    // The cache being keyed by SCOPE (not merely present) is the security property the class
    // advertises. Collapse tokensByScope to a single slot and this is the only test that fails —
    // in production the collapse would hand the BCeID endpoint an IDIR-search token and every
    // BCeID lookup would 403, surfacing as "the directory is unavailable".
    expectToken(SCOPE_IDIR, "idir-token");
    expectSearchWithBearer("idir-token");
    expectToken(SCOPE_BCEID, "bceid-token");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userId", "bizuser"))
        .andExpect(header("Authorization", "Bearer bceid-token"))
        .andRespond(withResourceNotFound());

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    assertTrue(client.findBusinessBceid("userId", "bizuser").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a token is re-exchanged once its renewal margin is reached")
  void tokenIsReExchangedAfterTheRenewalMargin() {
    // expires_in 300 minus the 60s renewal margin leaves 240s of freshness. Without a Clock seam
    // the margin is unobservable, so its value — and the isAfter comparison — could drift freely.
    expectToken(SCOPE_IDIR, "first-token", 300);
    expectSearchWithBearer("first-token");
    expectToken(SCOPE_IDIR, "second-token", 300);
    expectSearchWithBearer("second-token");

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    clock.advance(Duration.ofSeconds(241));
    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a token that expires inside the renewal margin is used once and never cached")
  void shortLivedTokenIsNotCached() {
    // expires_in absent deserializes to 0 on a primitive long, and any value at or below the
    // margin leaves nothing safely cacheable — so each call must exchange afresh rather than
    // cache a token that may already be dead.
    expectToken(SCOPE_IDIR, "short-1", 30);
    expectSearchWithBearer("short-1");
    expectToken(SCOPE_IDIR, "short-2", 30);
    expectSearchWithBearer("short-2");

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("an absurd expires_in is clamped rather than overflowing the expiry arithmetic")
  void absurdExpiryIsClamped() {
    // Instant.plusSeconds(Long.MAX_VALUE) raises DateTimeException, which is not a
    // RestClientException and would escape the failure translation as a raw 500.
    expectToken(SCOPE_IDIR, "long-1", Long.MAX_VALUE);
    expectSearchWithBearer("long-1");
    expectToken(SCOPE_IDIR, "long-2", Long.MAX_VALUE);
    expectSearchWithBearer("long-2");

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    clock.advance(Duration.ofHours(1).plusSeconds(1));
    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a rejected cached token is discarded and the call retried once with a fresh one")
  void authRejectionDiscardsTheTokenAndRetries() {
    // A credential rotated in Vault invalidates a token before its nominal expiry. Without this,
    // the dead token is re-presented until it expires and every call 502s meanwhile — while the
    // user-facing message advises waiting, which cannot help.
    expectToken(SCOPE_IDIR, "stale-token");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(header("Authorization", "Bearer stale-token"))
        .andRespond(withUnauthorizedRequest());
    expectToken(SCOPE_IDIR, "rotated-token");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(header("Authorization", "Bearer rotated-token"))
        .andRespond(
            withSuccess(
                """
                [{"guid":"%s","userId":"JDOE","displayName":"Doe, Jane"}]
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.searchIdir(null, "doe", null);

    assertEquals(1, found.size());
    assertEquals(GUID, found.get(0).userGuid());
    server.verify();
  }

  @Test
  @DisplayName("a BCeID hit surfaces the user GUID, never the business GUID")
  void bceidLookupNeverSurfacesTheBusinessGuid() {
    expectToken(SCOPE_BCEID, "svc-token-3");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userGuid", GUID))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("searchUserBy", "userGuid"))
        .andExpect(queryParam("searchValue", GUID))
        .andExpect(header("Authorization", "Bearer svc-token-3"))
        .andRespond(
            withSuccess(
                """
                {"guid":"%s","businessGuid":"82CCA045E74541719F0F1C30D22ABF78",
                 "userId":"bizuser","displayName":"Biz, User"}
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.findBusinessBceid("userGuid", GUID);

    assertEquals(1, found.size());
    assertEquals(GUID, found.get(0).userGuid());
    assertEquals("Biz, User", found.get(0).displayName());
    assertEquals("BCEIDBUSINESS", found.get(0).identityProvider());
    server.verify();
  }

  @Test
  @DisplayName("a search value padded with whitespace is trimmed before the exact lookup")
  void bceidSearchValueIsTrimmed() {
    // An exact lookup is unforgiving: a GUID pasted from email with a trailing newline would
    // percent-encode into the query and report a user who exists as unknown.
    expectToken(SCOPE_BCEID, "svc-token-trim");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userGuid", GUID))
        .andExpect(queryParam("searchValue", GUID))
        .andRespond(withResourceNotFound());

    assertTrue(client.findBusinessBceid("userGuid", "  " + GUID + "\n").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("an unknown BCeID user is an empty answer, not a failure")
  void bceidNoMatchIsEmpty() {
    expectToken(SCOPE_BCEID, "svc-token-4");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userId", "nobody"))
        .andRespond(withResourceNotFound());

    assertTrue(client.findBusinessBceid("userId", "nobody").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a BCeID failure that is not a 404 is an outage, not an empty answer")
  void bceidNonNotFoundFailureIsAnOutage() {
    // Widening the 404 rule to any 4xx would render an expired credential or a revoked scope as
    // "no such user" — a silent wrong answer in place of a visible outage.
    expectToken(SCOPE_BCEID, "svc-token-5");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userId", "bizuser"))
        .andRespond(withServerError());

    assertThrows(
        DirectoryUnavailableException.class, () -> client.findBusinessBceid("userId", "bizuser"));
    server.verify();
  }

  @Test
  @DisplayName("a directory failure becomes the unavailable rejection the screen can show")
  void apiFailureBecomesDirectoryUnavailable() {
    expectToken(SCOPE_IDIR, "svc-token-6");
    server.expect(requestToUriTemplate(SEARCH_URL)).andRespond(withServerError());

    assertThrows(DirectoryUnavailableException.class, () -> client.searchIdir("xy", null, null));
    server.verify();
  }

  @Test
  @DisplayName("a token-endpoint failure becomes the same unavailable rejection")
  void tokenFailureBecomesDirectoryUnavailable() {
    server.expect(requestToUriTemplate(TOKEN_URL)).andRespond(withServerError());

    assertThrows(DirectoryUnavailableException.class, () -> client.searchIdir("xy", null, null));
    server.verify();
  }

  @Test
  @DisplayName("a 200 from the token endpoint carrying no access token is an outage, not a null")
  void tokenWithoutAnAccessTokenIsAnOutage() {
    // A realistic misconfigured-realm answer (e.g. invalid_scope with a 200). It is not an HTTP
    // failure, so without the explicit guard the null token reaches setBearerAuth and the admin
    // gets a 500 instead of the designed 502.
    server
        .expect(requestToUriTemplate(TOKEN_URL))
        .andRespond(withSuccess("{\"error\":\"invalid_scope\"}", MediaType.APPLICATION_JSON));

    assertThrows(DirectoryUnavailableException.class, () -> client.searchIdir("xy", null, null));
    server.verify();
  }

  @Test
  @DisplayName("a candidate with no usable GUID is dropped — the GUID is the association key")
  void candidatesWithoutAGuidAreDropped() {
    // Surfacing one would put a null userGuid in the picker and, on assignment, an xref row that
    // joins to nothing.
    expectToken(SCOPE_IDIR, "svc-token-7");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andRespond(
            withSuccess(
                """
                [{"guid":null,"userId":"NOGUID"},{"guid":"  ","userId":"BLANK"},
                 {"guid":"%s","userId":"JDOE"}]
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.searchIdir(null, "doe", null);

    assertEquals(1, found.size());
    assertEquals(GUID, found.get(0).userGuid());
    server.verify();
  }

  @Test
  @DisplayName("a null-shaped response body is an empty answer, not a failure")
  void nullShapedResponsesAreEmpty() {
    expectToken(SCOPE_BCEID, "svc-token-8");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userId", "bizuser"))
        .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

    assertTrue(client.findBusinessBceid("userId", "bizuser").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a base URL with a trailing slash does not produce a double-slashed path")
  void trailingSlashInTheBaseUrlIsNormalized() {
    // A doubled slash is answered 404 by many gateways — which the BCeID branch would then report
    // as "no such user", making a misconfiguration look like an empty directory.
    RestClient.Builder ownBuilder = RestClient.builder();
    MockRestServiceServer ownServer = MockRestServiceServer.bindTo(ownBuilder).build();
    UserLookupClient slashed =
        new UserLookupClient(
            ownBuilder.build(), BASE + "/", TOKEN_URL, "ilcr-service", "vault-secret", clock);

    ownServer
        .expect(requestToUriTemplate(TOKEN_URL))
        .andRespond(
            withSuccess("{\"access_token\":\"t\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
    ownServer
        .expect(requestToUriTemplate(SEARCH_URL))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertTrue(slashed.searchIdir(null, "doe", null).isEmpty());
    ownServer.verify();
  }

  @Test
  @DisplayName("a candidate with no display name composes one from the name parts, or stays null")
  void displayNameComposition() {
    expectToken(SCOPE_IDIR, "svc-token-9");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andRespond(
            withSuccess(
                """
                [{"guid":"%1$s","userId":"NONAME"},
                 {"guid":"%1$s","userId":"FIRSTONLY","firstName":"Jane"},
                 {"guid":"%1$s","userId":"BLANKNAME","displayName":"   ","lastName":"Doe"}]
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.searchIdir(null, null, "NONAME");

    assertNull(found.get(0).displayName());
    // A single name part composes on its own rather than trailing a separator.
    assertEquals("Jane", found.get(1).displayName());
    // A blank displayName is treated as absent, so composition still runs.
    assertEquals("Doe", found.get(2).displayName());
    server.verify();
  }

  private void expectSearchWithBearer(String tokenValue) {
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(header("Authorization", "Bearer " + tokenValue))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
  }

  /** The client-credentials exchange: Basic auth with the Vault credential, one scope per call. */
  private void expectToken(String scope, String tokenValue) {
    expectToken(scope, tokenValue, 300);
  }

  private void expectToken(String scope, String tokenValue, long expiresIn) {
    server
        .expect(requestToUriTemplate(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", EXPECTED_BASIC))
        .andExpect(content().string(Matchers.containsString("grant_type=client_credentials")))
        .andExpect(content().string(Matchers.containsString("scope=" + scope.replace(":", "%3A"))))
        .andRespond(
            withSuccess(
                """
                {"access_token":"%s","expires_in":%d,"token_type":"Bearer"}
                """
                    .formatted(tokenValue, expiresIn),
                MediaType.APPLICATION_JSON));
  }

  /** A clock the test moves by hand, so token expiry is reachable without sleeping. */
  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant start) {
      this.now = start;
    }

    private void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
