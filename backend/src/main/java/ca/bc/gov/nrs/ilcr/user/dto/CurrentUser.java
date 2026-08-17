package ca.bc.gov.nrs.ilcr.user.dto;

import java.util.List;

/**
 * The signed-in user as the SPA needs them: identity and role read from the validated FAM/Cognito
 * ID token, never a database row (FAM is the source of truth). Returned by {@code GET /api/v1/me}.
 *
 * <p>{@code userGuid} is the {@code custom:idp_user_id} claim — the 32-char IDIR/BCeID directory
 * GUID that also keys the legacy {@code ILCR_MILL_USER_XREF.USER_GUID} and the NR User Lookup
 * directory, so a submitter matches their mill assignments. {@code displayName} never arrives empty:
 * it falls back through the display-name and given/family claims to {@code userGuid}. {@code email}
 * and {@code identityProvider} are absent for some providers and may be null. {@code roles} carries
 * the FAM group names ({@code ILCR_ADMIN} / {@code ILCR_SUBMITTER}); a token with no ILCR group
 * yields an empty list rather than an error, so the SPA can show a "no access" screen.
 */
public record CurrentUser(
    String userGuid,
    String displayName,
    String email,
    String identityProvider,
    List<String> roles) {}
