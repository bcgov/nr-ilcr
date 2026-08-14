package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.security.AudienceValidator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The FAM/Cognito {@link JwtDecoder}, active only when {@code ilcr.security.enabled=true}. Beyond the
 * default issuer and expiry checks it enforces the two constraints the identity spike (Story 1.0)
 * settled: the bearer must be the Cognito <em>ID</em> token ({@code token_use=id}), and its
 * {@code aud} must be this app's client id. An access token — which lacks {@code custom:idp_user_id}
 * and carries no {@code aud} — is refused here rather than accepted as an identity-less principal.
 */
@Configuration
public class CognitoResourceServerConfiguration {

  @Bean
  @ConditionalOnProperty(name = "ilcr.security.enabled", havingValue = "true")
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${ilcr.security.cognito.allowed-audience:}") String allowedAudience) {
    if (StringUtils.isBlank(allowedAudience)) {
      // Fail closed, consistent with the issuer/JWKS URIs (which have no default and so fail
      // startup if unset): with security on, a missing client id must not silently disable aud
      // validation and accept any token in the pool.
      throw new IllegalStateException(
          "ilcr.security.cognito.allowed-audience must be configured when security is enabled "
              + "(set COGNITO_CLIENT_ID) — the ID token's aud is validated against it.");
    }

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        new JwtClaimValidator<String>("token_use", "id"::equals),
        new AudienceValidator(allowedAudience)));
    return decoder;
  }
}
