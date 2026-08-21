package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link Schedule5Service} — the full derivation matrix, with no Spring and no
 * database. Every expected value is hand-derived from the legacy arithmetic ({@code CampReportType}
 * + {@code CoreUtil}) rather than from a run, so a change in served figures shows up as a failure
 * rather than as a quietly updated expectation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Service — server-side derivation (BR-04, AD-5)")
class Schedule5ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final int CAMP = 8401;

  @Mock private Schedule5Repository repository;

  private Schedule5Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule5Service(repository);
  }

  // -----------------------------------------------------------------------------------------
  // Fixture helpers
  // -----------------------------------------------------------------------------------------

  private static CampRow camp(BigDecimal associatedVolume) {
    return new CampRow(
        CAMP, "Test Camp", new BigDecimal("10.00"), 40, associatedVolume, "N", "comment", 0);
  }

  private static DetailRow detail(int detailId, int itemId, BigDecimal volume, Integer cost) {
    return detail(detailId, itemId, volume, cost, null);
  }

  /**
   * A detail row carrying an {@code ITEM_DESCRIPTION}. Story 7.2 added the column to the read query
   * because Check Status's fifth and seventh conditions flag any item-62/68 row whose description
   * is null or empty; the served document still does not expose it (itemizing those rows is 7.4's),
   * so every derivation test above is unaffected and passes null.
   */
  private static DetailRow detail(
      int detailId, int itemId, BigDecimal volume, Integer cost, String itemDescription) {
    return new DetailRow(detailId, CAMP, itemId, volume, cost, itemDescription);
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
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
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
    @DisplayName("widens to long — five max-width costs sum past Integer.MAX_VALUE")
    void sumAboveIntegerMaxValueDoesNotOverflow() {
      // Each component is 99,999,999 — the largest value the delivery COST column can hold
      // (NUMBER(8,0), Task 1 gate (iii)). This test previously used 2,000,000,000 per row, a
      // number that column can never store, so it proved the long widening against a scenario
      // the schema forbids while leaving the reachable case untested. Five real max-width rows
      // sum to 499,999,995 — which still fits an int, so push it over with the sub-page cost.
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
                  detail(1, 56, VOL_120K, 99_999_999),
                  detail(2, 58, VOL_120K, 99_999_999),
                  detail(3, 59, VOL_120K, 99_999_999),
                  detail(4, 60, VOL_120K, 99_999_999),
                  detail(5, 141, VOL_120K, null),
                  // Twenty-five sub-page rows at max width: the item-62 list is unbounded, so this
                  // is the
                  // realistic route past Integer.MAX_VALUE (2,147,483,647) on stored-shaped data.
                  detail(6, 62, null, 99_999_999),
                  detail(7, 62, null, 99_999_999),
                  detail(8, 62, null, 99_999_999),
                  detail(9, 62, null, 99_999_999),
                  detail(10, 62, null, 99_999_999),
                  detail(11, 62, null, 99_999_999),
                  detail(12, 62, null, 99_999_999),
                  detail(13, 62, null, 99_999_999),
                  detail(14, 62, null, 99_999_999),
                  detail(15, 62, null, 99_999_999),
                  detail(16, 62, null, 99_999_999),
                  detail(17, 62, null, 99_999_999),
                  detail(18, 62, null, 99_999_999),
                  detail(19, 62, null, 99_999_999),
                  detail(20, 62, null, 99_999_999),
                  detail(21, 62, null, 99_999_999),
                  detail(22, 62, null, 99_999_999),
                  detail(23, 62, null, 99_999_999)));

      // 4 fixed + 18 sub-page rows = 22 x 99,999,999 = 2,199,999,978 > Integer.MAX_VALUE.
      assertThat(served.campSubTotal().cost()).isEqualTo(2_199_999_978L);
    }
  }

  @Nested
  @DisplayName("Camp Total = Sub-Total - Recoveries (BR-04 / S09)")
  class CampTotal {

    @Test
    @DisplayName("subtracts Recoveries from Sub-Total")
    void subtractsRecoveries() {
      Camp served =
          serveCamp(
              camp(VOL_120K), List.of(detail(1, 56, VOL_120K, 480000), detail(2, 61, null, 44000)));

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
      Camp served =
          serveCamp(
              camp(new BigDecimal("10000")),
              List.of(detail(1, 56, new BigDecimal("10000"), 30000), detail(2, 61, null, 50000)));

      assertThat(served.campTotal().cost()).isEqualTo(-20_000L);
      assertThat(served.campTotal().costPerVolume()).isEqualByComparingTo("-2.00");
    }

    @Test
    @DisplayName("§T1 — Sub-Total is computed first, so Camp Total is never a stale null")
    void subTotalComputedBeforeCampTotal() {
      // Legacy getCampTotal() reads the campSubTotal FIELD, populated only as a side effect of a
      // prior getCampSubTotal() call. A port that skipped that ordering would serve null here while
      // Sub-Total itself looked correct — and campAndAccessTotal would collapse to Access alone.
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(detail(1, 56, VOL_120K, 480000), detail(2, 63, VOL_120K, 180000)));

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
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
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
      Camp served =
          serveCamp(camp(new BigDecimal("3")), List.of(detail(1, 56, new BigDecimal("3"), 1000)));

      assertThat(served.cateringAndFood().costPerVolume()).isEqualByComparingTo("333.33");
    }
  }

  @Nested
  @DisplayName("Sub-page aggregates (items 62 / 68) and their counts")
  class SubPageAggregates {

    @Test
    @DisplayName("cost is the row sum; volume is the STORED item-141 amount, not a sum")
    void aggregatesCostAndKeepsStoredVolume() {
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
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
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
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
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
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
      Camp served =
          serveCamp(
              camp(VOL_120K), List.of(detail(1, 141, VOL_120K, null), detail(2, 62, null, null)));

      assertThat(served.otherCampExpenses().cost()).isEqualTo(0L);
      assertThat(served.campSubTotal().cost()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ACCESS side: the mirror-image case yields null (cost-only helper)")
    void accessSideAsymmetryYieldsNull() {
      Camp served =
          serveCamp(
              camp(VOL_120K), List.of(detail(1, 142, VOL_120K, null), detail(2, 68, null, null)));

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
    @DisplayName("duplicate single-row item -> FIRST ROW WINS, and the service does not re-sort")
    void duplicateItemFirstRowWins() {
      // Fed in DESCENDING detail-id order on purpose. The service resolves duplicates with
      // putIfAbsent and never compares ids, so what it actually implements is "first row in
      // iteration order wins" — the ORDER BY in Schedule5Repository is what turns that into
      // deviation (f)'s "lowest detail id wins". Feeding ascending rows (as this test used to)
      // cannot tell the two apart and would pass even if the repository's ORDER BY were deleted.
      // Asserting the higher id wins when it arrives first documents the real division of
      // responsibility; Schedule5RepositoryIT pins the ordering half against the database.
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(detail(20, 56, VOL_120K, 777777), detail(10, 56, VOL_120K, 480000)));

      assertThat(served.cateringAndFood().cost()).isEqualTo(777_777L);
      assertThat(served.campSubTotal().cost()).isEqualTo(777_777L);
    }

    @Test
    @DisplayName("in repository order (ascending id) the LOWEST detail id wins — deviation (f)")
    void duplicateItemLowestIdWinsInRepositoryOrder() {
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(detail(10, 56, VOL_120K, 480000), detail(20, 56, VOL_120K, 777777)));

      assertThat(served.cateringAndFood().cost()).isEqualTo(480_000L);
      assertThat(served.campSubTotal().cost()).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("NULL cost item id is dropped like any unrecognized item, not an NPE")
    void nullItemIdIsDropped() {
      // Delivery declares ILCR_REPORT_COST_ITEM_ID NOT NULL (Task 1 gate (iii)), but the V1 test
      // snapshot does not. Before this, DetailRow narrowed the id to a primitive int, so a null
      // unboxed into an NPE inside the repository mapper and 500'd the WHOLE document — the one
      // unrecognized id that crashed instead of degrading.
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(
                  detail(1, 56, VOL_120K, 480000),
                  new DetailRow(2, CAMP, null, VOL_120K, 999999, null)));

      assertThat(served.campSubTotal().cost()).isEqualTo(480_000L);
      assertThat(served.campAndAccessTotal().cost()).isEqualTo(480_000L);
    }

    @Test
    @DisplayName("unregistered/undispatched item (57) is dropped and reaches no total")
    void unknownItemIsDropped() {
      Camp served =
          serveCamp(
              camp(VOL_120K),
              List.of(detail(1, 56, VOL_120K, 480000), detail(2, 57, VOL_120K, 999999)));

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
  @DisplayName("Operator diagnostics on dropped and duplicate rows (AC7)")
  class DroppedRowWarnings {

    // AC7 requires the first-wins rule to carry a log line on duplicates, and the unknown-item drop
    // is the operator's only signal that stored data is being discarded. Both were previously
    // evidenced only by a manual log inspection recorded in the story's Debug Log References —
    // deleting either call left the whole suite green, so the operability half of AC7 and
    // deviation (f) was unverified.
    //
    // Story 7.2 DEMOTED two of the three to DEBUG (deviation (O)): the duplicate and unknown-item
    // lines report properties of stored DATA, not events, and 7.2's writes make them reachable for
    // the first time — at WARN they would turn one malformed camp into permanent per-request noise
    // (deferred-work.md:246). The NULL-item-id line stays at WARN because the delivery column is
    // NOT
    // NULL, so it signals a genuine schema anomaly rather than ordinary bad data.
    //
    // The assertions therefore pin the LEVEL as well as the text: a silent re-promotion to WARN, or
    // a
    // demotion of the NULL-id line, now fails here instead of being discovered in production logs.

    private CapturingAppender appender;
    private Logger serviceLogger;
    private Level originalLevel;

    @BeforeEach
    void attachAppender() {
      // The app logs through Lombok @Slf4j -> SLF4J -> log4j2 (spring-boot-starter-log4j2; the
      // logback starter is excluded). There is no log4j2 config on the test classpath, so the
      // DefaultConfiguration's root level is ERROR — the level has to be lowered explicitly or
      // nothing reaches an appender at all. DEBUG now, so both levels under test are captured.
      serviceLogger = (Logger) LogManager.getLogger(Schedule5Service.class);
      originalLevel = serviceLogger.getLevel();
      appender = new CapturingAppender();
      appender.start();
      serviceLogger.addAppender(appender);
      serviceLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
      serviceLogger.removeAppender(appender);
      serviceLogger.setLevel(originalLevel);
      appender.stop();
    }

    private List<String> messagesAt(Level level) {
      return appender.events.stream()
          .filter(event -> event.level() == level)
          .map(CapturedEvent::message)
          .toList();
    }

    private List<String> debugMessages() {
      return messagesAt(Level.DEBUG);
    }

    private List<String> warnings() {
      return messagesAt(Level.WARN);
    }

    @Test
    @DisplayName("a duplicate single-row item logs at DEBUG, naming the kept and ignored detail id")
    void duplicateItemWarns() {
      serveCamp(
          camp(VOL_120K),
          List.of(detail(10, 56, VOL_120K, 480000), detail(20, 56, VOL_120K, 777777)));

      assertThat(debugMessages()).hasSize(1);
      assertThat(debugMessages().getFirst())
          .contains("more than one row for cost item 56")
          .contains("keeping detail id 10")
          .contains("ignoring detail id 20")
          .contains("camp " + CAMP)
          // AD-11: identifiers only. Neither cost may appear in the log.
          .doesNotContain("480000")
          .doesNotContain("777777");
      // Deviation (O): stored-data properties must not shout on every read.
      assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("an unrecognized item id logs at DEBUG and names the dropped row, never its cost")
    void unknownItemWarns() {
      serveCamp(camp(VOL_120K), List.of(detail(7, 57, VOL_120K, 999999)));

      assertThat(debugMessages()).hasSize(1);
      assertThat(debugMessages().getFirst())
          .contains("unrecognized cost item 57")
          .contains("detail id 7")
          .doesNotContain("999999");
      assertThat(warnings()).isEmpty();
    }

    @Test
    @DisplayName("a NULL item id stays at WARN — the column is NOT NULL, so it is a real anomaly")
    void nullItemIdWarns() {
      serveCamp(camp(VOL_120K), List.of(new DetailRow(9, CAMP, null, VOL_120K, 4200, null)));

      assertThat(warnings()).hasSize(1);
      assertThat(warnings().getFirst())
          .contains("NULL cost item id")
          .contains("detail id 9")
          .doesNotContain("4200");
      assertThat(debugMessages()).isEmpty();
    }

    @Test
    @DisplayName("a clean camp logs nothing at either level — this is signal, not noise")
    void cleanCampIsSilent() {
      serveCamp(
          camp(VOL_120K),
          List.of(detail(1, 56, VOL_120K, 480000), detail(2, 58, VOL_120K, 960000)));

      assertThat(warnings()).isEmpty();
      assertThat(debugMessages()).isEmpty();
    }

    /** One captured log event, so the assertions can pin the level as well as the text. */
    private record CapturedEvent(Level level, String message) {}

    /** Collects DEBUG and WARN events so the assertions above can read them back. */
    private static final class CapturingAppender extends AbstractAppender {

      private final List<CapturedEvent> events = new ArrayList<>();

      private CapturingAppender() {
        super("schedule5-capture", null, null, true, Property.EMPTY_ARRAY);
      }

      @Override
      public void append(LogEvent event) {
        if (event.getLevel() == Level.WARN || event.getLevel() == Level.DEBUG) {
          events.add(new CapturedEvent(event.getLevel(), event.getMessage().getFormattedMessage()));
        }
      }
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

  @Test
  @DisplayName("withMessage carries the whole document across — only message changes")
  void withMessagePreservesEveryField() {
    // withMessage ships in 7.1 with no caller, purely so Story 7.2's save echo needs no re-shape
    // (Schedule5Response:32). That makes it part of the pinned contract rather than incidental, and
    // an uncalled, untested copy method is exactly where a dropped field hides: returning
    // List.of() for camps would silently ship 7.2 an empty document.
    Camp served = serveCamp(camp(VOL_120K), List.of(detail(1, 56, VOL_120K, 480000)));
    Schedule5Response read = new Schedule5Response(MILL, YEAR, "D", true, List.of(served), null);

    MessageInfo message = new MessageInfo("sch5.copy.msg", "Camp copied.");
    Schedule5Response echo = read.withMessage(message);

    assertThat(echo.message()).isEqualTo(message);
    assertThat(echo.millId()).isEqualTo(MILL);
    assertThat(echo.year()).isEqualTo(YEAR);
    assertThat(echo.trackStatus()).isEqualTo("D");
    assertThat(echo.editable()).isTrue();
    assertThat(echo.camps()).containsExactly(served);
    // The original is untouched — it is a copy, not a mutation.
    assertThat(read.message()).isNull();
  }

  @Test
  @DisplayName("every registered cost item reaches its category; only 57 falls through")
  void everyKnownItemIsRouted() {
    // Schedule5Service keeps the item ids as fourteen constants AND a twelve-member
    // SINGLE_ROW_ITEMS set, with nothing tying the two together: adding a constant but forgetting
    // the set silently drops that category's row and shifts every total, and adding 57 to the set
    // would silently pull a dead item's cost into Sub-Total. The only runtime signal is a log
    // line. This drives one row per registered id at a distinct cost and asserts where each
    // landed, so either mistake fails here.
    Camp served =
        serveCamp(
            camp(VOL_120K),
            List.of(
                detail(1, 56, VOL_120K, 1),
                detail(2, 58, VOL_120K, 2),
                detail(3, 59, VOL_120K, 4),
                detail(4, 60, VOL_120K, 8),
                detail(5, 61, VOL_120K, 16),
                detail(6, 63, VOL_120K, 32),
                detail(7, 64, VOL_120K, 64),
                detail(8, 65, VOL_120K, 128),
                detail(9, 66, VOL_120K, 256),
                detail(10, 67, VOL_120K, 512),
                detail(11, 141, VOL_120K, null),
                detail(12, 142, VOL_120K, null),
                detail(13, 62, null, 1024),
                detail(14, 68, null, 2048),
                // 57 is REGISTERED in delivery but has no legacy dispatch branch, so it must fall
                // through
                // to the unknown-item drop and reach no total.
                detail(15, 57, VOL_120K, 1_000_000)));

    assertThat(served.cateringAndFood().cost()).isEqualTo(1L);
    assertThat(served.wagesAndBenefits().cost()).isEqualTo(2L);
    assertThat(served.depreciationLease().cost()).isEqualTo(4L);
    assertThat(served.generalCampExpenses().cost()).isEqualTo(8L);
    assertThat(served.recoveries().cost()).isEqualTo(16L);
    assertThat(served.crewTransportation().cost()).isEqualTo(32L);
    assertThat(served.equipAndSuppliesLand().cost()).isEqualTo(64L);
    assertThat(served.equipAndSuppliesRail().cost()).isEqualTo(128L);
    assertThat(served.equipAndSuppliesAir().cost()).isEqualTo(256L);
    assertThat(served.equipAndSuppliesWater().cost()).isEqualTo(512L);
    assertThat(served.otherCampExpenses().cost()).isEqualTo(1024L);
    assertThat(served.otherAccessExpenses().cost()).isEqualTo(2048L);
    assertThat(served.otherCampExpenseCount()).isEqualTo(1);
    assertThat(served.otherAccessExpenseCount()).isEqualTo(1);
    // Sub-Total = 1+2+4+8 (fixed camp) + 1024 (item-62 sum). Item 57's 1,000,000 is absent, and
    // Recoveries' 16 is excluded by construction.
    assertThat(served.campSubTotal().cost()).isEqualTo(1039L);
    // Access = 32+64+128+256+512 + 2048 (item-68 sum).
    assertThat(served.accessExpenseTotal().cost()).isEqualTo(3040L);
  }
}
