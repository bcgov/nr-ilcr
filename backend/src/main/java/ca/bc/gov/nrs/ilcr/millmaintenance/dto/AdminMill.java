package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

import java.time.LocalDate;

/**
 * A mill on the administration surface: identity, ILCR status, and the head-office/contact
 * selections the screen edits.
 *
 * <p>Extends the canonical {@link ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary} shape (mill id,
 * number, name, status code) rather than re-spelling it, and adds only what this surface owns.
 * {@code statusDescription} comes from {@code THE.ILCR_MILL_STATUS_CODE} — "Active" or "Close" —
 * which is the text legacy rendered and no source file carries.
 *
 * <p>{@code revisionCount} is the optimistic-lock token: a write echoes the value it last read.
 * Legacy had none here and silently let the last writer win.
 *
 * <p>{@code updateUserid} and {@code updateTimestamp} are the source for legacy's "Last Edited by :
 * … on date: …" line (mills.xhtml:41-49), which this projection previously carried no columns for —
 * closing that gap (deviation (A)). The audit pair was always written by this application's own
 * status and contact saves; only the read path omitted them. The time-of-day is discarded because
 * legacy's own rendering did (an {@code f:convertDateTime pattern="dd/MM/yyyy"}), so a {@link
 * java.time.LocalDate} is the faithful carrier, not an instant.
 *
 * @param millId the mill id, which is also its cross-reference id
 * @param millNumber the mill number as a display string, never arithmetic
 * @param millName the mill name
 * @param millStatusCode {@code ACT} or {@code CLS}
 * @param statusDescription the status text from the code table
 * @param headOfficeContactInd {@code Y}, {@code N}, or null when never set
 * @param headOfficeContactId the selected head-office contact, or null
 * @param divisionContactId the selected division contact, or null
 * @param revisionCount the row's current revision, echoed back on write
 * @param updateUserid the administrator who last saved the row
 * @param updateTimestamp the date the row was last saved, day precision only
 */
public record AdminMill(
    long millId,
    String millNumber,
    String millName,
    String millStatusCode,
    String statusDescription,
    String headOfficeContactInd,
    Long headOfficeContactId,
    Long divisionContactId,
    int revisionCount,
    String updateUserid,
    LocalDate updateTimestamp) {

  /** The active status code. */
  public static final String ACTIVE = "ACT";

  /** The closed status code. */
  public static final String CLOSED = "CLS";

  /** Whether the mill is active. */
  public boolean isActive() {
    return ACTIVE.equals(millStatusCode);
  }
}
