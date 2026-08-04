package ca.bc.gov.nrs.ilcr.fam.dto;

/**
 * A submitter-eligible user from the FAM directory (Story 2.3), for the admin's assignment picker.
 * {@code userGuid} is the FAM {@code custom:idp_user_id} — the SAME value the JWT identity ({@code /me})
 * and the {@code ILCR_MILL_USER_PROFILE_XREF.USER_GUID} carry, so an assigned submitter matches their
 * assignment (Story 1.0). NOT the Cognito {@code sub}.
 *
 * @param userGuid the FAM user GUID (custom:idp_user_id)
 * @param displayName the FAM display name (custom:idp_display_name)
 * @param idpUsername the FAM username (custom:idp_username, e.g. an IDIR short name)
 * @param identityProvider the IDP (custom:idp_name, e.g. {@code idir} / {@code bceidbusiness})
 */
public record FamSubmitter(
    String userGuid, String displayName, String idpUsername, String identityProvider) {
}
