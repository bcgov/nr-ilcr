package ca.bc.gov.nrs.ilcr.codetable.dto;

/**
 * A selectable code table for the maintenance dropdown (Story 24.3 / UC-CODE-001, BR-01/BR-06).
 *
 * @param key the stable API key (used to address the table's entries)
 * @param label the human-readable dropdown label
 * @param codeMaxLength per-table code length cap
 * @param descriptionMaxLength per-table description length cap
 * @param contractual true for the Schedule 9-backed description-only table
 */
public record CodeTableSummary(
    String key, String label, int codeMaxLength, int descriptionMaxLength, boolean contractual) {

  public CodeTableSummary(String key, String label, int codeMaxLength, int descriptionMaxLength) {
    this(key, label, codeMaxLength, descriptionMaxLength, false);
  }
}
