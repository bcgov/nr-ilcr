package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.reporting.ScheduleKey;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CheckedSchedule} is deliberately a parallel enum to the print-coupled {@link ScheduleKey},
 * not a reuse of it. The price of two enums is that their orders can drift; this pins them equal so
 * the Check Status page and the combined PDF always list the schedules the same way.
 */
class CheckedScheduleTest {

  @Test
  @DisplayName("the twelve constants match ScheduleKey's legacy order, name for name")
  void orderMatchesThePrintEnum() {
    assertThat(Arrays.stream(CheckedSchedule.values()).map(Enum::name))
        .containsExactly(
            Arrays.stream(ScheduleKey.values()).map(Enum::name).toArray(String[]::new));
  }

  @Test
  @DisplayName("the partition is 11 + 1: 7A and 7B are two of the 'ten', Schedule 11 sits alone")
  void partitionIsElevenPlusOne() {
    assertThat(Arrays.stream(CheckedSchedule.values()))
        .filteredOn(s -> s.track() == ScheduleTrack.SCHEDULES_1_TO_10)
        .extracting(CheckedSchedule::code)
        .containsExactly("1", "2", "3", "4", "5", "6", "7A", "7B", "8", "9", "10");
    assertThat(Arrays.stream(CheckedSchedule.values()))
        .filteredOn(s -> s.track() == ScheduleTrack.SCHEDULE_11)
        .extracting(CheckedSchedule::code)
        .containsExactly("11");
  }
}
