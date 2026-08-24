package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link ScheduleKey} declaration order (BR-08). The enum's declaration order IS the
 * combined-PDF section order (the orchestrator iterates {@link ScheduleKey#values()} with no
 * separate ordering table), so this pins the FIXED legacy sequence: Schedule 3 (Story 20.7) is
 * declared FIRST, and Schedule 10 (Story 20.4) sits between Schedule 9 and Schedule 11. A combined
 * print always emits each in that fixed position relative to the other selected sections. No Spring
 * context or database.
 */
@DisplayName("ScheduleKey — fixed legacy section order (BR-08)")
class ScheduleKeyTest {

  @Test
  @DisplayName("values() are in the fixed legacy print order (3 first, 10 between 9 and 11)")
  void values_areInFixedLegacyOrder() {
    List<ScheduleKey> order = Arrays.asList(ScheduleKey.values());

    assertThat(order)
        .containsExactly(
            ScheduleKey.SCHEDULE_3,
            ScheduleKey.SCHEDULE_5,
            ScheduleKey.SCHEDULE_6,
            ScheduleKey.SCHEDULE_7A,
            ScheduleKey.SCHEDULE_7B,
            ScheduleKey.SCHEDULE_9,
            ScheduleKey.SCHEDULE_10,
            ScheduleKey.SCHEDULE_11);

    // The load-bearing 20.7 fact: Schedule 3 renders ahead of Schedule 5.
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_3))
        .isLessThan(order.indexOf(ScheduleKey.SCHEDULE_5));
    // The load-bearing 20.4 fact: Schedule 10 renders after 9 and before 11.
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_10))
        .isGreaterThan(order.indexOf(ScheduleKey.SCHEDULE_9))
        .isLessThan(order.indexOf(ScheduleKey.SCHEDULE_11));
  }

  @Test
  @DisplayName("SCHEDULE_3 is bound to its template + bookmark title")
  void schedule3_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_3.templatePath()).isEqualTo("reports/schedule3.jrxml");
    assertThat(ScheduleKey.SCHEDULE_3.bookmarkTitle())
        .isEqualTo("Schedule 3: Forest Management Administration Costs");
  }

  @Test
  @DisplayName("SCHEDULE_10 is bound to its template + bookmark title")
  void schedule10_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_10.templatePath()).isEqualTo("reports/schedule10.jrxml");
    assertThat(ScheduleKey.SCHEDULE_10.bookmarkTitle())
        .isEqualTo("Schedule 10: New Road Construction Costs");
  }
}
