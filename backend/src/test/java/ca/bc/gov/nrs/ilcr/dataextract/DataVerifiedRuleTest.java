package ca.bc.gov.nrs.ilcr.dataextract;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.dataextract.csv.ScheduleSelection;
import ca.bc.gov.nrs.ilcr.millcontext.dto.MillYearTrackCodes;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The truth table for the title block's {@code Data Verified} line.
 *
 * <p>Three of these tests exist because legacy got the answer wrong three different ways, and each
 * wrong answer is plausible enough to be reintroduced by someone "restoring parity": it OR'd the
 * two status tracks when both were selected, printed {@code Yes} when the selection had no status
 * rows at all, and never counted Schedule 7 toward the Schedules 1–10 track because it tested the
 * detail list for a label that list never held. The rule implemented here is the one the
 * requirement states — every selected mill and year Verified on every applicable track — so those
 * three cases are pinned as {@code No} with names that say why.
 */
@DisplayName("DataVerifiedRule — every selected mill/year, every applicable track")
class DataVerifiedRuleTest {

  private static final String VERIFIED = "V";
  private static final String DRAFT = "D";
  private static final String SUBMITTED = "S";

  private static ScheduleSelection schedules(String... labels) {
    return ScheduleSelection.of(List.of(labels));
  }

  private static MillYearTrackCodes row(long millId, int year, String main, String silviculture) {
    return new MillYearTrackCodes(millId, year, main, silviculture);
  }

  /** The common case: two mills across two years, all four pairs present. */
  private static boolean verdict(ScheduleSelection selection, List<MillYearTrackCodes> rows) {
    return DataVerifiedRule.allVerified(List.of(1L, 2L), 2020, 2021, selection, rows);
  }

  private static List<MillYearTrackCodes> allPairs(String main, String silviculture) {
    return List.of(
        row(1L, 2020, main, silviculture),
        row(1L, 2021, main, silviculture),
        row(2L, 2020, main, silviculture),
        row(2L, 2021, main, silviculture));
  }

  @Nested
  @DisplayName("Yes")
  class Yes {

    @Test
    @DisplayName("every mill/year Verified on the one applicable track")
    void allVerifiedOnOneTrack() {
      assertThat(verdict(schedules("Schedule 1"), allPairs(VERIFIED, DRAFT))).isTrue();
    }

    @Test
    @DisplayName("every mill/year Verified on BOTH tracks when both kinds are selected")
    void allVerifiedOnBothTracks() {
      assertThat(verdict(schedules("Schedule 1", "Schedule 11"), allPairs(VERIFIED, VERIFIED)))
          .isTrue();
    }

