package ca.bc.gov.nrs.ilcr.codetable;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSummary;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Code-table maintenance service (Story 24.3 / UC-CODE-001, T2). Lists the selectable tables, reads a
 * table's entries, and upserts one entry inside a transaction.
 *
 * <p>Validation (FLD-001..005) runs before any write, so a rejection saves nothing. A persistence
 * failure is NOT swallowed — it propagates as an error (S11: the legacy false-success is fixed here by
 * never returning a success outcome for a write that did not happen). Every successful write logs the
 * acting admin + table + code as the audit trail (S13); these reference tables carry no per-row user
 * column, so the log IS the "who changed what" record.
 *
 * <p>Contractual Item Codes (BR-08, description-only, Schedule 9-backed) is a separate slice (S04) and
 * is intentionally not offered by {@link #listTables()} yet.
 */
@Slf4j
@Service
public class CodeTableService {

  private final CodeTableRepository repository;

  public CodeTableService(CodeTableRepository repository) {
    this.repository = repository;
  }

  /** The maintainable tables for the selector — the 18 generic tables (Contractual excluded for now). */
  public List<CodeTableSummary> listTables() {
    return Arrays.stream(CodeTableRegistry.values())
        .filter(table -> !table.contractual())
        .map(table -> new CodeTableSummary(
            table.key(), table.label(), table.codeMaxLength(), table.descriptionMaxLength()))
        .toList();
  }

  /** All entries of the selected table (unknown key → 404). */
  public List<CodeTableEntry> entries(String tableKey) {
    return repository.findEntries(resolve(tableKey));
  }

  /**
   * Validate then upsert one entry (insert if new, update the matching row if the code exists —
   * BR-03). Unknown table → 404; a validation failure → 400 and nothing is written.
   *
   * @param tableKey the selected table's key
   * @param entry the code/description/effective/expiry to persist
   * @param user the acting administrator (for the audit log)
   * @return whether the row was inserted or updated
   */
  @Transactional
  public UpsertResult save(String tableKey, CodeTableEntry entry, String user) {
    CodeTableRegistry table = resolve(tableKey);
    validate(entry);
    UpsertResult result = repository.upsert(table, entry);
    log.info("Table Maintenance: {} {} code '{}' in {}", user, result, entry.code(), table.key());
    return result;
  }

  private CodeTableRegistry resolve(String tableKey) {
    return CodeTableRegistry.byKey(tableKey)
        .filter(table -> !table.contractual())
        .orElseThrow(CodeTableException::unknownTable);
  }

  // FLD-001..005: code, description, both dates required; effective on or before expiry.
  private static void validate(CodeTableEntry entry) {
    if (!StringUtils.hasText(entry.code())) {
      throw CodeTableException.validation("codeRequiredErrorMsg");
    }
    if (!StringUtils.hasText(entry.description())) {
      throw CodeTableException.validation("descriptionRequiredErrorMsg");
    }
    if (entry.effectiveDate() == null) {
      throw CodeTableException.validation("effectiveDateRequiredErrorMsg");
    }
    if (entry.expiryDate() == null) {
      throw CodeTableException.validation("expiryDateRequiredErrorMsg");
    }
    if (entry.expiryDate().isBefore(entry.effectiveDate())) {
      throw CodeTableException.validation("expiryBeforeEffectiveErrorMsg");
    }
  }
}
