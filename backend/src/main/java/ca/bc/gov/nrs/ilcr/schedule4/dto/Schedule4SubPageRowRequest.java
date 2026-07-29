package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The Schedule 4 sub-page list-row add request (Story 4.3, AD-12). One row = a separate
 * {@code TRANSPORTATION_REPORT} sharing the location's name + a single {@code ILCR_COST_REPORT_DETAIL}
 * (item {@code type.code()} 43/46/55, with {@code ITEM_DESCRIPTION}).
 *
 * <p>Range validation resolves the LEGACY bundle keys (AD-6/AD-8, §Decision 3 pins the cost range to
 * the {@code costSize=7} band for all three sub-pages): Volume ∈ [0, 999,999]
 * ({@code volume6DigitValidatorErrorMsg}); Cost ∈ [-9,999,999, 9,999,999]
 * ({@code costSize7ValidatorErrorMsg}); Distance ∈ [0.0, 999,999.9]
 * ({@code distanceValidatorErrorMsg}); Cycle ∈ [0, 999,999] ({@code cycleValidatorErrorMsg}). Amounts
 * are nullable (range-checked only when present); {@code description} is required (≤ 120). {@code cycle}
 * applies to {@code TRUCK_REHAUL} only — the server ignores it for the other two types.
 *
 * @param type the sub-page type (required; determines the cost-item code)
 * @param description the row description (required, ≤ 120; stored as {@code ITEM_DESCRIPTION})
 * @param distance the row distance (nullable; [0.0, 999,999.9])
 * @param volume the row volume (nullable; [0, 999,999])
 * @param cost the row cost, whole dollars (nullable; ±9,999,999)
 * @param cycle the transportation cycle time (nullable; [0, 999,999]; Truck Rehaul only)
 */
public record Schedule4SubPageRowRequest(
    @NotNull(message = "type is required") SubPageRowType type,
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 120, message = "Description can not exceed 120 characters.")
    String description,
    @DecimalMin(value = "0", message = "{distanceValidatorErrorMsg}")
    @DecimalMax(value = "999999.9", message = "{distanceValidatorErrorMsg}")
    BigDecimal distance,
    @DecimalMin(value = "0", message = "{volume6DigitValidatorErrorMsg}")
    @DecimalMax(value = "999999", message = "{volume6DigitValidatorErrorMsg}")
    BigDecimal volume,
    @Min(value = -9999999, message = "{costSize7ValidatorErrorMsg}")
    @Max(value = 9999999, message = "{costSize7ValidatorErrorMsg}")
    Integer cost,
    @Min(value = 0, message = "{cycleValidatorErrorMsg}")
    @Max(value = 999999, message = "{cycleValidatorErrorMsg}")
    Integer cycle) {
}
