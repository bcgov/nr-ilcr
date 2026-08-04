package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * A mutating assign/end result from the service: the affected assignment plus the message-bundle KEY
 * (not yet resolved). The controller resolves the key to verbatim text (AD-8) and wraps both in an
 * {@link AssignmentResponse}.
 *
 * @param submitter the affected assignment row
 * @param messageKey the bundle key (e.g. {@code user.activate.mill})
 */
public record AssignmentOutcome(MillSubmitter submitter, String messageKey) {
}
