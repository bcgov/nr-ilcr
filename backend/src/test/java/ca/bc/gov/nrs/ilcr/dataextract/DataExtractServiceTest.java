package ca.bc.gov.nrs.ilcr.dataextract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ca.bc.gov.nrs.ilcr.dataextract.dto.DataExtractRequest;
import ca.bc.gov.nrs.ilcr.exception.MultiMessageException;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearGuard;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for the accumulation itself — the part of the gate that has no database in it.
 *
 * <p>The ITs prove the wire shape; these prove the two properties that are easy to break without
 * failing a wire assertion: that the accumulator collects rather than short-circuits, and that each
 * key travels with its OWN arguments. The second matters because the two blank-year messages share
 * one bundle key and differ only by their label argument — an off-by-one in the argument list would
 * render "Start Year" twice and still look like a plausible response.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataExtractService — the accumulating selection gate")
class DataExtractServiceTest {

  @Mock private ReportYearGuard reportYearGuard;

  @InjectMocks private DataExtractService service;

  private static DataExtractRequest request(
      String startYear, String endYear, List<Long> millIds, List<String> schedules) {
    return new DataExtractRequest(startYear, endYear, millIds, schedules);
  }

  private static final List<Long> ONE_MILL = List.of(514L);
  private static final List<String> ONE_SCHEDULE = List.of("Schedule 1");

  @Test
  @DisplayName("a valid selection reaches the generator seam, not a rejection")
  void validSelection_reachesTheGeneratorSeam() {
    assertThatThrownBy(() -> service.generate(request("2020", "2021", ONE_MILL, ONE_SCHEDULE)))
        .isInstanceOf(DataExtractUnavailableException.class);

    // The openness guard runs on BOTH years, and only after the gate passed.
    verify(reportYearGuard).requireOpenYear("2020");
    verify(reportYearGuard).requireOpenYear("2021");
  }

  @Test
  @DisplayName("the openness guard never runs while a selection message is outstanding")
  void failingGate_shortCircuitsTheOpennessGuard() {
    // Ordering matters: were the guard to run first, its single-message rejection would replace the
    // accumulated set and the defining behaviour of the screen would be gone.
    assertThatThrownBy(() -> service.generate(request(null, null, List.of(), List.of())))
        .isInstanceOf(MultiMessageException.class);

    verify(reportYearGuard, never()).requireOpenYear(anyString());
  }

