package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * Body for ending an assignment (Story 2.2): the optimistic-lock token the admin last saw. If it no
 * longer matches the stored {@code REVISION_COUNT}, the end is rejected with 409 (AD-9).
 *
 * @param revisionCount the expected current revision of the active assignment
 */
public record EndAssignmentRequest(int revisionCount) {
}
