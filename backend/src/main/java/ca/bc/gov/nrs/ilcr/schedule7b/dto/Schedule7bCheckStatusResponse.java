package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 7B Check Status (BR-07) result (Story 13.2, AD-12) — read-only validation, no status
 * transition, mutates nothing. Each culvert is walked in the exact legacy field order ({@code
 * Schedule7bMB.java:130-158}): span, comments, length, piece count, material cost, install cost.
 *
 * <p>The rules are TYPE-CONDITIONAL and this is the whole point of the schedule: span size is
 * required only for type {@code R} (Round), comments only for type {@code O} (Others), and
 * <strong>rise is never checked for any type</strong> — {@code Schedule7bCheckStatus} sets no rise
 * flag at all ({@code service/Schedule7bCheckStatus.java:10-23}). Length, piece count, material
 * cost, and install cost are required unconditionally.
 *
 * <p>Deliberately has NO per-culvert all-met list, unlike its Schedule 7A twin's {@code
 * bridgeMessages}: legacy 7B emits only the schedule-wide message ({@code
 * Schedule7bMB.java:162-164}) and inventing a per-culvert line would be a fabricated message.
 *
 * <p>Reuses {@link MessageInfo} (imported from {@code schedule1} — shared DTO, not duplicated).
 *
 * @param requirementsMet whether every stored culvert passes (no missing required value)
 * @param errors one entry per missing value, verbatim legacy-composed text (e.g. {@code "Culvert
 *     Report Id : 1 - Culvert Type Round - Span size: Value Required"}) carrying the shared {@code
 *     missingRequiredFieldMsg} = {@code "Value Required"} key; grouped per culvert in {@code
 *     CULVERT_REPORT_ID} order, field order per legacy; empty when met
 * @param requirementsMetMessage SUC-003 {@code scheduleRequirementsMetMsg} ({@code "All
 *     requirements for this schedule have been met"}) when all pass, else null
 */
public record Schedule7bCheckStatusResponse(
    boolean requirementsMet, List<MessageInfo> errors, MessageInfo requirementsMetMessage) {}
