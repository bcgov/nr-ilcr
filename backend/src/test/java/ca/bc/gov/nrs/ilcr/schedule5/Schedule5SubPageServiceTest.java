package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service.SubPage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageDocument;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageRowRequest;
import ca.bc.gov.nrs.ilcr.schedule5.dto.SubPageSaveRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for the Story 7.4 sub-page derivation (AC7, AC8) — no Spring, no database. Every
 * expected figure is hand-derived from the legacy arithmetic ({@code CoreUtil} +
 * {@code CampReportType}), never copied from a run.
 *
 * <p>The whole point of this class is the ASYMMETRY. The Camp and Access footers look identical on
 * screen and are computed by two different legacy helpers, and one of them yields {@code 0} where
 * the other yields {@code null}. Both sides are pinned here so a later "tidy-up" that symmetrizes
 * them fails loudly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Service sub-pages — the two footer shapes (AC8) and deviation (L)")
class Schedule5SubPageServiceTest {

  private static final long MILL = 680L;
  private static final int YEAR = 2016;
  private static final int CAMP = 8700;
  private static final BigDecimal VOL_120K = new BigDecimal("120000");

  private static final int ITEM_CAMP_ROW = 62;
  private static final int ITEM_ACCESS_ROW = 68;
  private static final int ITEM_CAMP_VOLUME = 141;
  private static final int ITEM_ACCESS_VOLUME = 142;

  @Mock
  private Schedule5Repository repository;

