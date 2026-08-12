package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The {@code findSubPageRows} adapter — the default method that turns the {@code @Query} entities
 * into the service-facing {@link DetailRow}. No database: the query itself is pinned by
 * {@code Schedule5RepositoryIT}, which surefire cannot run (CI runs no Oracle ITs — AR17), so the
 * component-order half of the mapping is pinned here instead.
 *
 * <p>Six same-shaped components, three of them nullable numbers, is precisely where a transposition
 * hides: swap {@code cost} and {@code volume} and every IT still passes, because a stored sub-page
 * row's volume is always null (deviation (B)) and the swap only surfaces as a wrong footer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Repository.findSubPageRows — entity to DetailRow")
class Schedule5RepositoryMappingTest {

  private static final int CAMP = 8700;
  private static final int ITEM_CAMP_ROW = 62;

  @Mock(answer = Answers.CALLS_REAL_METHODS)
  private Schedule5Repository repository;

  @Test
  @DisplayName("maps every component into its own slot, preserving query order")
  void mapsEveryComponent() {
    when(repository.findSubPageRowEntities(CAMP, ITEM_CAMP_ROW, 690L, 2016)).thenReturn(List.of(
        new CostReportDetailEntity(8724, CAMP, ITEM_CAMP_ROW, null, 500, "Generator Fuel"),
        new CostReportDetailEntity(8725, CAMP, ITEM_CAMP_ROW, new BigDecimal("120000"), 300,
            "Propane")));

    List<DetailRow> rows = repository.findSubPageRows(CAMP, ITEM_CAMP_ROW, 690L, 2016);

    assertThat(rows).containsExactly(
        new DetailRow(8724, CAMP, ITEM_CAMP_ROW, null, 500, "Generator Fuel"),
        new DetailRow(8725, CAMP, ITEM_CAMP_ROW, new BigDecimal("120000"), 300, "Propane"));
  }

  @Test
  @DisplayName("no rows is the normal delivery state, not an error")
  void mapsEmptyToEmpty() {
    when(repository.findSubPageRowEntities(CAMP, ITEM_CAMP_ROW, 690L, 2016))
        .thenReturn(List.of());

    assertThat(repository.findSubPageRows(CAMP, ITEM_CAMP_ROW, 690L, 2016)).isEmpty();
  }

  @Test
  @DisplayName("a null cost and a null description survive the mapping — cleared, not zeroed")
  void mapsNullsThrough() {
    when(repository.findSubPageRowEntities(CAMP, ITEM_CAMP_ROW, 690L, 2016)).thenReturn(
        List.of(new CostReportDetailEntity(8726, CAMP, ITEM_CAMP_ROW, null, null, null)));

    assertThat(repository.findSubPageRows(CAMP, ITEM_CAMP_ROW, 690L, 2016))
        .singleElement()
        .satisfies(row -> {
          assertThat(row.cost()).isNull();
          assertThat(row.itemDescription()).isNull();
          assertThat(row.detailId()).isEqualTo(8726);
        });
  }
}
