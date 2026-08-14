package ca.bc.gov.nrs.ilcr.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The FAM decoder factory in isolation. The acceptance tests mock the {@link JwtDecoder} (the
 * {@code jwt()} post-processor never decodes), so the bean method itself is exercised here — in
 * particular the fail-closed guard that refuses to build a decoder with no configured audience.
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
  @DisplayName("fails closed when the audience (client id) is blank — no silent skip of aud")
  void failsClosedWhenAudienceBlank() {
    assertThatThrownBy(() -> configuration.jwtDecoder(JWKS, ISSUER, "   "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("allowed-audience");
  }
}
