package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.math.BigDecimal;

/**
 * One Schedule 8 rate-adjustment row (AD-12) — a {@code TREE_TO_TRUCK_RATE_DETAIL} row surfaced under
 * a sample as either an addition or a deduction. Which list it lands in is decided by its cost item's
 * {@code ILCR_SUBCATEGORY_ID} (§Decision 1: {@code '1'}/{@code '2'} = addition, {@code '3'}/{@code '4'}
 * = deduction) — the row itself carries no add/deduct flag.
 *
 * <p>{@code costItemCode} is the legacy {@code ILCR_REPORT_COST_ITEM_ID}; {@code itemDescription} is
 * the row's stored free-text label. {@code costTypeCode} is {@code ILCR_RATE_COST_TYPE_CODE} and
 * {@code costTypeDescription} is its label resolved from {@code ILCR_RATE_COST_TYPE_CODE.DESCRIPTION}
 * (§Decision 3 — both the code and its label are surfaced). {@code costingRate} is normalized to its
 * natural form for wire parity. All nullable fields are omitted from the JSON when null.
 */
public record RateRow(
    Integer id,
    Integer revisionCount,
    Integer costItemCode,
    String itemDescription,
    BigDecimal costingRate,
    String costTypeCode,
    String costTypeDescription) {
}
