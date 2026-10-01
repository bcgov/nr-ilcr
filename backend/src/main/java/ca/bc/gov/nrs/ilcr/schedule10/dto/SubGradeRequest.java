package ca.bc.gov.nrs.ilcr.schedule10.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * The sub-grade half of a road-detail write: two dimensions, three costs, and the six "Less"
 * deduction lines. The four derived values ({@code totalCosts}, {@code totalDeductions}, {@code
 * total}, {@code costPerLength}) are deliberately ABSENT — they are computed server-side on every
 * read and must never be accepted from a client.
 *
 * <p><strong>Every cost here is a stored row, not a column.</strong> Each maps to a legacy
 * cost-item ordinal in {@code ILCR_COST_REPORT_DETAIL}, and the six deductions span THREE
 * subcategories — {@code lessOtherEng} and {@code otherTransfer} live under subcategory 3 rather
 * than 1. Routing is by item id in the service.
 *
 * <p><strong>{@code length} is capped at 100, not 999.999.</strong> That looks wrong beside the
 * additional-stabilizing length, which correctly allows 999.999 — but legacy binds this field's
 * maximum to {@code percent.maxValue} ({@code schedule10.xhtml:507}) and its Check Status agrees
 * ({@code Schedule10CheckStatus:124}), so the cap is consistent across both legacy surfaces and is
 * part of the parity baseline. Raising it is a product decision, not a developer one.
 *
 * <p>Ranges are enforced as RANGE ONLY, with no {@code @Digits}: Bean Validation's {@code @Digits}
 * reads {@code BigDecimal.scale()}, so {@code 12.50} would be rejected while the numerically
 * identical {@code 12.5} passed. Scale is normalised on write instead.
 *
 * @param length the sub-grade length in km (optional, 0–100 — the legacy cap)
 * @param surfaceWidth the sub-grade surface width in m (optional, 0–999.9)
 * @param actualCost the actual cost (optional, 0–9,999,999); null clears the cost in place
 * @param ttTransfer the tree-to-truck transfer (optional, ±9,999,999); may be negative
 * @param otherTransfer the other transfer (optional, ±9,999,999); may be negative
 * @param lessBridges the bridges deduction (optional, 0–9,999,999)
 * @param lessCulverts the culverts deduction (optional, 0–9,999,999)
 * @param lessLandings the landings deduction (optional, 0–9,999,999)
 * @param lessOverland the overland deduction (optional, 0–9,999,999)
 * @param lessOtherEng the other-engineering deduction (optional, 0–9,999,999)
 * @param lessEndHaul the end-haul deduction (optional, 0–9,999,999)
 */
public record SubGradeRequest(
    @DecimalMin(value = "0", message = "{rangeZeroToOneHundredErrorMsg}") @DecimalMax(value = "100", message = "{rangeZeroToOneHundredErrorMsg}") BigDecimal length,
    @DecimalMin(value = "0", message = "{rangeZeroTo999Point9ErrorMsg}") @DecimalMax(value = "999.9", message = "{rangeZeroTo999Point9ErrorMsg}") BigDecimal surfaceWidth,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer actualCost,
    @Min(value = -9999999, message = "{costSize7ValidatorErrorMsg}") @Max(value = 9999999, message = "{costSize7ValidatorErrorMsg}") Integer ttTransfer,
    @Min(value = -9999999, message = "{costSize7ValidatorErrorMsg}") @Max(value = 9999999, message = "{costSize7ValidatorErrorMsg}") Integer otherTransfer,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessBridges,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessCulverts,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessLandings,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessOverland,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessOtherEng,
    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}") @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}") Integer lessEndHaul) {}
