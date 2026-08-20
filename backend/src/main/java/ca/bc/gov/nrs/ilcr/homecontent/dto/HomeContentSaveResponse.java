package ca.bc.gov.nrs.ilcr.homecontent.dto;

import java.util.List;

/**
 * The response to a Content Editing save (Story 24.2 / UC-CNT-001): the verbatim success message
 * (AD-8) and the reloaded messages so the editors refresh in a single round-trip.
 *
 * @param messageKey the {@code messages.properties} key of the success message
 * @param message the verbatim success text (SUC-001)
 * @param entries the three role messages after the save
 */
public record HomeContentSaveResponse(String messageKey, String message, List<HomeContentEntry> entries) {
}
