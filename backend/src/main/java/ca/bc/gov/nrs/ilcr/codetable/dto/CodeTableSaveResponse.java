package ca.bc.gov.nrs.ilcr.codetable.dto;

import java.util.List;

/**
 * The response to a Table Maintenance save (Story 24.3 / UC-CODE-001). Carries which arm of the
 * upsert ran, the verbatim success message (AD-8), and the reloaded table so the grid refreshes in a
 * single round-trip (legacy reloaded the grid after every save).
 *
 * @param outcome {@code INSERTED} or {@code UPDATED}
 * @param messageKey the {@code messages.properties} key of the success message
 * @param message the verbatim success text (SUC-001)
 * @param entries the table's entries after the save, code-ordered
 */
public record CodeTableSaveResponse(
    String outcome, String messageKey, String message, List<CodeTableEntry> entries) {
}
