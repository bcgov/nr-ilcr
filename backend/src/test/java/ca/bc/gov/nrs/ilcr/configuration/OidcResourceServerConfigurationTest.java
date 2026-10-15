package ca.bc.gov.nrs.ilcr.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The FAM decoder factory in isolation. The acceptance tests mock the {@link JwtDecoder} (the
 * {@code jwt()} post-processor never decodes), so the bean method itself is exercised here — the
 * configured happy path, the fail-closed guard that refuses to start without a client id, and the
 * signing-key fetch retry.
 */
class OidcResourceServerConfigurationTest {

  private static final String ISSUER = "https://loginproxy.example.test/auth/realms/standard";
  private static final String JWKS = ISSUER + "/protocol/openid-connect/certs";
  private static final String CLIENT = "ilcr-browser";

  private final OidcResourceServerConfiguration configuration =
      new OidcResourceServerConfiguration();

  @Test
  @DisplayName("builds a JwtDecoder when the client id (authorized party) is configured")
  void buildsDecoderWhenAudienceConfigured() {
    JwtDecoder decoder = configuration.jwtDecoder(JWKS, ISSUER, CLIENT);

    assertThat(decoder).isNotNull();
  }

  @Test
  @DisplayName(
      "fails closed (IllegalStateException) when security is on but the client id is unset")
  void throwsWhenAudienceMissing() {
    assertThatThrownBy(() -> configuration.jwtDecoder(JWKS, ISSUER, "   "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ILCR_OIDC_CLIENT_ID");
  }

  @Test
  @DisplayName("a signing-key fetch survives one transient SSO failure")
  void signingKeyFetchSurvivesOneTransientSsoFailure() throws Exception {
    RSAKey key = new RSAKeyGenerator(2048).keyID("sso-key").generate();
    String jwks = new JWKSet(key.toPublicJWK()).toString();
    AtomicInteger requests = new AtomicInteger();
    JwtDecoder decoder =
        OidcResourceServerConfiguration.createDecoder(
            JWKS,
            ISSUER,
            CLIENT,
            url -> {
              if (requests.incrementAndGet() == 1) {
                throw new IOException("HTTP 503: Service Unavailable");
              }
              return new Resource(jwks, "application/json");
            });
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sso-key").build(),
            new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("user")
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("azp", CLIENT)
                .claim("typ", "Bearer")
                .claim("identity_provider", "azureidir")
                .claim("idir_username", "JSMITH")
                .claim("idir_user_guid", "0123456789ABCDEF0123456789ABCDEF")
                .build());
    token.sign(new RSASSASigner(key));

    // Spring's default decoder rejects this token outright when the key fetch fails.
    assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("user");
    assertThat(requests).hasValue(2);
  }
}
