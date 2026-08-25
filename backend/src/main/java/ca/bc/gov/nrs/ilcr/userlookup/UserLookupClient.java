package ca.bc.gov.nrs.ilcr.userlookup;

import ca.bc.gov.nrs.ilcr.userlookup.dto.DirectoryUser;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
 * <p>Tokens are requested with the single scope the operation needs and cached per scope. Cache
 * misses are serialized per scope, so a burst of picker keystrokes costs one token exchange rather
 * than one each — including when the burst arrives concurrently from several admins.
 *
 * <p>Every call carries an explicit connect and read timeout. This is the app's only outbound HTTP
 * dependency and it is owned by another ministry team: without a read timeout, a directory that
 * accepts the connection and then hangs would pin a servlet thread per keystroke until the pool is
 * exhausted — taking the local assignments view down with it, which is precisely the coupling
 * {@link DirectoryUnavailableException} exists to prevent.
 */
@Component
@ConditionalOnProperty(name = "ilcr.user-lookup.enabled", havingValue = "true")
public class UserLookupClient {

  static final String IDP_IDIR = "IDIR";
  static final String IDP_BCEID_BUSINESS = "BCEIDBUSINESS";

  private static final Logger log = LoggerFactory.getLogger(UserLookupClient.class);

  private static final String SCOPE_IDIR_SEARCH = "user-lookup:idir:search";
  private static final String SCOPE_BCEID_READ = "user-lookup:business-bceid:read";

  private static final String IDIR_SEARCH_PATH = "/api/v1/user-lookup/idir-users/search";
  private static final String BCEID_PATH = "/api/v1/user-lookup/businessBceid";

  /**
   * The documented IDIR search page size (AC1). Sent on every search: the directory is
   * government-wide and the criteria are contains-matches, so an uncapped request would deserialize
   * an unbounded array on every keystroke.
   */
  private static final int IDIR_SEARCH_PAGE_SIZE = 50;

  /** Renew this many seconds before the token would expire, so a call never rides a stale one. */
  private static final long TOKEN_RENEWAL_MARGIN_SECONDS = 60;

  /**
   * Never trust a token for longer than this, whatever {@code expires_in} claims. Bounds both a
   * mis-issued long-lived token and the arithmetic — an absurd {@code expires_in} would otherwise
   * overflow {@code Instant.plusSeconds} with a {@code DateTimeException}, which is not a {@link
   * RestClientException} and would escape the translation below as a raw 500.
   */
  private static final long MAX_TOKEN_FRESH_SECONDS = Duration.ofHours(1).toSeconds();

  private final RestClient http;
  private final String baseUrl;
  private final String tokenUrl;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;
  private final Map<String, CachedToken> tokensByScope = new ConcurrentHashMap<>();
  private final Map<String, Object> locksByScope = new ConcurrentHashMap<>();

  /**
   * Creates the client over Boot's shared builder and the {@code ilcr.user-lookup} configuration.
   *
   * <p>Configuration is validated here rather than on first use. The bean only exists when the
   * feature flag is on, so a blank value at this point means someone enabled the lookup before the
   * DL-27 service account was provisioned — better to refuse to start with a named cause than to
   * serve a 500 on the first search, which is what a blank {@code base-url} would produce (the
   * resulting relative URI raises {@code IllegalArgumentException}, not a {@code
   * RestClientException}, so it escapes the failure translation entirely).
   *
   * @param builder Boot's auto-configured builder
   * @param baseUrl the lookup API root
   * @param tokenUrl the Keycloak token endpoint issuing the service-account token
   * @param clientId the service-account client id (Vault-sourced)
   * @param clientSecret the service-account client secret (Vault-sourced, never committed)
   * @param connectTimeout ceiling on establishing the connection
   * @param readTimeout ceiling on waiting for the response
   */
  @Autowired
  public UserLookupClient(
      RestClient.Builder builder,
      @Value("${ilcr.user-lookup.base-url}") String baseUrl,
      @Value("${ilcr.user-lookup.token-url}") String tokenUrl,
      @Value("${ilcr.user-lookup.client-id}") String clientId,
      @Value("${ilcr.user-lookup.client-secret}") String clientSecret,
      @Value("${ilcr.user-lookup.connect-timeout:5s}") Duration connectTimeout,
      @Value("${ilcr.user-lookup.read-timeout:10s}") Duration readTimeout) {
    this(
        builder.requestFactory(timeoutBoundFactory(connectTimeout, readTimeout)).build(),
        required(baseUrl, "ilcr.user-lookup.base-url"),
        required(tokenUrl, "ilcr.user-lookup.token-url"),
        required(clientId, "ilcr.user-lookup.client-id"),
        required(clientSecret, "ilcr.user-lookup.client-secret"),
        Clock.systemUTC());
  }

