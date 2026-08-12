package ca.bc.gov.nrs.ilcr.codetable;

import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Generic read/upsert over the maintainable {@code THE.*_CODE} lookup tables (Story 24.3 / T2).
 *
 * <p>One repository serves all 18 backing tables rather than 18 hand-written ones: the table and
 * code-column identifiers come from {@link CodeTableRegistry} (a fixed enum), never from a caller, so
 * interpolating them into the SQL is safe — every VALUE (code, description, dates) is still a bound
 * named parameter. Columns are exactly those the legacy {@code AbstractILCRCode} mapped: the code PK,
 * {@code DESCRIPTION}, {@code EFFECTIVE_DATE}, {@code EXPIRY_DATE}, and {@code UPDATE_TIMESTAMP}
 * (stamped {@code SYSTIMESTAMP} on every write — the only audit column these reference tables carry).
 *
 * <p>The Contractual Item Codes table has no backing {@code *_CODE} table (BR-08) and is rejected
 * here; it is maintained through the Schedule 9 cost-item path instead.
 */
@Repository
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
// java:S2077 — the only interpolated SQL tokens are the table + code-column identifiers, and those
// come exclusively from the CodeTableRegistry enum (a fixed compile-time whitelist), never from a
// caller; every value is a bound named parameter. There is no user-controlled input in the query
// text, so the dynamically formatted SQL is safe.
@SuppressWarnings("java:S2077")
public class CodeTableRepository {

  /** Which arm of an {@link #upsert} ran — drives the "silent update" case (S05) and messaging. */
  public enum UpsertResult {
    INSERTED,
    UPDATED
  }

  private static final RowMapper<CodeTableEntry> ENTRY_MAPPER = CodeTableRepository::mapEntry;

  private final NamedParameterJdbcTemplate jdbc;

  public CodeTableRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** All entries of a table, ordered by code — the full maintenance grid (not year-filtered). */
  public List<CodeTableEntry> findEntries(CodeTableRegistry table) {
    String codeColumn = requireBackingTable(table).codeColumn();
    String sql = "SELECT %s AS code, DESCRIPTION AS description, EFFECTIVE_DATE, EXPIRY_DATE FROM %s "
        .formatted(codeColumn, qualified(table)) + "ORDER BY " + codeColumn;
    return jdbc.query(sql, ENTRY_MAPPER);
  }

  /** Whether a code already exists in the table (BR-03: existing-row check drives insert vs update). */
  public boolean exists(CodeTableRegistry table, String code) {
    String codeColumn = requireBackingTable(table).codeColumn();
    String sql = "SELECT COUNT(*) FROM %s WHERE %s = :code".formatted(qualified(table), codeColumn);
    Integer count = jdbc.queryForObject(sql, new MapSqlParameterSource("code", code), Integer.class);
    return count != null && count > 0;
  }

  /**
   * Insert the code when it does not exist, otherwise update the matching row (BR-03) — the same
   * upsert legacy {@code CodeListDAO.save*} performed. Returns which arm ran.
   *
   * <p>Atomic: it tries the UPDATE first (one statement), and only INSERTs when no row matched. If a
   * concurrent save inserts the same brand-new code in the race window, our INSERT hits the primary
   * key and we fall back to UPDATE — so two simultaneous saves of a new code both succeed as the
   * intended silent-update (S05) rather than one 500ing on a constraint violation.
   */
  public UpsertResult upsert(CodeTableRegistry table, CodeTableEntry entry) {
    if (update(table, entry) > 0) {
      return UpsertResult.UPDATED;
    }
    try {
      insert(table, entry);
      return UpsertResult.INSERTED;
    } catch (DataIntegrityViolationException raced) {
      update(table, entry);
      return UpsertResult.UPDATED;
    }
  }

  private void insert(CodeTableRegistry table, CodeTableEntry entry) {
    String codeColumn = requireBackingTable(table).codeColumn();
    String sql = ("INSERT INTO %s (%s, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP) "
        + "VALUES (:code, :description, :effectiveDate, :expiryDate, SYSTIMESTAMP)")
        .formatted(qualified(table), codeColumn);
    jdbc.update(sql, params(entry));
  }

  private int update(CodeTableRegistry table, CodeTableEntry entry) {
    String codeColumn = requireBackingTable(table).codeColumn();
    String sql = ("UPDATE %s SET DESCRIPTION = :description, EFFECTIVE_DATE = :effectiveDate, "
        + "EXPIRY_DATE = :expiryDate, UPDATE_TIMESTAMP = SYSTIMESTAMP WHERE %s = :code")
        .formatted(qualified(table), codeColumn);
    return jdbc.update(sql, params(entry));
  }

  private static MapSqlParameterSource params(CodeTableEntry entry) {
    return new MapSqlParameterSource()
        .addValue("code", entry.code())
        .addValue("description", entry.description())
        .addValue("effectiveDate", entry.effectiveDate())
        .addValue("expiryDate", entry.expiryDate());
  }

  /** {@code THE.<table>}; the identifier is a trusted registry constant, never caller input. */
  private static String qualified(CodeTableRegistry table) {
    return "THE." + requireBackingTable(table).table();
  }

  private static CodeTableRegistry requireBackingTable(CodeTableRegistry table) {
    if (table.contractual() || table.table() == null) {
      throw new IllegalArgumentException(
          "Contractual Item Codes has no *_CODE table; maintain it via the Schedule 9 path");
    }
    return table;
  }

  private static CodeTableEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
    return new CodeTableEntry(
        rs.getString("code"),
        rs.getString("description"),
        toLocalDate(rs, "EFFECTIVE_DATE"),
        toLocalDate(rs, "EXPIRY_DATE"));
  }

  private static LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
    java.sql.Date value = rs.getDate(column);
    return value == null ? null : value.toLocalDate();
  }
}
