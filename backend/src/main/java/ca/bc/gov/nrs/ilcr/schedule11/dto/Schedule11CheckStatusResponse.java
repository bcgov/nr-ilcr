package ca.bc.gov.nrs.ilcr.schedule11.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import java.util.List;

/**
 * The Schedule 11 Check Status (BR-07) result (Story 25.2, AD-12) — read-only validation, no status
 * transition. A location passes iff BOTH its Actual and Planned cost are non-null (missing = null;
 * {@code 0} is present); {@code requirementsMet} is true iff {@code errors} is empty (zero
 * locations ⇒ vacuously met).
 *
 * <p>Reuses {@link MessageInfo} (imported from {@code schedule1} — shared DTO, not duplicated). Two
 * legacy-faithful specifics distinguish this from Schedule 1's check-status: {@code message} is the
 * SUC-004 "Status has been checked" text emitted on EVERY invocation (legacy {@code
 * Schedule11MB.checkStatus()} always adds it), and {@code requirementsMetMessage} is the SUC-003
 * "all requirements met" text present only when {@code requirementsMet}.
 *
 * @param requirementsMet whether every location passes (no errors)
 * @param errors one entry per missing cost, verbatim legacy-composed text ({@code "location :
 *     {location} - Actual|Planned cost: Value Required"}), in {@code BASIC_SILVICULTURE_REPORT_ID}
 *     order, actual before planned; empty when met
 * @param requirementsMetMessage SUC-003 ({@code scheduleRequirementsMetMsg}) when met, else null
 * @param message SUC-004 ({@code checkStatusMessage}) — ALWAYS present (pass or fail)
 */
public record Schedule11CheckStatusResponse(
    boolean requirementsMet,
    List<MessageInfo> errors,
    MessageInfo requirementsMetMessage,
    MessageInfo message) {}
