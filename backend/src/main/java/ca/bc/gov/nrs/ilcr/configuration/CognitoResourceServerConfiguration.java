package ca.bc.gov.nrs.ilcr.configuration;

import ca.bc.gov.nrs.ilcr.security.AudienceValidator;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The FAM/Cognito {@link JwtDecoder}, active only when {@code ilcr.security.enabled=true}. Beyond the
 * default issuer and expiry checks it enforces the constraints the identity spike (Story 1.0)
 * settled: the bearer must be the Cognito <em>ID</em> token ({@code token_use=id}), and — once the
 * client id is configured — its {@code aud} must be this app's client id.
 *
 * <p>The audience check is applied only when {@code ilcr.security.cognito.allowed-audience}
 * ({@code COGNITO_CLIENT_ID}) is set. It is not yet provisioned in the deployed environments (real
 * sign-in arrives with Story 1.2), so requiring it here would stop the pod from starting. Until then
 * the decoder starts with issuer + {@code token_use=id} enforced and logs a warning that {@code aud}
 * validation is off — it must be turned on (client id provisioned) before real users sign in.
 */
@Configuration
public class CognitoResourceServerConfiguration {

  private static final Logger log = LoggerFactory.getLogger(CognitoResourceServerConfiguration.class);

  @Bean
  @ConditionalOnProperty(name = "ilcr.security.enabled", havingValue = "true")
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${ilcr.security.cognito.allowed-audience:}") String allowedAudience) {
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
    validators.add(new JwtClaimValidator<String>("token_use", "id"::equals));

    if (StringUtils.isNotBlank(allowedAudience)) {
      validators.add(new AudienceValidator(allowedAudience));
    } else {
      log.warn("ilcr.security.cognito.allowed-audience is not set (COGNITO_CLIENT_ID unset): aud "
          + "validation is DISABLED. Issuer and token_use=id are still enforced. Provision the "
          + "client id before real sign-in is wired (Story 1.2).");
    }

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }
}
