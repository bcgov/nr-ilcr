package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.CampRow;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult.CampCheckMessage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The Check Status condition matrix (BR-08), transcribed from {@code
 * Schedule5CheckStatus.java:13-97} + {@code Schedule5MB.checkValidatedCurrentCamp():341-438}.
 *
 * <p>The service emits bundle KEYS with {@code null} text — the resolver resolves and composes
 * (AD-8) — so every assertion here is about WHICH findings fire, in what order, and what the
 * outcome is. The byte-exact rendered text is {@link Schedule5CheckStatusCompositionTest}'s.
 *
 * <p><strong>Four parity rules a tidier implementation would get wrong, each pinned below:</strong>
 * a stored {@code 0} PASSES the three numeric descriptors (pure null tests, the D2 precedent); the
 * camp-name test IS trimmed; the sub-list description test is NOT trimmed; and the all-met branch
 * emits the schedule banner ALONE with no per-camp results at all (deviation (C)) — which
 * contradicts both the epics AC and {@code UC-SCH5-001-detailed.md:151}, so legacy wins by explicit
 * decision.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule5Service.checkStatus — the eight conditions and the two outcomes")
class Schedule5CheckStatusServiceTest {

  private static final long MILL = 673L;
  private static final int YEAR = 2021;

  @Mock private Schedule5Repository repository;

  private Schedule5Service service;

  @BeforeEach
  void setUp() {
    service = new Schedule5Service(repository);
  }

  /** A camp that passes every descriptor condition. */
  private static CampRow complete(int campId, String name) {
    return new CampRow(
        campId, name, new BigDecimal("10.00"), 40, new BigDecimal("50000"), "N", null, 0);
  }

  private static CampRow campRow(
      int campId, String name, BigDecimal distance, Integer size, BigDecimal volume) {
    return new CampRow(campId, name, distance, size, volume, "N", null, 0);
  }

  private static DetailRow subRow(
      int detailId, int campId, int itemId, Integer cost, String description) {
    return new DetailRow(detailId, campId, itemId, null, cost, description);
  }

  private static List<String> fieldsFlaggedFor(
      CampRow row, List<DetailRow> campRows, List<DetailRow> accessRows) {
    return Schedule5Service.evaluateCamp(row, campRows, accessRows).stream()
        .map(CampCheckMessage::field)
        .toList();
  }

  private static List<String> fieldsFlaggedFor(CampRow row) {
    return fieldsFlaggedFor(row, List.of(), List.of());
  }

  @Nested
  @DisplayName("the four descriptor conditions")
  class Descriptors {

    @Test
    @DisplayName("a complete camp flags nothing")
    void completeCampIsClean() {
      assertThat(fieldsFlaggedFor(complete(8211, "Complete Camp"))).isEmpty();
    }

    @Test
    @DisplayName("all three numeric descriptors flag in the legacy EMISSION order")
    void allNumericDescriptorsFlagInOrder() {
      // Emission order is distance, size, volume (Schedule5MB.java:351-359) — which deliberately
      // DIFFERS from the order the flags are computed in (Schedule5CheckStatus.java:18-20 does
      // volume, distance, size). Only emission order is observable, so that is what is pinned.
      assertThat(fieldsFlaggedFor(campRow(8212, "Bare Camp", null, null, null)))
          .containsExactly(
              Schedule5Service.FIELD_ROAD_DISTANCE,
              Schedule5Service.FIELD_SIZE_OF_CAMP,
              Schedule5Service.FIELD_ASSOCIATED_CAMP_VOLUME);
    }

    @Test
    @DisplayName("a stored ZERO passes all three — they are PURE null tests (the D2 precedent)")
    void zeroPassesTheNumericDescriptors() {
      // Schedule5CheckStatus.java:18-20 is `!= null ? false : true`, nothing more. A `> 0` or
      // `!= BigDecimal.ZERO` test would flag a legitimately zero camp and tell the licensee to fill
      // in
      // a field that is already filled in.
      assertThat(fieldsFlaggedFor(campRow(8211, "Zero Camp", BigDecimal.ZERO, 0, BigDecimal.ZERO)))
          .isEmpty();
    }

    @Test
    @DisplayName("the camp-name test IS trimmed, so a whitespace-only name flags")
    void whitespaceNameFlags() {
      // CoreUtil.isNullOrEmptyString(name, true) at :17 — the `true` is the trim flag. CAMP_NAME is
      // NOT NULL in delivery, so a whitespace name is the only way this condition is reachable from
      // stored data.
      assertThat(fieldsFlaggedFor(complete(8213, "   ")))
          .containsExactly(Schedule5Service.FIELD_CAMP_NAME);
      assertThat(fieldsFlaggedFor(complete(8213, "")))
          .containsExactly(Schedule5Service.FIELD_CAMP_NAME);
      assertThat(fieldsFlaggedFor(complete(8213, null)))
          .containsExactly(Schedule5Service.FIELD_CAMP_NAME);
    }

