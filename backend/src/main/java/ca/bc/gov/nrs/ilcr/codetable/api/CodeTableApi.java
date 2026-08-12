package ca.bc.gov.nrs.ilcr.codetable.api;

import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSaveResponse;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSummary;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Table Maintenance API contract (Story 24.3 / UC-CODE-001; controller + api-interface split). The
 * interface owns the request mapping; {@code CodeTableController} implements it and adds the
 * ADMIN-only {@code MAINTAIN_CODE_TABLES} authorization (S13 — every endpoint 403s a non-admin).
 */
@RequestMapping("/api/v1/code-tables")
public interface CodeTableApi {

  /**
   * List the maintainable code tables for the selector (BR-01) — the 18 generic tables; Contractual
   * Item Codes is a separate slice.
   *
   * @param authentication the caller (must hold {@code MAINTAIN_CODE_TABLES})
   * @return 200 with the selectable tables
   */
  @GetMapping
  ResponseEntity<List<CodeTableSummary>> listTables(Authentication authentication);

  /**
   * Get every entry of a selected table for the maintenance grid (not year-filtered). Unknown table
   * key → 404.
   *
   * @param tableKey the selected table's key
   * @param authentication the caller (must hold {@code MAINTAIN_CODE_TABLES})
   * @return 200 with the table's entries, code-ordered
   */
  @GetMapping("/{tableKey}/entries")
  ResponseEntity<List<CodeTableEntry>> getEntries(
      @PathVariable String tableKey, Authentication authentication);

  /**
   * Add or edit one entry (upsert: insert when the code is new, else update the matching row — BR-03).
   * Required-field / date-range failures → 400 and nothing is saved (FLD-001..005); unknown table →
   * 404. Returns the outcome, the verbatim success message, and the reloaded grid.
   *
   * @param tableKey the selected table's key
   * @param entry the code/description/effective/expiry to persist
   * @param authentication the caller (must hold {@code MAINTAIN_CODE_TABLES}; drives the audit user)
   * @return 200 with the save outcome + reloaded entries
   */
  @PutMapping("/{tableKey}/entries")
  ResponseEntity<CodeTableSaveResponse> saveEntry(
      @PathVariable String tableKey,
      @RequestBody CodeTableEntry entry,
      Authentication authentication);
}
