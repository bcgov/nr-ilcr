package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * An assignment write's result: the assignment as it now stands, plus the legacy message the screen
 * shows for what just happened.
 *
 * <p>The message travels with the payload because the legacy screens always paired the two, and
 * because one of the outcomes is a refusal: re-assigning an already-active pair changes nothing and
 * answers with a warning rather than an error. A caller that only inspected the status code could
 * not tell that apart from a successful assignment, so the key is explicit.
 *
 * @param assignment the assignment as it now stands, unchanged when the message is a warning
 * @param messageKey the legacy bundle key identifying the outcome
 * @param message the resolved verbatim text for {@code messageKey}
 */
public record AssignmentResponse(MillSubmitter assignment, String messageKey, String message) {}
