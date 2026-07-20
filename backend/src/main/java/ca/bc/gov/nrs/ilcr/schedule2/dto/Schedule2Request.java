package ca.bc.gov.nrs.ilcr.schedule2.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The Schedule 2 PUT request (AD-12) — the two entered line items ONLY. Every derived/carried figure
 * ({@code perUnit}, {@code subtotal}, {@code netPurchased}, {@code totalCompanyLogging},
 * {@code totalAverage}) and read-only metadata ({@code trackStatus}/{@code editable}) is
 * recomputed/carried server-side (AD-5) and is NOT part of the request — a client-sent value for any
 * of them is ignored, never trusted.
 *
 * <p>Writable content: item 25 (Purchased/Private Log Costs — cost only; its volume is carried from
 * Schedule 3 item 118), item 26 (Less Log Sales — volume + cost), and {@code comments}. Blank
 * writable values are permitted (legacy accepts blank amounts at Save; clearing a field persists
 * null). {@code revisionCount} is the optimistic-lock token and is required ({@code @NotNull}); for a
 * new/unsaved schedule (no summary yet) the client sends {@code 0} — the revision of the row the
 * create-on-absent path inserts — NOT null (a null body fails {@code @NotNull} with a 400).
 *
 * <p>Range validation is Jakarta Bean Validation whose messages resolve the LEGACY bundle keys
 * (AD-6/AD-8): item 25 cost ∈ [-99,999,999, 99,999,999] ({@code costValidatorErrorMsg}); item 26
 * volume ∈ [0, 9,999,999] ({@code volumeValidatorErrorMsg}); item 26 cost — the widened
 * {@code costSize="9"} range — ∈ [-999,999,999, 999,999,999] ({@code costSize9ValidatorErrorMsg}).
 *
 * @param revisionCount optimistic-lock token from the last GET (AR11); required
 * @param comments free-text schedule comments (nullable)
 * @param purchasedLogCostCost item 25 cost (nullable; ±99,999,999)
 * @param lessLogSalesVolume item 26 volume (nullable; [0, 9,999,999])
 * @param lessLogSalesCost item 26 cost (nullable; ±999,999,999, widened costSize 9)
 */
public record Schedule2Request(
    @NotNull(message = "revisionCount is required") Integer revisionCount,
    String comments,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer purchasedLogCostCost,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}")
    @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}")
    BigDecimal lessLogSalesVolume,
    @Min(value = -999999999, message = "{costSize9ValidatorErrorMsg}")
    @Max(value = 999999999, message = "{costSize9ValidatorErrorMsg}")
    Integer lessLogSalesCost) {
}
