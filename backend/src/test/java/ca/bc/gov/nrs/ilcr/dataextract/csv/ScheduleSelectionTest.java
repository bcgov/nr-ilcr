package ca.bc.gov.nrs.ilcr.dataextract.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the picker labels a request carries, turned into schedule numbers.
 *
 * <p>Two properties here are contract rather than convenience. Section order is ASCENDING whatever
 * order the request listed its labels in, which is why this holds a sorted set and not a list; and
 * an unrecognised label is dropped rather than refused, which the selection gate ruled deliberately
 * and which this class must not quietly start rejecting.
 */
@DisplayName("ScheduleSelection — picker labels to schedule numbers")
class ScheduleSelectionTest {

  private static ScheduleSelection of(String... labels) {
    return ScheduleSelection.of(Arrays.asList(labels));
  }

  @Nested
  @DisplayName("parsing")
  class Parsing {

    @Test
    @DisplayName("all eleven labels parse")
    void allElevenParse() {
      for (int n = 1; n <= 11; n++) {
        assertThat(ScheduleSelection.parse("Schedule " + n)).contains(n);
      }
    }

    @Test
    @DisplayName("matching is case-insensitive on a trimmed label")
    void matchesCaseInsensitivelyAndTrims() {
      // Legacy dispatched with equalsIgnoreCase, so a differently-cased label selected the same
      // builder rather than silently matching nothing.
      assertThat(ScheduleSelection.parse("schedule 1")).contains(1);
      assertThat(ScheduleSelection.parse("SCHEDULE 11")).contains(11);
      assertThat(ScheduleSelection.parse("  Schedule 4  ")).contains(4);
    }

    @Test
    @DisplayName("a label that names no schedule is empty, including near misses")
    void unknownLabelIsEmpty() {
      assertThat(ScheduleSelection.parse("Schedule 12")).isEmpty();
      assertThat(ScheduleSelection.parse("Schedule 0")).isEmpty();
      assertThat(ScheduleSelection.parse("Schedule 7 A")).isEmpty();
      assertThat(ScheduleSelection.parse("Schedule")).isEmpty();
      assertThat(ScheduleSelection.parse("")).isEmpty();
      assertThat(ScheduleSelection.parse(null)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the set of numbers")
  class Numbers {

    @Test
    @DisplayName("is ascending regardless of the order the labels arrived in")
    void isAscending() {
      // Sections are emitted in this order, so it is the file's order and not an implementation
      // detail: a client that listed its schedules backwards must still get an ascending file.
      // containsExactly asserts the ITERATION order, which is the property that matters.
      assertThat(of("Schedule 11", "Schedule 2", "Schedule 7").numbers()).containsExactly(2, 7, 11);
      assertThat(of("Schedule 10", "Schedule 9", "Schedule 1").numbers()).containsExactly(1, 9, 10);
    }

    @Test
    @DisplayName("collapses duplicates")
    void collapsesDuplicates() {
      assertThat(of("Schedule 1", "Schedule 1", "schedule 1").numbers()).containsExactly(1);
    }

    @Test
    @DisplayName("drops unknown labels and keeps the rest")
    void dropsUnknownKeepsKnown() {
      // The ruled behaviour: an unknown label contributes no section, exactly as legacy's dispatch
      // matched no builder for it, and does NOT refuse the whole request.
      assertThat(of("Schedule 1", "Schedule 12").numbers()).containsExactly(1);
    }

    @Test
    @DisplayName("is unmodifiable, so a caller cannot widen the selection after validation")
    void isUnmodifiable() {
      ScheduleSelection selection = of("Schedule 1");

      assertThatThrownBy(() -> selection.numbers().add(9))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("contains() answers per schedule")
    void containsAnswersPerSchedule() {
      ScheduleSelection selection = of("Schedule 3", "Schedule 8");

      assertThat(selection.contains(3)).isTrue();
      assertThat(selection.contains(8)).isTrue();
      assertThat(selection.contains(4)).isFalse();
    }
  }

  @Nested
  @DisplayName("which status track applies")
  class Tracks {

    @Test
    @DisplayName("any of Schedules 1-10 brings in the Schedules 1-10 track")
    void oneToTenSelectsTheMainTrack() {
      for (int n = 1; n <= 10; n++) {
        ScheduleSelection selection = of("Schedule " + n);
        assertThat(selection.includesSchedules1To10())
            .as("Schedule %d should name the Schedules 1-10 track", n)
            .isTrue();
        assertThat(selection.includesSchedule11()).isFalse();
      }
    }

    @Test
    @DisplayName("Schedule 7 counts toward the Schedules 1-10 track")
    void scheduleSevenCounts() {
      // Decided here, by number, because legacy decided it by searching its expanded detail list
      // for the literal "Schedule 7" — a string that list never contained, since the label expanded
      // into 7 A and 7 B. That hole made a Schedule-7-only extract claim no track at all.
      assertThat(of("Schedule 7").includesSchedules1To10()).isTrue();
    }

    @Test
    @DisplayName("Schedule 11 brings in the silviculture track only")
    void elevenSelectsTheSilvicultureTrack() {
      ScheduleSelection selection = of("Schedule 11");

      assertThat(selection.includesSchedule11()).isTrue();
      assertThat(selection.includesSchedules1To10()).isFalse();
    }

    @Test
    @DisplayName("a mixed selection brings in both tracks")
    void mixedSelectsBoth() {
      ScheduleSelection selection = of("Schedule 4", "Schedule 11");

      assertThat(selection.includesSchedules1To10()).isTrue();
      assertThat(selection.includesSchedule11()).isTrue();
    }

    @Test
    @DisplayName("an empty selection names no track")
    void emptyNamesNoTrack() {
      assertThat(of().includesSchedules1To10()).isFalse();
      assertThat(of().includesSchedule11()).isFalse();
    }
  }

  @Nested
  @DisplayName("the combined Schedule 1 + 2 trigger")
  class CombinedTrigger {

    @Test
    @DisplayName("holds for exactly Schedules 1 and 2 and nothing else")
    void holdsForExactlyOneAndTwo() {
      assertThat(of("Schedule 1", "Schedule 2").isExactlySchedules1And2()).isTrue();
      // Order and case do not matter; the set does.
      assertThat(of("Schedule 2", "schedule 1").isExactlySchedules1And2()).isTrue();
    }

    @Test
    @DisplayName("fails for a third schedule, or for either one alone")
    void failsOtherwise() {
      assertThat(of("Schedule 1", "Schedule 2", "Schedule 3").isExactlySchedules1And2()).isFalse();
      assertThat(of("Schedule 1").isExactlySchedules1And2()).isFalse();
      assertThat(of("Schedule 2").isExactlySchedules1And2()).isFalse();
      assertThat(of("Schedule 1", "Schedule 11").isExactlySchedules1And2()).isFalse();
    }

    @Test
    @DisplayName("an unknown third label does not break the trigger, since it is dropped first")
    void unknownThirdLabelIsDropped() {
      // Consistent with the drop-unknown rule: the request carried three labels but names two
      // schedules, so the combined layout still applies.
      assertThat(of("Schedule 1", "Schedule 2", "Schedule 12").isExactlySchedules1And2()).isTrue();
    }
  }
}
