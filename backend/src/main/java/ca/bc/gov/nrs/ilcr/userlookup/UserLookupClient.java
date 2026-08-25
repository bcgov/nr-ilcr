package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Server-side client for the ministry NR User Lookup API (DL-27) — the sanctioned replacement for
 * the legacy WebADE/ADAM directory search.
 *
 * <p>Every outbound call authenticates with the ILCR <em>service account</em> (client-credentials
 * token from the Keycloak token endpoint) — never the end user's token, which the lookup API's own
 * contract forbids forwarding. The credential arrives from a per-environment Vault secret via
 * environment variables and never exists in source.
 *
 * <p>Tokens are requested with the single scope the operation needs and cached per scope until
 * shortly before expiry, so a burst of picker keystrokes costs one token exchange, not one each.
 */
@Component
@ConditionalOnProperty(name = "ilcr.user-lookup.enabled", havingValue = "true")
public class UserLookupClient {

  static final String IDP_IDIR = "IDIR";
  static final String IDP_BCEID_BUSINESS = "BCEIDBUSINESS";

  private static final String SCOPE_IDIR_SEARCH = "user-lookup:idir:search";
  private static final String SCOPE_BCEID_READ = "user-lookup:business-bceid:read";

  /** Renew this many seconds before the token would expire, so a call never rides a stale one. */
  private static final long TOKEN_RENEWAL_MARGIN_SECONDS = 60;

  private final RestClient http;
  private final String baseUrl;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final Map<String, CachedToken> tokensByScope = new ConcurrentHashMap<>();

  /**
   * Creates the client over Boot's shared builder and the {@code ilcr.user-lookup} configuration.
   *
   * @param builder Boot's auto-configured builder, so tests can bind a mock server to it
   * @param baseUrl the lookup API root
   * @param tokenUrl the Keycloak token endpoint issuing the service-account token
   * @param clientId the service-account client id (Vault-sourced)
   * @param clientSecret the service-account client secret (Vault-sourced, never committed)
   */
  public UserLookupClient(
      RestClient.Builder builder,
      @Value("${ilcr.user-lookup.base-url}") String baseUrl,
      @Value("${ilcr.user-lookup.token-url}") String tokenUrl,
      @Value("${ilcr.user-lookup.client-id}") String clientId,
      @Value("${ilcr.user-lookup.client-secret}") String clientSecret) {
    this.http = builder.build();
    this.baseUrl = baseUrl;
    this.tokenUrl = tokenUrl;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  /**
   * Contains-search of the IDIR directory. Blank criteria are omitted from the request; the API
   * requires at least one, which the controller enforces before calling.
   *
   * @param firstName contains-match, or null
   * @param lastName contains-match, or null
   * @param userId contains-match on the IDIR username, or null
   * @return the candidates, empty when nothing matches
   * @throws DirectoryUnavailableException when the directory or token endpoint cannot answer
   */
  public List<DirectoryUser> searchIdir(String firstName, String lastName, String userId) {
    Map<String, String> criteria = new LinkedHashMap<>();
    putIfNotBlank(criteria, "firstName", firstName);
    putIfNotBlank(criteria, "lastName", lastName);
    putIfNotBlank(criteria, "userId", userId);

    try {
      LookupUser[] found =
          http.post()
              .uri(baseUrl + "/api/v1/user-lookup/idir-users/search")
              .headers(headers -> headers.setBearerAuth(serviceToken(SCOPE_IDIR_SEARCH)))
              .contentType(MediaType.APPLICATION_JSON)
              .body(criteria)
              .retrieve()
              .body(LookupUser[].class);
      return toDirectoryUsers(found, IDP_IDIR);
    } catch (RestClientException failure) {
      throw new DirectoryUnavailableException();
    }
  }

  /**
   * Exact lookup of a BCeID business user by username or directory GUID.
   *
   * <p>The response's {@code guid} is the association key; its {@code businessGuid} is the separate
   * business identifier and is never surfaced as {@code userGuid}.
   *
   * @param searchUserBy {@code userId} or {@code userGuid}
   * @param searchValue the exact value
   * @return zero or one candidate — an unknown user is an empty answer, not a failure
   * @throws DirectoryUnavailableException when the directory or token endpoint cannot answer
   */
  public List<DirectoryUser> findBusinessBceid(String searchUserBy, String searchValue) {
    try {
      LookupUser found =
          http.get()
              .uri(
                  baseUrl + "/api/v1/user-lookup/businessBceid?searchUserBy={by}&searchValue={val}",
                  searchUserBy,
                  searchValue)
              .headers(headers -> headers.setBearerAuth(serviceToken(SCOPE_BCEID_READ)))
              .retrieve()
              .body(LookupUser.class);
      return found == null
          ? List.of()
          : toDirectoryUsers(new LookupUser[] {found}, IDP_BCEID_BUSINESS);
    } catch (RestClientResponseException failure) {
      // An unknown user is a normal answer for an exact lookup; anything else is an outage.
      if (failure.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return List.of();
      }
      throw new DirectoryUnavailableException();
    } catch (RestClientException failure) {
      throw new DirectoryUnavailableException();
    }
  }

  /**
   * The service-account bearer token for one operation scope, from the per-scope cache or a fresh
   * client-credentials exchange. Requesting only the needed scope keeps each token as narrow as the
   * operation it authorizes.
   */
  private String serviceToken(String scope) {
    CachedToken cached = tokensByScope.get(scope);
    if (cached != null && cached.freshUntil().isAfter(Instant.now())) {
      return cached.value();
    }

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", scope);
    try {
      TokenResponse token =
          http.post()
              .uri(tokenUrl)
              .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (token == null || StringUtils.isBlank(token.accessToken())) {
        throw new DirectoryUnavailableException();
      }
      long freshSeconds = Math.max(0, token.expiresIn() - TOKEN_RENEWAL_MARGIN_SECONDS);
      tokensByScope.put(
          scope, new CachedToken(token.accessToken(), Instant.now().plusSeconds(freshSeconds)));
      return token.accessToken();
    } catch (RestClientException failure) {
      throw new DirectoryUnavailableException();
    }
  }

  private static void putIfNotBlank(Map<String, String> criteria, String name, String value) {
    if (StringUtils.isNotBlank(value)) {
      criteria.put(name, value);
    }
  }

  private static List<DirectoryUser> toDirectoryUsers(LookupUser[] found, String idp) {
    if (found == null) {
      return List.of();
    }
    return Arrays.stream(found)
        .filter(Objects::nonNull)
        .map(user -> new DirectoryUser(user.guid(), displayNameOf(user), user.userId(), idp))
        .toList();
  }

  /**
   * The directory's own display name when it sends one, otherwise composed from the name parts —
   * null when the record carries no name at all, which the picker renders as the username alone.
   */
  private static String displayNameOf(LookupUser user) {
    if (StringUtils.isNotBlank(user.displayName())) {
      return user.displayName();
    }
    return StringUtils.trimToNull(StringUtils.joinWith(" ", user.firstName(), user.lastName()));
  }

  /** The Keycloak client-credentials grant answer; only these two fields matter here. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn) {}

  private record CachedToken(String value, Instant freshUntil) {}

  /**
   * One user as the lookup API sends it (field names per Paulo's documented contract — confirm
   * against the live README at DL-27 onboarding). Unknown fields are ignored so an enriched
   * upstream response cannot break the picker.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record LookupUser(
      String guid,
      String userId,
      String displayName,
      String firstName,
      String lastName,
      String email,
      String businessGuid) {}
}
