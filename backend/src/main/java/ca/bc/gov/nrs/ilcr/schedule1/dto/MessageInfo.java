package ca.bc.gov.nrs.ilcr.schedule1.dto;

/**
 * A user-facing message carried on a mutating response (AD-8/EQ-M3): the LEGACY bundle key plus the
 * resolved verbatim text. The frontend renders {@code text} and never hardcodes SUC-* strings.
 *
 * @param key the legacy {@code messages.properties} key (e.g. {@code dataSavedSuccesfullyInfoMsg})
 * @param text the resolved verbatim message text (e.g. {@code Data saved successfully})
 */
public record MessageInfo(String key, String text) {
}
