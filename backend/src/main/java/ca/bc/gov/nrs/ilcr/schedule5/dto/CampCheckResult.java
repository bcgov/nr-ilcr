package ca.bc.gov.nrs.ilcr.schedule5.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One camp's Check Status result (S06/S20). Camps appear in {@code CAMP_REPORT_ID} order — 7.1's
 * deviation (c), because legacy iterates a {@code HashMap} and has no ORDER BY anywhere on this
 * path ({@code Schedule5DAO.java:58, 111-113}).
 *
 * <p>{@code campName} is what the message text keys on, not an ordinal: legacy's {@code
 * addMessageCheckStatus(reportID, …)} takes the camp NAME as its {@code reportID} argument ({@code
 * Schedule5MB.java:338, 347}), unlike Schedule 6 which composes from a 1-based {@code rowCounter}.
 * {@code campId} travels alongside purely for UI correlation.
 *
 * <p>{@code messages} carries EITHER a single {@code campRequirementsMetMsg} (when this camp
 * passes) OR one {@code missingRequiredFieldMsg} line per missing field, in the verbatim legacy
 * emission order. A passing camp's met message appears ONLY when the schedule outcome is {@code
 * ISSUES}: the all-met branch emits {@code scheduleRequirementsMetMsg} alone and never enters the
 * per-camp loop at all ({@code Schedule5MB.java:324-333}) — deviation (C), which contradicts both
 * the epics AC and {@code UC-SCH5-001-detailed.md:151}, each of which describes an all-met PAIR.
 * Legacy wins.
 *
 * @param campId the camp's DB id ({@code CAMP_REPORT_ID}) — UI correlation only
 * @param campName the camp name — THE identifier the composed message text carries
 * @param requirementsMet whether this camp meets its requirements
 * @param messages the met message, or the per-field {@code Value Required} lines — never both
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CampCheckResult(
    int campId, String campName, boolean requirementsMet, List<CampCheckMessage> messages) {

  /**
   * One check-status line: the legacy bundle key, the request-DTO field it points at, and the
   * composed verbatim text.
   *
   * <p>{@code field} is present only on a {@code Value Required} finding and is omitted from the
   * JSON on the met message ({@code NON_NULL}) — it names the {@link CampRequest} property the
   * licensee must supply, so a frontend can focus the right input rather than parse the sentence.
   * The service emits the key and the field with {@code text} null; the check-status resolver
   * resolves and composes the text (the house key/text split, AD-8).
   *
   * @param key the legacy {@code messages.properties} key
   * @param field the {@link CampRequest} field name this finding points at (null on the met
   *     message)
   * @param text the resolved, composed verbatim line
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CampCheckMessage(String key, String field, String text) {}
}
