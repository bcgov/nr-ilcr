package ca.bc.gov.nrs.ilcr.schedule7a.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 7A bridge (AD-12). Entered fields only — the four derived totals
 * and read-only document fields are never client-supplied. Range/required messages resolve the
 * LEGACY bundle keys (AD-8) via the wired {@code MessageSource} ({@code ValidationConfiguration}).
 *
 * <p>Required (legacy JSF {@code required="true"} on {@code schedule7A.xhtml}, BR-03/BR-04):
 * {@code locationName}, {@code builtDate}, the five code values, {@code lifeSpan},
 * {@code abutmentHeight}, {@code length}, {@code width}, {@code distance} (slices S06/S07/S15/
 * S21–S26). The ten costs are OPTIONAL at Save (legacy — only Check Status flags missing costs,
 * BR-08). Ranges: life span 0–999 (BR-04); abutment height / length / width 0.0–9,999.9 (BR-04);
 * distance 0–9,999 (BR-04 — corrected range, the legacy message text said 0–999.99 against an
 * enforced 0–9,999); each cost ±99,999,999 (BR-06). {@code builtDate} format ({@code yyyy-MM},
 * non-lenient) is checked in {@link ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService} (verbatim
 * {@code bridgeDateformatErrorMsg}) since Bean Validation cannot express a non-lenient calendar
 * parse.
 *
 * <p>{@code revisionCount} is the per-row optimistic-lock token — required only on UPDATE (the
 * {@link OnUpdate} group), ignored on create.
 *
 * @param locationName the bridge name/location (required, ≤ 30)
 * @param builtDate the completion date (required; {@code yyyy-MM}, validated in the service)
 * @param constructionTypeCode the New/Used code (required; must resolve to a code-table row)
 * @param superstructureTypeCode the superstructure type code (required)
 * @param deckTypeCode the decking type code (required)
 * @param abutmentTypeCode the abutment type code (required)
 * @param loadRatingCode the load rating code (required)
 * @param lifeSpan the expected life span (required, 0–999)
 * @param abutmentHeight the abutment height (required, 0.0–9,999.9)
 * @param length the bridge length (required, 0.0–9,999.9)
 * @param width the deck width (required, 0.0–9,999.9)
 * @param distance the distance from storage (required, 0–9,999)
 * @param sitePlanCost site plan (optional, ±99,999,999); null clears the row
 * @param superstructureMaterialCost superstructure material (optional)
 * @param superstructureDeliverCost superstructure deliver (optional)
 * @param superstructureInstallCost superstructure install (optional)
 * @param abutmentMaterialCost abutment material (optional)
 * @param abutmentDeliverCost abutment deliver (optional)
 * @param abutmentInstallCost abutment install (optional)
 * @param approachCost approach works (optional)
 * @param afterInstallCost certification after install (optional)
 * @param otherCost other costs (optional)
 * @param comments the row comments (optional, ≤ 3500)
 * @param revisionCount the optimistic-lock token echoed from the served row (required on UPDATE)
 */
public record BridgeRequest(
    @NotBlank(message = "{missingRequiredFieldMsg}")
    @Size(max = 30, message = "{bridgeLocationMaxLengthErrorMsg}")
    String locationName,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String builtDate,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String constructionTypeCode,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String superstructureTypeCode,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String deckTypeCode,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String abutmentTypeCode,

    @NotBlank(message = "{missingRequiredFieldMsg}")
    String loadRatingCode,

    @NotNull(message = "{missingRequiredFieldMsg}")
    @Min(value = 0, message = "{lifeSpanValidatorErrorMsg}")
    @Max(value = 999, message = "{lifeSpanValidatorErrorMsg}")
    Integer lifeSpan,

    @NotNull(message = "{missingRequiredFieldMsg}")
    @DecimalMin(value = "0.0", message = "{abutmentsHtValidatorErrorMsg}")
    @DecimalMax(value = "9999.9", message = "{abutmentsHtValidatorErrorMsg}")
    @Digits(integer = 4, fraction = 1, message = "{abutmentsHtValidatorErrorMsg}")
    BigDecimal abutmentHeight,

    @NotNull(message = "{missingRequiredFieldMsg}")
    @DecimalMin(value = "0.0", message = "{bridgeLengthValidatorErrorMsg}")
    @DecimalMax(value = "9999.9", message = "{bridgeLengthValidatorErrorMsg}")
    @Digits(integer = 4, fraction = 1, message = "{bridgeLengthValidatorErrorMsg}")
    BigDecimal length,

    @NotNull(message = "{missingRequiredFieldMsg}")
    @DecimalMin(value = "0.0", message = "{bridgeWidthValidatorErrorMsg}")
    @DecimalMax(value = "9999.9", message = "{bridgeWidthValidatorErrorMsg}")
    @Digits(integer = 4, fraction = 1, message = "{bridgeWidthValidatorErrorMsg}")
    BigDecimal width,

    @NotNull(message = "{missingRequiredFieldMsg}")
    @Min(value = 0, message = "{bridgeDistanceValidatorErrorMsg}")
    @Max(value = 9999, message = "{bridgeDistanceValidatorErrorMsg}")
    Integer distance,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer sitePlanCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer superstructureMaterialCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer superstructureDeliverCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer superstructureInstallCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer abutmentMaterialCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer abutmentDeliverCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer abutmentInstallCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer approachCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer afterInstallCost,

    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer otherCost,

    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}")
    String comments,

    @NotNull(groups = OnUpdate.class, message = "{missingRequiredFieldMsg}")
    Integer revisionCount) {
}
