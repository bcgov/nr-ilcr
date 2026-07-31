package ca.bc.gov.nrs.ilcr.schedule3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Constants.LineSpec;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3CostDerivation.Schedule1Sources;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Repository.SummaryRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link Schedule3CostDerivation} — the Schedule-3 values Schedule 1 pulls (BR-03 crown
 * volume, BR-04 Forest Mgmt Admin = Subtotal Actual Costs crown, BR-04 Less Silviculture Admin =
 * item-37 crown). The Subtotal Actual Costs is DERIVED from the fixed lines (never a persisted row),
 * so the {@code fullDocument()} line set below MUST yield the same subtotal Schedule 3 computes for the
 * same data — cross-checked against {@code Schedule3ServiceTest}/{@code Schedule3DocumentIT} (harvest
 * 900000 / PO&amp;P 300000 -> crown 600000 on the shared 514/2021 fixture). Mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class Schedule3CostDerivationTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock
  private Schedule3Repository repository;

  @InjectMocks
  private Schedule3CostDerivation derivation;

  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null, null);
  }

  private static DetailRow volume(int code, String vol) {
    return new DetailRow(code, new BigDecimal(vol), null, null, null);
  }

  private void stubDetails(List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR))
        .thenReturn(Optional.of(new SummaryRow(1003, "N", "comment", 0)));
    lenient().when(repository.findDetails(1003)).thenReturn(details);
  }

  /** The full seed on 514/2021 (V17 fixed lines + V5 item 37 + timber volumes). */
  private static List<DetailRow> fullDocument() {
    List<DetailRow> rows = new ArrayList<>();
    rows.add(cost(27, 100000));
    rows.add(cost(125, 40000));
    rows.add(cost(28, 50000));
    rows.add(cost(126, 20000));
    rows.add(cost(29, 30000));       // Annual Rents — Harvest-only
    rows.add(cost(30, 285000));
    rows.add(cost(128, 155000));
    rows.add(cost(31, 40000));
    rows.add(cost(129, 10000));
    rows.add(cost(32, 25000));
    rows.add(cost(130, 5000));
    rows.add(cost(33, 60000));       // Scaling — PO&P derived (0.5 * 60000 = 30000)
    rows.add(cost(34, 35000));
    rows.add(cost(132, 15000));
    rows.add(cost(35, 45000));
    rows.add(cost(133, 5000));
    rows.add(cost(36, 80000));
    rows.add(cost(134, 20000));
    rows.add(cost(37, 150000));      // Silviculture Admin — Harvest-only
    rows.add(volume(118, "54321"));  // PO&P Timber volume
    rows.add(volume(119, "54321"));  // Crown Timber volume
    return rows;
  }

  @Test
  void fullDocument_derivesForestMgmtAdminSilvAdminAndCrownVolume() {
    stubDetails(fullDocument());
    Schedule1Sources sources = derivation.schedule1Sources(MILL, YEAR);
    // Subtotal Actual Costs crown = 900000 (harvest) − 300000 (PO&P) = 600000 (matches Schedule 3).
    assertEquals(600000L, sources.forestMgmtAdminCrownCost());
    // Less Silviculture Admin = item-37 cost (PO&P forced 0 ⇒ crown = cost).
    assertEquals(150000, sources.silvicultureAdminCrownCost());
    // Crown Timber volume for BR-03 pre-fill.
    assertEquals(0, new BigDecimal("54321").compareTo(sources.crownTimberVolume()));
  }

  @Test
  void emptySchedule3Summary_forestMgmtAdminIsZero_notNull() {
    // A summary that exists but has no cost lines: subtotal seeds at 0 ⇒ crown 0 (not null).
    stubDetails(List.of());
    Schedule1Sources sources = derivation.schedule1Sources(MILL, YEAR);
    assertEquals(0L, sources.forestMgmtAdminCrownCost());
    assertNull(sources.silvicultureAdminCrownCost());
    assertNull(sources.crownTimberVolume());
  }

  @Test
  void noSchedule3Summary_allSourcesNull() {
    when(repository.findSummary(MILL, YEAR)).thenReturn(Optional.empty());
    Schedule1Sources sources = derivation.schedule1Sources(MILL, YEAR);
    assertNull(sources.forestMgmtAdminCrownCost());
    assertNull(sources.silvicultureAdminCrownCost());
    assertNull(sources.crownTimberVolume());
  }

  @Test
  void resolvePop_nullPopCodeSpec_forcesZeroWhenHarvestPresentElseNull() {
    // Harvest-only lines (Annual Rents 29 / Silviculture Admin 37) carry a null popCode: resolvePop
    // must force PO&P to 0 when a Harvest is present (so crown = harvest) and stay null when it isn't —
    // never dereferencing the absent PO&P item. Volumes are irrelevant on this path (scaling-only).
    LineSpec harvestOnly = new LineSpec(29, null, true);
    assertEquals(0, Schedule3Constants.resolvePop(harvestOnly, 30000, Map.of(), null, null));
    assertNull(Schedule3Constants.resolvePop(harvestOnly, null, Map.of(), null, null));
  }

  @Test
  void otherAcceptableGroups_foldIntoSubtotalActual() {
    // Item-124: a TOT row adds to harvest, its PO&P peer to pop; crown picks up the difference.
    List<DetailRow> rows = new ArrayList<>(List.of(
        cost(27, 100000), cost(125, 40000)));                     // crown 60000
    rows.add(new DetailRow(124, null, 20000, "desc", "SCH3_2_TOT_GRP1"));  // harvest += 20000
    rows.add(new DetailRow(124, null, 5000, "desc", "SCH3_2_POP_GRP1"));   // pop += 5000
    stubDetails(rows);
    Schedule1Sources sources = derivation.schedule1Sources(MILL, YEAR);
    // harvest 120000 − pop 45000 = 75000.
    assertEquals(75000L, sources.forestMgmtAdminCrownCost());
  }
}
