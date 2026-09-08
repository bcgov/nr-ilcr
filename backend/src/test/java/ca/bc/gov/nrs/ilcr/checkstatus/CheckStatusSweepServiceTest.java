package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.checkstatus.dto.CheckStatusSweepResponse;
import ca.bc.gov.nrs.ilcr.checkstatus.dto.ScheduleCheckResult;
import ca.bc.gov.nrs.ilcr.dto.base.CheckStatusOutcome;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.TrackStatusCodes;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult.CampCheckMessage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8CheckStatusResolver;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.NoSuchMessageException;

/**
 * Unit test for {@link CheckStatusSweepService} — the aggregation over the twelve schedule
 * validations (Story 15.1, AC 1/2/3/4/8). Every schedule's own check-status method is mocked, so
 * this isolates what the sweep ADDS: the 11 + 1 track partition in legacy order, the per-schedule
 * validity normalization, the track roll-up, and the pass-through of each schedule's own response.
 * What each schedule decides is each schedule's own test's business (AD-5).
 */
@ExtendWith(MockitoExtension.class)
class CheckStatusSweepServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  private static final MessageInfo MET_BANNER =
      new MessageInfo(
          "scheduleRequirementsMetMsg", "All requirements for this schedule have been met");
  private static final MessageInfo VALUE_REQUIRED =
      new MessageInfo("missingRequiredFieldMsg", "Value Required");

  @Mock private MillContextService millContextService;
  @Mock private Schedule1Service schedule1Service;
  @Mock private Schedule2CheckStatusResolver schedule2;
  @Mock private Schedule3Service schedule3Service;
  @Mock private Schedule4CheckStatusResolver schedule4;
  @Mock private Schedule5CheckStatusResolver schedule5;
  @Mock private Schedule6CheckStatusResolver schedule6;
  @Mock private Schedule7aService schedule7aService;
  @Mock private Schedule7bService schedule7bService;
  @Mock private Schedule8CheckStatusResolver schedule8;
  @Mock private Schedule9Service schedule9Service;
  @Mock private Schedule10CheckStatusResolver schedule10;
  @Mock private Schedule11Service schedule11Service;

  @InjectMocks private CheckStatusSweepService service;

  @BeforeEach
  void bothTracksDraft() {
    lenient()
        .when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes("D", "D")));
  }

  // ===============================================================================================
  // Fixtures — the two response families, one MET and one ISSUES instance each
  // ===============================================================================================

  private static Schedule1CheckStatusResponse schedule1(boolean met) {
    return new Schedule1CheckStatusResponse(
        met, met ? List.of() : List.of(VALUE_REQUIRED), List.of(), met ? MET_BANNER : null);
  }

  private static Schedule2CheckStatusResponse schedule2(boolean met) {
    return new Schedule2CheckStatusResponse(
        outcome(met), List.of(met ? MET_BANNER : VALUE_REQUIRED));
  }

  private static Schedule3CheckStatusResponse schedule3(boolean met) {
    return new Schedule3CheckStatusResponse(
        met, met ? List.of() : List.of(VALUE_REQUIRED), List.of(), met ? MET_BANNER : null);
  }

  private static Schedule4CheckStatusResponse schedule4(boolean met) {
    return new Schedule4CheckStatusResponse(
        outcome(met), met ? List.of(MET_BANNER) : List.of(), List.of());
  }

  private static Schedule5CheckStatusResponse schedule5(boolean met) {
    if (met) {
      return new Schedule5CheckStatusResponse(
          CheckStatusOutcome.MET, List.of(MET_BANNER), List.of());
    }
    // The per-camp finding is a bespoke CampCheckMessage, NOT a MessageInfo — the shape a
    // type-based
    // sweep dropped silently in Story 15.0's first in-process run.
    CampCheckResult failingCamp =
        new CampCheckResult(
            8212,
            "Bare Descriptor Camp",
            false,
            List.of(
                new CampCheckMessage(
                    "missingRequiredFieldMsg",
                    "sizeOfCamp",
                    "Camp Report Name : Bare Descriptor Camp - Size of Camp: Value Required")));
    return new Schedule5CheckStatusResponse(
        CheckStatusOutcome.ISSUES, List.of(), List.of(failingCamp));
  }

  private static Schedule6CheckStatusResponse schedule6(boolean met) {
    return new Schedule6CheckStatusResponse(
        outcome(met), met ? List.of(MET_BANNER) : List.of(), List.of());
  }

  private static Schedule7aCheckStatusResponse schedule7a(boolean met) {
    return new Schedule7aCheckStatusResponse(
        met, met ? List.of() : List.of(VALUE_REQUIRED), List.of(), met ? MET_BANNER : null);
  }

  private static Schedule7bCheckStatusResponse schedule7b(boolean met) {
    return new Schedule7bCheckStatusResponse(
        met, met ? List.of() : List.of(VALUE_REQUIRED), met ? MET_BANNER : null);
  }

  private static Schedule8CheckStatusResponse schedule8(boolean met) {
    return new Schedule8CheckStatusResponse(
        outcome(met), met ? List.of(MET_BANNER) : List.of(), List.of());
  }

  private static Schedule9CheckStatusResponse schedule9(boolean met) {
    return new Schedule9CheckStatusResponse(
        met, met ? List.of() : List.of(VALUE_REQUIRED), met ? MET_BANNER : null);
  }

  private static Schedule10CheckStatusResponse schedule10(boolean met) {
    return new Schedule10CheckStatusResponse(
        outcome(met), met ? List.of(MET_BANNER) : List.of(), List.of());
  }

  private static Schedule11CheckStatusResponse schedule11(boolean met) {
    return new Schedule11CheckStatusResponse(
        met,
        met ? List.of() : List.of(VALUE_REQUIRED),
        met ? MET_BANNER : null,
        new MessageInfo("checkStatusMessage", "Status has been checked"));
  }

  private static String outcome(boolean met) {
    return met ? CheckStatusOutcome.MET : CheckStatusOutcome.ISSUES;
  }

  /** Stub ONE schedule's verdict; every other schedule is stubbed by {@link #allMet()}. */
  private void stub(CheckedSchedule schedule, boolean met) {
    switch (schedule) {
      case SCHEDULE_1 ->
          when(schedule1Service.checkSchedule1Status(MILL, YEAR)).thenReturn(schedule1(met));
      case SCHEDULE_2 -> when(schedule2.checkStatus(MILL, YEAR)).thenReturn(schedule2(met));
      case SCHEDULE_3 ->
          when(schedule3Service.checkSchedule3Status(MILL, YEAR)).thenReturn(schedule3(met));
      case SCHEDULE_4 -> when(schedule4.checkStatus(MILL, YEAR)).thenReturn(schedule4(met));
      case SCHEDULE_5 -> when(schedule5.checkStatus(MILL, YEAR)).thenReturn(schedule5(met));
      case SCHEDULE_6 -> when(schedule6.checkStatusStored(MILL, YEAR)).thenReturn(schedule6(met));
      case SCHEDULE_7A ->
          when(schedule7aService.checkStatus(MILL, YEAR)).thenReturn(schedule7a(met));
      case SCHEDULE_7B ->
          when(schedule7bService.checkStatus(MILL, YEAR)).thenReturn(schedule7b(met));
      case SCHEDULE_8 -> when(schedule8.checkStatus(MILL, YEAR)).thenReturn(schedule8(met));
      case SCHEDULE_9 -> when(schedule9Service.checkStatus(MILL, YEAR)).thenReturn(schedule9(met));
      case SCHEDULE_10 -> when(schedule10.checkStatus(MILL, YEAR)).thenReturn(schedule10(met));
      case SCHEDULE_11 ->
          when(schedule11Service.checkStatus(MILL, YEAR)).thenReturn(schedule11(met));
    }
  }

  private void allMet() {
    for (CheckedSchedule schedule : CheckedSchedule.values()) {
      stub(schedule, true);
    }
  }

  private static List<String> codesOf(List<ScheduleCheckResult> results) {
    return results.stream().map(ScheduleCheckResult::schedule).toList();
  }

  // ===============================================================================================
  // AC 1 — the sweep: all twelve, partitioned 11 + 1, in legacy order, both track statuses
  // ===============================================================================================

  @Test
  @DisplayName("S01: every schedule passes -> both tracks MET, 11 + 1 partition in legacy order")
  void allSchedulesPass_bothTracksMet() {
    allMet();

    CheckStatusSweepResponse response = service.sweep(MILL, YEAR);

    assertThat(response.millId()).isEqualTo(MILL);
    assertThat(response.year()).isEqualTo(YEAR);
    assertThat(response.schedules1To10().requirementsMet()).isTrue();
    assertThat(response.schedule11().requirementsMet()).isTrue();
    // 7A and 7B are two of the "ten"; Schedule 11 sits alone (UC-CHK-004-technical.md:165).
    assertThat(codesOf(response.schedules1To10().schedules()))
        .containsExactly("1", "2", "3", "4", "5", "6", "7A", "7B", "8", "9", "10");
    assertThat(codesOf(response.schedule11().schedules())).containsExactly("11");
    assertThat(response.schedules1To10().schedules())
        .allSatisfy(result -> assertThat(result.requirementsMet()).isTrue());
  }

  @Test
  @DisplayName("both track status codes ride on the response, read once from millcontext")
  void trackStatusCodes_carriedPerTrack() {
    allMet();
    when(millContextService.findTrackStatusCodes(MILL, YEAR))
        .thenReturn(Optional.of(new TrackStatusCodes("S", null)));

    CheckStatusSweepResponse response = service.sweep(MILL, YEAR);

    assertThat(response.schedules1To10().statusCode()).isEqualTo("S");
    assertThat(response.schedule11().statusCode()).isNull();
    verify(millContextService).findTrackStatusCodes(MILL, YEAR);
  }

  @Test
  @DisplayName("no status row -> both track status codes null (the guard has already 404'd)")
  void noStatusRow_nullStatusCodes() {
    allMet();
    when(millContextService.findTrackStatusCodes(MILL, YEAR)).thenReturn(Optional.empty());

    CheckStatusSweepResponse response = service.sweep(MILL, YEAR);

    assertThat(response.schedules1To10().statusCode()).isNull();
    assertThat(response.schedule11().statusCode()).isNull();
  }

  // ===============================================================================================
  // AC 2 — each schedule's own response rides through verbatim; the sweep never re-shapes it
  // ===============================================================================================

  @Test
  @DisplayName("AC 2: the verdict IS the schedule's own response object — both families, untouched")
  void verdict_isTheSchedulesOwnResponse() {
    allMet();
    Schedule5CheckStatusResponse schedule5Issues = schedule5(false);
    when(schedule5.checkStatus(MILL, YEAR)).thenReturn(schedule5Issues);
    Schedule9CheckStatusResponse schedule9Issues = schedule9(false);
    when(schedule9Service.checkStatus(MILL, YEAR)).thenReturn(schedule9Issues);

    List<ScheduleCheckResult> results = service.sweep(MILL, YEAR).schedules1To10().schedules();

    // Same instance, not a copy: Schedule 5's bespoke CampCheckMessage findings cannot be dropped
    // because nothing in the sweep reads them.
    assertThat(results.get(4).verdict()).isSameAs(schedule5Issues);
    assertThat(results.get(9).verdict()).isSameAs(schedule9Issues);
    assertThat(results.get(0).verdict()).isInstanceOf(Schedule1CheckStatusResponse.class);
    assertThat(results.get(1).verdict()).isInstanceOf(Schedule2CheckStatusResponse.class);
  }

  // ===============================================================================================
  // AC 3 — one uniform per-schedule verdict, normalized from the two families; one test per
  // schedule
  // ===============================================================================================

  @ParameterizedTest(
      name = "only Schedule {0} failing -> only its result and its track are not met")
  @EnumSource(CheckedSchedule.class)
  @DisplayName("AC 3: validity normalizes per schedule and rolls up per track")
  void onlyOneScheduleFailing_normalizesAndIsolates(CheckedSchedule failing) {
    allMet();
    stub(failing, false);

    CheckStatusSweepResponse response = service.sweep(MILL, YEAR);

    List<ScheduleCheckResult> all =
        java.util.stream.Stream.concat(
                response.schedules1To10().schedules().stream(),
                response.schedule11().schedules().stream())
            .toList();
    assertThat(all).hasSize(12);
    assertThat(all)
        .allSatisfy(
            result ->
                assertThat(result.requirementsMet())
                    .as("schedule %s", result.schedule())
                    .isEqualTo(!result.schedule().equals(failing.code())));

    boolean on1To10 = failing.track() == ScheduleTrack.SCHEDULES_1_TO_10;
    assertThat(response.schedules1To10().requirementsMet()).isEqualTo(!on1To10);
    assertThat(response.schedule11().requirementsMet()).isEqualTo(on1To10);
  }

  // ===============================================================================================
  // AC 4 / CHK-009 S17 — Schedule 7B carries its OWN verdict
  // ===============================================================================================

  @Test
  @DisplayName("S17: 7A passing and 7B failing -> 7B reports its own failure (legacy showed 7A's)")
  void schedule7bFailing_reportsItsOwnVerdict() {
    allMet();
    stub(CheckedSchedule.SCHEDULE_7B, false);

    List<ScheduleCheckResult> results = service.sweep(MILL, YEAR).schedules1To10().schedules();

    assertThat(results.get(6).schedule()).isEqualTo("7A");
    assertThat(results.get(6).requirementsMet()).isTrue();
    assertThat(results.get(7).schedule()).isEqualTo("7B");
    assertThat(results.get(7).requirementsMet()).isFalse();
  }

  @Test
  @DisplayName(
      "S17 inverse: 7A failing and 7B passing -> 7B reports MET (legacy rendered a blank tab)")
  void schedule7aFailing_doesNotMask7b() {
    allMet();
    stub(CheckedSchedule.SCHEDULE_7A, false);

    List<ScheduleCheckResult> results = service.sweep(MILL, YEAR).schedules1To10().schedules();

    assertThat(results.get(6).requirementsMet()).isFalse();
    assertThat(results.get(7).requirementsMet()).isTrue();
  }

  // ===============================================================================================
  // AC 8 — per-track decomposition for Story 15.3's submit gate
  // ===============================================================================================

  @Test
  @DisplayName("checkTrack(1-10) evaluates the eleven 1-10 schedules and never touches Schedule 11")
  void checkTrack_schedules1To10_leavesSchedule11Untouched() {
    for (CheckedSchedule schedule : CheckedSchedule.values()) {
      if (schedule != CheckedSchedule.SCHEDULE_11) {
        stub(schedule, true);
      }
    }

    List<ScheduleCheckResult> results =
        service.checkTrack(ScheduleTrack.SCHEDULES_1_TO_10, MILL, YEAR);

    assertThat(codesOf(results))
        .containsExactly("1", "2", "3", "4", "5", "6", "7A", "7B", "8", "9", "10");
    verify(schedule11Service, never()).checkStatus(anyLong(), anyInt());
    verify(millContextService, never()).findTrackStatusCodes(anyLong(), anyInt());
  }

  @Test
  @DisplayName("checkTrack(11) evaluates Schedule 11 alone")
  void checkTrack_schedule11_alone() {
    stub(CheckedSchedule.SCHEDULE_11, false);

    List<ScheduleCheckResult> results = service.checkTrack(ScheduleTrack.SCHEDULE_11, MILL, YEAR);

    assertThat(codesOf(results)).containsExactly("11");
    assertThat(results.get(0).requirementsMet()).isFalse();
    verify(schedule1Service, never()).checkSchedule1Status(anyLong(), anyInt());
  }

  @Test
  @DisplayName("checkTrack rejects a missing track instead of returning a vacuously complete list")
  void checkTrack_nullTrack_rejected() {
    assertThatThrownBy(() -> service.checkTrack(null, MILL, YEAR))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("track");

    verify(schedule1Service, never()).checkSchedule1Status(anyLong(), anyInt());
    verify(schedule11Service, never()).checkStatus(anyLong(), anyInt());
  }

  // ===============================================================================================
  // S02 — the correct-and-re-check loop: the sweep holds no state
  // ===============================================================================================

  @Test
  @DisplayName(
      "S02: a re-check after a correction reflects the corrected schedule — nothing cached")
  void reCheck_reflectsCorrection() {
    allMet();
    when(schedule4.checkStatus(MILL, YEAR)).thenReturn(schedule4(false), schedule4(true));

    CheckStatusSweepResponse first = service.sweep(MILL, YEAR);
    CheckStatusSweepResponse second = service.sweep(MILL, YEAR);

    assertThat(first.schedules1To10().schedules().get(3).requirementsMet()).isFalse();
    assertThat(first.schedules1To10().requirementsMet()).isFalse();
    assertThat(second.schedules1To10().schedules().get(3).requirementsMet()).isTrue();
    assertThat(second.schedules1To10().requirementsMet()).isTrue();
  }

  // ===============================================================================================
  // Sweep-level failure isolation — decided: a missing bundle key fails the WHOLE call
  // ===============================================================================================

  @Test
  @DisplayName(
      "a fail-loud resolver's NoSuchMessageException aborts the sweep — never a schedule reported"
          + " clean")
  void missingBundleKey_failsTheWholeSweep() {
    // Only the schedules AHEAD of Schedule 5 in legacy order are ever reached (strict stubs would
    // flag the rest as unnecessary — which is itself the point being tested).
    stub(CheckedSchedule.SCHEDULE_1, true);
    stub(CheckedSchedule.SCHEDULE_2, true);
    stub(CheckedSchedule.SCHEDULE_3, true);
    stub(CheckedSchedule.SCHEDULE_4, true);
    when(schedule5.checkStatus(MILL, YEAR))
        .thenThrow(new NoSuchMessageException("missingRequiredFieldMsg"));

    assertThatThrownBy(() -> service.sweep(MILL, YEAR)).isInstanceOf(NoSuchMessageException.class);

    // Legacy order: the failure at Schedule 5 means 6..11 were never evaluated — there is no
    // partial response in which they could have appeared.
    verify(schedule6, never()).checkStatusStored(anyLong(), anyInt());
    verify(schedule11Service, never()).checkStatus(anyLong(), anyInt());
  }
}
