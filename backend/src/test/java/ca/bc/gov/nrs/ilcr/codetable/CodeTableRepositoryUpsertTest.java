package ca.bc.gov.nrs.ilcr.codetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.codetable.CodeTableRepository.UpsertResult;
import ca.bc.gov.nrs.ilcr.codetable.dto.CodeTableEntry;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * The {@link CodeTableRepository#upsert} race-vs-real-failure distinction. A brand-new code whose
 * INSERT raises an integrity violation may be reported as a silent update ONLY when the fallback
 * UPDATE actually matches a row (a genuine duplicate-key race). When nothing was written the
 * exception must propagate — otherwise a NOT NULL / CHECK / FK constraint failure surfaces as a
 * false "saved" while the entry never persists (the Table Maintenance silent-data-loss bug).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CodeTableRepository.upsert — integrity violation on insert")
class CodeTableRepositoryUpsertTest {

  private static final CodeTableRegistry TABLE =
      Arrays.stream(CodeTableRegistry.values())
          .filter(table -> !table.contractual() && table.table() != null)
          .findFirst()
          .orElseThrow();
  private static final CodeTableEntry NEW_ENTRY =
      new CodeTableEntry("NEW", "New description", LocalDate.of(2024, 1, 1), null);

  @Mock private NamedParameterJdbcTemplate jdbc;

  @Test
  @DisplayName(
      "insert fails and the row still does not exist -> exception propagates (no false save)")
  void insertFailsWithNoRace_reThrows() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenReturn(0) // UPDATE probe: brand-new code, nothing to update
        .thenThrow(
            new DataIntegrityViolationException("ORA-01400: cannot insert NULL")) // INSERT fails
        .thenReturn(0); // fallback UPDATE: still nothing there -> the write truly failed
    CodeTableRepository repository = new CodeTableRepository(jdbc);

    assertThatThrownBy(() -> repository.upsert(TABLE, NEW_ENTRY))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("insert loses a duplicate-key race -> absorbed as a silent UPDATE")
  void insertLosesRace_absorbedAsUpdate() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenReturn(0) // UPDATE probe
        .thenThrow(
            new DataIntegrityViolationException("ORA-00001: unique constraint")) // INSERT races
        .thenReturn(1); // fallback UPDATE: the concurrently-inserted row now exists
    CodeTableRepository repository = new CodeTableRepository(jdbc);

    assertThat(repository.upsert(TABLE, NEW_ENTRY)).isEqualTo(UpsertResult.UPDATED);
  }
}
