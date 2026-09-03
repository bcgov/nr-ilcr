package ca.bc.gov.nrs.ilcr.schedule1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.DetailRow;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Repository.SummaryRow;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link Schedule1CostDerivation} — the legacy {@code
 * Schedule1DO.getSubtotalLoggingCost} (Subtotal Company Logging Cost, no silviculture) that
 * Schedule 2 carries into its {@code totalCompanyLogging}.
 *
 * <p>This is where the "Forest Management Administration is NOT part of the figure" relationship is
 * pinned (bcgov/nr-ilcr#252). Before #252 Schedule 2 re-derived the figure as {@code
 * subtotalCompanyLoggingCost − forestMgmtAdminCost} off the assembled Schedule 1 document, and the
 * pin lived at that boundary ({@code
 * Schedule2ServiceTest.totalCompanyLogging_usesSchedule1SubtotalMinusFma_notRawSubtotal}). The
 * arithmetic now lives here, so the pin does too: {@link
 * #loggingLinesAndItemizedOtherCosts_summed_forestMgmtAdminExcluded} fails if an item-143 cost ever
 * leaks back into the subtotal.
 *
 * <p>The same computation feeds {@code Schedule1Service.assemble} (which adds Forest Mgmt Admin
 * back on for its own item-144 display figure), so the served document and Schedule 2's carried
 * term cannot drift. Mocked repository.
 */
@ExtendWith(MockitoExtension.class)
class Schedule1CostDerivationTest {

  private static final long MILL = 514L;
  private static final int YEAR = 2021;

  @Mock private Schedule1Repository repository;

  @InjectMocks private Schedule1CostDerivation derivation;

  private static DetailRow cost(int code, Integer amount) {
    return new DetailRow(code, null, amount, null);
  }

  /**
   * An Other-Costs (item-19) row: itemized when it carries a description, shared when it doesn't.
   */
  private static DetailRow otherCost(String description, Integer amount, String volume) {
    return new DetailRow(19, volume == null ? null : new BigDecimal(volume), amount, description);
  }

  private void stubDetails(List<DetailRow> details) {
    when(repository.findSummary(MILL, YEAR, "1"))
        .thenReturn(Optional.of(new SummaryRow(1001, 12345, "c", 3)));
    lenient().when(repository.findDetails(1001)).thenReturn(details);
  }

  /**
   * A fully-populated Schedule 1: the seven logging lines (12–18), the pulled/derived rows that
   * must NOT count (143 Forest Mgmt Admin, 144 the stored subtotal), the silviculture rows (1, 2,
   * 139, 140 — never part of the "no silviculture" subtotal), and an Other-Costs block with a
   * shared volume row plus two itemized rows.
   */
  private static List<DetailRow> fullDocument() {
    List<DetailRow> rows = new ArrayList<>();
    rows.add(cost(12, 40000)); // Standing Tree to Loaded Truck
    rows.add(cost(13, 10000)); // Log Transportation
    rows.add(cost(14, 5000)); // Road Management
    rows.add(cost(15, 3000)); // Road Construction Costs
    rows.add(cost(16, 2000)); // Post Logging Treatment
    rows.add(cost(17, 7000)); // Stumpage and Royalty
    rows.add(cost(18, 1000)); // Other Logging Costs
    rows.add(cost(143, 600000)); // Forest Mgmt Admin — PULLED from Sch 3, excluded (the #252 pin)
    rows.add(cost(144, 999999)); // the stored subtotal row — legacy never reads it
    rows.add(cost(1, 200000)); // Silviculture Actual $ Spent — "no silviculture" subtotal
    rows.add(cost(2, 50000)); // Silviculture Accrued $ Spent
    rows.add(cost(139, 150000)); // Less Silviculture Admin
    rows.add(cost(140, 100000)); // Total Silviculture
    rows.add(otherCost(null, 111111, "2000")); // shared volume row — its cost is not itemized
    rows.add(otherCost("Fuel", 4000, null));
    rows.add(otherCost("Insurance", 6000, null));
    return rows;
  }

  @Test
  void loggingLinesAndItemizedOtherCosts_summed_forestMgmtAdminExcluded() {
    stubDetails(fullDocument());
    // Σ logging 12–18 (68000) + Σ itemized Other Costs (10000) = 78000. Nothing else contributes:
    // adding the 143 Forest Mgmt Admin cost back in would make this 678000.
    assertEquals(Optional.of(78000L), derivation.subtotalLoggingNoFmaCost(MILL, YEAR));
  }

  @Test
  void noSchedule1Summary_isEmpty_soSchedule2sCarriedTermStaysNull() {
    // The absence signal Schedule 2 relies on (#296): an absent Schedule 1 must leave the carried
    // term blank, NOT $0 — an empty Optional here, never Optional.of(0L).
    when(repository.findSummary(MILL, YEAR, "1")).thenReturn(Optional.empty());
    assertTrue(derivation.subtotalLoggingNoFmaCost(MILL, YEAR).isEmpty());
  }

  @Test
  void emptySchedule1Summary_isZero_notEmpty() {
    // A saved-but-blank Schedule 1: legacy's Subtotal Company Logging is never blank (its Subtotal
    // Other Costs term seeds at 0), so an existing summary with no rows yields 0, not absence.
    stubDetails(List.of());
    assertEquals(Optional.of(0L), derivation.subtotalLoggingNoFmaCost(MILL, YEAR));
  }

  @Test
  void nullLoggingCosts_treatedAsZero() {
    // Legacy null-safe sums: a line with a volume but no cost entered contributes 0, not null.
    stubDetails(List.of(cost(12, 40000), cost(13, null), cost(14, null)));
    assertEquals(Optional.of(40000L), derivation.subtotalLoggingNoFmaCost(MILL, YEAR));
  }

  @Test
  void sharedOtherCostsRow_cost_excludedFromSubtotal() {
    // Only DESCRIBED item-19 rows are itemized costs; the description-less row carries the block's
    // shared volume. Legacy splits on isNullOrEmptyString, so a whitespace-only description is an
    // itemized row — matching Schedule1Service.toOtherCosts and the IS NULL / IS NOT NULL SQL.
    stubDetails(
        List.of(
            otherCost(null, 111111, "2000"), otherCost(" ", 500, null), otherCost("", 700, null)));
    assertEquals(Optional.of(500L), derivation.subtotalLoggingNoFmaCost(MILL, YEAR));
  }

  @Test
  void duplicateFixedCodeRows_lastRowWins() {
    // Schedule 1 partitions last-row-wins (details arrive ordered by detail id), UNLIKE
    // Schedule3CostDerivation's first-wins putIfAbsent. Pinned because the partition moved here
    // from Schedule1Service (#252) and the served document depends on this exact semantic.
    stubDetails(List.of(cost(12, 40000), cost(12, 25000)));
    assertEquals(Optional.of(25000L), derivation.subtotalLoggingNoFmaCost(MILL, YEAR));
  }
}
