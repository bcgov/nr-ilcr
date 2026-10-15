package ca.bc.gov.nrs.ilcr.util;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dto.base.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtPrincipalUtilFamClaimsTest {
  private static final String GUID = "12345678-9abc-def0-1234-56789abcdef0";
  private static final String CANONICAL_GUID = "123456789ABCDEF0123456789ABCDEF0";

  private Jwt token(String provider, Map<String, Object> claims) {
    var builder =
        Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("keycloak-subject-is-not-the-directory-id")
            .claim("identity_provider", provider);
    claims.forEach(builder::claim);
    return builder.build();
  }

  @ParameterizedTest
  @ValueSource(strings = {"idir", "azureidir"})
  void staffIdentityPreservesDirectoryKeysAndProfile(String provider) {
    var jwt =
        token(
            provider,
            Map.of(
                "idir_username", " JSMITH ",
                "idir_user_guid", GUID,
                "display_name", "José Smith",
                "name", "Fallback Name",
                "given_name", "José",
                "family_name", "Smith",
                "email", " jsmith@example.test "));
    var principal = new JwtAuthenticationToken(jwt, List.of());

    assertThat(JwtPrincipalUtil.getProvider(jwt)).isEqualTo("IDIR");
    assertThat(JwtPrincipalUtil.getUserId(principal)).isEqualTo("IDIR\\JSMITH");
    assertThat(JwtPrincipalUtil.getIdpUsername(jwt)).isEqualTo("JSMITH");
    assertThat(JwtPrincipalUtil.getAuditUsername(jwt)).isEqualTo("JSMITH");
    assertThat(JwtPrincipalUtil.getIdpUserId(jwt)).isEqualTo(CANONICAL_GUID);
    assertThat(JwtPrincipalUtil.getDisplayName(jwt)).isEqualTo("José Smith");
    assertThat(JwtPrincipalUtil.getFirstName(jwt)).isEqualTo("José");
    assertThat(JwtPrincipalUtil.getLastName(jwt)).isEqualTo("Smith");
    assertThat(JwtPrincipalUtil.getEmail(principal)).isEqualTo("jsmith@example.test");
  }

  @Test
  void businessIdentityKeepsUserAndBusinessIdentifiersSeparate() {
    var jwt =
        token(
            "bceidbusiness",
            Map.of(
                "bceid_username", "COMPANY01",
                "bceid_user_guid", GUID,
                "bceid_business_guid", "business-guid",
                "bceid_business_name", "Example Business",
                "display_name", "External User"));

    assertThat(JwtPrincipalUtil.getProvider(jwt)).isEqualTo("BCEIDBUSINESS");
    assertThat(JwtPrincipalUtil.getUserId(jwt)).isEqualTo("BCEIDBUSINESS\\COMPANY01");
    assertThat(JwtPrincipalUtil.getAuditUsername(jwt)).isEqualTo("COMPANY01");
    assertThat(JwtPrincipalUtil.getIdpUserId(jwt)).isEqualTo(CANONICAL_GUID);
    assertThat(JwtPrincipalUtil.getBusinessId(jwt)).isEqualTo("business-guid");
    assertThat(JwtPrincipalUtil.getBusinessName(jwt)).isEqualTo("Example Business");
  }

  @Test
  void modernGroupsIgnoreLegacyAliasesAndMalformedEntries() {
    var jwt =
        token(
            "azureidir",
            Map.of(
                "client_roles",
                Arrays.asList(
                    " ILCR_ADMIN ", "", null, 123, "OTHER_ILCR_ADMIN", "FAM:EXPIRES:ILCR_ADMIN")));
    assertThat(JwtPrincipalUtil.getGroups(jwt)).containsExactly("ILCR_ADMIN");
    assertThat(JwtPrincipalUtil.getRoles(jwt)).containsOnlyKeys(Role.ADMIN);
  }

  @Test
  void businessRoleMappingCannotGrantAdministratorAccess() {
    var jwt =
        token("bceidbusiness", Map.of("client_roles", List.of("ILCR_ADMIN", "ILCR_SUBMITTER")));
    assertThat(JwtPrincipalUtil.getGroups(jwt)).containsExactly("ILCR_SUBMITTER");
    assertThat(JwtPrincipalUtil.getRoles(jwt)).containsOnlyKeys(Role.SUBMITTER);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void blankDisplayNameFallsBackToTheCssName(String displayName) {
    var jwt = token("idir", Map.of("display_name", displayName, "name", "Fallback Name"));
    assertThat(JwtPrincipalUtil.getDisplayName(jwt)).isEqualTo("Fallback Name");
  }

  @Test
  void missingOrMalformedProfileClaimsDoNotBecomeObjectStrings() {
    var jwt =
        token("idir", Map.of("display_name", Map.of(), "name", "Fallback Name", "email", 123));
    assertThat(JwtPrincipalUtil.getDisplayName(jwt)).isEqualTo("Fallback Name");
    assertThat(JwtPrincipalUtil.getEmail(jwt)).isEmpty();
    assertThat(JwtPrincipalUtil.getAuditUsername(jwt)).isEmpty();
    assertThat(JwtPrincipalUtil.getIdpUserId(jwt)).isEmpty();
  }

  @Test
  void unsupportedProviderHasNoDirectoryProviderLabel() {
    assertThat(JwtPrincipalUtil.getProvider(token("service-account", Map.of()))).isEmpty();
  }

  @Test
  void legacyCustomClaimsAndSubjectAreNeverAnIdentity() {
    var jwt =
        Jwt.withTokenValue("legacy-token")
            .header("alg", "RS256")
            .subject("not-an-audit-username")
            .claim("custom:idp_name", "idir")
            .claim("custom:idp_username", "LEGACY_USER")
            .claim("custom:idp_user_id", CANONICAL_GUID)
            .build();
    assertThat(JwtPrincipalUtil.getProvider(jwt)).isEmpty();
    assertThat(JwtPrincipalUtil.getAuditUsername(jwt)).isEmpty();
    assertThat(JwtPrincipalUtil.getIdpUserId(jwt)).isEmpty();
    assertThat(JwtPrincipalUtil.getUserId(jwt)).isEmpty();
  }
}
