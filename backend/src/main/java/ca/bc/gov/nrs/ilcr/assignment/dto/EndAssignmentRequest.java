package ca.bc.gov.nrs.ilcr.assignment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Wire shape for ending a submitter's mill assignment. The mill and user are path variables and the
 * acting administrator comes from the token, so the body carries only the optimistic-lock token.
 *
 * <p>The revision is required rather than optional: ending an assignment revokes reporting rights,
 * and doing that against a row someone else has already changed is exactly the lost update the
 * token exists to catch.
 *
 * @param revisionCount the {@code revisionCount} the caller read from the assignment
 */
public record EndAssignmentRequest(@NotNull Integer revisionCount) {}
