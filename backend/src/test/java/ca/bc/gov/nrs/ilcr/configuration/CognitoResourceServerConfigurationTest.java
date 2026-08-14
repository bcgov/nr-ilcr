package ca.bc.gov.nrs.ilcr.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The FAM decoder factory in isolation. The acceptance tests mock the {@link JwtDecoder} (the
 * {@code jwt()} post-processor never decodes), so the bean method itself is exercised here — both
 * the audience-configured path and the startup-safe path used before the client id is provisioned.
 */
class CognitoResourceServerConfigurationTest {

  private static final String JWKS =
      "https://cognito-idp.ca-central-1.amazonaws.com/ca-central-1_pool/.well-known/jwks.json";
  private static final String ISSUER =
      "https://cognito-idp.ca-central-1.amazonaws.com/ca-central-1_pool";

  private final CognitoResourceServerConfiguration configuration =
      new CognitoResourceServerConfiguration();

  @Test
  @DisplayName("builds a JwtDecoder when the client id (audience) is configured")
  void buildsDecoderWhenAudienceConfigured() {
    JwtDecoder decoder = configuration.jwtDecoder(JWKS, ISSUER, "web-client-id");

    assertThat(decoder).isNotNull();
  }

  @Test
  @DisplayName("still starts (issuer + token_use enforced) when the audience is not yet configured")
  void startsWithoutAudienceConfigured() {
    JwtDecoder decoder = configuration.jwtDecoder(JWKS, ISSUER, "   ");

    assertThat(decoder).isNotNull();
  }
}
