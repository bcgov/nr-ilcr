package ca.bc.gov.nrs.ilcr.schedule8.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;

/**
 * One Schedule 8 Check Status field finding (Story 14.6): the human field label (e.g. {@code Contact},
 * {@code Cut Block}, {@code Actual Harvested}, {@code Skidding/Yarding}) and its message. The service
 * emits the bundle key ({@code message.text} null); the controller resolves the verbatim text (AD-8).
 */
public record Schedule8CheckFieldIssue(String field, MessageInfo message) {
}
