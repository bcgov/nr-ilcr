package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;

/**
 * One outstanding Check Status requirement on a page or a road detail.
 *
 * <p>{@code field} is the machine name the frontend correlates against ({@code divisionName},
 * {@code roadName}, {@code materialTypeTotal}, …); {@code message} carries the resolved legacy
 * text. The service emits the key with null text and the check-status resolver composes the final
 * string, so the verbatim byte composition lives in exactly one place.
 *
 * @param field the machine field name, stable for UI correlation
 * @param message the legacy bundle key and its composed, resolved text
 */
public record FieldIssue(String field, MessageInfo message) {}
