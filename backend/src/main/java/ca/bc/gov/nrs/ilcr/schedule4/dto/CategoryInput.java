package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * One entered transportation-category amount in a {@link Schedule4LocationRequest} (Story 4.2, AD-12
 * write contract). {@code code} is the legacy {@code ILCR_REPORT_COST_ITEM_ID}; the server derives
 * FIXED vs DISTANCE from the code and ignores any {@code distance} on a fixed code.
 *
 * <p>Range validation is Jakarta Bean Validation resolving the LEGACY bundle keys (AD-6/AD-8):
 * Volume ∈ [0, 9,999,999] ({@code volumeValidatorErrorMsg}); Cost ∈ [-99,999,999, 99,999,999]
 * ({@code costValidatorErrorMsg}); Distance ∈ [0.0, 999,999.9] ({@code distanceValidatorErrorMsg}).
 * BR-04 (distance ⇄ volume/cost, distance codes only) is the class-level
 * {@link DistanceCategoryComplete} cross-field constraint. All amounts are nullable — a blank
 * category (name-only location, S08) and a cleared field (persists null) are legal.
 *
 * @param code the legacy cost-item code (required; one of the 12 in-scope Schedule 4 codes)
 * @param volume the entered volume (nullable; [0, 9,999,999])
 * @param cost the entered cost, whole dollars (nullable; ±99,999,999)
 * @param distance the entered distance for a distance code (nullable; [0.0, 999,999.9]; ignored for
 *     fixed codes)
 */
@DistanceCategoryComplete
public record CategoryInput(
    @NotNull(message = "code is required") Integer code,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}")
    @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}")
    BigDecimal volume,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer cost,
    @DecimalMin(value = "0", message = "{distanceValidatorErrorMsg}")
    @DecimalMax(value = "999999.9", message = "{distanceValidatorErrorMsg}")
    BigDecimal distance) {
}
