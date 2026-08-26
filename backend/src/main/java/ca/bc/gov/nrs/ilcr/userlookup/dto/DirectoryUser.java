package ca.bc.gov.nrs.ilcr.userlookup.dto;

/**
 * One directory candidate for the assignment picker (UC-USR-001).
 *
 * <p>{@code userGuid} is the directory GUID ({@code custom:idp_user_id}) — the exact value written
 * to {@code ILCR_USER.USER_GUID} and the mill xref, and the same value {@code CurrentUser.userGuid}
 * carries. It is never the Cognito {@code sub} and never the BCeID {@code businessGuid}; either
 * substitute would break the association join silently.
 *
 * <p>A candidate is a lookup result only — holding one proves nothing about mill access or the
 * {@code ILCR_SUBMITTER} role. Authorization stays with the xref check.
 *
 * @param userGuid the 32-char directory GUID, the association key
 * @param displayName the person's name as the directory renders it, or null when it has none
 * @param idpUsername the provider username (e.g. the IDIR id), for display beside the name
 * @param identityProvider {@code IDIR} or {@code BCEIDBUSINESS}
 */
public record DirectoryUser(
    String userGuid, String displayName, String idpUsername, String identityProvider) {}
