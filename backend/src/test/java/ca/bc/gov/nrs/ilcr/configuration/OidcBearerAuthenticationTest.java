package ca.bc.gov.nrs.ilcr.configuration;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.FamJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import ca.bc.gov.nrs.ilcr.user.UserController;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Signed bearer tokens exercise the production decoder, token validators, role converter and
 * security filter chain together. The acceptance tests inject an already-decoded principal, so this
 * is where a real CSS access token is checked end to end. Keys are served from memory, so neither
 * SSO nor Oracle is needed.
 */
@SpringJUnitWebConfig(OidcBearerAuthenticationTest.TestConfiguration.class)
@TestPropertySource(properties = "ilcr.security.enabled=true")
class OidcBearerAuthenticationTest {
  private static final String ISSUER = "https://loginproxy.example.test/auth/realms/standard";
  private static final String CLIENT = "ilcr-browser";
  // CSS may send the directory GUID hyphenated and in lower case; ILCR keys on 32 upper-case hex.
  private static final String DIRECTORY_GUID = "01234567-89ab-cdef-0123-456789abcdef";
  private static final RSAKey SIGNING_KEY;
  private static final RSAKey WRONG_KEY;

  static {
    try {
      SIGNING_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
      WRONG_KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
    } catch (Exception exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @Autowired private WebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"idir", "azureidir"})
  void staffAccessTokenShouldKeepDirectoryAndAuditIdentity(String provider) throws Exception {
    String token =
        bearer(
            b ->
                b.claim("identity_provider", provider)
                    .claim("client_roles", List.of("ILCR_ADMIN")));
    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGuid").value("0123456789ABCDEF0123456789ABCDEF"))
        .andExpect(jsonPath("$.displayName").value("Smith, Jo IIT:EX"))
        .andExpect(jsonPath("$.identityProvider").value("IDIR"))
        .andExpect(jsonPath("$.roles", contains("ILCR_ADMIN")));
    mvc.perform(get("/api/v1/admin-probe").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.principal").value("JSMITH"))
        .andExpect(jsonPath("$.authorities", contains("ADMIN")));
  }

  @Test
  void businessBceidTokenCannotObtainAdministratorAccess() throws Exception {
    String token =
        bearer(
            b ->
                b.claim("identity_provider", "bceidbusiness")
                    .claim("idir_username", null)
                    .claim("idir_user_guid", null)
                    .claim("bceid_username", "LICENSEE01")
                    .claim("bceid_user_guid", "ABCDEF0123456789ABCDEF0123456789")
                    .claim("client_roles", List.of("ILCR_ADMIN", "ILCR_SUBMITTER")));
    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userGuid").value("ABCDEF0123456789ABCDEF0123456789"))
        .andExpect(jsonPath("$.identityProvider").value("BCEIDBUSINESS"))
        .andExpect(jsonPath("$.roles", contains("ILCR_SUBMITTER")));
    mvc.perform(get("/api/v1/admin-probe").header("Authorization", token))
        .andExpect(status().isForbidden());
  }

  @Test
  void issuerClientBindingAndTimeClaimsShouldBeValidated() throws Exception {
    unauthorized(b -> b.issuer("https://loginproxy.example.test/auth/realms/other"));
    unauthorized(b -> b.issuer(null));
    unauthorized(b -> b.claim("azp", "another-client"));
    unauthorized(b -> b.claim("azp", null));
    unauthorized(b -> b.expirationTime(null));
    unauthorized(b -> b.expirationTime(Date.from(Instant.now().minusSeconds(120))));
    unauthorized(b -> b.notBeforeTime(Date.from(Instant.now().plusSeconds(120))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ID", "Refresh", "", "JWT"})
  void otherTokenTypesShouldBeRejectedEvenWithValidRolesAndClient(String type) throws Exception {
    unauthorized(b -> b.claim("typ", type));
  }

  @Test
  void missingTypeIdentityOrUnsupportedProviderShouldBeRejected() throws Exception {
    unauthorized(b -> b.claim("typ", null));
    unauthorized(b -> b.claim("identity_provider", "bceidbasic"));
    unauthorized(b -> b.claim("identity_provider", null));
    unauthorized(b -> b.claim("idir_username", null));
    unauthorized(b -> b.claim("idir_user_guid", "not-a-directory-guid"));
  }

  @Test
  void malformedTokenAndInvalidSignatureShouldReturnUnauthorized() throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", sign(staffClaims(), WRONG_KEY)))
        .andExpect(status().isUnauthorized());
  }

  private void unauthorized(Consumer<JWTClaimsSet.Builder> change) throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(change)))
        .andExpect(status().isUnauthorized());
  }

  private String bearer(Consumer<JWTClaimsSet.Builder> change) throws Exception {
    JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder(staffClaims());
    change.accept(builder);
    return sign(builder.build(), SIGNING_KEY);
  }

  /** The shape of a CSS access token for this client, as issued to an IDIR user. */
  private static JWTClaimsSet staffClaims() {
    return new JWTClaimsSet.Builder()
        .issuer(ISSUER)
        .subject("keycloak-subject@idir")
        .audience("account")
        .issueTime(Date.from(Instant.now()))
        .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        .claim("azp", CLIENT)
        .claim("typ", "Bearer")
        .claim("identity_provider", "azureidir")
        .claim("idir_username", "JSMITH")
        .claim("idir_user_guid", DIRECTORY_GUID)
        .claim("display_name", "Smith, Jo IIT:EX")
        .claim("email", "jo.smith@gov.bc.ca")
        .claim("client_roles", List.of("ILCR_SUBMITTER"))
        .build();
  }

  private static String sign(JWTClaimsSet claims, RSAKey key) throws Exception {
    SignedJWT token =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .type(JOSEObjectType.JWT)
                .build(),
            claims);
    token.sign(new RSASSASigner(key));
    return "Bearer " + token.serialize();
  }

  @Configuration
  @EnableWebMvc
  @Import({
    SecurityConfiguration.class,
    FamJwtAuthenticationConverter.class,
    SchedulePermissions.class,
    UserController.class,
    AdminProbeController.class
  })
  static class TestConfiguration {
    @Bean
    JwtDecoder jwtDecoder() {
      String jwks = new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
      return OidcResourceServerConfiguration.createDecoder(
          ISSUER + "/protocol/openid-connect/certs",
          ISSUER,
          CLIENT,
          url -> new Resource(jwks, "application/json"));
    }
  }

  /** An administrator-only endpoint, gated the way ILCR's own controllers are. */
  @RestController
  static class AdminProbeController {
    @GetMapping("/api/v1/admin-probe")
    @PreAuthorize("@permissions.hasPermission(authentication, 'MAINTAIN_USERS')")
    Map<String, Object> identity(Authentication authentication) {
      return Map.of(
          "principal",
          authentication.getName(),
          "authorities",
          authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }
  }
}
