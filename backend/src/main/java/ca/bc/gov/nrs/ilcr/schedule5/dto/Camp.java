package ca.bc.gov.nrs.ilcr.schedule5.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One Schedule 5 logging camp (AD-12) — its descriptors, its twelve stored category amounts, the
 * four derived totals, and the two sub-page row counts. Field order mirrors the legacy screen
 * ({@code schedule5ExistingCamp.xhtml}) so the grid reads top-to-bottom in the same order a
 * licensee sees it.
 *
 * <p><strong>Twelve categories, eleven volumes.</strong> Exactly eleven categories carry a volume
 * (legacy enumerates them at {@code Schedule5MB.java:248-261}); {@code recoveries} is the
 * volume-less twelfth and serializes as {@code {"cost": n}} alone.
 *
 * <p><strong>Derived, never stored.</strong> {@code campSubTotal}, {@code campTotal}, {@code
 * accessExpenseTotal}, {@code campAndAccessTotal}, every {@code costPerVolume}, both counts, and
 * the {@code cost} halves of {@code otherCampExpenses}/{@code otherAccessExpenses} (the item-62 /
 * item-68 row sums) are all computed server-side per BR-04 and must be ignored or rejected on write
 * (7.2). {@code campTotal = campSubTotal - recoveries} (BR-04/S09): Recoveries is stored positive
 * and SUBTRACTED, and a negative Recoveries therefore increases the total — never clamped.
 *
 * <p>{@code revisionCount} is this camp's own optimistic-lock token. Schedule 5 has no
 * category-{@code '5'} {@code ILCR_REPORT_SUMMARY} row (delivery-confirmed, Story 7.1 Task 1 gate
 * (ii)), so there is no schedule-level revision — the AR11 keying delta, recorded as deviation (b).
 * It is a primitive {@code int} for the same reason {@code campId} is: {@code REVISION_COUNT} is
 * {@code NOT NULL} in delivery and in the snapshot. Boxing it would let {@code NON_NULL} drop the
 * token from the JSON silently, and 7.2's write path needs it present — a client that sent a write
 * with no token would get no signal that one was expected.
 *
 * <p>{@code isolatedCamp} is the {@code Y}/{@code N} indicator as a Boolean. It is nullable only
 * defensively (deviation (e)): the delivery column is {@code NOT NULL DEFAULT 'N'}, so no stored
 * row can serve null — legacy's unguarded {@code .equals()} would NPE the whole page if one did.
 * {@code associatedCampVolume} is modelled as {@code BigDecimal} rather than an integer because it
 * IS the volume of all four derived totals; serving one volume type keeps the grid consistent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Camp(
    int campId,
    int revisionCount,
    String campName,
    BigDecimal roadDistanceToOperatingArea,
    Integer sizeOfCamp,
    BigDecimal associatedCampVolume,
    Boolean isolatedCamp,
    String comments,
    CategoryAmount cateringAndFood,
    CategoryAmount wagesAndBenefits,
    CategoryAmount depreciationLease,
    CategoryAmount generalCampExpenses,
    CategoryAmount otherCampExpenses,
    CategoryAmount campSubTotal,
    CategoryAmount recoveries,
    CategoryAmount campTotal,
    CategoryAmount crewTransportation,
    CategoryAmount equipAndSuppliesLand,
    CategoryAmount equipAndSuppliesRail,
    CategoryAmount equipAndSuppliesAir,
    CategoryAmount equipAndSuppliesWater,
    CategoryAmount otherAccessExpenses,
    CategoryAmount accessExpenseTotal,
    CategoryAmount campAndAccessTotal,
    int otherCampExpenseCount,
    int otherAccessExpenseCount) {}
