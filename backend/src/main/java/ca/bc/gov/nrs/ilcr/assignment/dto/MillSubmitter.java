package ca.bc.gov.nrs.ilcr.assignment.dto;

import java.time.LocalDate;

/**
 * Wire shape for one dated submitter↔mill assignment (AD-12 pin, Story 2.1; Stories 2.2–2.4 derive
 * from it).
 *
 * <p>{@code userGuid} is the 32-char directory GUID carried by the {@code custom:idp_user_id}
 * claim, so it is the same value the signed-in identity and the directory lookup carry and an
 * assigned submitter matches their own assignment.
 *
 * <p>{@code displayName} is resolved when the row is read, from the directory or the token, and is
 * deliberately not persisted anywhere: storing a copy would leave renamed users showing a stale
 * name. The cost of that choice is that a departed user who no longer resolves has no name to show,
 * in which case callers fall back to the GUID rather than inventing one.
 *
 * <p>{@code activeDate} and {@code inactiveDate} narrow the underlying Oracle {@code DATE} columns
 * to a date. Those columns do carry a time component in real data, so this narrowing is lossy and
 * exists only because the assignment screens display a date; the time is preserved in the entity
 * and must never be written back from this shape.
 *
 * @param userGuid directory GUID ({@code custom:idp_user_id})
 * @param displayName name resolved at read time; null when the user no longer resolves
 * @param millId the mill id
 * @param millNumber the mill number, joined from {@code THE.MILL}
 * @param millName the mill name, joined from {@code THE.MILL}
 * @param status {@code "ACTIVE"} when {@code inactiveDate} is null, otherwise {@code "ENDED"}
 * @param activeDate the date the assignment was made active; null once ended
 * @param inactiveDate the date the assignment was ended; null while active
 * @param revisionCount optimistic-lock token
 */
public record MillSubmitter(
    String userGuid,
    String displayName,
    long millId,
    String millNumber,
    String millName,
    String status,
    LocalDate activeDate,
    LocalDate inactiveDate,
    int revisionCount) {

  /** The {@code status} value for an assignment that is currently active. */
  public static final String ACTIVE = "ACTIVE";

  /** The {@code status} value for an assignment that has been ended. */
  public static final String ENDED = "ENDED";
}