  @Test
  @DisplayName("every failing check accumulates; the gate never returns on the first one")
  void allReachableChecks_accumulate() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request(null, null, List.of(), List.of())));

    assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(thrown.getMessageKeys())
        .containsExactly(
            "javax.faces.component.UIInput.REQUIRED",
            "javax.faces.component.UIInput.REQUIRED",
            "extractMillsNotSelectedMsg",
            "extractSchedulesNotSelectedMsg");
  }

  @Test
  @DisplayName("the two shared-key year messages carry their OWN label arguments, in screen order")
  void yearMessagesCarryTheirOwnLabels() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request(null, null, ONE_MILL, ONE_SCHEDULE)));

    assertThat(thrown.getMessageArgs(0)).containsExactly("Start Year");
    assertThat(thrown.getMessageArgs(1)).containsExactly("End Year");
  }

  @Test
  @DisplayName("an argument-free key is raised with no arguments, so its text is not reformatted")
  void plainKeysCarryNoArguments() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request("2020", "2021", List.of(), List.of())));

    assertThat(thrown.getMessageArgs(0)).isNull();
    assertThat(thrown.getMessageArgs(1)).isNull();
  }

  @Test
  @DisplayName("start == end passes; only start > end is a range failure")
  void startEqualToEnd_isNotARangeFailure() {
    assertThatThrownBy(() -> service.generate(request("2021", "2021", ONE_MILL, ONE_SCHEDULE)))
        .isInstanceOf(DataExtractUnavailableException.class);

    MultiMessageException inverted =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request("2021", "2020", ONE_MILL, ONE_SCHEDULE)));
    assertThat(inverted.getMessageKeys()).containsExactly("extractReportingYearsNotMetMsg");
  }

  @Test
  @DisplayName("the range check is skipped, not guessed, when a year is unusable")
  void rangeCheckIsSkippedWhenAYearIsUnusable() {
    // Legacy's parseInt was unguarded here, so a blank year threw before any message was queued.
    // The range check needs two parseable years, which is why it can never join the blank-year
    // messages — and why treating a blank as zero would invent a failure legacy never reported.
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request(null, "2020", ONE_MILL, ONE_SCHEDULE)));

    assertThat(thrown.getMessageKeys())
        .containsExactly("javax.faces.component.UIInput.REQUIRED")
        .doesNotContain("extractReportingYearsNotMetMsg");
  }

  @Test
  @DisplayName("null collections are the same refusal as empty ones")
  void nullCollections_areTreatedAsEmpty() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request("2020", "2021", null, null)));

    assertThat(thrown.getMessageKeys())
        .containsExactly("extractMillsNotSelectedMsg", "extractSchedulesNotSelectedMsg");
  }

  @Test
  @DisplayName("a collection holding only null or blank entries is nothing selected")
  void blankOnlyCollections_areNothingSelected() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () ->
                service.generate(
                    request("2020", "2021", Arrays.asList((Long) null), List.of("", "   "))));

    assertThat(thrown.getMessageKeys())
        .containsExactly("extractMillsNotSelectedMsg", "extractSchedulesNotSelectedMsg");
  }

  @Test
  @DisplayName("a surrounding-whitespace year is still a year")
  void whitespacePaddedYear_parses() {
    assertThatThrownBy(() -> service.generate(request(" 2020 ", "2021", ONE_MILL, ONE_SCHEDULE)))
        .isInstanceOf(DataExtractUnavailableException.class);
  }

  @Test
  @DisplayName("an all-digit year too large for an int was SUPPLIED — not required, not compared")
  void overflowYear_isSuppliedNotMissing() {
    // The ruling ReportYearGuard makes for the single-year report endpoints (its SUPPLIED_NUMBER
    // pattern): a caller who typed 99999999999 chose a year, just not an open one. Reporting
    // "Start Year: Value is required." for it would contradict the guard this service reuses two
    // lines later (21.1 review P6). No range verdict either — there is nothing to compare.
    assertThatThrownBy(
            () -> service.generate(request("99999999999", "2021", ONE_MILL, ONE_SCHEDULE)))
        .isNotInstanceOf(MultiMessageException.class)
        .isInstanceOf(DataExtractUnavailableException.class);

    // ...and the openness guard, not the gate, is what answers for it.
    verify(reportYearGuard).requireOpenYear("99999999999");
  }

  @Test
  @DisplayName(
      "an overflow year still accumulates with the OTHER failures, and adds none of its own")
  void overflowYear_doesNotJoinTheAccumulatedSet() {
    MultiMessageException thrown =
        catchThrowableOfType(
            MultiMessageException.class,
            () -> service.generate(request("99999999999", null, List.of(), ONE_SCHEDULE)));

    assertThat(thrown.getMessageKeys())
        .containsExactly("javax.faces.component.UIInput.REQUIRED", "extractMillsNotSelectedMsg");
    assertThat(thrown.getMessageArgs(0)).containsExactly("End Year");
    verify(reportYearGuard, never()).requireOpenYear(anyString());
  }

  @Test
  @DisplayName("the validated selection is DISTINCT and stripped of unusable entries (D-R2)")
  void validatedSelection_isDistinctAndUsable() {
    // Legacy's checkbox menu could never send a repeat or an empty entry, so there is no message
    // for either and none is invented: they are simply collapsed before the generator sees them.
    DataExtractService.ValidatedSelection selection =
        service.validate(
            request(
                "2020",
                "2021",
                Arrays.asList(514L, 514L, null, 516L),
                Arrays.asList("Schedule 7", "", "Schedule 7", "Schedule 1")));

    assertThat(selection.millIds()).containsExactly(514L, 516L);
    assertThat(selection.schedules()).containsExactly("Schedule 7", "Schedule 1");
  }

  @Test
  @DisplayName("an unknown schedule label passes the gate — membership is the generator's concern")
  void unknownScheduleLabel_isNotRejected() {
    // D-R2 (2026-09-11): legacy had no membership check; an unknown name matched no builder and
    // was ignored. The gate asks only whether SOMETHING usable was selected.
    DataExtractService.ValidatedSelection selection =
        service.validate(request("2020", "2021", ONE_MILL, List.of("Schedule 12")));

    assertThat(selection.schedules()).containsExactly("Schedule 12");
  }

  @Test
  @DisplayName("the 501 seam carries the bundle key and status the handler resolves")
  void unavailableException_carriesKeyAndStatus() {
    // GlobalExceptionHandler resolves BusinessException keys with the key itself as the default, so
    // a mistyped key ships the raw key as `detail` and nothing else fails. Pin both halves here;
    // the
    // ITs pin the resolved text.
    DataExtractUnavailableException seam = new DataExtractUnavailableException();

    assertThat(seam.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    assertThat(seam.getMessageKey()).isEqualTo("dataExtractUnavailableMsg");
    assertThat(seam.getMessageArgs()).isNull();
  }
}
