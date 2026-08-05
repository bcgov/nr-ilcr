package ca.bc.gov.nrs.ilcr.schedule6.dto;

import jakarta.validation.constraints.Size;

/**
 * Save request for the Schedule 6 schedule-level General Comment (S04) — independent of any road
 * record. A null/blank value CLEARS the comment (BR-09 third branch: a lone placeholder row is
 * deleted). Carries NO revision token — recorded deviation (c2), mirroring the systemic AR11 DELETE
 * posture (the comment is replicated on every row; a per-row token has no single owner).
 *
 * <p>The 3500 cap is the legacy UI {@code maxlength} ({@code schedule6.xhtml:496}; the column is
 * {@code VARCHAR2(4000)}) — server-side enforcement is recorded deviation (g).
 *
 * @param generalComments the schedule-level comment (&le; 3500; null/blank = clear)
 */
public record GeneralCommentsRequest(
    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}")
    String generalComments) {
}
