package ca.bc.gov.nrs.ilcr.schedule4.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * One location's Check Status result (Story 4.4, S31 per-location breakdown). {@code met} is true
 * when every in-scope category / sub-page row on the location has a non-null Cost (0 counts as
 * present; distance is NOT enforced — §Decision 2, legacy parity).
 *
 * <p>A passing location carries one {@code locationRequirementsMetMsg} ("All requirements for {0}
 * have been met.", {0} = {@code name}) in {@code messages} and no {@code issues}; a failing location
 * carries one {@link FieldIssue} per missing-Cost field and an empty {@code messages}. The service
 * emits bundle keys; the controller resolves the text, substituting {@code name} for the per-location
 * met message (AD-8).
 *
 * @param id the location's primary report id ({@link Location#id()})
 * @param name the location description
 * @param met whether the location meets its requirements
 * @param messages the per-location message(s) — the met banner when passing, empty when failing
 * @param issues the missing-Cost fields — empty when passing
 */
public record LocationCheckResult(
    Integer id,
    String name,
    boolean met,
    List<MessageInfo> messages,
    List<FieldIssue> issues) {
}
