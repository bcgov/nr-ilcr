package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.util.List;

/**
 * The Schedule 3 Check Status (BR-11/BR-03/BR-10) result (Story 4.2, AD-12) — read-only validation, no
 * status transition, mutates nothing (AD-5). {@code requirementsMet} is true iff {@code errors} is
 * empty. Each {@code errors} entry carries the legacy bundle key plus the verbatim resolved text
 * (label-prefixed, AD-8), in legacy field order. Schedule 3 has no Check-Status warnings branch (its
 * WRN-001/002 ride on Save), so {@code warnings} is always empty. Schedule3-local copy of the generic
 * envelope (peer-domain, per Story 4.1).
 *
 * @param requirementsMet whether the schedule passes (no errors)
 * @param errors the blocking missing-field / Harvest≥PO&amp;P messages (verbatim), in legacy order
 * @param warnings always empty for Schedule 3 (kept for envelope symmetry with Schedule 1)
 * @param message the "all requirements met" success message when {@code requirementsMet}, else null
 */
public record CheckStatusResponse(
    boolean requirementsMet,
    List<MessageInfo> errors,
    List<MessageInfo> warnings,
    MessageInfo message) {
}
