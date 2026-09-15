package ca.bc.gov.nrs.ilcr.security;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The principal {@link MockPrincipalFilter} presents when security is off (AD-7) — a short display
 * name plus the directory GUID a real caller would carry as {@code custom:idp_user_id}. Never
 * constructed when security is on: with a real IdP the principal is a {@code Jwt}.
 *
 * <p><b>Why a type rather than the token's {@code details}.</b> The GUID first lived in {@code
 * Authentication.getDetails()}, which works but squats on a slot Spring Security gives an
 * established meaning ({@code WebAuthenticationDetails} — remote address, session id). Anything
 * that later set proper details on this token would have silently blanked the GUID, and a blank
 * GUID fail-closes {@link ca.bc.gov.nrs.ilcr.millcontext.MillContextService#listMills(boolean,
 * String)} to an EMPTY mill list — an empty Home dropdown with no error anywhere. A dedicated type
 * cannot be displaced by anything else and says what it is at the read site.
 *
 * <p><b>Why the GUID is NOT the name.</b> {@link #getName()} is what every write controller stores
 * in the {@code ENTRY_USERID} / {@code UPDATE_USERID} audit columns, and those are {@code
 * VARCHAR2(30)} while a FAM GUID is 32 characters — so naming the principal after it would {@code
 * ORA-12899} on every save. Implementing {@link AuthenticatedPrincipal} is what keeps that safe:
 * {@code AbstractAuthenticationToken.getName()} consults it before falling back to {@code
 * toString()}, so {@code getName()} stays the short {@code dev-<roles>} and the record's own {@code
 * toString()} can never leak into an audit column.
 *
 * @param name the short display name ({@code dev-submitter}, {@code dev-admin}) — audit-safe
 * @param userGuid the stand-in directory GUID, from {@code ilcr.security.mock-user-guid}
 */
public record MockUserPrincipal(String name, String userGuid) implements AuthenticatedPrincipal {

  @Override
  public String getName() {
    return name;
  }

  /**
   * The name only — deliberately NOT the record's generated {@code toString()}, which would include
   * {@code userGuid}.
   *
   * <p>Spring Security's {@code AbstractAuthenticationToken.toString()} embeds {@code Principal:
   * <principal>}, and authentication objects reach logs on their own (framework DEBUG, and any
   * {@code log.*("… {}", auth)} anywhere). The default GUID is synthetic and harmless, but this
   * class's own javadoc tells operators to point {@code ilcr.security.mock-user-guid} at a GUID
   * their database associates — which on a real dataset means a REAL directory identifier of a real
   * person. Printing the name alone keeps that out of log aggregation while losing nothing: the
   * GUID is read through {@link #userGuid()} by the one caller that needs it (NFR3/AD-11 — logs
   * carry mill/user references, never identifiers or tokens).
   */
  @Override
  public String toString() {
    return name;
  }
}
