package ca.bc.gov.nrs.ilcr.millcontext.dto;

/**
 * A user-facing message carried on a confirming/mutating response (AD-8): the LEGACY {@code
 * messages.properties} bundle key plus its server-resolved verbatim text. The frontend renders
 * {@code text} and never hardcodes SUC-* strings.
 *
 * <p>Deliberately a {@code millcontext}-local record rather than importing {@code
 * schedule1.dto.MessageInfo}: cross-domain DTO coupling is disallowed (per-domain layering,
 * NFR6/AD-6), and promoting the schedule1 record to a shared package would require editing
 * schedule1 (outside Story 1.3's Home-only file scope). Structurally identical to schedule1's
 * record so the pinned wire contract stays uniform (AD-12).
 *
 * @param key the legacy bundle key (e.g. {@code dataSavedSuccesfullyInfoMsg})
 * @param text the resolved verbatim message text (e.g. {@code Data saved successfully})
 */
public record MessageInfo(String key, String text) {}
