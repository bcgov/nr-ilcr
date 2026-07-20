package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.LineItem;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the Schedule 1 document assembly + server-side derivation (AD-5/AD-6). Mocked
 * repository — no DB, no Spring.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1ServiceTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule1Repository repository;

  @InjectMocks
  private Schedule1Service service;

  private void stub(String trackStatus, List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(1001, 12345, "c", 3)));
    lenient().when(repository.findDetails(1001)).thenReturn(details);
    when(repository.findTrackStatus(MILL, YEAR)).thenReturn(Optional.ofNullable(trackStatus));
  }

  private LineItem lineItem(Schedule1Response doc, int code) {
    return doc.lineItems().stream().filter(li -> li.costItemCode() == code).findFirst().orElseThrow();
  }

  @Test
  void perUnit_isCostOverVolume_asDecimal() {
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000.0000"), 50000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    // 50000 / 1000 = 50.0 (kept as a decimal, scale >= 1)
    assertEquals(0, new BigDecimal("50.0").compareTo(lineItem(doc, 12).perUnit()));
  }

  @Test
  void perUnit_nullOrZeroVolume_isNull() {
    stub("D", List.of(
        new DetailRow(12, BigDecimal.ZERO, 50000, null),
        new DetailRow(13, null, 40000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertNull(lineItem(doc, 12).perUnit());
    assertNull(lineItem(doc, 13).perUnit());
  }

  @Test
  void otherCosts_subtotalCountPerUnit_fromItem19Rows() {
    stub("D", List.of(
        new DetailRow(19, new BigDecimal("8000"), null, null),   // shared volume row
        new DetailRow(19, null, 12000, "Row A"),                 // itemized
        new DetailRow(19, null, 12000, "Row B")));               // itemized
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(0, new BigDecimal("8000").compareTo(doc.otherCosts().volume()));
    assertEquals(24000L, doc.otherCosts().costSubtotal());
    assertEquals(2, doc.otherCosts().count());
    assertEquals(0, new BigDecimal("3.0").compareTo(doc.otherCosts().perUnit()));
  }

  @Test
  void otherCosts_alwaysPresent_whenNoItem19Rows() {
    // A schedule with no Other Costs still carries a zero-summary (present, not null) so the
    // client can tell "zero" from "missing".
    stub("D", List.of(new DetailRow(12, new BigDecimal("1000"), 50000, null)));
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(0, doc.otherCosts().count());
    assertEquals(0L, doc.otherCosts().costSubtotal());
    assertNull(doc.otherCosts().volume());
    assertNull(doc.otherCosts().perUnit());
  }

  @Test
  void editable_trueOnlyWhenCallerMayEditAndDraft() {
    stub("D", List.of());
    assertTrue(service.getSchedule1(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenNotDraft() {
    stub("S", List.of());
    assertFalse(service.getSchedule1(MILL, YEAR, true).editable());
  }

  @Test
  void editable_falseWhenCallerMayNotEdit() {
    stub("D", List.of());
    assertFalse(service.getSchedule1(MILL, YEAR, false).editable());
  }

  @Test
  void noSummary_returnsEmptyLockedSkeleton_notFound() {
    // "Not initiated": active Draft mill/year with NO Schedule 1 summary -> 200 empty, editable:false.
    when(repository.findSummary(515L, YEAR, "1")).thenReturn(Optional.empty());
    when(repository.findTrackStatus(515L, YEAR)).thenReturn(Optional.of("D"));

    Schedule1Response doc = service.getSchedule1(515L, YEAR, true);

    assertEquals("D", doc.trackStatus());
    assertFalse(doc.editable()); // locked even for a Draft caller who may edit
    assertNull(doc.crownVolume());
    assertNull(doc.revisionCount());
    assertNull(doc.comments());
    // All nine canonical line items present, all values null.
    assertEquals(List.of(12, 13, 14, 15, 16, 17, 18, 143, 144),
        doc.lineItems().stream().map(LineItem::costItemCode).toList());
    doc.lineItems().forEach(li -> {
      assertNull(li.volume());
      assertNull(li.cost());
      assertNull(li.perUnit());
    });
    // Silviculture block all null.
    assertNull(doc.silviculture().actualSpent());
    assertNull(doc.silviculture().accruedLessActual());
    assertNull(doc.silviculture().lessAdmin());
    assertNull(doc.silviculture().total());
    assertNull(doc.forestMgmtAdminCost());
    assertNull(doc.lessSilvAdminCost());
    // Other Costs zeroed/empty (count 0).
    assertEquals(0, doc.otherCosts().count());
    assertEquals(0L, doc.otherCosts().costSubtotal());
    assertNull(doc.otherCosts().volume());
    assertNull(doc.otherCosts().perUnit());
  }

  @Test
  void noSummary_noStatusRow_trackStatusNull() {
    when(repository.findSummary(515L, YEAR, "1")).thenReturn(Optional.empty());
    when(repository.findTrackStatus(515L, YEAR)).thenReturn(Optional.empty());
    Schedule1Response doc = service.getSchedule1(515L, YEAR, true);
    assertNull(doc.trackStatus());
    assertFalse(doc.editable());
  }

  @Test
  void silvicultureAndScalars_mappedByCode() {
    stub("D", List.of(
        new DetailRow(1, new BigDecimal("100"), 500, null),      // silviculture actual
        new DetailRow(139, new BigDecimal("50"), 300, null),     // less silv admin (also scalar)
        new DetailRow(143, new BigDecimal("70"), 700, null)));   // forest mgmt admin (also scalar)
    Schedule1Response doc = service.getSchedule1(MILL, YEAR, true);
    assertEquals(500, doc.silviculture().actualSpent().cost());
    assertEquals(300, doc.silviculture().lessAdmin().cost());
    assertEquals(300, doc.lessSilvAdminCost());
    assertEquals(700, doc.forestMgmtAdminCost());
  }
}
