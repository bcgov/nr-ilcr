package ca.bc.gov.nrs.ilcr.codetable;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableSummary;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Code-table maintenance service (Story 24.3 / UC-CODE-001, T2). Lists the selectable tables, reads
 * a table's entries, and upserts one entry inside a transaction.
 *
 * <p>Validation (FLD-001..005) runs before any write, so a rejection saves nothing. A persistence
 * failure is NOT swallowed — it propagates as an error (S11: the legacy false-success is fixed here
 * by never returning a success outcome for a write that did not happen). Every successful write
 * logs the acting admin + table + code as the audit trail (S13); these reference tables carry no
 * per-row user column, so the log IS the "who changed what" record.
 *
 * <p>Contractual Item Codes (BR-08) is included in the selector but routed through its Schedule
 * 9-backed repository path because it has no generic {@code *_CODE} table.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class CodeTableService {

  private final CodeTableRepository repository;

  public CodeTableService(CodeTableRepository repository) {
    this.repository = repository;
  }

  /**
   * The maintainable tables for the selector — all 19 legacy tables, including the Schedule
   * 9-backed Contractual Item Codes table.
   */
  public List<CodeTableSummary> listTables() {
    return Arrays.stream(CodeTableRegistry.values())
        .map(
            table ->
                new CodeTableSummary(
                    table.key(),
                    table.label(),
                    table.codeMaxLength(),
                    table.descriptionMaxLength(),
                    table.contractual()))
        .toList();
  }

  /** All entries of the selected table (unknown key → 404). */
  public List<CodeTableEntry> entries(String tableKey) {
    CodeTableRegistry table = resolve(tableKey);
    return table.contractual()
        ? repository.findContractualEntries()
        : repository.findEntries(table);
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
    validate(table, entry);
    UpsertResult result =
        table.contractual()
            ? repository.upsertContractualItem(entry, user)
            : repository.upsert(table, entry);
    log.info("Table Maintenance: {} {} code '{}' in {}", user, result, entry.code(), table.key());
    return result;
  }

  private CodeTableRegistry resolve(String tableKey) {
    return CodeTableRegistry.byKey(tableKey).orElseThrow(CodeTableException::unknownTable);
  }

  /**
   * FLD-001..005 + BR-06: code, description, and effective date are required; the per-table code /
   * description length caps are enforced; and when BOTH dates are present, effective must be on or
   * before expiry. Expiry is OPTIONAL — a null expiry is the "never expires" case the read side
   * NVLs to a far-future date, so an existing open-ended row can be edited without inventing an
   * expiry (recorded deviation from the legacy always-required-expiry, AD-8).
   */
  private static void validate(CodeTableRegistry table, CodeTableEntry entry) {
    if (!table.contractual() && !StringUtils.hasText(entry.code())) {
      throw CodeTableException.validation("codeRequiredErrorMsg");
    }
    if (StringUtils.hasText(entry.code()) && entry.code().length() > table.codeMaxLength()) {
      throw CodeTableException.validation("codeTableCodeLengthErrorMsg");
    }
    if (!StringUtils.hasText(entry.description())) {
      throw CodeTableException.validation("descriptionRequiredErrorMsg");
    }
    if (entry.description().getBytes(StandardCharsets.UTF_8).length
        > table.descriptionMaxLength()) {
      throw CodeTableException.validation("codeTableDescriptionLengthErrorMsg");
    }
    if (entry.effectiveDate() == null) {
      throw CodeTableException.validation("effectiveDateRequiredErrorMsg");
    }
    if (table.contractual() && entry.expiryDate() == null) {
      throw CodeTableException.validation("expiryDateRequiredErrorMsg");
    }
    if (entry.expiryDate() != null && entry.expiryDate().isBefore(entry.effectiveDate())) {
      throw CodeTableException.validation("expiryBeforeEffectiveErrorMsg");
    }
  }
}
