package ca.bc.gov.nrs.ilcr.schedule10.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The material-composition percentages of a road-detail write. The derived {@code totalPct} is
 * deliberately ABSENT — it is computed server-side.
 *
 * <p><strong>FIVE percentages, not six.</strong> Boulder Area % is removed by business direction
 * and must not appear on the wire. The column remains in the table and is simply never written.
 *
 * <p><strong>There is deliberately no sum-to-100 rule on save.</strong> Legacy has none — the save
 * succeeds regardless of what the five add up to. Check Status is where the total is enforced, and
 * it carries a quirk worth knowing: because the legacy total coerces nulls to zero and is therefore
 * never null, a road detail with all five percentages blank still reports that the total must equal
 * 100. That is reproduced in the Check Status port, not here.
 *
 * @param solidRockPct the solid (hard) rock percentage (optional, 0–100)
 * @param rippableRockPct the rippable rock percentage (optional, 0–100)
 * @param coarsePct the coarse material percentage (optional, 0–100)
 * @param finePct the fine material percentage (optional, 0–100)
 * @param organicPct the organic material percentage (optional, 0–100)
 */
public record MaterialCompositionRequest(
    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}")
    Integer solidRockPct,

    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}")
    Integer rippableRockPct,

    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}")
    Integer coarsePct,

    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}")
    Integer finePct,

    @Min(value = 0, message = "{percentageValidatorErrorMsg}")
    @Max(value = 100, message = "{percentageValidatorErrorMsg}")
    Integer organicPct) {
}
