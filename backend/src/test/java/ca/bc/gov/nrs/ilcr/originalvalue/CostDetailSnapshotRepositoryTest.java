package ca.bc.gov.nrs.ilcr.originalvalue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The {@code IN}-list finders' empty-parent-set guard.
 *
 * <p>Spring expands a named collection parameter one placeholder per element and does no empty-case
 * rewriting: {@code NamedParameterUtils.substituteNamedParameters} turns an empty list into the
 * literal SQL {@code IN ()}, which Oracle rejects with {@code ORA-00936: missing expression}. An
 * empty parent set is an ordinary state — a document beyond Draft that has no rows yet — so every
 * finder must answer it without issuing its statement.
 *
 * <p>The Oracle ITs cannot cover this: the statement they would have to observe is precisely the
 * one that must never be sent. {@code CALLS_REAL_METHODS} runs the interface's {@code default}
 * guards for real while leaving the {@code @Query} methods as unimplemented mocks, so each
 * assertion pins the guard's answer AND that the query behind it stayed unreached.
 */
class CostDetailSnapshotRepositoryTest {

  private final CostDetailSnapshotRepository repository =
      mock(CostDetailSnapshotRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

  @Test
  void everyInListFinder_readsAnEmptyParentSetAsNoRows() {
    assertTrue(repository.findByCampReports(List.of()).isEmpty());
    assertTrue(repository.findByTransportationReports(List.of()).isEmpty());
    assertTrue(repository.findByRoadMaintenanceReports(List.of()).isEmpty());
    assertTrue(repository.findByBridgeReports(List.of()).isEmpty());
    assertTrue(repository.findByCulvertReports(List.of()).isEmpty());
    assertTrue(repository.findByContractualWorkReports(List.of()).isEmpty());
    assertTrue(repository.findByRoadConstructionDetails(List.of()).isEmpty());
    assertTrue(repository.findBySilvicultureLocations(List.of()).isEmpty());

    // Not one @Query method behind those guards was reached — no IN () left for Oracle to reject.
    verify(repository, never()).findByCampReportsIn(anyList());
    verify(repository, never()).findByTransportationReportsIn(anyList());
    verify(repository, never()).findByRoadMaintenanceReportsIn(anyList());
    verify(repository, never()).findByBridgeReportsIn(anyList());
    verify(repository, never()).findByCulvertReportsIn(anyList());
    verify(repository, never()).findByContractualWorkReportsIn(anyList());
    verify(repository, never()).findByRoadConstructionDetailsIn(anyList());
    verify(repository, never()).findBySilvicultureLocationsIn(anyList());
  }

  @Test
  void nullParentSet_isTreatedAsEmpty() {
    // Defensive: a caller handing over a null list gets no rows rather than an NPE inside the
    // template. No caller does today, and the guard is what keeps that cheap to keep true.
    assertTrue(repository.findByCampReports(null).isEmpty());
    verify(repository, never()).findByCampReportsIn(anyList());
  }

  @Test
  void nonEmptyParentSet_reachesTheQueryVerbatim() {
    List<List<Integer>> seen = new ArrayList<>();
    doAnswer(
            invocation -> {
              seen.add(invocation.getArgument(0));
              return List.of();
            })
        .when(repository)
        .findByCampReportsIn(anyList());

    repository.findByCampReports(List.of(11L, 22L));

    // The guard is a pass-through, not a filter: the parent ids arrive unaltered and in order.
    assertEquals(List.of(List.of(11L, 22L)), seen);
  }
}
