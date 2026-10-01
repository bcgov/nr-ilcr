package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

/**
 * That the sweeper is actually WIRED AND SCHEDULED in a running context.
 *
 * <p>Its own test rather than a line in the context smoke test, because that smoke test runs with
 * {@code ilcr.datasource.enabled=false} and {@link SpoolReaper} — like {@link FileSpooler}, whose
 * directory it sweeps — only exists when the datasource is on. The bean is therefore invisible
 * there, which is precisely the blind spot that has bitten this application before: a
 * {@code @ConditionalOnProperty} bean whose wiring fault the no-DB context test could not see.
 *
 * <p>The scheduling half matters as much as the bean half. {@code @Scheduled} does nothing at all
 * without {@code @EnableScheduling}, which this application did not have before this story, and a
 * reaper that is constructed but never fired would leak exactly as if it had never been written —
 * silently, and with every unit test still green.
 */
@DisplayName("SpoolReaper — wiring and scheduling in a real context")
class SpoolReaperWiringIT extends AbstractOracleIT {

  @Autowired private ApplicationContext context;

  @Autowired private SpoolReaper reaper;

  @Test
  @DisplayName("the reaper's sweep is registered as a scheduled task on the reaper bean itself")
  void reaperSweep_isRegisteredAsAScheduledTask() {
    // Absent holder and empty holder have different causes — a missing @EnableScheduling versus a
    // @Scheduled that was not picked up — so they are asserted separately.
    assertThat(context.getBeansOfType(ScheduledTaskHolder.class)).isNotEmpty();

    boolean scheduled =
        context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
            .flatMap(holder -> holder.getScheduledTasks().stream())
            .map(task -> unwrap(task.getTask().getRunnable()))
            .filter(ScheduledMethodRunnable.class::isInstance)
            .map(ScheduledMethodRunnable.class::cast)
            .anyMatch(
                runnable ->
                    runnable.getTarget() == reaper
                        && "sweep".equals(runnable.getMethod().getName()));

    assertThat(scheduled).as("SpoolReaper.sweep() must be registered as a scheduled task").isTrue();
  }

  @Test
  @DisplayName("the defaults bind to an hour's grace and an enabled sweeper")
  void defaults_bindToAnHourAndEnabled() {
    // The BOUND values on the context's own bean, not the text of the @Value. A max age that
    // silently bound to zero would reap every spool file the instant it was written — deleting live
    // downloads rather than stranded ones — and a default that bound to disabled would leak exactly
    // as before this class existed. Neither failure shows up anywhere else: both leave a perfectly
    // healthy bean and a green unit suite.
    assertThat(ReflectionTestUtils.getField(reaper, "maxAge")).isEqualTo(Duration.ofHours(1));
    assertThat(ReflectionTestUtils.getField(reaper, "enabled")).isEqualTo(true);
  }

  @Test
  @DisplayName("sweeping a live spool directory is harmless")
  void sweep_onARealContext_doesNotThrow() {
    // The real bean against the real configured directory. The sweep runs on a background thread
    // where a throw is swallowed into a log line, so calling it directly is the only place a
    // failure can be made to fail a build.
    reaper.sweep();
  }

  /**
   * The {@link ScheduledMethodRunnable} inside whatever Spring has wrapped it in.
   *
   * <p>Spring wraps a scheduled method in {@code Task$OutcomeTrackingRunnable} for its observation
   * support, so {@code getRunnable()} does not hand the method runnable back directly — a plain
   * {@code instanceof} matches nothing and the assertion fails while the task is in fact perfectly
   * registered. That is not hypothetical: it is what this test did when first written, and the
   * false alarm cost more than the unwrapping does.
   *
   * <p>Walking {@code Runnable}-typed fields keeps the assertion STRUCTURAL — it still matches on
   * the target bean identity and the method name rather than on a {@code toString}, which is a
   * formatting detail of Spring's and not a contract — and it survives the wrapper being added,
   * removed or renamed. The depth bound is there so a self-referential delegate cannot spin.
   */
  private static Runnable unwrap(Runnable runnable) {
    Runnable current = runnable;
    for (int depth = 0; depth < 5 && !(current instanceof ScheduledMethodRunnable); depth++) {
      Runnable delegate = null;
      for (Field field : current.getClass().getDeclaredFields()) {
        if (Runnable.class.isAssignableFrom(field.getType())) {
          ReflectionUtils.makeAccessible(field);
          delegate = (Runnable) ReflectionUtils.getField(field, current);
          break;
        }
      }
      if (delegate == null) {
        return current;
      }
      current = delegate;
    }
    return current;
  }
}
