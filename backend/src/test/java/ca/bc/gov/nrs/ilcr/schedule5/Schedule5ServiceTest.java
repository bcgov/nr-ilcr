package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
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

/**
 * Unit tests for {@link Schedule5Service} — the full derivation matrix, with no Spring and no
 * database. Every expected value is hand-derived from the legacy arithmetic
 * ({@code CampReportType} + {@code CoreUtil}) rather than from a run, so a change in served figures
 * shows up as a failure rather than as a quietly updated expectation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Service — server-side derivation (BR-04, AD-5)")
class Schedule5ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final int CAMP = 8401;

  @Mock
  private Schedule5Repository repository;

  private Schedule5Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule5Service(repository);
  }

  // -----------------------------------------------------------------------------------------
  // Fixture helpers
  // -----------------------------------------------------------------------------------------

  private static CampRow camp(BigDecimal associatedVolume) {
    return new CampRow(CAMP, "Test Camp", new BigDecimal("10.00"), 40, associatedVolume, "N",
        "comment", 0);
  }

  private static DetailRow detail(int detailId, int itemId, BigDecimal volume, Integer cost) {
    return new DetailRow(detailId, CAMP, itemId, volume, cost);
  }

  /** Drives the service with one camp and the given detail rows, returning the served camp. */
  private Camp serveCamp(CampRow campRow, List<DetailRow> details) {
    when(repository.findTrackStatus(anyLong(), anyInt())).thenReturn(Optional.of("D"));
    when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of(campRow));
    when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(details);
    return service.getSchedule5(MILL, YEAR, true).camps().getFirst();
  }

  private static final BigDecimal VOL_120K = new BigDecimal("120000");

  @Nested
  @DisplayName("Camp Sub-Total (five components, Recoveries excluded)")
  class CampSubTotal {

    @Test
    @DisplayName("sums exactly the five camp-expense costs and derives $/m3")
    void sumsFiveComponents() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 56, VOL_120K, 480000),
          detail(2, 58, VOL_120K, 960000),
          detail(3, 59, VOL_120K, 120000),
          detail(4, 60, VOL_120K, 60000),
          detail(5, 141, VOL_120K, null),
          detail(6, 62, null, 24000),
          // Recoveries must NOT be part of the Sub-Total.
          detail(7, 61, null, 44000)));

      assertThat(served.campSubTotal().cost()).isEqualTo(1_644_000L);
      assertThat(served.campSubTotal().costPerVolume()).isEqualByComparingTo("13.70");
      assertThat(served.campSubTotal().volume()).isEqualByComparingTo(VOL_120K);
    }

    @Test
    @DisplayName("all five components null -> null, never 0")
    void allNullComponentsYieldNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of());

      assertThat(served.campSubTotal().cost()).isNull();
      assertThat(served.campSubTotal().costPerVolume()).isNull();
    }

    @Test
    @DisplayName("widens to long — a sum above Integer.MAX_VALUE does not overflow")
    void sumAboveIntegerMaxValueDoesNotOverflow() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 56, VOL_120K, 2_000_000_000),
          detail(2, 58, VOL_120K, 2_000_000_000)));

      assertThat(served.campSubTotal().cost()).isEqualTo(4_000_000_000L);
    }
  }

  @Nested
  @DisplayName("Camp Total = Sub-Total - Recoveries (BR-04 / S09)")
  class CampTotal {

    @Test
    @DisplayName("subtracts Recoveries from Sub-Total")
    void subtractsRecoveries() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 56, VOL_120K, 480000),
          detail(2, 61, null, 44000)));

      assertThat(served.campTotal().cost()).isEqualTo(436_000L);
    }

    @Test
    @DisplayName("null Recoveries leaves the Sub-Total unchanged")
    void nullRecoveriesPassesTotalThrough() {
      Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 56, VOL_120K, 480000)));

      assertThat(served.campTotal().cost()).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("null Sub-Total yields null REGARDLESS of Recoveries (never a bare negative)")
    void nullSubTotalYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 61, null, 5000)));

      assertThat(served.campSubTotal().cost()).isNull();
      assertThat(served.campTotal().cost()).isNull();
    }

    @Test
    @DisplayName("Recoveries exceeding Sub-Total gives a NEGATIVE total — never clamped")
    void negativeTotalIsNotClamped() {
      Camp served = serveCamp(camp(new BigDecimal("10000")), List.of(
          detail(1, 56, new BigDecimal("10000"), 30000),
          detail(2, 61, null, 50000)));

      assertThat(served.campTotal().cost()).isEqualTo(-20_000L);
      assertThat(served.campTotal().costPerVolume()).isEqualByComparingTo("-2.00");
    }

    @Test
    @DisplayName("§T1 — Sub-Total is computed first, so Camp Total is never a stale null")
    void subTotalComputedBeforeCampTotal() {
      // Legacy getCampTotal() reads the campSubTotal FIELD, populated only as a side effect of a
      // prior getCampSubTotal() call. A port that skipped that ordering would serve null here while
      // Sub-Total itself looked correct — and campAndAccessTotal would collapse to Access alone.
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 56, VOL_120K, 480000),
          detail(2, 63, VOL_120K, 180000)));

      assertThat(served.campSubTotal().cost()).isEqualTo(480_000L);
      assertThat(served.campTotal().cost()).isEqualTo(480_000L);
      assertThat(served.accessExpenseTotal().cost()).isEqualTo(180_000L);
      // The canary: 660000, not the 180000 a collapsed Camp Total would leave behind.
      assertThat(served.campAndAccessTotal().cost()).isEqualTo(660_000L);
    }
  }

  @Nested
  @DisplayName("Access Expense Total (six components) and Camp-and-Access")
  class AccessTotals {

    @Test
    @DisplayName("sums exactly the six access costs")
    void sumsSixComponents() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 63, VOL_120K, 180000),
          detail(2, 64, VOL_120K, 90000),
          detail(3, 65, VOL_120K, 0),
          detail(4, 66, VOL_120K, 12000),
          detail(5, 67, VOL_120K, 6000),
          detail(6, 142, VOL_120K, null),
          detail(7, 68, null, 3000)));

      assertThat(served.accessExpenseTotal().cost()).isEqualTo(291_000L);
      assertThat(served.accessExpenseTotal().costPerVolume()).isEqualByComparingTo("2.43");
    }

    @Test
    @DisplayName("Camp-and-Access adds null-tolerantly: one side null passes the other through")
    void campAndAccessWithOneSideNull() {
      Camp campOnly = serveCamp(camp(VOL_120K), List.of(detail(1, 56, VOL_120K, 480000)));
      assertThat(campOnly.accessExpenseTotal().cost()).isNull();
      assertThat(campOnly.campAndAccessTotal().cost()).isEqualTo(480_000L);

      Camp accessOnly = serveCamp(camp(VOL_120K), List.of(detail(1, 63, VOL_120K, 180000)));
      assertThat(accessOnly.campTotal().cost()).isNull();
      assertThat(accessOnly.campAndAccessTotal().cost()).isEqualTo(180_000L);
    }

    @Test
    @DisplayName("both sides null -> Camp-and-Access is null, never 0")
    void bothSidesNullYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of());

      assertThat(served.campAndAccessTotal().cost()).isNull();
    }
  }

  @Nested
  @DisplayName("$/m3 null branches (CoreUtil.bigDecimalDivision)")
  class CostPerVolume {

    @Test
    @DisplayName("null cost -> null")
    void nullCostYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 56, VOL_120K, null)));

      assertThat(served.cateringAndFood().costPerVolume()).isNull();
    }

    @Test
    @DisplayName("null volume -> null")
    void nullVolumeYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 56, null, 480000)));

      assertThat(served.cateringAndFood().cost()).isEqualTo(480_000L);
      assertThat(served.cateringAndFood().costPerVolume()).isNull();
    }

    @Test
    @DisplayName("ZERO volume -> null (no divide-by-zero), while the cost still totals")
    void zeroVolumeYieldsNull() {
      BigDecimal zero = BigDecimal.ZERO;
      Camp served = serveCamp(camp(zero), List.of(detail(1, 56, zero, 25000)));

      assertThat(served.cateringAndFood().costPerVolume()).isNull();
      assertThat(served.campSubTotal().cost()).isEqualTo(25_000L);
      assertThat(served.campSubTotal().costPerVolume()).isNull();
    }

    @Test
    @DisplayName("rounds HALF_UP at scale 2 after dividing at scale 10")
    void roundsHalfUpAtScaleTwo() {
      // 1000/3 = 333.3333333333 -> 333.33
      Camp served = serveCamp(camp(new BigDecimal("3")), List.of(
          detail(1, 56, new BigDecimal("3"), 1000)));

      assertThat(served.cateringAndFood().costPerVolume()).isEqualByComparingTo("333.33");
    }
  }

  @Nested
  @DisplayName("Sub-page aggregates (items 62 / 68) and their counts")
  class SubPageAggregates {

    @Test
    @DisplayName("cost is the row sum; volume is the STORED item-141 amount, not a sum")
    void aggregatesCostAndKeepsStoredVolume() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 141, VOL_120K, null),
          detail(2, 62, null, 10000),
          detail(3, 62, null, 10000),
          detail(4, 62, null, 4000)));

      assertThat(served.otherCampExpenses().cost()).isEqualTo(24_000L);
      assertThat(served.otherCampExpenses().volume()).isEqualByComparingTo(VOL_120K);
    }

    @Test
    @DisplayName("$/m3 is PER-TERM rounded, not the ratio of sums")
    void perTermRoundingNotRatioOfSums() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 141, VOL_120K, null),
          detail(2, 62, null, 10000),
          detail(3, 62, null, 10000),
          detail(4, 62, null, 4000)));

      // 0.08 + 0.08 + 0.03 = 0.19; the ratio-of-sums shortcut would give 24000/120000 = 0.20.
      assertThat(served.otherCampExpenses().costPerVolume()).isEqualByComparingTo("0.19");
    }

    @Test
    @DisplayName("counts are raw row counts — rows with null description AND null cost still count")
    void countsIncludeEmptyRows() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 62, null, 10000),
          detail(2, 62, null, null),
          detail(3, 68, null, null)));

      assertThat(served.otherCampExpenseCount()).isEqualTo(2);
      assertThat(served.otherAccessExpenseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("no rows -> counts are 0, never null; aggregate costs are null, never 0")
    void noRowsYieldZeroCountsAndNullCosts() {
      Camp served = serveCamp(camp(VOL_120K), List.of());

      assertThat(served.otherCampExpenseCount()).isZero();
      assertThat(served.otherAccessExpenseCount()).isZero();
      assertThat(served.otherCampExpenses().cost()).isNull();
      assertThat(served.otherAccessExpenses().cost()).isNull();
    }

    @Test
    @DisplayName("CAMP side: all-null costs + a stored item-141 volume yield 0, NOT null")
    void campSideAsymmetryYieldsZero() {
      // CoreUtil.sumDescriptionCostVolumeType flags "something was added" on a non-null cost OR a
      // non-null VOLUME, and getOtherCampExpensesList() stamps every row's volume with the
      // camp-level item-141 volume before summing. Legacy-faithful and deliberately asymmetric with
      // the access side below; that 0 then propagates into Camp Sub-Total.
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 141, VOL_120K, null),
          detail(2, 62, null, null)));

      assertThat(served.otherCampExpenses().cost()).isEqualTo(0L);
      assertThat(served.campSubTotal().cost()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ACCESS side: the mirror-image case yields null (cost-only helper)")
    void accessSideAsymmetryYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 142, VOL_120K, null),
          detail(2, 68, null, null)));

      assertThat(served.otherAccessExpenses().cost()).isNull();
      assertThat(served.accessExpenseTotal().cost()).isNull();
    }

    @Test
    @DisplayName("CAMP side with NO stored item-141 volume falls back to null on all-null costs")
    void campSideWithoutStoredVolumeYieldsNull() {
      Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 62, null, null)));

      assertThat(served.otherCampExpenses().cost()).isNull();
    }
  }

  @Nested
  @DisplayName("Row keying: duplicates, unknown items, and the isolated-camp indicator")
  class RowKeying {

    @Test
    @DisplayName("duplicate single-row item -> FIRST detail id wins (deviation (f))")
    void duplicateItemFirstWins() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(10, 56, VOL_120K, 480000),
          detail(20, 56, VOL_120K, 777777)));

      assertThat(served.cateringAndFood().cost()).isEqualTo(480_000L);
      assertThat(served.campSubTotal().cost()).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("unregistered/undispatched item (57) is dropped and reaches no total")
    void unknownItemIsDropped() {
      Camp served = serveCamp(camp(VOL_120K), List.of(
          detail(1, 56, VOL_120K, 480000),
          detail(2, 57, VOL_120K, 999999)));

      assertThat(served.campSubTotal().cost()).isEqualTo(480_000L);
      assertThat(served.campAndAccessTotal().cost()).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("isolated-camp indicator: Y -> true, N -> false, anything else -> false")
    void isolatedCampIndicatorMapping() {
      assertThat(serveCampWithIndicator("Y").isolatedCamp()).isTrue();
      assertThat(serveCampWithIndicator("N").isolatedCamp()).isFalse();
      assertThat(serveCampWithIndicator("X").isolatedCamp()).isFalse();
    }

    @Test
    @DisplayName("NULL indicator serves null rather than failing the request (deviation (e))")
    void nullIndicatorServesNull() {
      assertThat(serveCampWithIndicator(null).isolatedCamp()).isNull();
    }

    private Camp serveCampWithIndicator(String indicator) {
      CampRow row = new CampRow(CAMP, "Test Camp", null, null, VOL_120K, indicator, null, 0);
      return serveCamp(row, List.of());
    }
  }

  @Nested
  @DisplayName("editable matrix (AD-9 / S19 — the server is the sole authority)")
  class EditableMatrix {

    @Test
    @DisplayName("EDIT_SCHEDULE + Draft -> true; every other combination -> false")
    void editableOnlyWhenDraftAndPermitted() {
      assertThat(editableFor("D", true)).isTrue();
      assertThat(editableFor("D", false)).isFalse();
      assertThat(editableFor("S", true)).isFalse();
      assertThat(editableFor("V", true)).isFalse();
      // Dead status code O passes through as a non-Draft value (A-8).
      assertThat(editableFor("O", true)).isFalse();
      // No status row at all.
      assertThat(editableFor(null, true)).isFalse();
    }

    private boolean editableFor(String trackStatus, boolean callerMayEdit) {
      when(repository.findTrackStatus(anyLong(), anyInt()))
          .thenReturn(Optional.ofNullable(trackStatus));
      when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of());
      when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(List.of());
      return service.getSchedule5(MILL, YEAR, callerMayEdit).editable();
    }
  }

  @Test
  @DisplayName("document envelope: mill/year/track echoed, no camps -> empty list, no message")
  void documentEnvelope() {
    when(repository.findTrackStatus(anyLong(), anyInt())).thenReturn(Optional.of("D"));
    when(repository.findCamps(anyLong(), anyInt())).thenReturn(List.of());
    when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(List.of());

    Schedule5Response document = service.getSchedule5(MILL, YEAR, true);

    assertThat(document.millId()).isEqualTo(MILL);
    assertThat(document.year()).isEqualTo(YEAR);
    assertThat(document.trackStatus()).isEqualTo("D");
    assertThat(document.camps()).isEmpty();
    assertThat(document.message()).isNull();
  }
}
