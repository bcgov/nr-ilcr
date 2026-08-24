package ca.bc.gov.nrs.ilcr.schedule6.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One road record's Check Status result (S09–S11, S20, S21). The message text keys on {@code
 * rowCounter} — the 1-based ordinal in payload order (legacy {@code Schedule6MB} composes from
 * {@code rowCounter}, assigned by DB read order in the pre-Task-6 stored-rows evaluation; the
 * Gherkin pins {@code "Road : 1 - …"}). {@code recordId} carries that same 1-based payload ordinal,
 * not a database id — a payload row addresses no stored record, so there is no id to echo; the
 * frontend only ever uses it as a React list key, never to correlate a result back to a stored row.
 *
 * <p>{@code metMessage} ({@code roadRequirementsMetMsg}, "All requirements for {rowCounter} have
 * been met.") is present only when this record is met AND the schedule outcome is ISSUES — the
 * legacy pass branch never enters the per-record loop, so an all-clean schedule emits no per-record
 * messages at all. {@code issues} carries one entry per missing field in legacy order (type,
 * TFL/Supply Block, cost); the service emits bundle keys, the controller resolves the composed
 * verbatim text (AD-8).
 *
 * @param recordId the 1-based payload ordinal (no stored id exists for an on-screen row) — a React
 *     list key only, never a correlation back to a database record
 * @param rowCounter the 1-based ordinal used in the message text (THE identifier the user sees)
 * @param met whether the record meets its requirements
 * @param metMessage the per-record met banner — only when met and the schedule outcome is ISSUES
 * @param issues the missing-field findings — empty when met
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoadRecordCheckResult(
    int recordId, int rowCounter, boolean met, MessageInfo metMessage, List<FieldIssue> issues) {

  /**
   * One missing-field finding: {@code field} names the request field the user must supply ({@code
   * areaType}, {@code tflNumber}, {@code supplyBlock}, or {@code cost}); {@code message} carries
   * the composed verbatim line (e.g. {@code "Road : 1 - TFL Number : Value Required"}) under the
   * shared {@code missingRequiredFieldMsg} key.
   *
   * @param field the request-DTO field name the finding points at
   * @param message the composed "Value Required" line (key + resolved text)
   */
  public record FieldIssue(String field, MessageInfo message) {}
}
