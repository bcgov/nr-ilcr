package ca.bc.gov.nrs.ilcr.schedule7b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link Schedule7bService}: the derived total, cost routing by item id,
 * editability, the type-conditional Check Status matrix (BR-07) with its verbatim legacy labels,
 * and write-time validation (Stories 13.1/13.2). Pure logic — the repository and message source are
 * mocked; the Testcontainers path is proven in the {@code *IT} classes.
 *
 * <p>The Check Status block is the substance of this class. BR-07 is the one rule a reader is most
 * likely to get wrong by copying Schedule 7A, so every branch is asserted in BOTH directions:
 * flagged when it should be, and <em>not</em> flagged when it should not be. Rise gets its own test
 * because "never checked for any type" is only provable by its absence.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule7bService — total, routing, type-conditional check status, validation")
class Schedule7bServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final String USER = "tester";

  private static final List<CodeDescriptionDto> TYPES =
      List.of(
          new CodeDescriptionDto("R", "Round"),
          new CodeDescriptionDto("O", "Others"),
          new CodeDescriptionDto("PA", "Pipe Arch"));

  @Mock private Schedule7bRepository repository;
  @Mock private MessageSource messageSource;
  @InjectMocks private Schedule7bService service;

  @BeforeEach
  void resolveMessagesToTheirKeyText() {
    // The bundle is exercised for real in the ITs; here resolve the two keys the service composes
    // with, so the assertions can pin the composed shape rather than the bundle's wording.
    lenient()
        .when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), anyString(), any()))
        .thenReturn("Value Required");
    lenient()
        .when(messageSource.getMessage(eq("scheduleRequirementsMetMsg"), any(), anyString(), any()))
        .thenReturn("All requirements for this schedule have been met");
    lenient().when(repository.culvertTypeOptions(YEAR)).thenReturn(TYPES);
  }

  /**
   * A stored culvert row; every optional value is a parameter so each test states only what
   * matters.
   */
  private static CulvertReportEntity row(
      long id,
      String type,
      Integer span,
      Integer rise,
      BigDecimal length,
      Integer pieces,
      String comments) {
    return new CulvertReportEntity(id, type, span, rise, length, pieces, comments, 0);
  }

  /** A complete Round culvert: span present, all unconditional values present. */
  private static CulvertReportEntity completeRound(long id) {
    return row(id, "R", 1200, 900, new BigDecimal("12.5"), 3, "Main haul road");
  }

  private static CulvertCostEntity cost(long detailId, long culvertId, int item, Integer value) {
    return new CulvertCostEntity(detailId, culvertId, item, value);
  }

  /** Both cost rows present for a culvert (the storage shape a complete culvert always has). */
  private static List<CulvertCostEntity> bothCosts(
      long culvertId, Integer material, Integer install) {
    return List.of(
        cost(culvertId * 10, culvertId, 77, material),
        cost(culvertId * 10 + 1, culvertId, 78, install));
  }

  private static CulvertRequest request(
      String type,
      Integer span,
      Integer rise,
      BigDecimal length,
      Integer pieces,
      Integer material,
      Integer install,
      String comments,
      Integer revision) {
    return new CulvertRequest(
        type, span, rise, length, pieces, material, install, comments, revision);
  }

  private static CulvertRequest validRequest(Integer revision) {
    return request("R", 1200, 900, new BigDecimal("12.5"), 3, 4000, 1500, "ok", revision);
  }

  // ===============================================================================================
  // Read + derivation (Story 13.1)
  // ===============================================================================================

  @Nested
  @DisplayName("Served document (Story 13.1)")
  class Document {

    @Test
    @DisplayName("AC2: totalCost = material + install, computed server-side")
    void totalIsMaterialPlusInstall() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(completeRound(7801)));
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(bothCosts(7801, 4000, 1500));

      Culvert culvert = service.getSchedule7b(MILL, YEAR, true).culverts().getFirst();

      assertThat(culvert.materialCost()).isEqualTo(4000);
      assertThat(culvert.installCost()).isEqualTo(1500);
      assertThat(culvert.totalCost()).isEqualTo(5500);
    }

    @Test
    @DisplayName("AC2: a single null operand is treated as absent, not as zero")
    void oneNullOperandYieldsTheOther() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(completeRound(7801)));
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(bothCosts(7801, 900, null));

      assertThat(service.getSchedule7b(MILL, YEAR, true).culverts().getFirst().totalCost())
          .isEqualTo(900);
    }

    @Test
    @DisplayName("AC2: both costs absent -> totalCost null, NEVER 0 (legacy sumBigDecimalAreas)")
    void bothNullOperandsYieldNull() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(completeRound(7801)));
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(bothCosts(7801, null, null));

      assertThat(service.getSchedule7b(MILL, YEAR, true).culverts().getFirst().totalCost())
          .isNull();
    }

    @Test
    @DisplayName("AC3: costs route by item id — 77 material, 78 install, never swapped")
    void costsRouteByItemId() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(completeRound(7801)));
      // Deliberately listed install-first so a positional bug would surface.
      when(repository.findCostDetails(MILL, YEAR))
          .thenReturn(List.of(cost(1, 7801, 78, 1500), cost(2, 7801, 77, 4000)));

      Culvert culvert = service.getSchedule7b(MILL, YEAR, true).culverts().getFirst();

      assertThat(culvert.materialCost()).isEqualTo(4000);
      assertThat(culvert.installCost()).isEqualTo(1500);
    }

    @Test
    @DisplayName("AC3: rowCounter is the 1-based index in CULVERT_REPORT_ID order")
    void rowCounterIsOneBased() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR))
          .thenReturn(List.of(completeRound(7801), completeRound(7802), completeRound(7803)));
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      assertThat(service.getSchedule7b(MILL, YEAR, true).culverts())
          .extracting(Culvert::rowCounter)
          .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("AC3: a whole length serializes at scale 1 (12 -> 12.0)")
    void lengthNormalizedToOneDecimal() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR))
          .thenReturn(List.of(row(7801, "R", 1200, 900, new BigDecimal("12"), 3, null)));
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      assertThat(service.getSchedule7b(MILL, YEAR, true).culverts().getFirst().length())
          .isEqualByComparingTo("12.0")
          .hasToString("12.0");
    }

    @Test
    @DisplayName("AC4: the Type list is read for THIS reporting year")
    void typeListIsYearScoped() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of());
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      Schedule7bResponse document = service.getSchedule7b(MILL, YEAR, true);

      assertThat(document.codeLists().culvertTypes()).isEqualTo(TYPES);
      verify(repository).culvertTypeOptions(YEAR);
    }

    @Test
    @DisplayName("AC5: editable requires BOTH EDIT_SCHEDULE and a Draft track")
    void editabilityIsTheConjunction() {
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of());
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      assertThat(service.getSchedule7b(MILL, YEAR, true).editable()).isTrue();
      assertThat(service.getSchedule7b(MILL, YEAR, false).editable()).isFalse();

      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
      assertThat(service.getSchedule7b(MILL, YEAR, true).editable()).isFalse();
    }

    @Test
    @DisplayName("AC6: an empty culvert list is a valid document, not an error")
    void emptyListIsValid() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of());
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      assertThat(service.getSchedule7b(MILL, YEAR, true).culverts()).isEmpty();
    }
  }

  // ===============================================================================================
  // Check Status — the type-conditional matrix (Story 13.2, BR-07)
  // ===============================================================================================

  @Nested
  @DisplayName("Check Status — type-conditional matrix (BR-07)")
  class CheckStatus {

    private Schedule7bCheckStatusResponse check(
        CulvertReportEntity culvert, Integer material, Integer install) {
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(culvert));
      when(repository.findCostDetails(MILL, YEAR))
          .thenReturn(bothCosts(culvert.culvertReportId(), material, install));
      return service.checkStatus(MILL, YEAR);
    }

    private List<String> texts(Schedule7bCheckStatusResponse response) {
      return response.errors().stream().map(MessageInfo::text).toList();
    }

    @Test
    @DisplayName("S01: a complete culvert passes and carries the schedule-wide all-met message")
    void completeCulvertPasses() {
      Schedule7bCheckStatusResponse response = check(completeRound(7801), 4000, 1500);

      assertThat(response.requirementsMet()).isTrue();
      assertThat(response.errors()).isEmpty();
      assertThat(response.requirementsMetMessage().text())
          .isEqualTo("All requirements for this schedule have been met");
    }

    @Test
    @DisplayName("S15: Round with no span -> flagged, verbatim label")
    void roundWithoutSpanIsFlagged() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "R", null, 900, new BigDecimal("12.5"), 3, null), 4000, 1500);

      assertThat(response.requirementsMet()).isFalse();
      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id : 1 - Culvert Type Round - Span size: Value Required");
    }

    @Test
    @DisplayName("S26: a NON-Round culvert with no span PASSES — span is conditional on type R")
    void nonRoundWithoutSpanPasses() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "PA", null, null, new BigDecimal("6.5"), 4, null), 1800, 300);

      assertThat(response.requirementsMet()).isTrue();
      assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("S16: Others with no comments -> flagged, verbatim label")
    void othersWithoutCommentsIsFlagged() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "O", null, null, new BigDecimal("8.0"), 2, null), 2500, 700);

      assertThat(response.requirementsMet()).isFalse();
      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id : 1 - Culvert Type Others - Comments: Value Required");
    }

    @Test
    @DisplayName("S16: Others with EMPTY comments is missing (legacy isNullOrEmptyString)")
    void othersWithEmptyCommentsIsFlagged() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "O", null, null, new BigDecimal("8.0"), 2, ""), 2500, 700);

      assertThat(response.requirementsMet()).isFalse();
      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id : 1 - Culvert Type Others - Comments: Value Required");
    }

    @Test
    @DisplayName("S16: Others with WHITESPACE-only comments PASSES — legacy does not trim")
    void othersWithWhitespaceCommentsPasses() {
      // legacy CoreUtil.isNullOrEmptyString(String) delegates with doTrim = false
      // (util/CoreUtil.java:166-176), so "   " is NOT empty to legacy and the culvert passes. An
      // isBlank() check here would flag a culvert legacy let through.
      Schedule7bCheckStatusResponse response =
          check(row(7801, "O", null, null, new BigDecimal("8.0"), 2, "   "), 2500, 700);

      assertThat(response.requirementsMet()).isTrue();
      assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("A NULL culvert type applies neither conditional check (recorded deviation)")
    void nullTypeAppliesNeitherConditionalCheck() {
      // Legacy called getCulvertTypeCode().equals(...) unguarded and NPE'd. Here both conditional
      // checks simply do not apply, so the culvert is judged on the four unconditional values only.
      Schedule7bCheckStatusResponse response =
          check(row(7801, null, null, null, new BigDecimal("8.0"), 2, null), 2500, 700);

      assertThat(response.requirementsMet()).isTrue();
      assertThat(texts(response))
          .noneMatch(text -> text.contains("Span size"))
          .noneMatch(text -> text.contains("Comments"));
    }

    @Test
    @DisplayName(
        "S27: a NON-Others culvert with no comments PASSES — comments conditional on type O")
    void nonOthersWithoutCommentsPasses() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "R", 1200, 900, new BigDecimal("12.5"), 3, null), 4000, 1500);

      assertThat(response.requirementsMet()).isTrue();
      assertThat(response.errors()).isEmpty();
    }

    @Test
    @DisplayName("S28: rise is NEVER checked — blank rise passes for Round AND for Others")
    void riseIsNeverChecked() {
      Schedule7bCheckStatusResponse round =
          check(row(7801, "R", 1200, null, new BigDecimal("12.5"), 3, null), 4000, 1500);
      assertThat(round.requirementsMet()).isTrue();
      assertThat(texts(round)).noneMatch(text -> text.contains("Rise"));

      Schedule7bCheckStatusResponse others =
          check(row(7802, "O", null, null, new BigDecimal("8.0"), 2, "has comments"), 2500, 700);
      assertThat(others.requirementsMet()).isTrue();
      assertThat(texts(others)).noneMatch(text -> text.contains("Rise"));
    }

    @Test
    @DisplayName("S17: length is required for EVERY type, with legacy's ' : ' spacing")
    void lengthIsUnconditional() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "PA", null, null, null, 4, null), 1800, 300);

      assertThat(texts(response)).containsExactly("Culvert Report Id: 1 - Length : Value Required");
    }

    @Test
    @DisplayName("S18: piece count is required for EVERY type")
    void pieceCountIsUnconditional() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "PA", null, null, new BigDecimal("6.5"), null, null), 1800, 300);

      assertThat(texts(response))
          .containsExactly("Culvert Report Id: 1 - Piece Count : Value Required");
    }

    @Test
    @DisplayName("S19/S20: both costs are required for EVERY type, material before install")
    void bothCostsAreUnconditional() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "PA", null, null, new BigDecimal("6.5"), 4, null), null, null);

      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id: 1 - Material Cost : Value Required",
              "Culvert Report Id: 1 - Install Cost : Value Required");
    }

    @Test
    @DisplayName("S20: a cost row present with COST NULL counts as missing, not as zero")
    void nullCostRowCountsAsMissing() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "PA", null, null, new BigDecimal("6.5"), 4, null), 900, null);

      assertThat(texts(response))
          .containsExactly("Culvert Report Id: 1 - Install Cost : Value Required");
    }

    @Test
    @DisplayName("S24: several gaps on one culvert compose in the exact legacy field order")
    void multipleGapsComposeInLegacyOrder() {
      Schedule7bCheckStatusResponse response =
          check(row(7801, "R", null, null, null, null, null), null, null);

      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id : 1 - Culvert Type Round - Span size: Value Required",
              "Culvert Report Id: 1 - Length : Value Required",
              "Culvert Report Id: 1 - Piece Count : Value Required",
              "Culvert Report Id: 1 - Material Cost : Value Required",
              "Culvert Report Id: 1 - Install Cost : Value Required");
    }

    @Test
    @DisplayName("Round AND Others gaps never appear together — a culvert has exactly one type")
    void spanAndCommentsAreMutuallyExclusiveByType() {
      Schedule7bCheckStatusResponse others =
          check(row(7801, "O", null, null, new BigDecimal("8.0"), 2, null), 2500, 700);

      assertThat(texts(others))
          .noneMatch(text -> text.contains("Span size"))
          .containsExactly(
              "Culvert Report Id : 1 - Culvert Type Others - Comments: Value Required");
    }

    @Test
    @DisplayName("Errors are grouped per culvert with each culvert's own rowCounter")
    void errorsCarryPerCulvertRowCounter() {
      when(repository.findCulverts(MILL, YEAR))
          .thenReturn(
              List.of(
                  completeRound(7801), row(7802, "R", null, null, new BigDecimal("9.0"), 1, null)));
      when(repository.findCostDetails(MILL, YEAR))
          .thenReturn(
              List.of(
                  cost(1, 7801, 77, 4000), cost(2, 7801, 78, 1500),
                  cost(3, 7802, 77, 100), cost(4, 7802, 78, 50)));

      Schedule7bCheckStatusResponse response = service.checkStatus(MILL, YEAR);

      assertThat(response.requirementsMet()).isFalse();
      assertThat(texts(response))
          .containsExactly(
              "Culvert Report Id : 2 - Culvert Type Round - Span size: Value Required");
    }

    @Test
    @DisplayName("No per-culvert all-met message exists (unlike Schedule 7A)")
    void noPerCulvertAllMetMessage() {
      Schedule7bCheckStatusResponse response = check(completeRound(7801), 4000, 1500);

      // The response shape itself carries only the schedule-wide message; this test documents that
      // absence so a future "parity with 7A" change has to argue with a failing test.
      assertThat(response.requirementsMetMessage()).isNotNull();
      assertThat(Schedule7bCheckStatusResponse.class.getRecordComponents())
          .extracting(java.lang.reflect.RecordComponent::getName)
          .containsExactly("requirementsMet", "errors", "requirementsMetMessage");
    }

    @Test
    @DisplayName("An empty schedule reports all-met and mutates nothing")
    void emptyScheduleIsAllMet() {
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of());
      when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());

      Schedule7bCheckStatusResponse response = service.checkStatus(MILL, YEAR);

      assertThat(response.requirementsMet()).isTrue();
      verify(repository, never()).updateCulvert(any(), anyLong(), anyInt(), anyInt(), anyString());
      verify(repository, never()).insertCulvert(any(), anyLong(), anyInt(), anyString());
    }
  }

  // ===============================================================================================
  // Writes (Story 13.2)
  // ===============================================================================================

  @Nested
  @DisplayName("Writes (Story 13.2)")
  class Writes {

    private void draft() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      lenient().when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of());
      lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    }

    /**
     * A Draft context in which the given culverts EXIST. The write path resolves existence from the
     * one {@code findCulverts} read that also feeds the unchanged-type exemption, so a correction
     * test has to say which ids are there — otherwise the 404 pre-check fires before anything it
     * means to exercise.
     */
    private void draftWith(long... culvertIds) {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("D"));
      lenient()
          .when(repository.findCulverts(MILL, YEAR))
          .thenReturn(
              java.util.Arrays.stream(culvertIds)
                  .mapToObj(Schedule7bServiceTest::completeRound)
                  .toList());
      lenient().when(repository.findCostDetails(MILL, YEAR)).thenReturn(List.of());
    }

    @Test
    @DisplayName("AC1: add writes the culvert then BOTH cost rows")
    void addWritesCulvertAndBothCostRows() {
      draft();
      when(repository.nextCulvertReportId()).thenReturn(9501L);

      service.addCulvert(MILL, YEAR, validRequest(null), true, USER);

      verify(repository)
          .insertCulvert(any(CulvertReportEntity.class), eq(MILL), eq(YEAR), eq(USER));
      verify(repository).upsertCost(9501L, 77, 4000, USER);
      verify(repository).upsertCost(9501L, 78, 1500, USER);
    }

    @Test
    @DisplayName("AC1: a cleared cost still writes its row, as NULL — never omits the row")
    void clearedCostStillWritesItsRow() {
      draft();
      when(repository.nextCulvertReportId()).thenReturn(9501L);

      service.addCulvert(
          MILL,
          YEAR,
          request("R", 1200, 900, new BigDecimal("12.5"), 3, null, null, null, null),
          true,
          USER);

      verify(repository).upsertCost(9501L, 77, null, USER);
      verify(repository).upsertCost(9501L, 78, null, USER);
    }

    @Test
    @DisplayName("AC5: every write is Draft-gated on the 1-10 track")
    void writesAreDraftGated() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of("S"));
      CulvertRequest added = validRequest(null);
      CulvertRequest corrected = validRequest(0);
      CulvertSaveAllRequest batch =
          new CulvertSaveAllRequest(List.of(new CulvertSaveAllRequest.Item(1L, corrected)));

      assertThatThrownBy(() -> service.addCulvert(MILL, YEAR, added, true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 1L, corrected, true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
      assertThatThrownBy(() -> service.deleteCulvert(MILL, YEAR, 1L, true))
          .isInstanceOf(ScheduleNotEditableException.class);
      // The page-level Save must be gated too — it was the one write verb the gate was never
      // asserted on, so deleting requireDraft() from saveAllCulverts left the suite green while a
      // Submitted report could be mutated wholesale.
      assertThatThrownBy(() -> service.saveAllCulverts(MILL, YEAR, batch, true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);

      verify(repository, never()).insertCulvert(any(), anyLong(), anyInt(), anyString());
      verify(repository, never()).deleteCulvert(anyLong(), anyLong(), anyInt());
      verify(repository, never()).updateCulvert(any(), anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("AC5: a missing report-status row is not Draft either")
    void absentTrackStatusIsNotDraft() {
      when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.empty());
      CulvertRequest added = validRequest(null);

      assertThatThrownBy(() -> service.addCulvert(MILL, YEAR, added, true, USER))
          .isInstanceOf(ScheduleNotEditableException.class);
    }

    @Test
    @DisplayName("AC8: a type outside the year's effective codes is rejected before any write")
    void unknownTypeIsRejected() {
      draft();
      CulvertRequest retiredType =
          request("XOLD", 1200, 900, new BigDecimal("12.5"), 3, 4000, 1500, null, null);

      assertThatThrownBy(() -> service.addCulvert(MILL, YEAR, retiredType, true, USER))
          .isInstanceOf(InvalidCulvertTypeException.class);

      verify(repository, never()).insertCulvert(any(), anyLong(), anyInt(), anyString());
    }

    @Test
    @DisplayName(
        "AC8: the type check also runs on correct and on the page-level Save, not just add")
    void unknownTypeIsRejectedOnUpdateAndSaveAll() {
      draft();
      // A culvert whose stored type is 'R', so changing it to XOLD is a genuine change.
      when(repository.findCulverts(MILL, YEAR)).thenReturn(List.of(completeRound(7801)));
      CulvertRequest bad =
          request("XOLD", 1200, 900, new BigDecimal("12.5"), 3, 4000, 1500, null, 0);
      CulvertSaveAllRequest batch =
          new CulvertSaveAllRequest(List.of(new CulvertSaveAllRequest.Item(7801L, bad)));

      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 7801L, bad, true, USER))
          .isInstanceOf(InvalidCulvertTypeException.class);
      assertThatThrownBy(() -> service.saveAllCulverts(MILL, YEAR, batch, true, USER))
          .isInstanceOf(InvalidCulvertTypeException.class);

      verify(repository, never()).updateCulvert(any(), anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("AC8: an UNCHANGED out-of-window type is exempt, so the page can still be saved")
    void unchangedOutOfWindowTypeIsExempt() {
      draft();
      // 7801 is stored with XOLD (retired). Resubmitting XOLD unchanged must NOT 400 — otherwise no
      // culvert on the page could ever be corrected. Legacy did not block the save (it silently
      // wiped
      // the type instead), so blocking here would be worse than legacy in a different way.
      when(repository.findCulverts(MILL, YEAR))
          .thenReturn(List.of(row(7801, "XOLD", 1200, 900, new BigDecimal("12.5"), 3, null)));
      when(repository.updateCulvert(any(), eq(MILL), eq(YEAR), eq(0), eq(USER))).thenReturn(1);
      CulvertRequest unchangedType =
          request("XOLD", 1300, 900, new BigDecimal("12.5"), 3, 4000, 1500, null, 0);

      service.updateCulvert(MILL, YEAR, 7801L, unchangedType, true, USER);

      verify(repository).updateCulvert(any(), eq(MILL), eq(YEAR), eq(0), eq(USER));
    }

    @Test
    @DisplayName("The length scale is normalised to 1 on write rather than rejected")
    void lengthScaleIsNormalisedOnWrite() {
      draft();
      when(repository.nextCulvertReportId()).thenReturn(9501L);

      // 12.50 is the SAME number as the accepted 12.5; a @Digits(fraction=1) constraint rejected it
      // because it reads BigDecimal.scale(). 12.55 is what legacy let NUMBER(7,1) round to 12.6.
      service.addCulvert(
          MILL,
          YEAR,
          request("R", 1200, 900, new BigDecimal("12.50"), 3, 4000, 1500, null, null),
          true,
          USER);
      service.addCulvert(
          MILL,
          YEAR,
          request("R", 1200, 900, new BigDecimal("12.55"), 3, 4000, 1500, null, null),
          true,
          USER);

      var captor = org.mockito.ArgumentCaptor.forClass(CulvertReportEntity.class);
      verify(repository, times(2))
          .insertCulvert(captor.capture(), anyLong(), anyInt(), anyString());
      assertThat(captor.getAllValues().get(0).length()).hasToString("12.5");
      assertThat(captor.getAllValues().get(1).length()).hasToString("12.6");
    }

    @Test
    @DisplayName("AC2: 0 rows updated with the id present -> stale revision (409), not 404")
    void staleRevisionIsDistinguishedFromNotFound() {
      draftWith(7801L);
      when(repository.updateCulvert(any(), eq(MILL), eq(YEAR), eq(0), eq(USER))).thenReturn(0);
      when(repository.countCulvert(7801L, MILL, YEAR)).thenReturn(1);
      CulvertRequest corrected = validRequest(0);

      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 7801L, corrected, true, USER))
          .isInstanceOf(StaleRevisionException.class);
    }

    @Test
    @DisplayName("AC2: an unknown id is 404 and no UPDATE is attempted")
    void unknownIdIsNotFound() {
      draft();
      CulvertRequest corrected = validRequest(0);

      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 7801L, corrected, true, USER))
          .isInstanceOf(CulvertNotFoundException.class);

      verify(repository, never()).updateCulvert(any(), anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("AC2: a culvert deleted concurrently still falls to the countCulvert 404 backstop")
    void concurrentDeleteIsNotFound() {
      // The pre-check reads committed state, so a row deleted by another transaction between that
      // read
      // and the UPDATE gets here with 0 rows updated and 0 rows counted. Without this the backstop
      // could be deleted and the suite would stay green.
      draftWith(7801L);
      when(repository.updateCulvert(any(), eq(MILL), eq(YEAR), eq(0), eq(USER))).thenReturn(0);
      when(repository.countCulvert(7801L, MILL, YEAR)).thenReturn(0);
      CulvertRequest corrected = validRequest(0);

      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 7801L, corrected, true, USER))
          .isInstanceOf(CulvertNotFoundException.class);
    }

    @Test
    @DisplayName("PR #266: an unknown id with an INVALID type is 404, not the type-validation 400")
    void unknownIdBeatsTypeValidationOnThePut() {
      // The status must not depend on the body. Validating the submitted type first answered 400
      // here,
      // because storedTypes.get(unknownId) is null so the unchanged-type exemption cannot apply.
      draft();
      CulvertRequest retiredType =
          request("ZZ", 1200, 900, new BigDecimal("12.5"), 3, 4000, 1500, "ok", 0);

      assertThatThrownBy(() -> service.updateCulvert(MILL, YEAR, 7801L, retiredType, true, USER))
          .isInstanceOf(CulvertNotFoundException.class);
    }

    @Test
    @DisplayName("PR #266: the same holds for every entry of a page-level Save")
    void unknownIdBeatsTypeValidationInTheBatch() {
      draftWith(7801L);
      CulvertSaveAllRequest batch =
          new CulvertSaveAllRequest(
              List.of(
                  new CulvertSaveAllRequest.Item(7801L, validRequest(0)),
                  new CulvertSaveAllRequest.Item(
                      9999L,
                      request("ZZ", 1200, 900, new BigDecimal("12.5"), 3, 4000, 1500, "ok", 0))));

      assertThatThrownBy(() -> service.saveAllCulverts(MILL, YEAR, batch, true, USER))
          .isInstanceOf(CulvertNotFoundException.class);
    }

    @Test
    @DisplayName("AC4: delete removes the cost children FIRST, then the culvert")
    void deleteCascadesCosts() {
      draft();
      when(repository.countCulvert(7801L, MILL, YEAR)).thenReturn(1);
      when(repository.deleteCulvert(7801L, MILL, YEAR)).thenReturn(1);

      service.deleteCulvert(MILL, YEAR, 7801L, true);

      // Order is the whole point: THE.ILCR_COST_REPORT_DETAIL carries ILCR_LCRD_CLV_RPT_FK on
      // CULVERT_REPORT_ID with DELETE_RULE = NO ACTION, so a parent-first delete raises ORA-02292
      // and the request 500s.
      var order = org.mockito.Mockito.inOrder(repository);
      order.verify(repository).deleteCostsForCulvert(7801L);
      order.verify(repository).deleteCulvert(7801L, MILL, YEAR);
    }

    @Test
    @DisplayName("AC4: a parent delete affecting 0 rows is a 404, not a false success")
    void deleteParentVanishedMidFlight() {
      draft();
      // The probe passes, then the row is gone by the time the delete runs (concurrent delete).
      when(repository.countCulvert(7801L, MILL, YEAR)).thenReturn(1);
      when(repository.deleteCulvert(7801L, MILL, YEAR)).thenReturn(0);

      assertThatThrownBy(() -> service.deleteCulvert(MILL, YEAR, 7801L, true))
          .isInstanceOf(CulvertNotFoundException.class);
    }

    @Test
    @DisplayName("AC4: deleting an id that is not this mill's culvert is a 404, and costs survive")
    void deleteOfForeignIdIsNotFound() {
      draft();
      // countCulvert is mill/year/category-scoped, so it still refuses a foreign id — it has to be,
      // because the cost delete keys on the culvert id alone.
      when(repository.countCulvert(7801L, MILL, YEAR)).thenReturn(0);

      assertThatThrownBy(() -> service.deleteCulvert(MILL, YEAR, 7801L, true))
          .isInstanceOf(CulvertNotFoundException.class);

      verify(repository, never()).deleteCostsForCulvert(anyLong());
      verify(repository, never()).deleteCulvert(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("AC3: a batch naming the same culvert twice is rejected before any write")
    void duplicateBatchIdsRejected() {
      draft();
      CulvertSaveAllRequest batch =
          new CulvertSaveAllRequest(
              List.of(
                  new CulvertSaveAllRequest.Item(7801L, validRequest(0)),
                  new CulvertSaveAllRequest.Item(7801L, validRequest(0))));

      assertThatThrownBy(() -> service.saveAllCulverts(MILL, YEAR, batch, true, USER))
          .isInstanceOf(DuplicateCulvertException.class);

      verify(repository, never()).updateCulvert(any(), anyLong(), anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("AC3: the batch reads the year's code table ONCE, not once per culvert")
    void batchReadsCodeTableOnce() {
      draftWith(7801L, 7802L, 7803L);
      when(repository.updateCulvert(any(), eq(MILL), eq(YEAR), eq(0), eq(USER))).thenReturn(1);
      CulvertSaveAllRequest batch =
          new CulvertSaveAllRequest(
              List.of(
                  new CulvertSaveAllRequest.Item(7801L, validRequest(0)),
                  new CulvertSaveAllRequest.Item(7802L, validRequest(0)),
                  new CulvertSaveAllRequest.Item(7803L, validRequest(0))));

      service.saveAllCulverts(MILL, YEAR, batch, true, USER);

      // Once for the batch validation + once for the echoed document = 2, not 1-per-culvert.
      verify(repository, times(2)).culvertTypeOptions(YEAR);
    }

    @Test
    @DisplayName("AC10: a persistence failure surfaces as ScheduleNotSaved (500/ERR-001)")
    void persistenceFailureBecomesScheduleNotSaved() {
      draft();
      when(repository.nextCulvertReportId()).thenReturn(9501L);
      doThrow(new DataIntegrityViolationException("boom"))
          .when(repository)
          .insertCulvert(any(), anyLong(), anyInt(), anyString());
      CulvertRequest added = validRequest(null);

      assertThatThrownBy(() -> service.addCulvert(MILL, YEAR, added, true, USER))
          .isInstanceOf(ScheduleNotSavedException.class);
    }

    @Test
    @DisplayName("A write echoes the recomputed document without re-reading the track status")
    void writeEchoReusesTheProvenDraftStatus() {
      draft();
      when(repository.nextCulvertReportId()).thenReturn(9501L);

      Schedule7bResponse echoed = service.addCulvert(MILL, YEAR, validRequest(null), true, USER);

      assertThat(echoed.trackStatus()).isEqualTo("D");
      assertThat(echoed.editable()).isTrue();
      // Exactly one status read: the Draft gate. The echo must not issue a second one.
      verify(repository, times(1)).findTrackStatus(MILL, YEAR);
    }
  }
}
