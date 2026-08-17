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
 * default issuer and expiry checks it enforces the constraints the identity spike (Story 1.0)
 * settled: the bearer must be the Cognito <em>ID</em> token ({@code token_use=id}), and its
 * {@code aud} must be this app's client id.
 *
 * <p>The client id ({@code COGNITO_CLIENT_ID} → {@code ilcr.security.cognito.allowed-audience}) is
 * <strong>mandatory</strong> when security is on: refusing to start beats silently accepting any
 * token the pool issued (including tokens minted for other clients). Local/test profiles run with
 * {@code ilcr.security.enabled=false}, so this bean is absent there.
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
      throw new IllegalStateException(
          "ilcr.security.cognito.allowed-audience (COGNITO_CLIENT_ID) is required when "
              + "ilcr.security.enabled=true — refusing to start with audience validation disabled.");
    }

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        new JwtClaimValidator<String>("token_use", "id"::equals),
        new AudienceValidator(allowedAudience)));
    return decoder;
  }
}
