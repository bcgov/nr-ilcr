package ca.bc.gov.nrs.ilcr.dto.base;

/**
 * A user-facing message carried on a mutating/confirming response (AD-8/EQ-M3): the LEGACY {@code
 * messages.properties} bundle key plus its server-resolved verbatim text. The frontend renders
 * {@code text} and never hardcodes SUC/WRN strings.
 *
 * <p>The canonical, feature-neutral home for this envelope (Story 29.9): it lives in the shared
 * {@code dto.base} package so no feature module (schedule1..11, millcontext) has to reach into
 * another for it. Structurally identical across every consumer, so the pinned wire contract stays
 * uniform (AD-12).
 *
 * @param key the legacy {@code messages.properties} key (e.g. {@code dataSavedSuccesfullyInfoMsg})
 * @param text the resolved verbatim message text (e.g. {@code Data saved successfully})
 */
public record MessageInfo(String key, String text) {}
