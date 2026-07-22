package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.util.List;

/**
 * The Schedule 1 Check Status (BR-07) result (Story 2.6, AD-12) — read-only validation, no status
 * transition. {@code requirementsMet} is true iff {@code errors} is empty (warnings do not block).
 * Each {@code errors}/{@code warnings} entry carries the legacy bundle key plus the verbatim resolved
 * text (label-prefixed, AD-8). {@code message} is the SUC-003 success text when requirements are met,
 * else null.
 *
 * @param requirementsMet whether the schedule passes (no errors)
 * @param errors the blocking missing-field / consistency messages (verbatim), in legacy field order
 * @param warnings the non-blocking advisories (e.g. WRN-002 empty-cost row)
 * @param message the "all requirements met" success message when {@code requirementsMet}, else null
 */
public record CheckStatusResponse(
    boolean requirementsMet,
    List<MessageInfo> errors,
    List<MessageInfo> warnings,
    MessageInfo message) {
}
