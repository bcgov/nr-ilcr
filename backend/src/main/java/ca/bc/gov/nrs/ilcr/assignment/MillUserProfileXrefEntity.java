package ca.bc.gov.nrs.ilcr.assignment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for {@code THE.ILCR_MILL_USER_PROFILE_XREF} — one submitter↔mill
 * assignment (Story 2.1, AD-3). FAM is the source of truth for identity: {@code USER_GUID} is the
 * FAM {@code custom:idp_user_id} (32-char IDIR/BCeID GUID, Story 1.0), with no FK to any user
 * table. {@code USER_DISPLAY_NAME}/{@code IDP_USERNAME} are snapshotted from FAM at assign time
 * (Q5) so an ended assignment whose user has left FAM still renders. {@code END_DATE} null ⇒
 * ACTIVE.
 *
 * <p>{@link MillUserProfileXrefRepository} reads these; the service maps them to the wire {@code
 * MillSubmitter} DTO (Story 2.2 — entities never cross the service boundary).
 */
@Table(name = "ILCR_MILL_USER_PROFILE_XREF", schema = "THE")
public record MillUserProfileXrefEntity(
    @Id @Column("ILCR_MILL_USER_PROFILE_XREF_ID") Long id,
    @Column("USER_GUID") String userGuid,
    @Column("ILCR_MILL_ID") long millId,
    @Column("USER_DISPLAY_NAME") String userDisplayName,
    @Column("IDP_USERNAME") String idpUsername,
    @Column("START_DATE") LocalDate startDate,
    @Column("END_DATE") LocalDate endDate,
    @Column("REVISION_COUNT") int revisionCount,
    @Column("ENTRY_USERID") String entryUserid,
    @Column("ENTRY_TIMESTAMP") LocalDateTime entryTimestamp,
    @Column("UPDATE_USERID") String updateUserid,
    @Column("UPDATE_TIMESTAMP") LocalDateTime updateTimestamp) {}
