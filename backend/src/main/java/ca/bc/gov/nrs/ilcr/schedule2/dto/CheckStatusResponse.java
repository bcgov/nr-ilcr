package ca.bc.gov.nrs.ilcr.schedule2.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.util.List;

/**
 * The Schedule 2 Check Status result (BR-07) — a read-only (AD-5) evaluation of the server-assembled
 * document. {@code outcome} is {@code "MET"} when the assembled {@code purchasedLogCost.cost}
 * (cost-item 25) is present, else {@code "ISSUES"} (including the unsaved-schedule state). Each
 * {@link MessageInfo} carries BOTH the legacy bundle key and its resolved verbatim text (AD-8), the
 * same shape as the save/delete success message: {@code MET} -> {@code scheduleRequirementsMetMsg};
 * {@code ISSUES} -> {@code missingRequiredFieldMsg}.
 *
 * @param outcome {@code "MET"} or {@code "ISSUES"}
 * @param messages the user-facing message(s) for the outcome (key + resolved text)
 */
public record CheckStatusResponse(
    String outcome,
    List<MessageInfo> messages) {
}
