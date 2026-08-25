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

import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import java.nio.charset.StandardCharsets;
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
 * token exchange (and its cache), the request shapes from Paulo's documented contract, the identity
 * mapping rules — {@code guid} is the association key, {@code businessGuid} never is — and the
 * failure translation the screen depends on.
 */
@DisplayName("UserLookupClient — NR User Lookup API calls (UC-USR-001, DL-27)")
class UserLookupClientTest {

  private static final String BASE = "https://lookup.example";
  private static final String TOKEN_URL = "https://sso.example/token";
  private static final String SEARCH_URL = BASE + "/api/v1/user-lookup/idir-users/search";
  private static final String BCEID_URL = BASE + "/api/v1/user-lookup/businessBceid";
  private static final String GUID = "AAAABBBBCCCCDDDDEEEEFFFF00001111";

  private static final String EXPECTED_BASIC =
      "Basic "
          + Base64.getEncoder()
              .encodeToString("ilcr-service:vault-secret".getBytes(StandardCharsets.UTF_8));

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final UserLookupClient client =
      new UserLookupClient(builder, BASE, TOKEN_URL, "ilcr-service", "vault-secret");

  @Test
  @DisplayName("an IDIR search authenticates with the service account and maps the candidates")
  void idirSearchUsesTheServiceTokenAndMaps() {
    expectToken("user-lookup:idir:search", "svc-token-1");
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
  @DisplayName("the service token is fetched once and reused while fresh")
  void tokenIsCachedPerScope() {
    expectToken("user-lookup:idir:search", "svc-token-2");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(header("Authorization", "Bearer svc-token-2"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andExpect(header("Authorization", "Bearer svc-token-2"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    assertTrue(client.searchIdir(null, "doe", null).isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a BCeID hit surfaces the user GUID, never the business GUID")
  void bceidLookupNeverSurfacesTheBusinessGuid() {
    expectToken("user-lookup:business-bceid:read", "svc-token-3");
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
  @DisplayName("an unknown BCeID user is an empty answer, not a failure")
  void bceidNoMatchIsEmpty() {
    expectToken("user-lookup:business-bceid:read", "svc-token-4");
    server
        .expect(
            requestToUriTemplate(
                BCEID_URL + "?searchUserBy={by}&searchValue={val}", "userId", "nobody"))
        .andRespond(withResourceNotFound());

    assertTrue(client.findBusinessBceid("userId", "nobody").isEmpty());
    server.verify();
  }

  @Test
  @DisplayName("a directory failure becomes the unavailable rejection the screen can show")
  void apiFailureBecomesDirectoryUnavailable() {
    expectToken("user-lookup:idir:search", "svc-token-5");
    server.expect(requestToUriTemplate(SEARCH_URL)).andRespond(withServerError());

    assertThrows(DirectoryUnavailableException.class, () -> client.searchIdir("x", null, null));
    server.verify();
  }

  @Test
  @DisplayName("a token-endpoint failure becomes the same unavailable rejection")
  void tokenFailureBecomesDirectoryUnavailable() {
    server.expect(requestToUriTemplate(TOKEN_URL)).andRespond(withServerError());

    assertThrows(DirectoryUnavailableException.class, () -> client.searchIdir("x", null, null));
    server.verify();
  }

  @Test
  @DisplayName("a candidate with no display name composes one from the name parts, or stays null")
  void displayNameComposition() {
    expectToken("user-lookup:idir:search", "svc-token-6");
    server
        .expect(requestToUriTemplate(SEARCH_URL))
        .andRespond(
            withSuccess(
                """
                [{"guid":"%s","userId":"NONAME"}]
                """
                    .formatted(GUID),
                MediaType.APPLICATION_JSON));

    List<DirectoryUser> found = client.searchIdir(null, null, "NONAME");

    assertNull(found.get(0).displayName());
    server.verify();
  }

  /** The client-credentials exchange: Basic auth with the Vault credential, one scope per call. */
  private void expectToken(String scope, String tokenValue) {
    server
        .expect(requestToUriTemplate(TOKEN_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", EXPECTED_BASIC))
        .andExpect(content().string(Matchers.containsString("grant_type=client_credentials")))
        .andExpect(content().string(Matchers.containsString("scope=" + scope.replace(":", "%3A"))))
        .andRespond(
            withSuccess(
                """
                {"access_token":"%s","expires_in":300,"token_type":"Bearer"}
                """
                    .formatted(tokenValue),
                MediaType.APPLICATION_JSON));
  }
}
