package ca.bc.gov.nrs.ilcr.schedule1.dto;

/**
 * Minimal body for a mutating action that returns no document — currently {@code DELETE /schedule1}
 * (AD-8/EQ-M3). Carries only the success {@link MessageInfo} so the frontend renders server text.
 *
 * @param message the success message (key + verbatim text)
 */
public record MessageResponse(MessageInfo message) {
}