    @Test
    @DisplayName("isolatedCamp is NEVER tested, even though Save requires it (deviation (E))")
    void isolatedCampIsNotTested() {
      CampRow noIndicator =
          new CampRow(
              8211,
              "Complete Camp",
              new BigDecimal("10.00"),
              40,
              new BigDecimal("50000"),
              null,
              null,
              0);

      assertThat(fieldsFlaggedFor(noIndicator)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the four sub-list conditions")
  class SubLists {

    @Test
    @DisplayName("an EMPTY sub-list flags nothing — a camp with no sub-page rows is complete")
    void emptyListsAreClean() {
      // CheckStatusUtil's loops never execute for an empty list, so both helpers return false.
      assertThat(fieldsFlaggedFor(complete(8211, "Complete Camp"), List.of(), List.of())).isEmpty();
    }

    @Test
    @DisplayName("all four fire together, in the legacy emission order")
    void allFourSubListConditionsFireInOrder() {
      List<DetailRow> campRows =
          List.of(
              subRow(8275, 8214, 62, 300, null), // missing description
              subRow(8276, 8214, 62, null, "Named")); // missing cost
      List<DetailRow> accessRows =
          List.of(subRow(8277, 8214, 68, 400, null), subRow(8278, 8214, 68, null, "Named"));

      assertThat(fieldsFlaggedFor(complete(8214, "Sub Page Camp"), campRows, accessRows))
          .containsExactly(
              Schedule5Service.FIELD_OTHER_CAMP_DESCRIPTION,
              Schedule5Service.FIELD_OTHER_CAMP_COST,
              Schedule5Service.FIELD_OTHER_ACCESS_DESCRIPTION,
              Schedule5Service.FIELD_OTHER_ACCESS_COST);
    }

    @Test
    @DisplayName("the description test is NOT trimmed — a single space PASSES (legacy parity)")
    void whitespaceDescriptionPasses() {
      // CheckStatusUtil.java:134 is `description == null || "".equals(description)`. Using isBlank
      // here would silently TIGHTEN legacy and flag a camp legacy considered complete. This is the
      // mirror image of the camp-name rule above, which IS trimmed — the two must not be unified.
      List<DetailRow> campRows = List.of(subRow(8273, 8211, 62, 100, " "));

      assertThat(fieldsFlaggedFor(complete(8211, "Space Desc Camp"), campRows, List.of()))
          .isEmpty();
    }

    @Test
    @DisplayName("ONE bad row among good ones is enough — the helpers are 'any', not 'all'")
    void oneBadRowIsEnough() {
      List<DetailRow> campRows =
          List.of(
              subRow(8280, 8214, 62, 100, "Fine"),
              subRow(8281, 8214, 62, 200, "Also fine"),
              subRow(8282, 8214, 62, null, "No cost"));

      assertThat(fieldsFlaggedFor(complete(8214, "Mostly Fine Camp"), campRows, List.of()))
          .containsExactly(Schedule5Service.FIELD_OTHER_CAMP_COST);
    }

    @Test
    @DisplayName("a camp-side problem does not leak into the access-side finding, or vice versa")
    void theTwoListsAreIndependent() {
      assertThat(
              fieldsFlaggedFor(
                  complete(8214, "Camp Side Only"),
                  List.of(subRow(8283, 8214, 62, null, "Named")),
                  List.of()))
          .containsExactly(Schedule5Service.FIELD_OTHER_CAMP_COST);

      assertThat(
              fieldsFlaggedFor(
                  complete(8214, "Access Side Only"),
                  List.of(),
                  List.of(subRow(8284, 8214, 68, null, "Named"))))
          .containsExactly(Schedule5Service.FIELD_OTHER_ACCESS_COST);
    }
  }

  @Nested
  @DisplayName("outcomes")
  class Outcomes {

    private void millHolds(List<CampRow> camps, List<DetailRow> details) {
      when(repository.findCamps(MILL, YEAR)).thenReturn(camps);
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(details);
    }

    @Test
    @DisplayName("ZERO camps is vacuously MET — not ISSUES, and not an error")
    void zeroCampsIsVacuouslyMet() {
      millHolds(List.of(), List.of());

      Schedule5CheckStatusResponse result = service.checkStatus(MILL, YEAR);

      // isSchedule5Valid ANDs over the camps and returns true before its loop runs
      // (Schedule5CheckStatus.java:89-97).
      assertThat(result.outcome()).isEqualTo("MET");
      assertThat(result.messages()).extracting("key").containsExactly("scheduleRequirementsMetMsg");
      assertThat(result.camps()).isEmpty();
    }

    @Test
    @DisplayName(
        "all camps passing -> MET, the banner ALONE, and NO per-camp results (deviation (C))")
    void allMetEmitsTheBannerAlone() {
      millHolds(List.of(complete(8209, "Complete One"), complete(8210, "Complete Two")), List.of());

      Schedule5CheckStatusResponse result = service.checkStatus(MILL, YEAR);

      assertThat(result.outcome()).isEqualTo("MET");
      assertThat(result.messages()).extracting("key").containsExactly("scheduleRequirementsMetMsg");
      // THE deviation: both the epics AC and UC-SCH5-001-detailed.md:151 describe an all-met PAIR —
      // the banner PLUS a per-camp campRequirementsMetMsg. Legacy's pass branch returns before the
      // per-camp loop is ever entered (Schedule5MB.java:324-326), so there are no per-camp results
      // at
      // all. An implementation that emitted the pair would satisfy the written AC and diverge from
      // the screen.
      assertThat(result.camps()).isEmpty();
    }

    @Test
    @DisplayName(
        "mixed -> ISSUES, no schedule banner, per-camp met messages only for passing camps")
    void mixedEmitsPerCampResults() {
      millHolds(
          List.of(complete(8211, "Passing Camp"), campRow(8212, "Failing Camp", null, 5, null)),
          List.of());

      Schedule5CheckStatusResponse result = service.checkStatus(MILL, YEAR);

      assertThat(result.outcome()).isEqualTo("ISSUES");
      assertThat(result.messages()).isEmpty();
      assertThat(result.camps()).hasSize(2);

      // The passing camp carries SUC-005 and nothing else — and this is the ONLY branch in which a
      // per-camp met message is ever emitted (Schedule5MB.java:342-345 sits inside the else).
      CampCheckResult passing = result.camps().get(0);
      assertThat(passing.campId()).isEqualTo(8211);
      assertThat(passing.requirementsMet()).isTrue();
      assertThat(passing.messages()).extracting("key").containsExactly("campRequirementsMetMsg");
      assertThat(passing.messages().getFirst().field()).isNull();

      CampCheckResult failing = result.camps().get(1);
      assertThat(failing.campName()).isEqualTo("Failing Camp");
      assertThat(failing.requirementsMet()).isFalse();
      assertThat(failing.messages())
          .extracting("field")
          .containsExactly(
              Schedule5Service.FIELD_ROAD_DISTANCE, Schedule5Service.FIELD_ASSOCIATED_CAMP_VOLUME);
      // Keys and machine field names only — the controller owns the text (AD-8).
      assertThat(failing.messages()).allSatisfy(m -> assertThat(m.text()).isNull());
      assertThat(failing.messages()).extracting("key").containsOnly("missingRequiredFieldMsg");
    }

    @Test
    @DisplayName("camps are reported in the repository's CAMP_REPORT_ID order, not re-sorted")
    void campsKeepRepositoryOrder() {
      millHolds(
          List.of(
              campRow(8212, "First By Id", null, null, null),
              campRow(8213, "Second By Id", null, null, null),
              campRow(8214, "Third By Id", null, null, null)),
          List.of());

      assertThat(service.checkStatus(MILL, YEAR).camps())
          .extracting("campId")
          .containsExactly(8212, 8213, 8214);
    }

    @Test
    @DisplayName("the twelve category cost/volume fields are never evaluated (deviation (D))")
    void categoryAmountsAreNotEvaluated() {
      // A camp with complete descriptors and NOT ONE stored category row must still be MET.
      // Legacy's
      // category conditions and its ~65 lines of emission are commented out in three places
      // (Schedule5CheckStatus.java:21-34, 60-82; Schedule5MB.java:360-424), so re-enabling them is
      // a
      // behaviour change and not a bug fix.
      millHolds(List.of(complete(8209, "No Categories Camp")), List.of());

      assertThat(service.checkStatus(MILL, YEAR).outcome()).isEqualTo("MET");
    }

    @Test
    @DisplayName("check status MUTATES NOTHING and is not Draft-gated")
    void mutatesNothingAndIgnoresTheTrack() {
      millHolds(List.of(complete(8209, "Complete One")), List.of());

      service.checkStatus(MILL, YEAR);

      // No Draft gate: the endpoint is VIEW-gated (the 2.6 precedent, deferred-work.md:23), so a
      // Submitted mill can still be checked. Reading the track at all would be the first step
      // toward
      // gating it.
      verify(repository, never()).findTrackStatus(anyLong(), anyInt());
      verify(repository).findCamps(MILL, YEAR);
      verify(repository).findCostDetails(MILL, YEAR);
      // Nothing else at all — no insert, no update, no delete, no sequence draw.
      verifyNoMoreInteractions(repository);
      verify(repository, never()).upsertCostDetail(anyInt(), anyInt(), any(), any(), anyString());
    }
  }
}
