package ca.bc.gov.nrs.ilcr.schedule10.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The additional-stabilizing half of a road-detail write: the two ballast codes, four dimensions and
 * three costs. The derived {@code total} and {@code costPerLength} are deliberately ABSENT.
 *
 * <p><strong>{@code ballastMaterialCode} is CONDITIONALLY required</strong> — only when
 * {@code ballastMethodCode} is {@code "C"} ({@code isAdditionalStabilizingMandatory}
 * :1125-1135). That cross-field rule lives in the service, not here, because Bean Validation on a
 * single record component cannot see a sibling. The method code itself is unconditionally required.
 *
 * <p><strong>The ballast method drives more than requiredness.</strong> Legacy couples it to the
 * data: methods {@code N} and {@code D} clear all four dimensions and both costs and force the
 * material code to {@code "NA"}, while {@code C} — and, in legacy, a blank code — coerces both costs
 * to zero. The service reproduces that coupling, including legacy's asymmetry where {@code N} writes
 * zeros into the four dimensions and {@code D} leaves them null.
 *
 * <p>Note the band asymmetry legacy carries between its own two surfaces: the form permits negative
 * transfers here (±9,999,999) while its Check Status demands they be 0 or greater. Both are
 * reproduced as-is — the save accepts what the form accepted, and Check Status flags what it flagged.
 *
 * @param ballastMethodCode the ballast method (required)
 * @param ballastMaterialCode the ballast material; required only when the method is {@code "C"}, and
 *     forced to {@code "NA"} by the service for methods {@code N} and {@code D}
 * @param length the stabilizing length in km (optional, 0–999.999 — the correct cap, unlike
 *     sub-grade's legacy 100)
 * @param surfaceWidth the stabilizing surface width in m (optional, 0–999.9)
 * @param depth the stabilizing depth in m (optional, 0–99.9)
 * @param distanceToSource the distance to source in km (optional, 0–999.9)
 * @param actualCost the actual cost (optional, 0–9,999,999)
 * @param ttTransfer the tree-to-truck transfer (optional, ±9,999,999); may be negative
 * @param otherTransfer the other transfer (optional, ±9,999,999); may be negative
 */
public record StabilizingRequest(
    @NotBlank(message = "{javax.faces.component.UIInput.REQUIRED}")
    @Size(max = 10, message = "{invalidCodeValueErrorMsg}")
    String ballastMethodCode,

    @Size(max = 10, message = "{invalidCodeValueErrorMsg}")
    String ballastMaterialCode,

    @DecimalMin(value = "0", message = "{invalidRangeErrorMsg}")
    @DecimalMax(value = "999.999", message = "{invalidRangeErrorMsg}")
    BigDecimal length,

    @DecimalMin(value = "0", message = "{invalidRangeErrorMsg}")
    @DecimalMax(value = "999.9", message = "{invalidRangeErrorMsg}")
    BigDecimal surfaceWidth,

    @DecimalMin(value = "0", message = "{invalidRangeErrorMsg}")
    @DecimalMax(value = "99.9", message = "{invalidRangeErrorMsg}")
    BigDecimal depth,

    @DecimalMin(value = "0", message = "{invalidRangeErrorMsg}")
    @DecimalMax(value = "999.9", message = "{invalidRangeErrorMsg}")
    BigDecimal distanceToSource,

    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}")
    @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}")
    Integer actualCost,

    @Min(value = -9999999, message = "{costSize7ValidatorErrorMsg}")
    @Max(value = 9999999, message = "{costSize7ValidatorErrorMsg}")
    Integer ttTransfer,

    @Min(value = -9999999, message = "{costSize7ValidatorErrorMsg}")
    @Max(value = 9999999, message = "{costSize7ValidatorErrorMsg}")
    Integer otherTransfer) {
}
