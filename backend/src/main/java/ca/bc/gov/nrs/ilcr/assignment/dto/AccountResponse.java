package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * An account write's result: the account as it now stands, plus the legacy confirmation the screen
 * shows.
 *
 * @param account the account as it now stands
 * @param messageKey the legacy bundle key identifying the outcome
 * @param message the resolved verbatim text for {@code messageKey}
 */
public record AccountResponse(SubmitterAccount account, String messageKey, String message) {}
