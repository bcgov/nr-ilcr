package ca.bc.gov.nrs.ilcr.schedule1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation.Schedule1Sources;
import ca.bc.gov.nrs.ilcr.support.CallerRights;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Schedule 1's original-value indicators (Story 16.2, UC-CHK-005/UC-CHK-010 BR-04): what the served
 * document says about the values the licensee originally submitted.
 *
 * <p>Mocked repositories, real {@link OriginalValues} — see {@link OriginalValuesFixture} for why
 * the gate is never stubbed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Schedule 1 — original-value indicators")
class Schedule1OriginalValuesTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final int SUMMARY_ID = 9001;

  /** Standing Tree to Loaded Truck — the field every legacy citation for BR-04 uses. */
  private static final int CODE_STANDING_TREE = 12;

  /** Subtotal Company Logging — a pulled/subtotal row: volume original only, never cost. */
  private static final int CODE_SUBTOTAL = 144;

  @Mock private Schedule1Repository repository;

  @Mock private Schedule3CostDerivation schedule3CostDerivation;

  @Mock private MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule1Service service;

  @BeforeEach
  void storedSchedule() {
    lenient()
        .when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(
            Optional.of(new SummaryRow(SUMMARY_ID, 60_000, "the ministry's corrected note", 3)));
    lenient()
        .when(repository.findDetails(SUMMARY_ID))
        .thenReturn(
            List.of(
                new DetailRow(CODE_STANDING_TREE, new BigDecimal("60002"), 7_000, null),
                new DetailRow(CODE_SUBTOTAL, new BigDecimal("60002"), 99_000, null)));
    lenient()
        .when(schedule3CostDerivation.schedule1Sources(MILL, YEAR))
        .thenReturn(new Schedule1Sources(null, null, null));
  }

  /** The submitted snapshot: mill 12050 / 2016's real shape — volume 60000, cost 600. */
  private void submittedSnapshot() {
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(
                new CostDetailSnapshotRepository.Row(
                    7101,
                    (long) SUMMARY_ID,
                    CODE_STANDING_TREE,
                    new BigDecimal("60000"),
                    600,
                    null,
                    null),
                new CostDetailSnapshotRepository.Row(
                    7102,
                    (long) SUMMARY_ID,
                    CODE_SUBTOTAL,
                    new BigDecimal("60000"),
                    98_000,
                    null,
                    null)));
    when(summarySnapshots.findBySummaryId(SUMMARY_ID))
        .thenReturn(
            Optional.of(
                new ReportSummarySnapshotRepository.Snapshot(
                    60_000, null, "what the mill actually reported")));
  }

  private Schedule1Response served(String trackStatus) {
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.of(trackStatus));
    return service.getSchedule1(MILL, YEAR, CallerRights.ADMIN);
  }

  private static LineItem item(Schedule1Response doc, int code) {
    return doc.lineItems().stream()
        .filter(li -> li.costItemCode() == code)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no line item " + code));
  }

  @Test
  @DisplayName("at Draft nothing is exposed, and the snapshot views are never even read")
  void draft_exposesNothing_andSkipsTheSnapshotReads() {
    Schedule1Response doc = served("D");

    assertThat(doc.originalValues()).isNull();
    assertThat(item(doc, CODE_STANDING_TREE).originalValues()).isNull();
    // The load-bearing half: a Draft document must not pay for reads it cannot use, and a
    // regression
    // that fetched them anyway would be invisible to an assertion on the payload alone.
    verify(costSnapshots, never()).findBySummary(anyInt());
    verify(summarySnapshots, never()).findBySummaryId(anyInt());
  }

  @ParameterizedTest(name = "at {0} the submitted figures are served")
  @ValueSource(strings = {"S", "V"})
  @DisplayName("beyond Draft each line item carries what the licensee submitted")
  void beyondDraft_servesTheSubmittedFigures(String status) {
    submittedSnapshot();

    LineItem standingTree = item(served(status), CODE_STANDING_TREE);

    assertThat(standingTree.originalValues())
        .containsOnlyKeys("volume", "cost")
        .satisfies(
            map -> {
              assertThat(map.get("volume").value()).isEqualTo("60000");
              assertThat(map.get("volume").tooltip())
                  .isEqualTo(OriginalValuesFixture.tooltip("60,000"));
              assertThat(map.get("cost").value()).isEqualTo("600");
              assertThat(map.get("cost").tooltip()).isEqualTo(OriginalValuesFixture.tooltip("600"));
            });
    // Stored 60002 vs submitted 60000, and 7000 vs 600: this is the real divergence on mill 12050 /
    // 2016 / category 1, which is what makes it a usable verification target on the delivery DB.
    assertThat(standingTree.volume()).isEqualByComparingTo("60002");
    assertThat(standingTree.cost()).isEqualTo(7_000);
  }

  @Test
  @DisplayName("document comments carry their own original")
  void comments_carryTheirOriginal() {
    submittedSnapshot();

    Schedule1Response doc = served("S");

    assertThat(doc.originalValues()).containsOnlyKeys("comments");
    assertThat(doc.originalValues().get("comments").value())
        .isEqualTo("what the mill actually reported");
    assertThat(doc.comments()).isEqualTo("the ministry's corrected note");
  }

  @Test
  @DisplayName("a pulled/subtotal row gets a volume original but never a cost one — legacy parity")
  void subtotalRow_volumeOnly() {
    // Legacy set both on the nine entered rows (Schedule1DAO.java:147-204) and volume alone on the
    // pulled and subtotal rows (:208-218), and schedule1.xhtml renders the buttons to match: a
    // volume indicator at :513-522/:543-552/:728-737 and no cost indicator anywhere near them.
    // Asymmetric in legacy, so asymmetric here — a symmetrical implementation would invent an
    // indicator the ministry has never seen.
    submittedSnapshot();

    assertThat(item(served("S"), CODE_SUBTOTAL).originalValues()).containsOnlyKeys("volume");
  }

  @Test
  @DisplayName("no snapshot row at all leaves the map empty, not null")
  void noSnapshot_isEmptyNotNull() {
    // Roughly half the live cost-detail rows are in this state. Empty-not-null is what tells the
    // page to fall to the added-since-submission branch and flag every populated field, which is
    // what legacy's isOriginalVal did with a null original beyond Draft.
    when(costSnapshots.findBySummary(SUMMARY_ID)).thenReturn(List.of());
    when(summarySnapshots.findBySummaryId(SUMMARY_ID)).thenReturn(Optional.empty());

    Schedule1Response doc = served("S");

    assertThat(doc.originalValues()).isNotNull().isEmpty();
    assertThat(item(doc, CODE_STANDING_TREE).originalValues()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("a snapshot row present but with null figures writes no keys for them")
  void snapshotWithNullFigures_writesNoKeys() {
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(
                new CostDetailSnapshotRepository.Row(
                    7101, (long) SUMMARY_ID, CODE_STANDING_TREE, null, null, null, null)));
    when(summarySnapshots.findBySummaryId(SUMMARY_ID))
        .thenReturn(Optional.of(new ReportSummarySnapshotRepository.Snapshot(null, null, null)));

    Schedule1Response doc = served("S");

    assertThat(item(doc, CODE_STANDING_TREE).originalValues()).isEmpty();
    assertThat(doc.originalValues()).isEmpty();
  }

  @Test
  @DisplayName("the shared Other-Costs volume carries its original; the derived subtotal does not")
  void otherCosts_sharedVolumeOnly() {
    // Cost item 19 repeats within one summary — the shared-volume row (no description) plus one per
    // itemized cost — so the shared volume is matched on the description being empty, exactly as
    // findSharedOtherCostsVolume does on the current side. costSubtotal and perUnit are derived
    // here and were derived in legacy, so neither has a snapshot column to expose.
    when(costSnapshots.findBySummary(SUMMARY_ID))
        .thenReturn(
            List.of(
                new CostDetailSnapshotRepository.Row(
                    7201, (long) SUMMARY_ID, 19, new BigDecimal("12345"), null, null, null),
                new CostDetailSnapshotRepository.Row(
                    7202, (long) SUMMARY_ID, 19, null, 1_200, "Aerial survey", null)));
    when(summarySnapshots.findBySummaryId(SUMMARY_ID)).thenReturn(Optional.empty());

    Schedule1Response doc = served("S");

    assertThat(doc.otherCosts().originalValues()).containsOnlyKeys("volume");
    assertThat(doc.otherCosts().originalValues().get("volume").value()).isEqualTo("12345");
  }
}
