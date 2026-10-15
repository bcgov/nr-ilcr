package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.security.FamTokenClaims;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Signed CSS access tokens for this environment's public ILCR browser client. */
@Configuration
public class OidcResourceServerConfiguration {
  private static final int JWKS_CONNECT_TIMEOUT_MILLIS = (int) Duration.ofSeconds(10).toMillis();
  private static final int JWKS_READ_TIMEOUT_MILLIS = (int) Duration.ofSeconds(15).toMillis();
  private static final int JWKS_SIZE_LIMIT_BYTES = 50 * 1024;

  @Bean
  @ConditionalOnProperty(name = "ilcr.security.enabled", havingValue = "true")
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${ilcr.security.oidc.client-id:}") String clientId) {
    if (clientId.isBlank() || issuerUri.isBlank()) {
      throw new IllegalStateException(
          "ILCR_OIDC_ISSUER_URI and ILCR_OIDC_CLIENT_ID are required when security is enabled");
    }
    return createDecoder(
        jwkSetUri,
        issuerUri,
        clientId,
        new DefaultResourceRetriever(
            JWKS_CONNECT_TIMEOUT_MILLIS, JWKS_READ_TIMEOUT_MILLIS, JWKS_SIZE_LIMIT_BYTES));
  }

  /** The production decoder over any signing-key retriever, so tests need no SSO server. */
  static JwtDecoder createDecoder(
      String jwkSetUri, String issuerUri, String clientId, ResourceRetriever jwkSetRetriever) {
    NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor(jwkSetUri, jwkSetRetriever));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri), accessTokenValidator(clientId)));
    return decoder;
  }

  /**
   * Spring's JWK-set decoder caches keys for five minutes, then refetches them on a request thread
   * with no timeout or retry, so a slow SSO response at that moment fails the request with a 401.
   * As in LEXIS, keys are refreshed before they expire, a failed fetch is retried once and each
   * fetch is time-bounded. Otherwise this matches Spring's processor: RS256 signatures, with the
   * claims left to the token validators.
   */
  private static JWTProcessor<SecurityContext> jwtProcessor(
      String jwkSetUri, ResourceRetriever jwkSetRetriever) {
    URL jwkSetUrl;
    try {
      jwkSetUrl = URI.create(jwkSetUri).toURL();
    } catch (IllegalArgumentException | MalformedURLException exception) {
      throw new IllegalStateException(
          "spring.security.oauth2.resourceserver.jwt.jwk-set-uri must be a valid URL", exception);
    }
    JWKSource<SecurityContext> jwkSource =
        JWKSourceBuilder.create(jwkSetUrl, jwkSetRetriever)
            .retrying(true)
            .refreshAheadCache(true)
            .build();
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    return processor;
  }

  /** Requires this browser client's access token with both directory and audit identities. */
  public static OAuth2TokenValidator<Jwt> accessTokenValidator(String clientId) {
    return token -> {
      var claims = token.getClaims();
      if (!clientId.equals(FamTokenClaims.string(claims, "azp"))
          || !"Bearer".equals(FamTokenClaims.string(claims, "typ"))
          || token.getExpiresAt() == null
          || !(FamTokenClaims.isStaff(claims) || FamTokenClaims.isBusiness(claims))
          || FamTokenClaims.username(claims).isBlank()
          || !FamTokenClaims.guid(claims).matches("[0-9A-F]{32}")) {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error(
                "invalid_token",
                "An ILCR user access token with a directory and audit identity is required",
                null));
      }
      return OAuth2TokenValidatorResult.success();
    };
  }
}
