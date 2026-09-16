package ca.bc.gov.nrs.ilcr.dataextract.csv;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The schedules a selection names, as numbers 1..11, from the raw picker labels.
 *
 * <p>Matching is case-insensitive on a trimmed label, exactly as legacy's dispatch was ({@code
 * schedule.equalsIgnoreCase("schedule 1")}), so {@code "schedule 1"} selects Schedule 1. A label
 * that is not one of the eleven matches nothing and is ignored — the ratified rule for unknown
 * labels: legacy's checkbox menu could never send one, and its dispatch simply matched no builder.
 *
 * <p>Schedule 7 is one label covering both 7A and 7B. Whether it counts toward the Schedules 1–10
 * track is decided HERE, by number, so the blind spot in legacy's own check — which tested the
 * detail list for the literal {@code "Schedule 7"}, a string that list never contained — is not
 * reproduced.
 */
public final class ScheduleSelection {

  private final SortedSet<Integer> numbers;

  private ScheduleSelection(SortedSet<Integer> numbers) {
    this.numbers = Collections.unmodifiableSortedSet(numbers);
  }

  /** Parse the picker labels; unknown labels are dropped. */
  public static ScheduleSelection of(Collection<String> labels) {
    SortedSet<Integer> numbers = new TreeSet<>();
    for (String label : labels) {
      parse(label).ifPresent(numbers::add);
    }
    return new ScheduleSelection(numbers);
  }

  /** The schedule number a label names, if it names one. */
  public static Optional<Integer> parse(String label) {
    if (label == null) {
      return Optional.empty();
    }
    String normalized = label.trim().toLowerCase(Locale.ROOT);
    for (int n = 1; n <= 11; n++) {
      if (normalized.equals("schedule " + n)) {
        return Optional.of(n);
      }
    }
    return Optional.empty();
  }

  /** The selected schedule numbers, ascending. */
  public SortedSet<Integer> numbers() {
    return numbers;
  }

  public boolean contains(int schedule) {
    return numbers.contains(schedule);
  }

  /** Whether any of Schedules 1–10 is selected (Schedule 7 counts). */
  public boolean includesSchedules1To10() {
    return numbers.stream().anyMatch(n -> n <= 10);
  }

  /** Whether Schedule 11 is selected. */
  public boolean includesSchedule11() {
    return numbers.contains(11);
  }

  /**
   * Legacy's combined-layout trigger, minus the mill and year conditions the caller supplies:
   * exactly two schedules were chosen and they are 1 and 2.
   */
  public boolean isExactlySchedules1And2() {
    return numbers.size() == 2 && numbers.contains(1) && numbers.contains(2);
  }
}
