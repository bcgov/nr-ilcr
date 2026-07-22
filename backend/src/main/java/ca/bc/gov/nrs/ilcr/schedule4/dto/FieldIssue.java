package ca.bc.gov.nrs.ilcr.schedule4.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;

/**
 * One missing-field finding within a location's Check Status result (Story 4.4, S28/S31). {@code code}
 * is the legacy cost-item code (40–55) whose Cost is missing; {@code message} carries the verbatim
 * {@code missingRequiredFieldMsg} ("Value Required"). The service emits the bundle key; the controller
 * resolves the text (AD-8).
 *
 * @param code the cost-item code whose Cost is required but null
 * @param message the "Value Required" message (key + resolved text)
 */
public record FieldIssue(int code, MessageInfo message) {
}
