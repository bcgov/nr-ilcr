package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.OtherCostDetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

/**
 * Unit test for BR-07 Check Status logic (Story 2.6). Mocked repository + MessageSource (returns the
 * bundle key so assertions test structure/keys/order; verbatim text is asserted by the IT). No DB.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1CheckStatusServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;
  private static final int SUMMARY = 1001;

  // Codes that require volume+cost (12-18, silv 1&2) and volume-only (143,144,139,140).
  private static final List<Integer> VOL_COST = List.of(12, 13, 14, 15, 16, 17, 18, 1, 2);
  private static final List<Integer> VOL_ONLY = List.of(143, 144, 139, 140);

  @Mock
  private Schedule1Repository repository;

  @Mock
  private MessageSource messageSource;

  @InjectMocks
  private Schedule1Service service;

  @BeforeEach
  void stubMessages() {
    lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
        .thenAnswer(i -> i.getArgument(0)); // return the key
  }

  private void stub(List<DetailRow> details, BigDecimal sharedVol, List<OtherCostDetailRow> other) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(SUMMARY, null, null, 0)));
    when(repository.findDetails(SUMMARY)).thenReturn(details);
    lenient().when(repository.findSharedOtherCostsVolume(SUMMARY))
        .thenReturn(Optional.ofNullable(sharedVol));
    lenient().when(repository.findOtherCostRows(SUMMARY)).thenReturn(other == null ? List.of() : other);
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

  private boolean hasError(CheckStatusResponse r, String prefix) {
    return r.errors().stream().map(MessageInfo::text).anyMatch(t -> t.startsWith(prefix));
  }

  @Test
  void allPresent_requirementsMet_withSuccessMessage() {
    stub(allPresent(), BigDecimal.ZERO, List.of()); // shared vol 0, no rows -> consistent
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertTrue(r.requirementsMet());
    assertTrue(r.errors().isEmpty());
    assertEquals("scheduleRequirementsMetMsg", r.message().key());
  }

  @Test
  void missingVolumeAndCost_reportedAsSeparateVerbatimErrors() {
    List<DetailRow> details = new ArrayList<>(allPresent());
    details.removeIf(d -> d.costItemCode() == 12); // code 12 entirely missing -> vol + cost errors
    stub(details, BigDecimal.ZERO, List.of());
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
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
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertFalse(hasError(r, "Log Transportation -"));
  }

  @Test
  void volumeOnlyField_missingVolume_flagged_noCostError() {
    List<DetailRow> details = new ArrayList<>(allPresent());
    details.removeIf(d -> d.costItemCode() == 143);
    stub(details, BigDecimal.ZERO, List.of());
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertTrue(hasError(r, "Forest Management Administration - Volume:"));
    assertFalse(hasError(r, "Forest Management Administration - Cost:")); // volume-only
  }

  @Test
  void errorsAreInLegacyFieldOrder() {
    // Empty schedule -> every field missing; assert the legacy order (143 between 16 and 17, 144 after 18).
    stub(new ArrayList<>(), null, List.of());
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
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
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertTrue(hasError(r, "Subtotal Other Costs (0) - Volume:"));
  }

  @Test
  void otherCostsVolumePresentButNoCost_error() {
    stub(allPresent(), new BigDecimal("100"), List.of()); // vol>0, subtotal cost 0
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertTrue(r.errors().stream()
        .anyMatch(m -> "sch1.subtotal.other.costs.costs.grearter.than.zero".equals(m.key())));
  }

  @Test
  void otherCostsCostPresentButNoVolume_error() {
    stub(allPresent(), BigDecimal.ZERO,
        List.of(new OtherCostDetailRow(1, "A", 5000, BigDecimal.ZERO))); // vol 0, cost>0
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    assertTrue(r.errors().stream()
        .anyMatch(m -> "sch1.subtotal.other.costs.volume.grearter.than.zero".equals(m.key())));
  }

  @Test
  void emptyCostRow_isWarningNotError() {
    stub(allPresent(), new BigDecimal("100"),
        List.of(new OtherCostDetailRow(1, "Has desc, no cost", null, new BigDecimal("100")),
            new OtherCostDetailRow(2, "Priced", 5000, new BigDecimal("100"))));
    CheckStatusResponse r = service.checkSchedule1Status(MILL, YEAR);
    // vol>0 & subtotal cost>0 (5000) -> no consistency error; the null-cost row -> warning only.
    assertTrue(r.requirementsMet());
    assertEquals(1, r.warnings().size());
    assertEquals("warning.schedule1.checkstatus.subtotalother.costEmpty", r.warnings().get(0).key());
  }
}
