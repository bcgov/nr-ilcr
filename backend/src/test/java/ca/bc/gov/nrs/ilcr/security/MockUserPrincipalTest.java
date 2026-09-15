package ca.bc.gov.nrs.ilcr.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;

/** The dev mock principal's two load-bearing properties: it names itself, and it does not leak. */
@DisplayName("MockUserPrincipal — names itself, never prints its GUID")
class MockUserPrincipalTest {

  private static final String GUID = "REALLOOKINGGUIDAAAABBBBCCCC00001";
  private static final MockUserPrincipal PRINCIPAL = new MockUserPrincipal("dev-submitter", GUID);

  @Test
  @DisplayName("toString() omits the GUID — it must never reach a log")
  void toString_omitsTheGuid() {
    // A record's GENERATED toString() lists every component, so the default would have been
    // `MockUserPrincipal[name=dev-submitter, userGuid=...]`. That matters because
    // AbstractAuthenticationToken.toString() embeds the principal, and authentication objects reach
    // logs on their own (framework DEBUG, or any log.*("… {}", auth)). The default GUID is
    // synthetic, but MockPrincipalFilter's javadoc tells operators to point
    // ilcr.security.mock-user-guid at a GUID their database associates — on real data, a real
    // person's directory identifier. If you "simplify" the override away, this test is the thing
    // that stops it (NFR3/AD-11: logs carry mill/user references, never identifiers or tokens).
    assertEquals("dev-submitter", PRINCIPAL.toString());
    assertFalse(PRINCIPAL.toString().contains(GUID));
  }

  @Test
  @DisplayName("the enclosing token does not print the GUID either")
  void enclosingToken_doesNotPrintTheGuid() {
    // The realistic leak path: nobody logs the principal directly, they log the Authentication.
    Authentication auth =
        new UsernamePasswordAuthenticationToken(PRINCIPAL, "N/A", java.util.List.of());
    assertFalse(auth.toString().contains(GUID));
  }

  @Test
  @DisplayName("getName() is the short audit-safe name, via AuthenticatedPrincipal")
  void getName_isTheShortName() {
    // Implementing AuthenticatedPrincipal is what makes AbstractAuthenticationToken.getName()
    // return this rather than falling through to toString(); getName() feeds the VARCHAR2(30)
    // ENTRY_USERID/UPDATE_USERID audit columns, and a FAM GUID is 32 chars.
    assertEquals("dev-submitter", PRINCIPAL.getName());
    assertEquals(
        "dev-submitter", new UsernamePasswordAuthenticationToken(PRINCIPAL, "N/A").getName());
    assertEquals(true, PRINCIPAL instanceof AuthenticatedPrincipal);
  }

  @Test
  @DisplayName("the GUID is still readable by the one caller that needs it")
  void userGuid_isStillAccessible() {
    assertEquals(GUID, PRINCIPAL.userGuid());
  }
}
