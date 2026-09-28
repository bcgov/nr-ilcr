package ca.bc.gov.nrs.ilcr.schedule1;

import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.originalvalue.CostDetailSnapshotRepository;
import ca.bc.gov.nrs.ilcr.originalvalue.OriginalValues;
import ca.bc.gov.nrs.ilcr.originalvalue.ReportSummarySnapshotRepository;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckRequest.LineEntry;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.support.OriginalValuesFixture;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for BR-07 Check Status logic (Story 2.6). Mocked repository + MessageSource (returns
 * the bundle key so assertions test structure/keys/order; verbatim text is asserted by the IT). No
 * DB.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1CheckStatusServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final int SUMMARY = 1001;

  // Codes that require volume+cost (12-18, silv 1&2) and volume-only (143,144,139,140).
  private static final List<Integer> VOL_COST = List.of(12, 13, 14, 15, 16, 17, 18, 1, 2);
  private static final List<Integer> VOL_ONLY = List.of(143, 144, 139, 140);

  @Mock private Schedule1Repository repository;

  @Mock private MessageSource messageSource;

  @Mock private CostDetailSnapshotRepository costSnapshots;

  @Mock private ReportSummarySnapshotRepository summarySnapshots;

  // The real gate, not a stub: its whole substance is "not Draft", and a mock would make every
  // original-value assertion below an assertion about the mock (Story 16.2).
  @Spy private OriginalValues originalValues = OriginalValuesFixture.real();

  @InjectMocks private Schedule1Service service;

  @BeforeEach
  void stubMessages() {
    lenient()
        .when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
        .thenAnswer(i -> i.getArgument(0)); // return the key
  }

  /**
   * Defect #296: Check Status no longer 404s on a mill/year with no summary — it reports every
   * mandatory field missing, which is the honest answer and what Schedule 2 already did.
   *
   * <p>Without this test the three new null-guards on the absent-summary path ({@code findDetails}
   * / {@code findSharedOtherCostsVolume} / {@code findOtherCostRows}) are never entered, so an NPE
   * there would ship green (#296 code review).
   */
  @Test
  void checkStatus_noSummary_reportsMissingFields_ratherThanThrowing() {
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());

    Schedule1CheckStatusResponse res = service.checkStatusStored(MILL, YEAR);

    assertFalse(res.requirementsMet(), "a schedule with nothing entered cannot be complete");
    assertFalse(res.errors().isEmpty(), "every mandatory field must be reported missing");
    // No summary id exists, so none of the detail reads may be attempted.
    verify(repository, never()).findDetails(anyInt());
    verify(repository, never()).findSharedOtherCostsVolume(anyInt());
    verify(repository, never()).findOtherCostRows(anyInt());
  }

  private void stub(List<DetailRow> details, BigDecimal sharedVol, List<OtherCostDetailRow> other) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
    when(repository.findDetails(SUMMARY)).thenReturn(details);
    lenient()
        .when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.ofNullable(sharedVol));
    lenient()
        .when(repository.findOtherCostRows(SUMMARY))
        .thenReturn(other == null ? List.of() : other);
  }

  /** Every mandatory field present (volume + cost where applicable); consistent Other Costs. */
  private List<DetailRow> allPresent() {
    List<DetailRow> rows = new ArrayList<>();
    for (int code : VOL_COST) {
      rows.add(new DetailRow(code, new BigDecimal("100"), 500, null));
    }
    for (int code : VOL_ONLY) {
      rows.add(new DetailRow(code, new BigDecimal("100"), null, null)); // volume only
    }
    return rows;
  }

  private boolean hasError(Schedule1CheckStatusResponse r, String prefix) {
    return r.errors().stream().map(MessageInfo::text).anyMatch(t -> t.startsWith(prefix));
  }

  @Test
  void allPresent_requirementsMet_withSuccessMessage() {
    stub(allPresent(), BigDecimal.ZERO, List.of()); // shared vol 0, no rows -> consistent
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(r.requirementsMet());
    assertTrue(r.errors().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", r.message().key());
  }

  @Test
  void missingVolumeAndCost_reportedAsSeparateVerbatimErrors() {
    List<DetailRow> details = new ArrayList<>(allPresent());
    details.removeIf(d -> d.costItemCode() == 12); // code 12 entirely missing -> vol + cost errors
    stub(details, BigDecimal.ZERO, List.of());
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertFalse(r.requirementsMet());
    assertTrue(hasError(r, "Standing Tree to Loaded Truck - Volume:"));
    assertTrue(hasError(r, "Standing Tree to Loaded Truck - Cost:"));
  }

  @Test
  void zeroValueIsNotMissing() {
    List<DetailRow> details = new ArrayList<>(allPresent());
    details.removeIf(d -> d.costItemCode() == 13);
    details.add(new DetailRow(13, BigDecimal.ZERO, 0, null)); // zeros are present, not missing
    stub(details, BigDecimal.ZERO, List.of());
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertFalse(hasError(r, "Log Transportation -"));
  }

  @Test
  void volumeOnlyField_missingVolume_flagged_noCostError() {
    List<DetailRow> details = new ArrayList<>(allPresent());
    details.removeIf(d -> d.costItemCode() == 143);
    stub(details, BigDecimal.ZERO, List.of());
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(hasError(r, "Forest Management Administration - Volume:"));
    assertFalse(hasError(r, "Forest Management Administration - Cost:")); // volume-only
  }

  @Test
  void errorsAreInLegacyFieldOrder() {
    // Empty schedule -> every field missing; assert the legacy order (143 between 16 and 17, 144
    // after 18).
    stub(new ArrayList<>(), null, List.of());
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    List<String> texts = r.errors().stream().map(MessageInfo::text).toList();
    int forestMgmt = indexOfPrefix(texts, "Forest Management Administration - Volume");
    int stumpage = indexOfPrefix(texts, "Stumpage and Royalty - Volume");
    int subtotalCompany = indexOfPrefix(texts, "Subtotal Company Logging - Volume");
    int depletion = indexOfPrefix(texts, "Depletion and Amortization - Volume");
    assertTrue(forestMgmt >= 0 && forestMgmt < stumpage, "143 must come before 17 (Stumpage)");
    assertTrue(depletion < subtotalCompany, "144 must come after 18 (Depletion)");
  }

  private static int indexOfPrefix(List<String> texts, String prefix) {
    for (int i = 0; i < texts.size(); i++) {
      if (texts.get(i).startsWith(prefix)) {
        return i;
      }
    }
    return -1;
  }

  @Test
  void otherCostsVolumeNull_error() {
    stub(allPresent(), null, List.of());
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(hasError(r, "Subtotal Other Costs (0) - Volume:"));
  }

  @Test
  void otherCostsVolumePresentButNoCost_error() {
    stub(allPresent(), new BigDecimal("100"), List.of()); // vol>0, subtotal cost 0
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(
        r.errors().stream()
            .anyMatch(m -> "sch1.subtotal.other.costs.costs.grearter.than.zero".equals(m.key())));
  }

  @Test
  void otherCostsCostPresentButNoVolume_error() {
    stub(
        allPresent(),
        BigDecimal.ZERO,
        List.of(new OtherCostDetailRow(1, "A", 5000, BigDecimal.ZERO))); // vol 0, cost>0
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(
        r.errors().stream()
            .anyMatch(m -> "sch1.subtotal.other.costs.volume.grearter.than.zero".equals(m.key())));
  }

  @Test
  void otherCostsFractionalVolumeBelowOne_treatedAsZero_matchesLegacyIntValue() {
    // Legacy compares subtotalVolume.intValue(); a shared volume of 0.5 reads as 0, so with a cost
    // present it must raise "Volume must be > 0 when Cost > 0" — not pass (as BigDecimal.signum
    // would).
    stub(
        allPresent(),
        new BigDecimal("0.5"),
        List.of(new OtherCostDetailRow(1, "A", 5000, new BigDecimal("0.5"))));
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    assertTrue(
        r.errors().stream()
            .anyMatch(m -> "sch1.subtotal.other.costs.volume.grearter.than.zero".equals(m.key())));
  }

  @Test
  void emptyCostRow_isWarningNotError() {
    stub(
        allPresent(),
        new BigDecimal("100"),
        List.of(
            new OtherCostDetailRow(1, "Has desc, no cost", null, new BigDecimal("100")),
            new OtherCostDetailRow(2, "Priced", 5000, new BigDecimal("100"))));
    Schedule1CheckStatusResponse r = service.checkStatusStored(MILL, YEAR);
    // vol>0 & subtotal cost>0 (5000) -> no consistency error; the null-cost row -> warning only.
    assertTrue(r.requirementsMet());
    assertEquals(1, r.warnings().size());
    assertEquals(
        "warning.schedule1.checkstatus.subtotalother.costEmpty", r.warnings().get(0).key());
  }

  // ---------------------------------------------------------------------------------------------
  // The payload path (#359): the endpoint judges the SCREEN. The body's lines and shared Other
  // Costs volume replace the stored ones; the itemized Other Costs rows stay database-sourced.
  // ---------------------------------------------------------------------------------------------

  /** Only what the payload path may read: the summary and its itemized Other Costs rows. */
  private void stubPayload(List<OtherCostDetailRow> other) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
    when(repository.findOtherCostRows(SUMMARY)).thenReturn(other);
  }

  /** The screen as a check body, one line per row, values verbatim (nulls stay null). */
  private static Schedule1CheckRequest screen(List<DetailRow> rows, BigDecimal otherCostsVolume) {
    return new Schedule1CheckRequest(
        rows.stream().map(d -> new LineEntry(d.costItemCode(), d.volume(), d.cost())).toList(),
        otherCostsVolume);
  }

  /** {@link #allPresent} with one code's row replaced. */
  private List<DetailRow> allPresentWith(DetailRow replacement) {
    List<DetailRow> rows = new ArrayList<>(allPresent());
    rows.replaceAll(d -> d.costItemCode().equals(replacement.costItemCode()) ? replacement : d);
    return rows;
  }

  @Test
  void payload_clearedVolume_isReported_evenThoughStoredHasIt() {
    // S27: Standing Tree volume cleared on screen. The stored value is never read.
    stubPayload(List.of());
    Schedule1CheckStatusResponse r =
        service.checkStatus(
            MILL, YEAR, screen(allPresentWith(new DetailRow(12, null, 500, null)), ZERO));

    assertFalse(r.requirementsMet());
    assertNull(r.message(), "no MET line beside a finding");
    assertEquals(
        List.of("Standing Tree to Loaded Truck - Volume: missingRequiredFieldMsg"),
        r.errors().stream().map(MessageInfo::text).toList());
    verify(repository, never()).findDetails(anyInt());
    verify(repository, never()).findSharedOtherCostsVolume(anyInt());
  }

  @Test
  void payload_suppliedVolume_clearsTheFinding_andNothingElseChanges() {
    // S28: stored volume missing, supplied on screen -> MET, the finding gone.
    stubPayload(List.of());
    Schedule1CheckStatusResponse r = service.checkStatus(MILL, YEAR, screen(allPresent(), ZERO));

    assertTrue(r.requirementsMet());
    assertTrue(r.errors().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", r.message().key());
  }

  @Test
  void payload_typedZero_isPresent() {
    stubPayload(List.of());
    Schedule1CheckStatusResponse r =
        service.checkStatus(
            MILL, YEAR, screen(allPresentWith(new DetailRow(13, ZERO, 0, null)), ZERO));

    assertTrue(r.requirementsMet(), "0 is present — null test, not truthiness");
  }

  @Test
  void payload_prefilledCrownVolume_passes_whereStoredStillFlagsIt() {
    // The GET pre-fills a crown volume that is null in the database. It is on screen, so the
    // payload path passes it; the stored path (the sweep) still flags it — by design.
    List<DetailRow> stored = allPresentWith(new DetailRow(14, null, 500, null));
    stub(stored, ZERO, List.of());
    Schedule1CheckStatusResponse onScreen =
        service.checkStatus(
            MILL,
            YEAR,
            screen(allPresentWith(new DetailRow(14, new BigDecimal("12345"), 500, null)), ZERO));
    Schedule1CheckStatusResponse saved = service.checkStatusStored(MILL, YEAR);

    assertTrue(onScreen.requirementsMet());
    assertFalse(saved.requirementsMet());
    assertTrue(hasError(saved, "Road Management - Volume:"));
  }

  @Test
  void payload_otherCostsVolume_comesFromTheBody_rowsFromTheDatabase() {
    // The body clears the shared volume -> "Value Required", labelled with the STORED row count.
    stubPayload(
        List.of(
            new OtherCostDetailRow(1, "A", 5000, null), new OtherCostDetailRow(2, "B", 0, null)));
    Schedule1CheckStatusResponse r = service.checkStatus(MILL, YEAR, screen(allPresent(), null));

    assertEquals(
        List.of("Subtotal Other Costs (2) - Volume: missingRequiredFieldMsg"),
        r.errors().stream().map(MessageInfo::text).toList());
  }

  @Test
  void payload_subPageRows_areReportedExactlyAsStored() {
    // A described row with no cost is the WRN-002 warning, and a zero stored subtotal against an
    // on-screen volume > 0 is the cost-gt-zero error — both judged from the DATABASE rows.
    stubPayload(List.of(new OtherCostDetailRow(1, "Has desc, no cost", null, null)));
    Schedule1CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, screen(allPresent(), new BigDecimal("100")));

    assertTrue(
        r.errors().stream()
            .anyMatch(m -> "sch1.subtotal.other.costs.costs.grearter.than.zero".equals(m.key())));
    assertEquals(1, r.warnings().size());
    assertEquals(
        "warning.schedule1.checkstatus.subtotalother.costEmpty", r.warnings().get(0).key());
  }

  @Test
  void payload_nullLineItems_reportsEveryLineMissing_ratherThanThrowing() {
    stubPayload(List.of());
    Schedule1CheckStatusResponse r =
        service.checkStatus(MILL, YEAR, new Schedule1CheckRequest(null, ZERO));

    stub(new ArrayList<>(), ZERO, List.of());
    assertEquals(service.checkStatusStored(MILL, YEAR), r);
  }

  @Test
  void payload_noSummary_readsNoRows() {
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());
    Schedule1CheckStatusResponse r = service.checkStatus(MILL, YEAR, screen(allPresent(), ZERO));

    assertTrue(r.requirementsMet(), "an unsaved schedule fully entered on screen is complete");
    verify(repository, never()).findOtherCostRows(anyInt());
  }

  /**
   * One evaluator, two sources (AD-5): a screen that mirrors the record yields a byte-identical
   * verdict on both paths, across every branch — MET, missing fields, both Other Costs consistency
   * errors, the truncation quirk, and the warning.
   */
  @Test
  void storedAndPayload_agree_whenTheScreenMirrorsTheRecord() {
    List<List<DetailRow>> lines =
        List.of(
            allPresent(),
            new ArrayList<>(),
            allPresentWith(new DetailRow(12, null, null, null)),
            allPresentWith(new DetailRow(140, null, null, null)));
    List<BigDecimal> volumes =
        Arrays.asList(null, ZERO, new BigDecimal("0.5"), new BigDecimal("100"));
    List<List<OtherCostDetailRow>> others =
        List.of(
            List.of(),
            List.of(new OtherCostDetailRow(1, "A", 5000, null)),
            List.of(new OtherCostDetailRow(1, "No cost", null, null)));
    for (List<DetailRow> line : lines) {
      for (BigDecimal volume : volumes) {
        for (List<OtherCostDetailRow> other : others) {
          lenient()
              .when(repository.findSummary(MILL, YEAR, "1"))
              .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
          lenient().when(repository.findDetails(SUMMARY)).thenReturn(line);
          lenient()
              .when(repository.findSharedOtherCostsVolume(SUMMARY))
              .thenReturn(Optional.ofNullable(volume));
          lenient().when(repository.findOtherCostRows(SUMMARY)).thenReturn(other);

          assertEquals(
              service.checkStatusStored(MILL, YEAR),
              service.checkStatus(MILL, YEAR, screen(line, volume)),
              () -> "lines=" + line + " volume=" + volume + " other=" + other);
        }
      }
    }
  }
}
