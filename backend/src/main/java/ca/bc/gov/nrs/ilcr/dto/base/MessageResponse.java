package ca.bc.gov.nrs.ilcr.dto.base;

/**
 * Minimal body for a mutating action that returns no document — e.g. {@code DELETE /schedule1},
 * {@code DELETE /api/v1/schedule3} (AD-8/EQ-M3). Carries only the success {@link MessageInfo} so
 * the frontend renders server text.
 *
 * <p>The canonical, feature-neutral home for this envelope (Story 29.9): shared {@code dto.base} so
 * no feature module reaches into another for it.
 *
 * @param message the success message (key + verbatim text)
 */
public record MessageResponse(MessageInfo message) {}
