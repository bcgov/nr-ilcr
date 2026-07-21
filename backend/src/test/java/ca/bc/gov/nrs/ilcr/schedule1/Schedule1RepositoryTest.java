package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Schedule1RepositoryTest {

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private Schedule1Repository repository;

  @Test
  void findSummary_mapsSpringDataEntityToServiceRow() {
    when(repository.findSummaryEntity(518L, 2021, "1"))
        .thenReturn(Optional.of(new Schedule1SummaryEntity(1018, 12345, "comment", 7)));

    Optional<SummaryRow> result = repository.findSummary(518L, 2021, "1");

    assertEquals(Optional.of(new SummaryRow(1018, 12345, "comment", 7)), result);
  }

  @Test
  void findDetails_mapsSpringDataEntitiesToServiceRows() {
    when(repository.findDetailEntities(1018))
        .thenReturn(List.of(
            new Schedule1DetailEntity(5001, 12, new BigDecimal("2000"), 60000, null),
            new Schedule1DetailEntity(5002, 19, null, 12000, "Row A")));

    List<DetailRow> result = repository.findDetails(1018);

    assertEquals(List.of(
        new DetailRow(12, new BigDecimal("2000"), 60000, null),
        new DetailRow(19, null, 12000, "Row A")), result);
  }

  @Test
  void upsertFixedDetail_updatesExistingRowWithoutInsert() {
    when(repository.updateFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user"))
        .thenReturn(1);

    repository.upsertFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user");

    verify(repository, never()).insertFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user");
  }

  @Test
  void upsertFixedDetail_insertsWhenNoFixedRowExists() {
    when(repository.updateFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user"))
        .thenReturn(0);

    repository.upsertFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user");

    verify(repository).insertFixedDetail(1018, 12, new BigDecimal("2000"), 60000, "user");
  }

  @Test
  void deleteSchedule_deletesDetailsBeforeSummary() {
    repository.deleteSchedule(1018);

    InOrder inOrder = inOrder(repository);
    inOrder.verify(repository).deleteDetails(1018);
    inOrder.verify(repository).deleteSummary(1018);
  }
}
