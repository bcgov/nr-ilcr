package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository.AbutmentTypeCode;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository.ConstructionTypeCode;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository.DeckTypeCode;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository.LoadRatingCode;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository.SuperstructureTypeCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@code default}/mapping logic in {@link Schedule7aRepository} — the {@code
 * *_CODE} row → {@code CodeDescriptionDto} option mapping ({@code schedule8} idiom) and the {@code
 * upsertCost} update-in-place-else-insert branch. The {@code @Query} SQL itself is proven against
 * Oracle in the {@code *IT} suite; here the real default methods run over a mocked repository so
 * the pure Java is covered without a database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule7aRepository — code-option mapping and upsertCost branch")
class Schedule7aRepositoryTest {

  @Mock private Schedule7aRepository repository;

  @Test
  @DisplayName("each option list maps its code-table rows to {code, description}")
  void optionLists_mapCodeRowsToDto() {
    doCallRealMethod().when(repository).constructionTypeOptions(anyInt());
    doCallRealMethod().when(repository).superstructureTypeOptions(anyInt());
    doCallRealMethod().when(repository).deckTypeOptions(anyInt());
    doCallRealMethod().when(repository).abutmentTypeOptions(anyInt());
    doCallRealMethod().when(repository).loadRatingOptions(anyInt());
    when(repository.findConstructionTypeCodes(any()))
        .thenReturn(
            List.of(new ConstructionTypeCode("N", "New"), new ConstructionTypeCode("U", "Used")));
    when(repository.findSuperstructureTypeCodes(any()))
        .thenReturn(List.of(new SuperstructureTypeCode("STL", "Steel")));
    when(repository.findDeckTypeCodes(any())).thenReturn(List.of(new DeckTypeCode("WD", "Wood")));
    when(repository.findAbutmentTypeCodes(any()))
        .thenReturn(List.of(new AbutmentTypeCode("CONC", "Concrete")));
    when(repository.findLoadRatingCodes(any()))
        .thenReturn(List.of(new LoadRatingCode("L100", "L-100")));

    assertThat(repository.constructionTypeOptions(2021))
        .containsExactly(new CodeDescriptionDto("N", "New"), new CodeDescriptionDto("U", "Used"));
    assertThat(repository.superstructureTypeOptions(2021))
        .containsExactly(new CodeDescriptionDto("STL", "Steel"));
    assertThat(repository.deckTypeOptions(2021))
        .containsExactly(new CodeDescriptionDto("WD", "Wood"));
    assertThat(repository.abutmentTypeOptions(2021))
        .containsExactly(new CodeDescriptionDto("CONC", "Concrete"));
    assertThat(repository.loadRatingOptions(2021))
        .containsExactly(new CodeDescriptionDto("L100", "L-100"));
  }

  @Test
  @DisplayName("upsertCost updates in place when the row exists (no insert, no new sequence PK)")
  void upsertCost_updatesInPlace() {
    doCallRealMethod().when(repository).upsertCost(7601L, 70, 500, "user");
    when(repository.updateCost(7601L, 70, 500, "user")).thenReturn(1);

    repository.upsertCost(7601L, 70, 500, "user");

    verify(repository).updateCost(7601L, 70, 500, "user");
    verify(repository, never()).nextCostDetailId();
    verify(repository, never())
        .insertCost(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("upsertCost inserts with a fresh sequence PK when the row is absent")
  void upsertCost_insertsWhenAbsent() {
    doCallRealMethod().when(repository).upsertCost(7601L, 70, 500, "user");
    when(repository.updateCost(7601L, 70, 500, "user")).thenReturn(0);
    when(repository.nextCostDetailId()).thenReturn(9001L);

    repository.upsertCost(7601L, 70, 500, "user");

    verify(repository).insertCost(9001L, 7601L, 70, 500, "user");
  }
}
