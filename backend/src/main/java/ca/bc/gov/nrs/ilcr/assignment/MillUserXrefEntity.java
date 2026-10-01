package ca.bc.gov.nrs.ilcr.assignment;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for the existing {@code THE.ILCR_MILL_USER_XREF} table — one dated
 * submitter↔mill assignment (AD-3).
 *
 * <p>The real primary key is the composite {@code (ILCR_MILL_ID, USER_GUID)}, which Spring Data
 * JDBC cannot express as a single {@code @Id}. Following the {@code MillStatusXref} precedent,
 * {@code millId} is annotated purely to give the mapping context a root; it is NOT unique on its
 * own. Every read goes through an explicit query on this repository and the CRUD API must never be
 * used, because it would identify rows by {@code millId} alone and touch other users' assignments.
 * Uniqueness itself is enforced by the database, not by this annotation.
 *
 * <p>State lives in the two dates rather than a status column: an assignment is ACTIVE while {@code
 * inactiveDate} is null. Because the composite key allows only one row per pair, activating and
 * deactivating toggle that single row in place instead of appending history — so no reactivation
 * history is retained. Real data bears this out exactly: the two dates are strictly mutually
 * exclusive, with every active row carrying an {@code activeDate}, every ended row carrying a null
 * {@code activeDate}, and no row ever carrying both.
 *
 * <p>Both dates are Oracle {@code DATE} columns that really do carry a time component — every
 * non-null value in real data has one — so they are mapped as {@link LocalDateTime} to keep the
 * stored instant intact. The wire DTO narrows them to a date for display; that narrowing happens at
 * the service boundary and must never be written back.
 *
 * <p>{@code millId} holds a value that is numerically the mill id, yet the enforced foreign key
 * points at {@code ILCR_MILL_STATUS_XREF}. Both hold because that table shares its primary key with
 * {@code MILL}. The consequence for callers is that a mill lacking an {@code ILCR_MILL_STATUS_XREF}
 * row cannot be assigned at all.
 */
@Table(name = "ILCR_MILL_USER_XREF", schema = "THE")
public record MillUserXrefEntity(
    @Id @Column("ILCR_MILL_ID") long millId,
    @Column("USER_GUID") String userGuid,
    @Column("ACTIVE_DATE") LocalDateTime activeDate,
    @Column("INACTIVE_DATE") LocalDateTime inactiveDate,
    @Column("REVISION_COUNT") int revisionCount,
    @Column("ENTRY_USERID") String entryUserid,
    @Column("ENTRY_TIMESTAMP") LocalDateTime entryTimestamp,
    @Column("UPDATE_USERID") String updateUserid,
    @Column("UPDATE_TIMESTAMP") LocalDateTime updateTimestamp) {

  /** Whether this assignment is currently active, which is exactly a null {@code inactiveDate}. */
  public boolean isActive() {
    return inactiveDate == null;
  }
}
