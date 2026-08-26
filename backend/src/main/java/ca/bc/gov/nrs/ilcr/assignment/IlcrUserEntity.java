package ca.bc.gov.nrs.ilcr.assignment;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for the existing {@code THE.ILCR_USER} table — a licensee's ILCR
 * account record (AD-3).
 *
 * <p>{@code userGuid} is the 32-char IDIR/BCeID directory GUID carried by the {@code
 * custom:idp_user_id} claim, so the same value keys this row, the signed-in identity, and the NR
 * User Lookup directory. Without that equality an assigned submitter would never match their own
 * assignment.
 *
 * <p>{@code activeInd} is deliberately NOT an authorization input. It never gates login or access —
 * access keys off an active row in {@code ILCR_MILL_USER_XREF} alone. The flag drives only the
 * on-screen Activate/Deactivate control, and the two legacy write paths set it asymmetrically:
 * activating an account writes {@code 'Y'}, while provisioning the account as a side effect of a
 * first mill assignment writes {@code 'N'}. That leaves accounts sitting at {@code 'N'} while their
 * holder works normally — two such users exist in real data. It reads like a bug and must be
 * preserved, not corrected; changing it is a business decision, not a cleanup.
 *
 * <p>{@link IlcrUserRepository} reads these via explicit queries; the service maps them to the wire
 * {@code SubmitterAccount} DTO, so entities never cross the service boundary.
 */
@Table(name = "ILCR_USER", schema = "THE")
public record IlcrUserEntity(
    @Id @Column("USER_GUID") String userGuid,
    @Column("ILCR_ROLE_NAME") String roleName,
    @Column("ACTIVE_IND") String activeInd,
    @Column("REVISION_COUNT") int revisionCount,
    @Column("ENTRY_USERID") String entryUserid,
    @Column("ENTRY_TIMESTAMP") LocalDateTime entryTimestamp,
    @Column("UPDATE_USERID") String updateUserid,
    @Column("UPDATE_TIMESTAMP") LocalDateTime updateTimestamp) {}
