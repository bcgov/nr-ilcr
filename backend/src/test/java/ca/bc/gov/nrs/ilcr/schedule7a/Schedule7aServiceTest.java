package ca.bc.gov.nrs.ilcr.schedule7a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
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

/**
 * Unit tests for {@link Schedule7aService} derivation, cost routing, editability, the legacy-ordered
 * Check Status labels, and write-time validation (Stories 12.1/12.2). Pure logic — the repository
 * and message source are mocked; the Testcontainers path is proven in the {@code *IT} classes.
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
    lenient().when(messageSource.getMessage(any(), any(), any(), any()))
        .thenAnswer(inv -> inv.getArgument(2)); // default (the key) is fine for these assertions
    lenient().when(messageSource.getMessage(eq("missingRequiredFieldMsg"), any(), any(), any()))
        .thenReturn("Value Required");
  }

  private void stubCodeOptions() {
    lenient().when(repository.constructionTypeOptions()).thenReturn(CNSTRCTN);
    lenient().when(repository.superstructureTypeOptions()).thenReturn(SS);
    lenient().when(repository.deckTypeOptions()).thenReturn(DECK);
    lenient().when(repository.abutmentTypeOptions()).thenReturn(ABUT);
    lenient().when(repository.loadRatingOptions()).thenReturn(LOAD);
  }

  private static BridgeReportEntity bridge(long id, String location, LocalDate date) {
    return new BridgeReportEntity(
        id, location, date, 50, new BigDecimal("5.0"), new BigDecimal("20.0"),
        new BigDecimal("4.0"), 12, "N", "STL", "WD", "CONC", "L100", null, 0);
  }

  private static BridgeCostEntity cost(long id, long bridgeId, int item, Integer value) {
    return new BridgeCostEntity(id, bridgeId, item, value);
  }

  @Test
  @DisplayName("totals: grand total includes site plan; material/deliver/install sum SS+abutment")
  void totals_computedFromLegacyArithmetic() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021)).thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of(
        cost(1, 7601, 70, 1000), // site plan
        cost(2, 7601, 71, 700),  // approach
        cost(3, 7601, 72, 200),  // after install
        cost(4, 7601, 73, 100),  // other
        cost(5, 7601, 74, 3000), // abut material
        cost(6, 7601, 75, 300),  // abut deliver
        cost(7, 7601, 76, 400),  // abut install
        cost(8, 7601, 79, 5000), // ss material
        cost(9, 7601, 80, 500),  // ss deliver
        cost(10, 7601, 81, 800)  // ss install
    ));

    Schedule7aResponse doc = service.getSchedule7a(514, 2021, true);

    assertThat(doc.editable()).isTrue();
    assertThat(doc.bridges()).hasSize(1);
    Bridge b = doc.bridges().get(0);
    assertThat(b.rowCounter()).isEqualTo(1);
    assertThat(b.builtDate()).isEqualTo("2020-06");
    assertThat(b.totalMaterial()).isEqualTo(8000);   // 5000 + 3000
    assertThat(b.totalDeliver()).isEqualTo(800);      // 500 + 300
    assertThat(b.totalInstall()).isEqualTo(1200);     // 800 + 400
    assertThat(b.grandTotal()).isEqualTo(12000);      // 1000+8000+800+1200+700+200+100
  }

  @Test
  @DisplayName("totals: null-tolerant — a total with no contributing cost is null, not 0")
  void totals_nullWhenNoContributingCost() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021)).thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    // Only site plan present — material/deliver/install have no operands.
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of(cost(1, 7601, 70, 1000)));

    Bridge b = service.getSchedule7a(514, 2021, true).bridges().get(0);

    assertThat(b.totalMaterial()).isNull();
    assertThat(b.totalDeliver()).isNull();
    assertThat(b.totalInstall()).isNull();
    assertThat(b.grandTotal()).isEqualTo(1000); // site plan only
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
    assertThat(service.getSchedule7a(517, 2021, true).editable()).isFalse();  // not Draft
    assertThat(service.getSchedule7a(517, 2021, true).trackStatus()).isEqualTo("S");
  }

  @Test
  @DisplayName("rowCounter is the 1-based list index across bridges")
  void rowCounter_isOneBasedIndex() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.findBridges(514, 2021)).thenReturn(List.of(
        bridge(7601, "A", LocalDate.of(2020, 1, 1)),
        bridge(7602, "B", LocalDate.of(2020, 2, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of());

    List<Bridge> bridges = service.getSchedule7a(514, 2021, true).bridges();
    assertThat(bridges).extracting(Bridge::rowCounter).containsExactly(1, 2);
  }

  @Test
  @DisplayName("check-status: flags each missing required value per bridge in legacy label order (S29)")
  void checkStatus_flagsMissingCostsPerBridge() {
    when(repository.findBridges(514, 2021)).thenReturn(List.of(bridge(7603, "Old Mill", LocalDate.of(2018, 3, 1))));
    // All required attributes/measurements present; missing afterInstall(72) and other(73) costs.
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of(
        cost(1, 7603, 70, 500), cost(2, 7603, 71, 100),
        cost(3, 7603, 74, 800), cost(4, 7603, 75, 80), cost(5, 7603, 76, 120),
        cost(6, 7603, 79, 3000), cost(7, 7603, 80, 300), cost(8, 7603, 81, 500)));

    Schedule7aCheckStatusResponse result = service.checkStatus(514, 2021);

    assertThat(result.requirementsMet()).isFalse();
    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors()).allSatisfy(m -> {
      assertThat(m.key()).isEqualTo("missingRequiredFieldMsg");
      assertThat(m.text()).startsWith("Bridge Report Id : 1");
      assertThat(m.text()).endsWith("Value Required");
    });
    assertThat(result.errors().get(0).text()).contains(" - Certification After install Cost ");
    assertThat(result.errors().get(1).text()).contains(" - Other Costs ");
    assertThat(result.bridgeMessages()).isEmpty();
    assertThat(result.requirementsMetMessage()).isNull();
  }

  @Test
  @DisplayName("check-status: complete bridge → all-met per bridge and schedule-wide")
  void checkStatus_allMet() {
    when(repository.findBridges(514, 2021)).thenReturn(List.of(bridge(7601, "North Fork", LocalDate.of(2020, 6, 1))));
    when(repository.findCostDetails(514, 2021)).thenReturn(List.of(
        cost(1, 7601, 70, 1), cost(2, 7601, 71, 1), cost(3, 7601, 72, 1), cost(4, 7601, 73, 1),
        cost(5, 7601, 74, 1), cost(6, 7601, 75, 1), cost(7, 7601, 76, 1),
        cost(8, 7601, 79, 1), cost(9, 7601, 80, 1), cost(10, 7601, 81, 1)));

    Schedule7aCheckStatusResponse result = service.checkStatus(514, 2021);

    assertThat(result.requirementsMet()).isTrue();
    assertThat(result.errors()).isEmpty();
    assertThat(result.bridgeMessages()).hasSize(1);
    assertThat(result.bridgeMessages().get(0).key()).isEqualTo("bridgeRequirementsMetMsg");
    assertThat(result.requirementsMetMessage()).isNotNull();
    assertThat(result.requirementsMetMessage().key()).isEqualTo("scheduleRequirementsMetMsg");
  }

  @Test
  @DisplayName("add rejects a write outside Draft (409)")
  void add_rejectedOutsideDraft() {
    when(repository.findTrackStatus(517, 2021)).thenReturn(Optional.of("S"));
    assertThatThrownBy(() -> service.addBridge(517, 2021, validRequest(null), true, "user"))
        .isInstanceOf(ScheduleNotEditableException.class);
  }

  @Test
  @DisplayName("add rejects a malformed yyyy-MM date (400)")
  void add_rejectsBadDate() {
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    BridgeRequest bad = new BridgeRequest(
        "Loc", "2020-13", "N", "STL", "WD", "CONC", "L100", 50,
        new BigDecimal("5.0"), new BigDecimal("20.0"), new BigDecimal("4.0"), 12,
        null, null, null, null, null, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> service.addBridge(514, 2021, bad, true, "user"))
        .isInstanceOf(BridgeDateFormatException.class);
  }

  @Test
  @DisplayName("add rejects an unknown code value (400)")
  void add_rejectsUnknownCode() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    BridgeRequest bad = new BridgeRequest(
        "North Fork", "2020-06", "ZZZ", "STL", "WD", "CONC", "L100", 50,
        new BigDecimal("5.0"), new BigDecimal("20.0"), new BigDecimal("4.0"), 12,
        1000, 5000, 500, 800, 3000, 300, 400, 700, 200, 100, null, null);
    assertThatThrownBy(() -> service.addBridge(514, 2021, bad, true, "user"))
        .isInstanceOf(InvalidBridgeCodeException.class);
  }

  @Test
  @DisplayName("update disambiguates a stale revision (409) from an unknown id (404)")
  void update_staleVsNotFound() {
    stubCodeOptions();
    when(repository.findTrackStatus(514, 2021)).thenReturn(Optional.of("D"));
    when(repository.updateBridge(anyLong(), anyLong(), anyInt(), anyInt(), any(), any(), any(),
        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

    when(repository.countBridge(7601, 514, 2021)).thenReturn(1); // exists → stale
    assertThatThrownBy(() -> service.updateBridge(514, 2021, 7601, validRequest(0), true, "user"))
        .isInstanceOf(StaleRevisionException.class);

    when(repository.countBridge(9999, 514, 2021)).thenReturn(0); // absent → 404
    assertThatThrownBy(() -> service.updateBridge(514, 2021, 9999, validRequest(0), true, "user"))
        .isInstanceOf(BridgeNotFoundException.class);
  }

  private static BridgeRequest validRequest(Integer revisionCount) {
    return new BridgeRequest(
        "North Fork", "2020-06", "N", "STL", "WD", "CONC", "L100", 50,
        new BigDecimal("5.0"), new BigDecimal("20.0"), new BigDecimal("4.0"), 12,
        1000, 5000, 500, 800, 3000, 300, 400, 700, 200, 100, null, revisionCount);
  }
}
