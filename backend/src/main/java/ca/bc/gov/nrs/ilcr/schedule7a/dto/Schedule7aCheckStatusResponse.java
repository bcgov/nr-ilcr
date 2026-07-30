package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 7A Check Status (BR-08) result (Story 12.2, AD-12) — read-only validation, no status
 * transition. Each bridge is walked in the exact legacy field order ({@code Schedule7aMB.java:206-289});
 * a bridge passes iff none of its 17 required values (name/location, date, life span, abutment
 * height, length, width, distance, and the ten costs) is null.
 *
 * <p>Reuses {@link MessageInfo} (imported from {@code schedule1} — shared DTO, not duplicated).
 *
 * @param requirementsMet whether every stored bridge passes (no missing values)
 * @param errors one entry per missing value, verbatim legacy-composed text
 *     ({@code "Bridge Report Id : {rowCounter}{fieldLabel}"}) with the shared
 *     {@code missingRequiredFieldMsg} = {@code "Value Required"} key; grouped per bridge in
 *     {@code BRIDGE_REPORT_ID} order, field order per legacy; empty when met
 * @param bridgeMessages one SUC-005 {@code bridgeRequirementsMetMsg}
 *     ({@code "All requirements for {rowCounter} have been met."}) per bridge that passes
 * @param requirementsMetMessage SUC-004 {@code scheduleRequirementsMetMsg}
 *     ({@code "All requirements for this schedule have been met"}) when all pass, else null
 */
public record Schedule7aCheckStatusResponse(
    boolean requirementsMet,
    List<MessageInfo> errors,
    List<MessageInfo> bridgeMessages,
    MessageInfo requirementsMetMessage) {
}
