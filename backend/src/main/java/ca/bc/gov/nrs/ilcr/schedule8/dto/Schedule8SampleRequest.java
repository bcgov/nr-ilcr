package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * The Schedule 8 sample save (create-or-edit) request (Story 14.3, AD-12 write contract). Targets one
 * {@code TREE_TO_TRUCK_DETAIL_REPORT} row under a page; {@code id} null = CREATE, present = EDIT.
 * {@code revisionCount} is the per-sample optimistic-lock token (null on create).
 *
 * <p>Required at Save: {@code contractId} (S20). Each of the six skidding/yarding %s is individually
 * 0–100 (S17, {@code percentageValidatorErrorMsg}); their cross-field rules (sum ≤ 100 at Save — never
 * exact-100; Helicopter- and Other-conditional required fields) live in {@link Schedule8SampleRules}.
 * Volumes are 0..9,999,999 (S25); {@code originalRate} is 0..999,999.99 when provided (S26). Cut Block,
 * volumes, Original Rate, and the skyline/cycle fields are otherwise optional at Save (required only at
 * Check Status — 14.6). {@code uphillDirection}/{@code waterDumpDestination} are nullable Booleans so
 * "not provided" is distinguishable when the Helicopter conditional requires them. Derived/read-only
 * fields ({@code percentTotal}, {@code actualHarvested}, totals, labels) are never accepted (AD-5).
 */
@Schedule8SampleRules
public record Schedule8SampleRequest(
    Integer id,
    Integer revisionCount,
    @NotBlank(message = "{missingRequiredFieldMsg}") String contractId,
    String cutBlock,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer groundBasePct,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer grapplePct,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer skylinePct,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer highleadPct,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer helicopterPct,
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}") Integer otherSkiddingPct,
    Integer skylineSlopeDistance,
    Integer skylineSupportNumber,
    BigDecimal supportAvgDistance,
    BigDecimal cycleTime,
    BigDecimal distance,
    Boolean uphillDirection,
    Boolean waterDumpDestination,
    String skidTypeCode,
    @Min(value = 0, message = "{volumeValidatorErrorMsg}")
    @Max(value = 9_999_999, message = "{volumeValidatorErrorMsg}") Integer coniferousVolume,
    @Min(value = 0, message = "{volumeValidatorErrorMsg}")
    @Max(value = 9_999_999, message = "{volumeValidatorErrorMsg}") Integer deciduousVolume,
    @DecimalMin(value = "0", message = "{treeToTruckRateValidatorErrorMsg}")
    @DecimalMax(value = "999999.99", message = "{treeToTruckRateValidatorErrorMsg}")
    BigDecimal originalRate) {
}
