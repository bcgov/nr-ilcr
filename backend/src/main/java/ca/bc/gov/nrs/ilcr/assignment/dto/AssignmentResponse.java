package ca.bc.gov.nrs.ilcr.assignment.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;

/**
 * The mutating-endpoint response for assign / end (Story 2.2): the affected assignment plus the
 * server-resolved verbatim message (AD-8) — {@code user.activate.mill} / {@code user.deactivate.mill}
 * on success, or {@code user.not.associated.to.mill} when an assign was a no-op duplicate.
 *
 * @param submitter the affected assignment row (the existing active one on a duplicate)
 * @param message the resolved bundle message
 */
public record AssignmentResponse(MillSubmitter submitter, MessageInfo message) {
}
