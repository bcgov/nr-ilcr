package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link ScheduleKey} declaration order (BR-08). The enum's declaration order IS the
 * combined-PDF section order (the orchestrator iterates {@link ScheduleKey#values()} with no
 * separate ordering table), so this pins the FIXED legacy sequence: Schedule 1 (Story 20.5) is
 * declared FIRST, followed by Schedule 2 (Story 20.6), and Schedule 10 (Story 20.4) sits between
 * Schedule 9 and Schedule 11. A combined print always emits each in that fixed position relative to
 * the other selected sections. No Spring context or database.
 */
@DisplayName("ScheduleKey — fixed legacy section order (BR-08)")
class ScheduleKeyTest {

  @Test
  @DisplayName(
      "values() are in the fixed legacy print order (1 first, 2 second, 10 between 9 and 11)")
  void values_areInFixedLegacyOrder() {
    List<ScheduleKey> order = Arrays.asList(ScheduleKey.values());

    assertThat(order)
        .containsExactly(
            ScheduleKey.SCHEDULE_1,
            ScheduleKey.SCHEDULE_2,
            ScheduleKey.SCHEDULE_5,
            ScheduleKey.SCHEDULE_6,
            ScheduleKey.SCHEDULE_7A,
            ScheduleKey.SCHEDULE_7B,
            ScheduleKey.SCHEDULE_9,
            ScheduleKey.SCHEDULE_10,
            ScheduleKey.SCHEDULE_11);

    // The load-bearing 20.5 fact: Schedule 1 renders first (ahead of every other section).
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_1)).isZero();
    // The load-bearing 20.6 fact: Schedule 2 renders before Schedule 5 in any combined print.
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_2))
        .isLessThan(order.indexOf(ScheduleKey.SCHEDULE_5));
    // The load-bearing 20.4 fact: Schedule 10 renders after 9 and before 11.
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_10))
        .isGreaterThan(order.indexOf(ScheduleKey.SCHEDULE_9))
        .isLessThan(order.indexOf(ScheduleKey.SCHEDULE_11));
  }

  @Test
  @DisplayName("SCHEDULE_1 is bound to its template + bookmark title")
  void schedule1_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_1.templatePath()).isEqualTo("reports/schedule1.jrxml");
    assertThat(ScheduleKey.SCHEDULE_1.bookmarkTitle())
        .isEqualTo("Schedule 1: Average Cost of Logging");
  }

  @Test
  @DisplayName("SCHEDULE_2 is bound to its template + bookmark title")
  void schedule2_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_2.templatePath()).isEqualTo("reports/schedule2.jrxml");
    assertThat(ScheduleKey.SCHEDULE_2.bookmarkTitle())
        .isEqualTo("Schedule 2: Purchased and Private Log Costs and Sales");
  }

  @Test
  @DisplayName("SCHEDULE_10 is bound to its template + bookmark title")
  void schedule10_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_10.templatePath()).isEqualTo("reports/schedule10.jrxml");
    assertThat(ScheduleKey.SCHEDULE_10.bookmarkTitle())
        .isEqualTo("Schedule 10: New Road Construction Costs");
  }
}
