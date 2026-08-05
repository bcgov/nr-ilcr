package ca.bc.gov.nrs.ilcr.assignment.dto;

import java.time.LocalDate;

/**
 * Wire shape for one submitter↔mill assignment (AD-12 pin, Story 2.1). {@code userGuid} is the FAM
 * {@code custom:idp_user_id} — the same value the {@code /me} identity and the FAM directory picker
 * carry, so an assigned submitter matches their assignment. {@code displayName}/{@code idpUsername}
 * come from the xref's assign-time snapshot (Q5) so an {@code ENDED} row still renders. Served by
 * Story 2.2.
 *
 * @param userGuid FAM user GUID (custom:idp_user_id)
 * @param displayName snapshotted FAM display name (may be null for legacy rows)
 * @param idpUsername snapshotted FAM username (e.g. IDIR short name)
 * @param millId the shared THE mill id
 * @param millNumber the mill number (from THE.MILL, joined in the service)
 * @param millName the mill name (from THE.MILL)
 * @param status {@code "ACTIVE"} (END_DATE null) or {@code "ENDED"}
 * @param startDate assignment active-from date
 * @param endDate assignment ended date (null when active)
 * @param revisionCount optimistic-lock token
 */
public record MillSubmitter(
    String userGuid,
    String displayName,
    String idpUsername,
    long millId,
    String millNumber,
    String millName,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    int revisionCount) {
}
