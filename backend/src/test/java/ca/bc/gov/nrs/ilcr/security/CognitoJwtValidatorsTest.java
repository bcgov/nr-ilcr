package ca.bc.gov.nrs.ilcr.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;

/**
 * The two identity-spike (Story 1.0) token constraints, in isolation: the bearer must be the
 * Cognito ID token ({@code token_use=id}) with this app's client id in {@code aud}. The MockMvc
 * acceptance tests inject a pre-authenticated principal and so bypass decoding — these exercise the
 * validators the real decoder applies.
 */
class CognitoJwtValidatorsTest {

  private static Jwt jwtWith(String tokenUse, List<String> audience) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token").header("alg", "none").claim("token_use", tokenUse);
    if (audience != null) {
      builder.audience(audience);
    }
    // A JWT needs at least one claim beyond the header to build cleanly.
    builder.subject("subject");
    return builder.build();
  }

  @Test
  @DisplayName("token_use=id passes; access or missing fails (reject Cognito access tokens)")
  void tokenUseValidator() {
    JwtClaimValidator<String> validator = new JwtClaimValidator<>("token_use", "id"::equals);

    assertThat(validator.validate(jwtWith("id", List.of("client"))).hasErrors()).isFalse();
    assertThat(validator.validate(jwtWith("access", List.of("client"))).hasErrors()).isTrue();

    Jwt noTokenUse = Jwt.withTokenValue("token").header("alg", "none").subject("s").build();
    assertThat(validator.validate(noTokenUse).hasErrors()).isTrue();
  }

  @Test
  @DisplayName("audience validator requires this app's client id in aud")
  void audienceValidator() {
    AudienceValidator validator = new AudienceValidator("this-client");

    assertThat(validator.validate(jwtWith("id", List.of("this-client"))).hasErrors()).isFalse();
    assertThat(validator.validate(jwtWith("id", List.of("other-client"))).hasErrors()).isTrue();
    assertThat(validator.validate(jwtWith("id", null)).hasErrors()).isTrue();
  }
}
