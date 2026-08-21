package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link ScheduleKey} declaration order (BR-08). The enum's declaration order IS the
 * combined-PDF section order (the orchestrator iterates {@link ScheduleKey#values()} with no
 * separate ordering table), so this pins the FIXED legacy sequence and, in particular, that
 * Schedule 2 (Story 20.6) is declared FIRST — ahead of Schedule 5 — i.e. a combined print always
 * emits it in that fixed position relative to the other selected sections. No Spring context or
 * database.
 */
@DisplayName("ScheduleKey — fixed legacy section order (BR-08)")
class ScheduleKeyTest {

  @Test
  @DisplayName("values() are in the fixed legacy print order, with SCHEDULE_2 first (before 5)")
  void values_areInFixedLegacyOrder() {
    List<ScheduleKey> order = Arrays.asList(ScheduleKey.values());

    assertThat(order)
        .containsExactly(
            ScheduleKey.SCHEDULE_2,
            ScheduleKey.SCHEDULE_5,
            ScheduleKey.SCHEDULE_6,
            ScheduleKey.SCHEDULE_7A,
            ScheduleKey.SCHEDULE_7B,
            ScheduleKey.SCHEDULE_9,
            ScheduleKey.SCHEDULE_11);

    // The load-bearing 20.6 fact: Schedule 2 renders before Schedule 5 in any combined print.
    assertThat(order.indexOf(ScheduleKey.SCHEDULE_2))
        .isLessThan(order.indexOf(ScheduleKey.SCHEDULE_5));
  }

  @Test
  @DisplayName("SCHEDULE_2 is bound to its template + bookmark title")
  void schedule2_hasTemplateAndBookmark() {
    assertThat(ScheduleKey.SCHEDULE_2.templatePath()).isEqualTo("reports/schedule2.jrxml");
    assertThat(ScheduleKey.SCHEDULE_2.bookmarkTitle())
        .isEqualTo("Schedule 2: Purchased and Private Log Costs and Sales");
  }
}
