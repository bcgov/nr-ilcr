package ca.bc.gov.nrs.ilcr.schedule11.dto;

import java.math.BigDecimal;

/**
 * The Schedule 11 footer totals of the pinned Story 25.1 wire contract (AD-12), computed
 * server-side per BR-08 ({@code Schedule11DO} / {@code CoreUtil} verbatim): every field is null —
 * NEVER zero — when it has no contributors.
 *
 * <p>The cost totals are {@code Long}, not {@code Integer}: each per-location {@code COST} is
 * {@code NUMBER(8,0)} (up to 99,999,999) and a footer sum across enough locations exceeds
 * {@code Integer.MAX_VALUE} (~2.147e9) — legacy summed with {@code BigDecimal} and never wrapped,
 * so an {@code int} accumulator here would silently corrupt large footers. Per-row costs stay
 * {@code Integer} (each ≤ 199,999,998, always in range). Both serialize as bare JSON integers.
 *
 * @param netArea sum of non-null net areas, scale 1 HALF_UP ({@code sumBigDecimalAreas})
 * @param actualCost sum of non-null actual costs ({@code sumBigDecimalCosts}; whole dollars)
 * @param plannedCost sum of non-null planned costs
 * @param totalCost null-tolerant addition of the two cost totals ({@code bigDecimalAddition})
 * @param costPerNetArea {@code totalCost / netArea} against the ROUNDED footer area (legacy getter
 *     chain); null on null/zero denominator; scale 4 HALF_UP, min scale 1 (recorded deviation)
 */
public record SilvicultureTotals(
    BigDecimal netArea,
    Long actualCost,
    Long plannedCost,
    Long totalCost,
    BigDecimal costPerNetArea) {
}
