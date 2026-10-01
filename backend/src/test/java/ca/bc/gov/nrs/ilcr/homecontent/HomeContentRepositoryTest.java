package ca.bc.gov.nrs.ilcr.homecontent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class HomeContentRepositoryTest {

  @Mock private NamedParameterJdbcTemplate jdbc;

  @InjectMocks private HomeContentRepository repository;

  @Test
  void upsertMessage_requiresExactlyOneAffectedRow() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

    assertThrows(
        IllegalStateException.class, () -> repository.upsertMessage("ADMIN", "<p>A</p>", "user"));
  }

  @Test
  void upsertMessage_retriesUpdateAfterConcurrentInsertRace() {
    when(jdbc.update(anyString(), any(SqlParameterSource.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"))
        .thenReturn(1);

    assertEquals(1, repository.upsertMessage("ADMIN", "<p>A</p>", "user"));
    verify(jdbc, org.mockito.Mockito.times(2)).update(anyString(), any(SqlParameterSource.class));
  }
}
