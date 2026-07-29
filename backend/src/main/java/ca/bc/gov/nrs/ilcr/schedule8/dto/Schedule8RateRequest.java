package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The Schedule 8 rate-detail save (add-or-edit) request (Story 14.4, AD-12 write contract). Targets one
 * {@code TREE_TO_TRUCK_RATE_DETAIL} row under a sample; {@code id} null = ADD, present = EDIT.
 * {@code revisionCount} is the per-row optimistic-lock token (null on add).
 *
 * <p>Whether the row is an addition or a deduction is NOT carried here — it is derived on the read
 * (14.1) from the chosen cost item's {@code ILCR_SUBCATEGORY_ID} ('1'/'2' = addition, '3'/'4' =
 * deduction). Required at Save (S21, FLD-006): {@code costItemCode}, {@code costingRate}, and
 * {@code costTypeCode}. {@code itemDescription} is optional free text (≤ 30). {@code costingRate} is a
 * {@code $/m³} figure bounded 0..9,999,999.99 (S27). {@code costTypeDescription} is read-only (resolved
 * from the code table on read), so it is not accepted here.
 */
public record Schedule8RateRequest(
    Integer id,
    Integer revisionCount,
    @NotNull(message = "{missingRequiredFieldMsg}") Integer costItemCode,
    @NotNull(message = "{missingRequiredFieldMsg}")
    @DecimalMin(value = "0", message = "{costingRateValidatorErrorMsg}")
    @DecimalMax(value = "9999999.99", message = "{costingRateValidatorErrorMsg}")
    BigDecimal costingRate,
    @NotBlank(message = "{missingRequiredFieldMsg}") String costTypeCode,
    @Size(max = 30, message = "Description can not exceed 30 characters.") String itemDescription) {
}
