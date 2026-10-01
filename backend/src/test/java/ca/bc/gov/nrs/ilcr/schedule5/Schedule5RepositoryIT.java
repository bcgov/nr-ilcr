package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Repository-level acceptance test for the three Schedule 5 queries — the predicates and orderings
 * that {@link Schedule5DocumentIT} structurally cannot see.
 *
 * <p>Three specific gaps live here rather than in the endpoint test:
 *
 * <ul>
 *   <li><strong>The detail query's mill/year/category predicates.</strong> The seed's decoy detail
 *       rows (8436 on a wrong-YEAR camp, 8437 on a wrong-CATEGORY camp) hang off camps the service
 *       never looks up, so {@code Schedule5Service} discards them by construction: deleting {@code
 *       AND c.REPORT_YEAR} and {@code AND c.ILCR_CATEGORY_ID} from the query leaves every endpoint
 *       assertion green. Only a direct read can tell.
 *   <li><strong>The detail ORDER BY.</strong> Deviation (f)'s "lowest detail id wins" is entirely
 *       the repository's doing — the service uses {@code putIfAbsent} and never compares ids.
 *       Removing the {@code ORDER BY} would leave the endpoint tests passing for incidental
 *       reasons, because Oracle returns a small heap table in insertion order and the seed happens
 *       to insert the winner first.
 *   <li><strong>{@code findTrackStatus}'s year predicate.</strong> Every Schedule 5 fixture mill
 *       has exactly one status row, so dropping {@code AND REPORT_YEAR = :year} changes nothing for
 *       them — while in delivery, where mills carry many reporting years, it would return several
 *       rows into an {@code Optional} and 500 on the first request.
 * </ul>
 */
@DisplayName("Schedule5Repository — predicates and ordering the endpoint tests cannot see")
class Schedule5RepositoryIT extends AbstractOracleIT {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  /** Mill 661 carries status rows for 2019-2024 — the only multi-year fixture in the seed. */
  private static final long MULTI_YEAR_MILL = 661L;

  @Autowired private Schedule5Repository repository;

  @Test
  @DisplayName("findCostDetails returns ONLY the mill/year/category-'5' rows — decoys excluded")
  void detailQueryExcludesWrongYearAndWrongCategoryRows() {
    List<Integer> detailIds =
        repository.findCostDetails(MILL, YEAR).stream().map(DetailRow::detailId).toList();

    // 8436 hangs off camp 8406 (same mill, year 2020) and 8437 off camp 8407 (same mill/year,
    // category '4'). 8435 belongs to mill 517. None may appear.
    assertThat(detailIds).doesNotContain(8436, 8437, 8435);
    assertThat(detailIds).hasSize(24);
  }

  @Test
  @DisplayName("findCostDetails orders by CAMP_REPORT_ID first, then ILCR_COST_REPORT_DETAIL_ID")
  void detailQueryOrdersByCampThenDetailId() {
    List<Integer> detailIds =
        repository.findCostDetails(MILL, YEAR).stream().map(DetailRow::detailId).toList();

    // Camp-major, then detail id. Note 8438 (camp 8404) precedes 8433/8434 (camp 8405) even though
    // its id is higher — a flat detail-id sort would fail here, and so would no sort at all.
    assertThat(detailIds)
        .containsExactly(
            // camp 8401
            8411,
            8412,
            8413,
            8414,
            8415,
            8416,
            8417,
            8418,
            8419,
            8420,
            8421,
            8422,
            8423,
            8424,
            8425,
            8426,
            8427,
            8428,
            // camp 8403 (8402 has no detail rows)
            8430,
            // camp 8404
            8431,
            8432,
            8438,
            // camp 8405
            8433,
            8434);
  }

  @Test
  @DisplayName("findCostDetails puts the duplicate item-56 row AFTER the one that must win")
  void duplicateRowArrivesAfterTheWinner() {
    List<DetailRow> item56 =
        repository.findCostDetails(MILL, YEAR).stream()
            .filter(
                row -> row.campId() == 8401 && row.costItemId() != null && row.costItemId() == 56)
            .toList();

    // This is the ordering half of deviation (f). The service keeps whichever of these arrives
    // first; the contract says that must be 8411 (cost 480000), not 8428 (cost 777777).
    assertThat(item56).hasSize(2);
    assertThat(item56.getFirst().detailId()).isEqualTo(8411);
    assertThat(item56.getLast().detailId()).isEqualTo(8428);
  }

  @Test
  @DisplayName("findCamps returns category-'5' camps for the mill/year in CAMP_REPORT_ID order")
  void campQueryFiltersAndOrders() {
    List<Integer> campIds = repository.findCamps(MILL, YEAR).stream().map(CampRow::campId).toList();

    // 8406 (year 2020) and 8407 (category '4') are excluded; the seed inserts out of id order.
    assertThat(campIds).containsExactly(8401, 8402, 8403, 8404, 8405);
  }

  @Test
  @DisplayName("findTrackStatus resolves ONE row for a mill carrying six years of status")
  void trackStatusIsScopedToTheRequestedYear() {
    // Mill 661 has an ILCR_MILL_REPORT_STATUS row for each of 2019-2024. Without the year
    // predicate this call returns six rows into an Optional<String>, which Spring Data raises as
    // IncorrectResultSizeDataAccessException — so this assertion fails loudly rather than
    // silently serving another year's track.
    assertThat(repository.findTrackStatus(MULTI_YEAR_MILL, YEAR)).contains("D");
  }

  @Test
  @DisplayName("findTrackStatus is empty for a year the mill has no status row for")
  void trackStatusIsEmptyForAnUnseededYear() {
    assertThat(repository.findTrackStatus(MULTI_YEAR_MILL, 2016)).isEmpty();
  }
}
