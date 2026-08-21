package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link Schedule7aService} derivation, cost routing, editability, the
 * legacy-ordered Check Status labels, and write-time validation (Stories 12.1/12.2). Pure logic —
 * the repository and message source are mocked; the Testcontainers path is proven in the {@code
 * *IT} classes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule7aService — derivation, routing, check-status, validation")
class Schedule7aServiceTest {

  @Mock private Schedule7aRepository repository;
  @Mock private MessageSource messageSource;
  @InjectMocks private Schedule7aService service;

  private static final List<CodeDescriptionDto> CNSTRCTN =
      List.of(new CodeDescriptionDto("N", "New"), new CodeDescriptionDto("U", "Used"));
  private static final List<CodeDescriptionDto> SS =
      List.of(new CodeDescriptionDto("STL", "Steel"));
  private static final List<CodeDescriptionDto> DECK =
      List.of(new CodeDescriptionDto("WD", "Wood"));
  private static final List<CodeDescriptionDto> ABUT =
      List.of(new CodeDescriptionDto("CONC", "Concrete"));
  private static final List<CodeDescriptionDto> LOAD =
      List.of(new CodeDescriptionDto("L100", "L-100"));

  @BeforeEach
  void stubMessages() {
    lenient()
        .when(messageSource.getMessage(any(), any(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2)); // default (the key) is fine for these assertions
    lenient()
        .when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), any(), any()))
        .thenReturn("Value Required");
  }

  private void stubCodeOptions() {
    lenient().when(repository.constructionTypeOptions(anyInt())).thenReturn(CNSTRCTN);
    lenient().when(repository.superstructureTypeOptions(anyInt())).thenReturn(SS);
    lenient().when(repository.deckTypeOptions(anyInt())).thenReturn(DECK);
    lenient().when(repository.abutmentTypeOptions(anyInt())).thenReturn(ABUT);
    lenient().when(repository.loadRatingOptions(anyInt())).thenReturn(LOAD);
  }

  private static BridgeReportEntity bridge(long id, String location, LocalDate date) {
    return new BridgeReportEntity(
        id,
        location,
        date,
        50,
        new BigDecimal("5.0"),
        new BigDecimal("20.0"),
        new BigDecimal("4.0"),
        12,
        "N",
        "STL",
        "WD",
        "CONC",
        "L100",
        null,
        0);
  }

  private static BridgeCostEntity cost(long id, long bridgeId, int item, Integer value) {
    return new BridgeCostEntity(id, bridgeId, item, value);
  }

  @Test
  @DisplayName("totals: grand total includes site plan; material/deliver/install sum SS+abutment")
  void totals_computedFromLegacyArithmetic() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021))
        .thenReturn(
            List.of(
                cost(1, 7601, 70, 1000), // site plan
                cost(2, 7601, 71, 700), // approach
                cost(3, 7601, 72, 200), // after install
                cost(4, 7601, 73, 100), // other
                cost(5, 7601, 74, 3000), // abut material
                cost(6, 7601, 75, 300), // abut deliver
                cost(7, 7601, 76, 400), // abut install
                cost(8, 7601, 79, 5000), // ss material
                cost(9, 7601, 80, 500), // ss deliver
                cost(10, 7601, 81, 800) // ss install
                ));

    Schedule7aResponse doc = service.getSchedule7a(514, 2021, true);

    assertThat(doc.editable()).isTrue();
    assertThat(doc.bridges()).hasSize(1);
    Bridge b = doc.bridges().get(0);
    assertThat(b.rowCounter()).isEqualTo(1);
    assertThat(b.builtDate()).isEqualTo("2020-06");
    assertThat(b.totalMaterial()).isEqualTo(8000); // 5000 + 3000
    assertThat(b.totalDeliver()).isEqualTo(800); // 500 + 300
    assertThat(b.totalInstall()).isEqualTo(1200); // 800 + 400
    assertThat(b.grandTotal()).isEqualTo(12000); // 1000+8000+800+1200+700+200+100
  }

  @Test
  @DisplayName("totals: null-tolerant — a total with no contributing cost is null, not 0")
  void totals_nullWhenNoContributingCost() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    // Only site plan present — material/deliver/install have no operands.
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of(cost(1, 7601, 70, 1000)));

    Bridge b = service.getSchedule7a(514, 2021, true).bridges().get(0);

    assertThat(b.totalMaterial()).isNull();
    assertThat(b.totalDeliver()).isNull();
    assertThat(b.totalInstall()).isNull();
    assertThat(b.grandTotal()).isEqualTo(1000); // site plan only
  }

  @Test
  @DisplayName("totals: null-tolerant addition returns the lone present operand")
  void totals_partialNullAddition() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    // material: SS present, abutment absent → returns SS; deliver: SS absent, abutment present.
    when(repository.findCostDetails(514, 2021))
        .thenReturn(
            List.of(
                cost(1, 7601, 79, 5000), // ss material only  → add(5000, null) → 5000
                cost(2, 7601, 75, 300))); // abut deliver only → add(null, 300)  → 300

    Bridge b = service.getSchedule7a(514, 2021, true).bridges().get(0);

    assertThat(b.totalMaterial()).isEqualTo(5000);
    assertThat(b.totalDeliver()).isEqualTo(300);
    assertThat(b.totalInstall()).isNull();
  }

  @Test
  @DisplayName("read tolerates a null built date and null measurements")
  void read_nullDateAndMeasurements() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021))
        .thenReturn(
            List.of(
                new BridgeReportEntity(
                    7601,
                    "North Fork",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "N",
                    "STL",
                    "WD",
                    "CONC",
                    "L100",
                    null,
                    0)));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    Bridge b = service.getSchedule7a(514, 2021, true).bridges().get(0);

    assertThat(b.builtDate()).isNull();
    assertThat(b.abutmentHeight()).isNull();
    assertThat(b.length()).isNull();
    assertThat(b.width()).isNull();
    assertThat(b.grandTotal()).isNull();
  }

  @Test
  @DisplayName("editable false when caller cannot edit, or the 1-10 track is not Draft")
  void editable_requiresEditAndDraft() {
    stubCodeOptions();
    when(repository.findBridges(anyLong(), anyInt())).thenReturn(List.of());
    when(repository.findCostDetails(anyLong(), anyInt())).thenReturn(List.of());

    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    assertThat(service.getSchedule7a(514, 2021, false).editable()).isFalse(); // no EDIT_SCHEDULE

    when(repository.findTrackStatus(517, 2021)).thenReturn(Optional.of("S"));
    assertThat(service.getSchedule7a(517, 2021, true).editable()).isFalse(); // not Draft
    assertThat(service.getSchedule7a(517, 2021, true).trackStatus()).isEqualTo("S");
  }

  @Test
  @DisplayName("rowCounter is the 1-based list index across bridges")
  void rowCounter_isOneBasedIndex() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021))
        .thenReturn(
            List.of(
                bridge(7601, "A", LocalDate.of(2020, 1, 1)),
                bridge(7602, "B", LocalDate.of(2020, 2, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    List<Bridge> bridges = service.getSchedule7a(514, 2021, true).bridges();
    assertThat(bridges).extracting(Bridge::rowCounter).containsExactly(1, 2);
  }

  @Test
  @DisplayName(
      "check-status: flags each missing required value per bridge in legacy label order (S29)")
  void checkStatus_flagsMissingCostsPerBridge() {
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7603, "Old Mill", LocalDate.of(2018, 3, 1))));
    // All required attributes/measurements present; missing afterInstall(72) and other(73) costs.
    when(repository.findCostDetails(514, 2021))
        .thenReturn(
            List.of(
                cost(1, 7603, 70, 500),
                cost(2, 7603, 71, 100),
                cost(3, 7603, 74, 800),
                cost(4, 7603, 75, 80),
                cost(5, 7603, 76, 120),
                cost(6, 7603, 79, 3000),
                cost(7, 7603, 80, 300),
                cost(8, 7603, 81, 500)));

    Schedule7aCheckStatusResponse result = service.checkStatus(514, 2021);

    assertThat(result.requirementsMet()).isFalse();
    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors())
        .allSatisfy(
            m -> {
              assertThat(m.key()).isEqualTo("missingRequiredFieldMsg");
              assertThat(m.text()).startsWith("Bridge Report Id : 1");
              assertThat(m.text()).endsWith("Value Required");
            });
    // The full verbatim line, separator included: FacesUtil.addCheckStatusErrorMessage appended
    // ": " between the label and the bundle text for every schedule (util/FacesUtil.java:134), and
    // the label itself ends in a space — so the legacy line reads "... Cost : Value Required".
    assertThat(result.errors().get(0).text())
        .isEqualTo("Bridge Report Id : 1 - Certification After install Cost : Value Required");
    assertThat(result.errors().get(1).text())
        .isEqualTo("Bridge Report Id : 1 - Other Costs : Value Required");
    assertThat(result.bridgeMessages()).isEmpty();
    assertThat(result.requirementsMetMessage()).isNull();
  }

  @Test
  @DisplayName(
      "check-status: every bridge complete → the schedule-wide line ALONE, no per-bridge lines")
  void checkStatus_allMet() {
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021))
        .thenReturn(
            List.of(
                cost(1, 7601, 70, 1),
                cost(2, 7601, 71, 1),
                cost(3, 7601, 72, 1),
                cost(4, 7601, 73, 1),
                cost(5, 7601, 74, 1),
                cost(6, 7601, 75, 1),
                cost(7, 7601, 76, 1),
                cost(8, 7601, 79, 1),
                cost(9, 7601, 80, 1),
                cost(10, 7601, 81, 1)));

    Schedule7aCheckStatusResponse result = service.checkStatus(514, 2021);

    assertThat(result.requirementsMet()).isTrue();
    assertThat(result.errors()).isEmpty();
    // Legacy ran its per-bridge loop only when the SCHEDULE failed (Schedule7aMB.java:197-296), so
    // a
    // fully complete schedule showed one success line, not one per bridge plus a schedule-wide one.
    assertThat(result.bridgeMessages()).isEmpty();
    assertThat(result.requirementsMetMessage()).isNotNull();
    assertThat(result.requirementsMetMessage().key()).isEqualTo("scheduleRequirementsMetMsg");
  }

  @Test
  @DisplayName(
      "check-status: a MIXED schedule flags the failing bridge and all-mets the passing one")
  void checkStatus_mixed() {
    when(repository.findBridges(514, 2021))
        .thenReturn(
            List.of(
                bridge(7601, "North Fork", LocalDate.of(2020, 6, 1)),
                bridge(7602, "South Fork", LocalDate.of(2020, 7, 1))));
    // Bridge 1 complete; bridge 2 missing every cost.
    when(repository.findCostDetails(514, 2021))
        .thenReturn(
            List.of(
                cost(1, 7601, 70, 1),
                cost(2, 7601, 71, 1),
                cost(3, 7601, 72, 1),
                cost(4, 7601, 73, 1),
                cost(5, 7601, 74, 1),
                cost(6, 7601, 75, 1),
                cost(7, 7601, 76, 1),
                cost(8, 7601, 79, 1),
                cost(9, 7601, 80, 1),
                cost(10, 7601, 81, 1)));

    Schedule7aCheckStatusResponse result = service.checkStatus(514, 2021);

    assertThat(result.requirementsMet()).isFalse();
    assertThat(result.errors()).isNotEmpty();
    assertThat(result.errors())
        .allSatisfy(m -> assertThat(m.text()).startsWith("Bridge Report Id : 2"));
    assertThat(result.bridgeMessages()).hasSize(1);
    assertThat(result.bridgeMessages().get(0).key()).isEqualTo("bridgeRequirementsMetMsg");
    // No schedule-wide line on a mixed result.
    assertThat(result.requirementsMetMessage()).isNull();
  }

  @Test
  @DisplayName("add rejects a write outside Draft (409)")
  void add_rejectedOutsideDraft() {
    when(repository.findTrackStatus(517, 2021)).thenReturn(Optional.of("S"));
    BridgeRequest request = validRequest(null);
    assertThatThrownBy(() -> service.addBridge(517, 2021, request, true, "user"))
        .isInstanceOf(ScheduleNotEditableException.class);
  }

  @Test
  @DisplayName("add rejects a malformed yyyy-MM date (400)")
  void add_rejectsBadDate() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    BridgeRequest bad =
        new BridgeRequest(
            "Loc",
            "2020-13",
            "N",
            "STL",
            "WD",
            "CONC",
            "L100",
            50,
            new BigDecimal("5.0"),
            new BigDecimal("20.0"),
            new BigDecimal("4.0"),
            12,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> service.addBridge(514, 2021, bad, true, "user"))
        .isInstanceOf(BridgeDateFormatException.class);
  }

  @Test
  @DisplayName("add rejects an unknown code value (400)")
  void add_rejectsUnknownCode() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    BridgeRequest bad =
        new BridgeRequest(
            "North Fork",
            "2020-06",
            "ZZZ",
            "STL",
            "WD",
            "CONC",
            "L100",
            50,
            new BigDecimal("5.0"),
            new BigDecimal("20.0"),
            new BigDecimal("4.0"),
            12,
            1000,
            5000,
            500,
            800,
            3000,
            300,
            400,
            700,
            200,
            100,
            null,
            null);
    assertThatThrownBy(() -> service.addBridge(514, 2021, bad, true, "user"))
        .isInstanceOf(InvalidBridgeCodeException.class);
  }

  @Test
  @DisplayName("update disambiguates a stale revision (409) from an unknown id (404)")
  void update_staleVsNotFound() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
    BridgeRequest request = validRequest(0);

    when(repository.countBridge(7601, 514, 2021)).thenReturn(1); // exists → stale
    assertThatThrownBy(() -> service.updateBridge(514, 2021, 7601, request, true, "user"))
        .isInstanceOf(StaleRevisionException.class);

    when(repository.countBridge(9999, 514, 2021)).thenReturn(0); // absent → 404
    assertThatThrownBy(() -> service.updateBridge(514, 2021, 9999, request, true, "user"))
        .isInstanceOf(BridgeNotFoundException.class);
  }

  @Test
  @DisplayName("add writes ALL TEN cost rows, a null cost as a NULL row (legacy storage shape)")
  void add_writesAllTenCostRowsIncludingNulls() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.nextBridgeReportId()).thenReturn(7601L);
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());
    // Only site plan is entered; the other nine are null.
    BridgeRequest request =
        new BridgeRequest(
            "North Fork",
            "2020-06",
            "N",
            "STL",
            "WD",
            "CONC",
            "L100",
            50,
            new BigDecimal("5.0"),
            new BigDecimal("20.0"),
            new BigDecimal("4.0"),
            12,
            1000,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    Schedule7aResponse doc = service.addBridge(514, 2021, request, true, "user");

    assertThat(doc.bridges()).hasSize(1);
    verify(repository).insertBridge(any(BridgeReportEntity.class), eq(514L), eq(2021), eq("user"));
    // TEN rows, not one. The legacy app shares this database and its update path can only modify
    // rows that already exist, so a missing row is a cost it could never edit again.
    verify(repository, times(10)).upsertCost(eq(7601L), anyInt(), any(), eq("user"));
    verify(repository).upsertCost(7601L, 70, 1000, "user");
    verify(repository).upsertCost(7601L, 73, null, "user");
  }

  @Test
  @DisplayName("add rolls back and surfaces ERR-004 when the persistence layer fails (500)")
  void add_persistenceFailure() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.nextBridgeReportId()).thenReturn(7601L);
    doThrow(new DataIntegrityViolationException("insert failed"))
        .when(repository)
        .insertBridge(any(), anyLong(), anyInt(), any());
    BridgeRequest request = validRequest(null);

    assertThatThrownBy(() -> service.addBridge(514, 2021, request, true, "user"))
        .isInstanceOf(ScheduleNotSavedException.class);
  }

  @Test
  @DisplayName("update writes the correction, upserts its costs, and recomputes")
  void update_persistsAndRecomputes() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
    when(repository.findBridges(514, 2021))
        .thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());
    BridgeRequest request = validRequest(0);

    Schedule7aResponse doc = service.updateBridge(514, 2021, 7601, request, true, "user");

    assertThat(doc.bridges()).hasSize(1);
    verify(repository, times(10)).upsertCost(eq(7601L), anyInt(), anyInt(), eq("user"));
  }

  @Test
  @DisplayName("update rolls back and surfaces ERR-004 when the persistence layer fails (500)")
  void update_persistenceFailure() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    doThrow(new DataIntegrityViolationException("update failed"))
        .when(repository)
        .updateBridge(any(), anyLong(), anyInt(), anyInt(), any());
    BridgeRequest request = validRequest(0);

    assertThatThrownBy(() -> service.updateBridge(514, 2021, 7601, request, true, "user"))
        .isInstanceOf(ScheduleNotSavedException.class);
  }

  @Test
  @DisplayName("delete removes the cost children BEFORE the bridge, then recomputes (S04)")
  void delete_removesBridgeAndCosts() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.countBridge(7601, 514, 2021)).thenReturn(1);
    when(repository.deleteBridge(7601, 514, 2021)).thenReturn(1);
    when(repository.findBridges(514, 2021)).thenReturn(List.of());
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    Schedule7aResponse doc = service.deleteBridge(514, 2021, 7601, true);

    assertThat(doc.bridges()).isEmpty();
    // Order is the whole point: delivery's FK on ILCR_COST_REPORT_DETAIL.BRIDGE_REPORT_ID has no ON
    // DELETE CASCADE, so a parent-first delete raises ORA-02292 and the request 500s. Legacy
    // deleted
    // the children first for the same reason (Schedule7aDAO:566-570).
    var order = inOrder(repository);
    order.verify(repository).deleteCostsForBridge(7601);
    order.verify(repository).deleteBridge(7601, 514, 2021);
  }

  @Test
  @DisplayName("delete: a parent delete that affects 0 rows is a 404, not a false success (S04)")
  void delete_parentVanishedMidFlight() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    // The probe passes, then the row is gone by the time the delete runs (a concurrent delete won).
    when(repository.countBridge(7601, 514, 2021)).thenReturn(1);
    when(repository.deleteBridge(7601, 514, 2021)).thenReturn(0);

    // Acting on the row count is what makes this a 404 rather than a 200 "Data deleted
    // successfully"
    // over a bridge that is still on screen with its costs stripped (the cost delete having
    // committed).
    assertThatThrownBy(() -> service.deleteBridge(514, 2021, 7601, true))
        .isInstanceOf(BridgeNotFoundException.class);
  }

  @Test
  @DisplayName("delete of an unknown id → 404 and never touches either delete")
  void delete_unknownId() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.countBridge(9999, 514, 2021)).thenReturn(0);

    assertThatThrownBy(() -> service.deleteBridge(514, 2021, 9999, true))
        .isInstanceOf(BridgeNotFoundException.class);
    verify(repository, never()).deleteCostsForBridge(anyLong());
    verify(repository, never()).deleteBridge(anyLong(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("delete of another mill's bridge id → 404, never removing that mill's rows")
  void delete_otherMillsBridge() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    // The id exists, but not under this mill/year — countBridge is mill/year/category-scoped, so
    // the
    // check still refuses. (It has to be scoped: the cost delete keys on the bridge id alone.)
    when(repository.countBridge(7601, 514, 2021)).thenReturn(0);

    assertThatThrownBy(() -> service.deleteBridge(514, 2021, 7601, true))
        .isInstanceOf(BridgeNotFoundException.class);
    verify(repository, never()).deleteCostsForBridge(anyLong());
  }

  @Test
  @DisplayName("delete rolls back and surfaces ERR-004 when the persistence layer fails (500)")
  void delete_persistenceFailure() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.countBridge(7601, 514, 2021)).thenReturn(1);
    doThrow(new DataIntegrityViolationException("delete failed"))
        .when(repository)
        .deleteCostsForBridge(7601);

    assertThatThrownBy(() -> service.deleteBridge(514, 2021, 7601, true))
        .isInstanceOf(ScheduleNotSavedException.class);
  }

  @Test
  @DisplayName("delete outside Draft is rejected before any repository write (409)")
  void delete_rejectedOutsideDraft() {
    when(repository.findTrackStatus(517, 2021)).thenReturn(Optional.of("S"));

    assertThatThrownBy(() -> service.deleteBridge(517, 2021, 7601, true))
        .isInstanceOf(ScheduleNotEditableException.class);
    verify(repository, never()).deleteBridge(anyLong(), anyLong(), anyInt());
  }

  @Test
  @DisplayName("code lists are scoped to the REPORTING YEAR, not served unfiltered")
  void codeLists_scopedToReportingYear() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2019)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2019)).thenReturn(List.of());
    when(repository.findCostDetails(514, 2019)).thenReturn(List.of());

    service.getSchedule7a(514, 2019, true);

    // Legacy filtered every list through LookupCache.getCacheList(year), so a code retired before
    // the reporting year was never offered. Passing the year is what carries that filter.
    verify(repository).constructionTypeOptions(2019);
    verify(repository).superstructureTypeOptions(2019);
    verify(repository).deckTypeOptions(2019);
    verify(repository).abutmentTypeOptions(2019);
    verify(repository).loadRatingOptions(2019);
  }

  @Test
  @DisplayName("a write validates its codes against the reporting year's effective list")
  void writeValidatesCodesForTheReportingYear() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2019)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
    when(repository.findBridges(514, 2019)).thenReturn(List.of());
    when(repository.findCostDetails(514, 2019)).thenReturn(List.of());

    service.updateBridge(514, 2019, 7601, validRequest(0), true, "user");

    verify(repository, times(2)).constructionTypeOptions(2019); // validation + served document
  }

  @Test
  @DisplayName("save-all writes every bridge in one call and recomputes once (legacy page Save)")
  void saveAll_persistsEveryBridge() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
    when(repository.findBridges(514, 2021))
        .thenReturn(
            List.of(
                bridge(7601, "North Fork", LocalDate.of(2020, 6, 1)),
                bridge(7602, "South Creek", LocalDate.of(2021, 3, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    Schedule7aResponse doc = service.saveAllBridges(514, 2021, saveAll(7601L, 7602L), true, "user");

    assertThat(doc.bridges()).hasSize(2);
    // Two bridge writes, and each bridge carries its OWN ten cost upserts — not one row written
    // twice, which the per-id upsert counts below are what actually distinguish.
    verify(repository, times(2)).updateBridge(any(), eq(514L), eq(2021), eq(0), eq("user"));
    verify(repository, times(10)).upsertCost(eq(7601L), anyInt(), anyInt(), eq("user"));
    verify(repository, times(10)).upsertCost(eq(7602L), anyInt(), anyInt(), eq("user"));
    // The Draft gate is read once for the batch, not once per bridge.
    verify(repository).findTrackStatus(514, 2021);
  }

  @Test
  @DisplayName("save-all rejects a duplicate bridge id with 400, not a misleading 409")
  void saveAll_duplicateIdIsBadRequest() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    BridgeSaveAllRequest request = saveAll(7601L, 7601L);

    // Left to run, the second pass would meet the revision its own first pass bumped and 409 —
    // telling the caller another user changed the row when the request was simply malformed.
    assertThatThrownBy(() -> service.saveAllBridges(514, 2021, request, true, "user"))
        .isInstanceOf(DuplicateBridgeException.class);
    verify(repository, never()).updateBridge(any(), anyLong(), anyInt(), anyInt(), any());
  }

  @Test
  @DisplayName("save-all reads each code table ONCE for the batch, not once per bridge")
  void saveAll_readsCodeTablesOncePerBatch() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(1);
    when(repository.findBridges(514, 2021)).thenReturn(List.of());
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    service.saveAllBridges(514, 2021, saveAll(7601L, 7602L, 7603L), true, "user");

    // Three bridges: once for the batch's validation + once for the echoed document. Per-bridge
    // lookups would make this 4 for three rows, and 5N inside one transaction at scale.
    verify(repository, times(2)).constructionTypeOptions(2021);
  }

  @Test
  @DisplayName("save-all is rejected outside Draft before any bridge is written")
  void saveAll_rejectedOutsideDraft() {
    when(repository.findTrackStatus(517, 2021)).thenReturn(Optional.of("S"));
    BridgeSaveAllRequest request = saveAll(7601L);

    assertThatThrownBy(() -> service.saveAllBridges(517, 2021, request, true, "user"))
        .isInstanceOf(ScheduleNotEditableException.class);
    verify(repository, never()).updateBridge(any(), anyLong(), anyInt(), anyInt(), any());
  }

  @Test
  @DisplayName("save-all propagates a stale revision on ANY entry, so the batch rolls back whole")
  void saveAll_staleEntryAbortsTheBatch() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    // First bridge writes, second is stale — the exception must escape so @Transactional rolls the
    // first one back too. A partial save would leave the reporter unable to tell what persisted.
    when(repository.updateBridge(any(), eq(514L), eq(2021), anyInt(), any()))
        .thenReturn(1)
        .thenReturn(0);
    when(repository.countBridge(7602, 514, 2021)).thenReturn(1);
    BridgeSaveAllRequest request = saveAll(7601L, 7602L);

    assertThatThrownBy(() -> service.saveAllBridges(514, 2021, request, true, "user"))
        .isInstanceOf(StaleRevisionException.class);
    verify(repository, never()).findBridges(anyLong(), anyInt());
  }

  @Test
  @DisplayName("save-all rejects an unknown bridge id with 404")
  void saveAll_unknownIdIsNotFound() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(any(), anyLong(), anyInt(), anyInt(), any())).thenReturn(0);
    when(repository.countBridge(9999, 514, 2021)).thenReturn(0);
    BridgeSaveAllRequest request = saveAll(9999L);

    assertThatThrownBy(() -> service.saveAllBridges(514, 2021, request, true, "user"))
        .isInstanceOf(BridgeNotFoundException.class);
  }

  @Test
  @DisplayName("save-all surfaces ERR-004 when the persistence layer fails")
  void saveAll_persistenceFailure() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    doThrow(new DataIntegrityViolationException("update failed"))
        .when(repository)
        .updateBridge(any(), anyLong(), anyInt(), anyInt(), any());
    BridgeSaveAllRequest request = saveAll(7601L);

    assertThatThrownBy(() -> service.saveAllBridges(514, 2021, request, true, "user"))
        .isInstanceOf(ScheduleNotSavedException.class);
  }

  /** A save-all body carrying one valid entry per supplied bridge id, each at revision 0. */
  private static BridgeSaveAllRequest saveAll(Long... bridgeIds) {
    return new BridgeSaveAllRequest(
        java.util.Arrays.stream(bridgeIds)
            .map(id -> new BridgeSaveAllRequest.Item(id, validRequest(0)))
            .toList());
  }

  private static BridgeRequest validRequest(Integer revisionCount) {
    return new BridgeRequest(
        "North Fork",
        "2020-06",
        "N",
        "STL",
        "WD",
        "CONC",
        "L100",
        50,
        new BigDecimal("5.0"),
        new BigDecimal("20.0"),
        new BigDecimal("4.0"),
        12,
        1000,
        5000,
        500,
        800,
        3000,
        300,
        400,
        700,
        200,
        100,
        null,
        revisionCount);
  }
}
