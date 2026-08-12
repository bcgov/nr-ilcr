package ca.bc.gov.nrs.ilcr.schedule9.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The Schedule 9 Check Status result (Story 9.2, AD-12) — read-only readiness validation, no status
 * transition and no mutation. Reproduces {@code Schedule9CheckStatus.validateSchedule}: every stored
 * record is walked in served (id) order and each of its EIGHT fields tested in the exact legacy order
 * (Company ID, Contractual Item, Side Slope %, Number of Units, Unit Type, Biogeoclimatic Zone,
 * Cost$, Source). The three "Other" descriptions are deliberately NOT tested — the per-field
 * {@code isXxxDescriptionValid} methods exist in legacy but {@code validateSchedule} never calls them.
 *
 * <p><strong>The Save-vs-Check asymmetry is preserved, not repaired.</strong> Blank Number of
 * Units/Cost are accepted at Save (not required) but flagged here ({@code missingRequiredFieldMsg} =
 * "Value Required"); a Side Slope of exactly 100 saves (≤100) but is flagged here (the Check bound is
 * 0..99). A record with all eight satisfied contributes no line.
 *
 * <p>Each error is the verbatim legacy composition {@code FacesUtil.addCheckStatusErrorMessage}
 * produced: {@code title + ": " + text}, where the title is {@code "Contractual Work Report Id : " +
 * rowNumber + " " + fieldLabel} (the 1-based display row, not the DB id) and the text is either
 * "Value Required" or the {@code invalidRangeErrorMsg} the base validator emits for an out-of-range
 * value ({@code "Entered value must be between {0} and {1}."} with the field's own bounds). Reuses
 * {@link MessageInfo} (shared DTO, not duplicated).
 *
 * @param requirementsMet whether every stored record passes all eight checks (SUC-002 when true)
 * @param errors one entry per missing/out-of-range value, verbatim-composed, in record then legacy
 *     field order; empty when met
 * @param requirementsMetMessage SUC-002 {@code scheduleRequirementsMetMsg}
 *     ({@code "All requirements for this schedule have been met"}) when all pass, else null (omitted)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule9CheckStatusResponse(
    boolean requirementsMet,
    List<MessageInfo> errors,
    MessageInfo requirementsMetMessage) {
}
