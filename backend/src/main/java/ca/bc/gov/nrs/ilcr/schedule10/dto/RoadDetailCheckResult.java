package ca.bc.gov.nrs.ilcr.schedule10.dto;

import java.util.List;

/**
 * Check Status outcome for one road detail.
 *
 * <p>{@code rowNumber} is the 1-based positional ordinal within its page, matching what the read
 * path serves and what the UI renders. It — not the database id — is what legacy composes into the
 * message text, so it is contractual rather than cosmetic.
 *
 * <p>Unlike Schedule 6, there is no per-row "requirements met" message: legacy Schedule 10 emits
 * only outstanding lines plus a single schedule-level banner when everything passes.
 *
 * @param roadDetailId the database id, for UI correlation
 * @param rowNumber the 1-based positional ordinal within the page, as used in the message text
 * @param roadDetailLabel the legacy row label, the prefix of every composed message for this row
 * @param met whether this road detail has no outstanding requirements
 * @param issues the outstanding requirements, in legacy emission order
 */
public record RoadDetailCheckResult(
    int roadDetailId,
    int rowNumber,
    String roadDetailLabel,
    boolean met,
    List<FieldIssue> issues) {
}