  /**
   * Direct-injection constructor for tests: takes an already-built {@link RestClient} (so a mock
   * server can be bound to it) and an explicit {@link Clock} (so token expiry is reachable without
   * sleeping).
   */
  UserLookupClient(
      RestClient http,
      String baseUrl,
      String tokenUrl,
      String clientId,
      String clientSecret,
      Clock clock) {
    this.http = http;
    // A trailing slash would produce a double-slashed path that many gateways answer with 404 --
    // which the BCeID branch would then report as "no such user" rather than as a misconfiguration.
    this.baseUrl = StringUtils.removeEnd(StringUtils.trim(baseUrl), "/");
    this.tokenUrl = StringUtils.trim(tokenUrl);
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
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
    Map<String, Object> criteria = new LinkedHashMap<>();
    putIfNotBlank(criteria, "firstName", firstName);
    putIfNotBlank(criteria, "lastName", lastName);
    putIfNotBlank(criteria, "userId", userId);
    criteria.put("pageSize", IDIR_SEARCH_PAGE_SIZE);

    try {
      LookupUser[] found =
          withServiceToken(
              SCOPE_IDIR_SEARCH,
              token ->
                  http.post()
                      .uri(baseUrl + IDIR_SEARCH_PATH)
                      .headers(headers -> headers.setBearerAuth(token))
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(criteria)
                      .retrieve()
                      .body(LookupUser[].class));
      return toDirectoryUsers(found, IDP_IDIR);
    } catch (RestClientResponseException failure) {
      throw directoryUnavailable("IDIR search", failure);
    } catch (RestClientException failure) {
      throw directoryUnavailable("IDIR search", failure);
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
    String value = StringUtils.trim(searchValue);
    try {
      LookupUser found =
          withServiceToken(
              SCOPE_BCEID_READ,
              token ->
                  http.get()
                      .uri(
                          baseUrl + BCEID_PATH + "?searchUserBy={by}&searchValue={val}",
                          searchUserBy,
                          value)
                      .headers(headers -> headers.setBearerAuth(token))
                      .retrieve()
                      .body(LookupUser.class));
      return found == null
          ? List.of()
          : toDirectoryUsers(new LookupUser[] {found}, IDP_BCEID_BUSINESS);
    } catch (RestClientResponseException failure) {
      // An unknown user is a normal answer for an exact lookup. Only 404 earns that reading --
      // widening it to any 4xx would render an expired credential (401) or a revoked scope (403) as
      // "no such user", a silent wrong answer rather than a visible outage.
      if (failure.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        // Logged because a 404 is genuinely ambiguous: it is also what a wrong base-url or a
        // renamed upstream path returns, and that failure would otherwise be invisible -- every
        // BCeID user in the province reported as unknown, with nothing in the log. Path only; the
        // query carries the searched identifier (AD-11).
        log.debug("NR User Lookup answered 404 for {} — treating as no match", BCEID_PATH);
        return List.of();
      }
      throw directoryUnavailable("BCeID exact lookup", failure);
    } catch (RestClientException failure) {
      throw directoryUnavailable("BCeID exact lookup", failure);
    }
  }

  /**
   * Runs one directory call with a service token for the given scope, retrying exactly once against
   * a freshly-exchanged token if the directory rejects the first attempt as unauthenticated.
   *
   * <p>Without this, a credential rotated in Vault (or a session revoked upstream) leaves a dead
   * token in the cache that every subsequent call keeps presenting until its nominal expiry — a
   * self-inflicted outage of up to an hour, during which the user-facing advice to "try again
   * later" is guaranteed not to help.
   */
  private <T> T withServiceToken(String scope, Function<String, T> call) {
    try {
      return call.apply(serviceToken(scope));
    } catch (RestClientResponseException failure) {
      if (!isAuthRejection(failure)) {
        throw failure;
      }
      log.warn(
          "NR User Lookup rejected the cached service token for scope {} with {} — "
              + "discarding it and retrying once",
          scope,
          failure.getStatusCode());
      tokensByScope.remove(scope);
      return call.apply(serviceToken(scope));
    }
  }

  private static boolean isAuthRejection(RestClientResponseException failure) {
    int status = failure.getStatusCode().value();
    return status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value();
  }

  /**
   * The service-account bearer token for one operation scope, from the per-scope cache or a fresh
   * client-credentials exchange. Requesting only the needed scope keeps each token as narrow as the
   * operation it authorizes.
   *
   * <p>The exchange is serialized per scope. {@code ConcurrentHashMap} makes each map operation
   * atomic but not the check-then-exchange-then-store sequence, so without the lock N concurrent
   * first keystrokes would fire N exchanges at the SSO service and the slowest could store its
   * older token over a fresher one. The lock is held around the network call deliberately — the
   * alternative, {@code compute}, would block a whole map bin on outbound I/O.
   */
  private String serviceToken(String scope) {
    String cached = freshToken(scope);
    if (cached != null) {
      return cached;
    }

    synchronized (locksByScope.computeIfAbsent(scope, unused -> new Object())) {
      // Re-check: another thread may have completed the exchange while this one waited.
      String reChecked = freshToken(scope);
      if (reChecked != null) {
        return reChecked;
      }
      return exchangeServiceToken(scope);
    }
  }

  private String freshToken(String scope) {
    CachedToken cached = tokensByScope.get(scope);
    return cached != null && cached.freshUntil().isAfter(clock.instant()) ? cached.value() : null;
  }

  private String exchangeServiceToken(String scope) {
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
        // A 200 carrying no token -- e.g. an invalid_scope answer from a misconfigured realm. It is
        // not an HTTP failure, so nothing below would catch it and the token would reach
        // setBearerAuth(null) as a raw 500.
        log.warn("The token endpoint answered without an access token for scope {}", scope);
        throw new DirectoryUnavailableException();
      }
      cacheToken(scope, token);
      return token.accessToken();
    } catch (RestClientResponseException failure) {
      throw directoryUnavailable("service-account token exchange", failure);
    } catch (RestClientException failure) {
      throw directoryUnavailable("service-account token exchange", failure);
    }
  }

  private void cacheToken(String scope, TokenResponse token) {
    long usableSeconds = token.expiresIn() - TOKEN_RENEWAL_MARGIN_SECONDS;
    if (usableSeconds <= 0) {
      // expires_in absent (a primitive long defaults to 0), null, or shorter than the renewal
      // margin. Nothing here is safely cacheable, so the token is used once and not stored --
      // logged rather than silent, because it turns the cache off and that is worth knowing.
      log.warn(
          "The token endpoint reported expires_in={} for scope {} — at or below the {}s renewal "
              + "margin, so this token will not be cached",
          token.expiresIn(),
          scope,
          TOKEN_RENEWAL_MARGIN_SECONDS);
      return;
    }
    long freshSeconds = Math.min(usableSeconds, MAX_TOKEN_FRESH_SECONDS);
    tokensByScope.put(
        scope, new CachedToken(token.accessToken(), clock.instant().plusSeconds(freshSeconds)));
  }

  /**
   * Translates an upstream rejection, logging the status. All of them still surface as one 502 to
   * the admin — there is nothing they can do differently about a 401 than about a 500 — but the
   * status is the only thing that separates "our service account lost its scope" from "the
   * directory is genuinely down", and without it the first incident on another team's service is
   * undiagnosable. The response body is deliberately not logged: it can carry directory personal
   * data (AD-11).
   */
  private static DirectoryUnavailableException directoryUnavailable(
      String operation, RestClientResponseException failure) {
    log.warn(
        "NR User Lookup {} failed upstream with status {}", operation, failure.getStatusCode());
    return new DirectoryUnavailableException();
  }

  /**
   * Translates a transport failure — DNS, TLS, connection refused, timeout, or a response that
   * could not be deserialized. The most specific cause is logged rather than the exception itself,
   * whose message embeds the request URI: on the BCeID path that URI carries the searched
   * identifier (AD-11).
   */
  private static DirectoryUnavailableException directoryUnavailable(
      String operation, RestClientException failure) {
    log.warn(
        "NR User Lookup {} failed before a usable response: {}",
        operation,
        failure.getMostSpecificCause());
    return new DirectoryUnavailableException();
  }

  /**
   * A request factory with both ceilings bound. Built here from the JDK client rather than taken
   * from Boot's HTTP-client settings, because that module is not on this app's classpath and the
   * story's no-new-dependency constraint holds — the connect timeout belongs to the {@code
   * HttpClient}, the read timeout to the factory.
   */
  private static JdkClientHttpRequestFactory timeoutBoundFactory(
      Duration connectTimeout, Duration readTimeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(connectTimeout).build());
    factory.setReadTimeout(readTimeout);
    return factory;
  }

  private static String required(String value, String property) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException(
          property
              + " must be set when ilcr.user-lookup.enabled is true (DL-27 service account and"
              + " Vault secret)");
    }
    return value;
  }

  private static void putIfNotBlank(Map<String, Object> criteria, String name, String value) {
    // Trimmed, not just tested: a value pasted from email or a spreadsheet carries whitespace that
    // isNotBlank happily passes and the request then percent-encodes into the criterion.
    String trimmed = StringUtils.trimToNull(value);
    if (trimmed != null) {
      criteria.put(name, trimmed);
    }
  }

  private static List<DirectoryUser> toDirectoryUsers(LookupUser[] found, String idp) {
    if (found == null) {
      return List.of();
    }
    return Arrays.stream(found)
        .filter(Objects::nonNull)
        // A candidate without a guid cannot be assigned to anything -- the guid IS the association
        // key. Surfacing one would put a null userGuid in the picker and, on assignment, an xref
        // row that joins to nothing: exactly the silent breakage DirectoryUser's javadoc warns of.
        .filter(user -> StringUtils.isNotBlank(user.guid()))
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

  private record CachedToken(String value, java.time.Instant freshUntil) {}

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
