package ca.bc.gov.nrs.ilcr.codetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Unit test for the generic repository's control flow with a mocked JDBC template (Story 24.3 / T2)
 * — the upsert branches (including the insert-race fallback) without a database. The SQL itself is
 * exercised against real Oracle by {@link CodeTableRepositoryIT}.
 */
@ExtendWith(MockitoExtension.class)
class CodeTableRepositoryUnitTest {

  private static final CodeTableRegistry UNIT = CodeTableRegistry.UNIT_CODE;
  private static final CodeTableEntry ENTRY =
      new CodeTableEntry("M3", "Cubic Metres", LocalDate.of(2020, 1, 1), null);

  @Mock private NamedParameterJdbcTemplate jdbc;

  @InjectMocks private CodeTableRepository repository;

  @Test
  void upsert_updatesInPlace_whenTheRowExists() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    assertEquals(UpsertResult.UPDATED, repository.upsert(UNIT, ENTRY));
  }

  @Test
  void upsert_inserts_whenNoRowMatched() {
    // UPDATE affects 0 rows (new code), then the INSERT succeeds.
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0, 1);
    assertEquals(UpsertResult.INSERTED, repository.upsert(UNIT, ENTRY));
  }

  @Test
  void upsert_fallsBackToUpdate_whenTheInsertRacesAnotherSave() {
    // UPDATE 0 rows → INSERT hits a concurrent row (PK) → fall back to UPDATE.
    when(jdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenReturn(0)
        .thenThrow(new DataIntegrityViolationException("duplicate key"))
        .thenReturn(1);
    assertEquals(UpsertResult.UPDATED, repository.upsert(UNIT, ENTRY));
  }

  @Test
  void exists_reflectsTheCount() {
    when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
        .thenReturn(1, 0);
    assertTrue(repository.exists(UNIT, "M3"));
    assertFalse(repository.exists(UNIT, "NOPE"));
  }

  @Test
  void findEntries_returnsTheMappedRows() {
    when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(ENTRY));
    assertEquals(1, repository.findEntries(UNIT).size());
  }

  @Test
  void contractual_hasNoBackingTable_andIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.findEntries(CodeTableRegistry.CONTRACTUAL_ITEM_CODE));
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.upsert(CodeTableRegistry.CONTRACTUAL_ITEM_CODE, ENTRY));
  }
}
