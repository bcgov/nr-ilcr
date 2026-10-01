package ca.bc.gov.nrs.ilcr.checkstatus.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;

/**
 * The outcome of verifying the Schedules 1&ndash;10 track. A new shape rather than a field added to
 * the check-status sweep response: pinned sub-shapes are extended, never re-shaped (AD-12).
 *
 * @param trackStatus the track's status code after the transition — always {@code V}, since this
 *     shape is only returned on a 200 and a refused transition answers 409 instead
 * @param message the verbatim legacy success text with its bundle key, resolved by the controller
 *     because the API returns final text rather than a code for the client to look up (AD-8)
 */
public record VerifyReportResponse(String trackStatus, MessageInfo message) {}
