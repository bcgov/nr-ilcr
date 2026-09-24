package ca.bc.gov.nrs.ilcr.schedule4.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;

/**
 * One missing-field finding within a location's Check Status result (Story 4.4, S31). {@code code}
 * identifies the field: {@link #LOCATION_DESCRIPTION} for the location description — the only field
 * legacy's Schedule 4 check ever enforced (issue #465) — while the legacy cost-item codes (40–55)
 * remain the wire vocabulary for a per-category Cost should a check ever be enabled. {@code
 * message} carries the verbatim {@code missingRequiredFieldMsg} ("Value Required"). The service
 * emits the bundle key; the check-status resolver resolves the text (AD-8).
 *
 * @param code the field: {@link #LOCATION_DESCRIPTION}, or a cost-item code
 * @param message the "Value Required" message (key + resolved text)
 */
public record FieldIssue(int code, MessageInfo message) {

  /**
   * The location description field. Not a cost-item code — those start at 40 — so it can never
   * collide with a category. Legacy's Check Status tab labelled this row "Description" ({@code
   * checkStatusSchedule4.xhtml:16-23}).
   */
  public static final int LOCATION_DESCRIPTION = 0;
}
