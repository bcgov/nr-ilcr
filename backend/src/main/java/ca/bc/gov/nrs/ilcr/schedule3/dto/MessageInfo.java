package ca.bc.gov.nrs.ilcr.schedule3.dto;

/**
 * A user-facing message carried on a mutating response (AD-8/EQ-M3): the LEGACY bundle key plus the
 * resolved verbatim text. The frontend renders {@code text} and never hardcodes the SUC/WRN strings.
 *
 * <p>Schedule 3 keeps its own copy of this generic envelope rather than importing Schedule 1's
 * {@code schedule1.dto.MessageInfo} — the schedule domains are peers and neither depends on the other
 * (a later refactor may promote it to a shared {@code dto.base} package).
 *
 * @param key the legacy {@code messages.properties} key (e.g. {@code dataSavedSuccesfullyInfoMsg})
 * @param text the resolved verbatim message text (e.g. {@code Data saved successfully})
 */
public record MessageInfo(String key, String text) {
}