    @Test
    @DisplayName("a single mill and a single year")
    void singlePair() {
      assertThat(
              DataVerifiedRule.allVerified(
                  List.of(1L),
                  2020,
                  2020,
                  schedules("Schedule 5"),
                  List.of(row(1L, 2020, "V", null))))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("No — the ordinary cases")
  class No {

    @Test
    @DisplayName("one mill/year short of Verified on the applicable track")
    void oneUnverifiedPair() {
      List<MillYearTrackCodes> rows =
          List.of(
              row(1L, 2020, VERIFIED, VERIFIED),
              row(1L, 2021, VERIFIED, VERIFIED),
              row(2L, 2020, SUBMITTED, VERIFIED),
              row(2L, 2021, VERIFIED, VERIFIED));

      assertThat(verdict(schedules("Schedule 1"), rows)).isFalse();
    }

    @Test
    @DisplayName("a null code on an applicable track")
    void nullCodeOnApplicableTrack() {
      // A null is not a pass. The comparison is on the literal "V", as legacy's was, so a null
      // simply fails it — pinned because a rewrite to a negated "not D and not S" test would not.
      assertThat(verdict(schedules("Schedule 1"), allPairs(null, VERIFIED))).isFalse();
    }
  }

  @Nested
  @DisplayName("No — the three legacy defects, not reproduced")
  class LegacyDefects {

    @Test
    @DisplayName("both tracks selected and only ONE is Verified is No, not Yes")
    void bothTracksRequireBoth() {
      // Legacy OR'd the two tracks, so a mill whose silviculture track was never verified still
      // printed Yes as long as the main track was. Every applicable track must pass.
      assertThat(verdict(schedules("Schedule 1", "Schedule 11"), allPairs(VERIFIED, DRAFT)))
          .isFalse();
      assertThat(verdict(schedules("Schedule 1", "Schedule 11"), allPairs(DRAFT, VERIFIED)))
          .isFalse();
    }

    @Test
    @DisplayName("a MISSING status row is No — legacy's vacuous Yes is not reproduced")
    void missingRowIsNo() {
      // Legacy decided No by looking for a false in a list it built only from rows that existed, so
      // a selection with no status rows at all contained no false and printed Yes. A mill/year with
      // nothing reported is the clearest possible "not verified".
      assertThat(verdict(schedules("Schedule 1"), List.of())).isFalse();

      // And the same when only SOME pairs have rows: the absent one decides it.
      List<MillYearTrackCodes> threeOfFour =
          List.of(
              row(1L, 2020, VERIFIED, VERIFIED),
              row(1L, 2021, VERIFIED, VERIFIED),
              row(2L, 2020, VERIFIED, VERIFIED));
      assertThat(verdict(schedules("Schedule 1"), threeOfFour)).isFalse();
    }

    @Test
    @DisplayName("Schedule 7 alone counts toward the Schedules 1-10 track")
    void scheduleSevenCountsTowardTheMainTrack() {
      // Legacy tested its detail list for the literal "Schedule 7", which never appeared there
      // because Schedule 7 expanded into 7 A and 7 B — so a Schedule-7-only extract matched no
      // track, evaluated nothing, and printed No however verified the data was.
      assertThat(verdict(schedules("Schedule 7"), allPairs(VERIFIED, DRAFT))).isTrue();
      assertThat(verdict(schedules("Schedule 7"), allPairs(DRAFT, VERIFIED))).isFalse();
    }
  }

  @Nested
  @DisplayName("which track applies")
  class ApplicableTracks {

    @Test
    @DisplayName("Schedules 1-10 only ignores the silviculture track entirely")
    void mainTrackOnly() {
      assertThat(verdict(schedules("Schedule 1", "Schedule 10"), allPairs(VERIFIED, null)))
          .isTrue();
    }

    @Test
    @DisplayName("Schedule 11 only ignores the Schedules 1-10 track entirely")
    void silvicultureTrackOnly() {
      assertThat(verdict(schedules("Schedule 11"), allPairs(null, VERIFIED))).isTrue();
      assertThat(verdict(schedules("Schedule 11"), allPairs(VERIFIED, DRAFT))).isFalse();
    }

    @Test
    @DisplayName("a selection naming no known schedule cannot be Verified")
    void noTrackIsNo() {
      // Reachable: unknown labels are passed through by the selection gate rather than refused, so
      // a request carrying only an unrecognised label arrives here with nothing to check. Nothing
      // applies, so nothing has been verified, and the line must not claim otherwise.
      assertThat(verdict(schedules("Schedule 12"), allPairs(VERIFIED, VERIFIED))).isFalse();
      assertThat(verdict(schedules(), allPairs(VERIFIED, VERIFIED))).isFalse();
    }
  }

  @Nested
  @DisplayName("row matching")
  class RowMatching {

    @Test
    @DisplayName("a row for an unselected mill or an out-of-range year cannot satisfy a pair")
    void ignoresRowsOutsideTheSelection() {
      // The bulk read is a range query, so it can return more than the selection needs; a row for
      // mill 3 must not stand in for the missing mill 2.
      List<MillYearTrackCodes> rows =
          List.of(
              row(1L, 2020, VERIFIED, VERIFIED),
              row(1L, 2021, VERIFIED, VERIFIED),
              row(3L, 2020, VERIFIED, VERIFIED),
              row(3L, 2021, VERIFIED, VERIFIED));

      assertThat(verdict(schedules("Schedule 1"), rows)).isFalse();
    }

    @Test
    @DisplayName("every year in the range must be present, not just the endpoints")
    void checksEveryYearInTheRange() {
      List<MillYearTrackCodes> endpointsOnly =
          List.of(row(1L, 2020, VERIFIED, VERIFIED), row(1L, 2022, VERIFIED, VERIFIED));

      assertThat(
              DataVerifiedRule.allVerified(
                  List.of(1L), 2020, 2022, schedules("Schedule 1"), endpointsOnly))
          .isFalse();
    }
  }
}