  private Schedule5Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule5Service(repository);
  }

  private static CampRow camp(BigDecimal associatedVolume) {
    return new CampRow(CAMP, "Reconcile Camp", null, null, associatedVolume, "N", null, 0);
  }

  private static DetailRow row(int detailId, int itemId, Integer cost, String description) {
    // VOLUME is null on every stored sub-page row — legacy never writes one (deviation (B)).
    return new DetailRow(detailId, CAMP, itemId, null, cost, description);
  }

  private static DetailRow volumeRow(int detailId, int itemId, BigDecimal volume) {
    return new DetailRow(detailId, CAMP, itemId, volume, null, null);
  }

  /** Wire a read: the camp, the item-141/142 volume carriers, and this page's rows. */
  private SubPageDocument serve(
      SubPage page, BigDecimal campVolume, List<DetailRow> volumeRows, List<DetailRow> pageRows) {
    when(repository.findTrackStatus(anyLong(), anyInt())).thenReturn(Optional.of("D"));
    when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of(camp(campVolume)));
    when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(volumeRows);
    when(repository.findSubPageRows(anyInt(), anyInt(), anyLong(), anyInt()))
        .thenReturn(pageRows);
    return service.getSubPage(MILL, YEAR, CAMP, page, true);
  }

  @Nested
  @DisplayName("Camp footer — volume is the SUM of the stamped row volumes")
  class CampFooter {

    @Test
    @DisplayName("three rows at a 120000 camp volume total 360000, not 120000")
    void sumsRowVolumes() {
      SubPageDocument doc = serve(SubPage.CAMP, VOL_120K,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, VOL_120K)),
          List.of(row(8722, ITEM_CAMP_ROW, 10000, "Generator Fuel"),
              row(8723, ITEM_CAMP_ROW, 2500, "Propane"),
              row(8724, ITEM_CAMP_ROW, 500, null)));

      // CoreUtil.sumDescriptionCostVolumeType (:610-632) adds EACH row's volume, and
      // CampReportType.getOtherCampExpensesList (:433-438) stamped all three with 120000.
      assertThat(doc.totals().volume()).isEqualByComparingTo("360000");
      assertThat(doc.totals().cost()).isEqualTo(13000L);
      // 13000 / 360000 = 0.036111… -> scale 2 HALF_UP = 0.04
      assertThat(doc.totals().costPerVolume()).isEqualByComparingTo("0.04");
    }

    @Test
    @DisplayName("every row's volume is the camp volume, stamped at read — never the stored null")
    void stampsEachRowVolume() {
      SubPageDocument doc = serve(SubPage.CAMP, VOL_120K,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, VOL_120K)),
          List.of(row(8722, ITEM_CAMP_ROW, 10000, "Generator Fuel")));

      assertThat(doc.rows()).singleElement().satisfies(r -> {
        assertThat(r.volume()).isEqualByComparingTo(VOL_120K);
        // 10000 / 120000 = 0.0833… -> 0.08
        assertThat(r.costPerVolume()).isEqualByComparingTo("0.08");
      });
    }

    @Test
    @DisplayName("an empty list yields a null cost, never 0")
    void emptyListIsNull() {
      SubPageDocument doc = serve(SubPage.CAMP, VOL_120K,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, VOL_120K)), List.of());

      assertThat(doc.totals().cost()).isNull();
      assertThat(doc.totals().volume()).isNull();
      assertThat(doc.totals().costPerVolume()).isNull();
    }
  }

  @Nested
  @DisplayName("Access footer — volume is the SINGLE camp volume (deviation (C))")
  class AccessFooter {

    @Test
    @DisplayName("two rows at a 120000 camp volume still total 120000, not 240000")
    void usesSingleCampVolume() {
      SubPageDocument doc = serve(SubPage.ACCESS, VOL_120K,
          List.of(volumeRow(8721, ITEM_ACCESS_VOLUME, VOL_120K)),
          List.of(row(8725, ITEM_ACCESS_ROW, 7000, "Bridge Rental"),
              row(8726, ITEM_ACCESS_ROW, 3000, "Culvert Hire")));

      // getOtherAccessExpensesTotal (:460-464) sums cost only, then OVERWRITES the volume with the
      // single camp volume. This is the line that makes the two footers differ.
      assertThat(doc.totals().volume()).isEqualByComparingTo("120000");
      assertThat(doc.totals().cost()).isEqualTo(10000L);
      // 10000 / 120000 = 0.0833… -> 0.08. The camp side would have divided by 240000.
      assertThat(doc.totals().costPerVolume()).isEqualByComparingTo("0.08");
    }

    @Test
    @DisplayName("an empty list yields a null cost but still reports the camp volume")
    void emptyListKeepsVolume() {
      SubPageDocument doc = serve(SubPage.ACCESS, VOL_120K,
          List.of(volumeRow(8721, ITEM_ACCESS_VOLUME, VOL_120K)), List.of());

      assertThat(doc.totals().cost()).isNull();
      assertThat(doc.totals().costPerVolume()).isNull();
      // Legacy sets the volume unconditionally, including on the empty list.
      assertThat(doc.totals().volume()).isEqualByComparingTo("120000");
    }
  }

  @Nested
  @DisplayName("7.1 deviation (h)/(L) — the one place a null-component total is 0, not null")
  class ZeroNotNull {

    @Test
    @DisplayName("CAMP: all costs null + a non-null item-141 volume serves 0")
    void campSideServesZero() {
      SubPageDocument doc = serve(SubPage.CAMP, new BigDecimal("60000"),
          List.of(volumeRow(8732, ITEM_CAMP_VOLUME, new BigDecimal("60000"))),
          List.of(row(8734, ITEM_CAMP_ROW, null, "Cost Free Camp Row")));

      // sumDescriptionCostVolumeType sets its added-flag on a non-null VOLUME as well as a non-null
      // cost, and every row's volume was stamped from item 141 before the sum ran. So the
      // zero-initialised cost accumulator is RETURNED rather than discarded.
      assertThat(doc.totals().cost()).isZero();
      assertThat(doc.totals().volume()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("ACCESS: the mirror-image case correctly stays null")
    void accessSideStaysNull() {
      SubPageDocument doc = serve(SubPage.ACCESS, new BigDecimal("60000"),
          List.of(volumeRow(8733, ITEM_ACCESS_VOLUME, new BigDecimal("60000"))),
          List.of(row(8735, ITEM_ACCESS_ROW, null, "Cost Free Access Row")));

      // sumDescriptionCostVolumeTypeCostOnly (:590-608) checks cost alone, so nothing flags and the
      // empty accumulator is discarded. Pinned opposite the camp case above deliberately: the two
      // are seeded identically and only the CODE differs.
      assertThat(doc.totals().cost()).isNull();
    }

    @Test
    @DisplayName("CAMP with a null camp volume: nothing flags, so the total stays null")
    void campSideWithoutVolumeStaysNull() {
      SubPageDocument doc = serve(SubPage.CAMP, null, List.of(),
          List.of(row(8734, ITEM_CAMP_ROW, null, "Cost Free Camp Row")));

      // The added-flag needs a non-null cost OR a non-null stamped volume; with neither, legacy
      // returns a fresh empty total. This is the boundary that makes the zero above deliberate
      // rather than accidental.
      assertThat(doc.totals().cost()).isNull();
      assertThat(doc.totals().volume()).isNull();
    }
  }

  @Nested
  @DisplayName("failure surfacing — the camp path's deviation (P), owed to the sub-pages too")
  class FailureSurfacing {

    @Test
    @DisplayName("a DataAccessException on save becomes ScheduleNotSavedException, not a leaked 500")
    void saveDataAccessFailureBecomesNotSaved() {
      when(repository.findTrackStatusForUpdate(anyLong(), anyInt()))
          .thenReturn(Optional.of("D"));
      when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of(camp(VOL_120K)));
      when(repository.findSubPageRows(anyInt(), anyInt(), anyLong(), anyInt()))
          .thenReturn(List.of());
      when(repository.nextCostDetailId())
          .thenThrow(new DataAccessResourceFailureException("boom"));

      SubPageSaveRequest request = new SubPageSaveRequest(
          List.of(new SubPageRowRequest(null, "New Row", 100)));

      // Deleting the try/catch in saveSubPage leaks the raw DataAccessException (with its ORA
      // message) past the handler — Schedule5WriteServiceTest pins the same contract for the camp
      // path; this is the sub-page analog (review patch, 2026-08-12).
      assertThatThrownBy(() -> service.saveSubPage(MILL, YEAR, CAMP, SubPage.CAMP, request,
          true, "tester"))
          .isInstanceOf(ScheduleNotSavedException.class);
    }

    @Test
    @DisplayName("a DataAccessException on delete becomes ScheduleNotSavedException")
    void deleteDataAccessFailureBecomesNotSaved() {
      when(repository.findTrackStatusForUpdate(anyLong(), anyInt()))
          .thenReturn(Optional.of("D"));
      when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of(camp(VOL_120K)));
      when(repository.deleteSubPageRow(anyInt(), anyInt(), anyInt()))
          .thenThrow(new DataAccessResourceFailureException("boom"));

      assertThatThrownBy(() -> service.deleteSubPageRow(MILL, YEAR, CAMP, SubPage.CAMP, 8722,
          true))
          .isInstanceOf(ScheduleNotSavedException.class);
    }
  }

  @Nested
  @DisplayName("Row shape")
  class RowShape {

    @Test
    @DisplayName("a null description survives to the wire — it is storable, not invalid")
    void nullDescriptionSurvives() {
      SubPageDocument doc = serve(SubPage.CAMP, VOL_120K,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, VOL_120K)),
          List.of(row(8724, ITEM_CAMP_ROW, 500, null)));

      assertThat(doc.rows()).singleElement().satisfies(r -> {
        assertThat(r.description()).isNull();
        assertThat(r.rowId()).isEqualTo(8724);
      });
    }

    @Test
    @DisplayName("a null cost yields a null $/m³ rather than 0.00")
    void nullCostYieldsNullRatio() {
      SubPageDocument doc = serve(SubPage.CAMP, VOL_120K,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, VOL_120K)),
          List.of(row(8734, ITEM_CAMP_ROW, null, "No Cost")));

      assertThat(doc.rows()).singleElement()
          .satisfies(r -> assertThat(r.costPerVolume()).isNull());
    }

    @Test
    @DisplayName("a zero camp volume yields a null $/m³ — no divide-by-zero")
    void zeroVolumeYieldsNullRatio() {
      SubPageDocument doc = serve(SubPage.CAMP, BigDecimal.ZERO,
          List.of(volumeRow(8720, ITEM_CAMP_VOLUME, BigDecimal.ZERO)),
          List.of(row(8722, ITEM_CAMP_ROW, 10000, "Generator Fuel")));

      assertThat(doc.rows()).singleElement()
          .satisfies(r -> assertThat(r.costPerVolume()).isNull());
      assertThat(doc.totals().costPerVolume()).isNull();
    }
  }
}
