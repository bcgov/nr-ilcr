package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * Minimal body for a mutating action that returns no document — {@code DELETE /api/v1/schedule3}
 * (AD-8/EQ-M3). Carries only the success {@link MessageInfo} so the frontend renders server text.
 * Schedule3-local copy of the generic envelope (peer-domain, per Story 4.1).
 *
 * @param message the success message (key + verbatim text)
 */
public record MessageResponse(MessageInfo message) {
}
