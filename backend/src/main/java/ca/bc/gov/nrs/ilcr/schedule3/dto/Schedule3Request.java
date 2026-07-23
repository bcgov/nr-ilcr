package ca.bc.gov.nrs.ilcr.schedule3.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Schedule 3 PUT request (AD-12) — entered fields ONLY. Derived values (each line's {@code crown},
 * subtotals/totals, timber costs, {@code perUnit}), read-only metadata ({@code trackStatus}/
 * {@code editable}/counts) and the sub-page rows (items 124/38, Story 4.4) are NOT part of the request
 * and are recomputed/preserved server-side (AD-5). The server is the sole writer of derived data.
 *
 * <p>Range validation is Jakarta Bean Validation resolving the LEGACY bundle keys (AD-6/AD-8) — the
 * application {@code MessageSource} is wired into the validator ({@code ValidationConfiguration}).
 * Null amounts are permitted (legacy accepts blank at Save; Check Status catches missing required
 * fields). Schedule 3 volumes are non-negative ({@code [0, 9,999,999]}, {@code volumeValidatorErrorMsg})
 * — distinct from Schedule 1's signed 7-digit range.
 *
 * @param revisionCount optimistic-lock token from the last GET (AR11); mismatch → 409
 * @param comments free-text schedule comments (nullable, max 3500)
 * @param overrideHarvestTotalPop the Override Harvest/Total PO&amp;P indicator ("Y"/"N"); persisted to
 *     {@code ILCR_REPORT_SUMMARY.LOCATION}
 * @param lineItems the 11 fixed admin-cost lines' entered Harvest/PO&amp;P amounts (by cost-item code);
 *     PO&amp;P is ignored for the Harvest-only lines (29/33/37)
 * @param popTimberVolume PO&amp;P Timber (item 118) volume
 * @param crownTimberVolume Crown Timber (item 119) volume — drives the BR-09 push into Schedule 1
 */
public record Schedule3Request(
    @NotNull(message = "revisionCount is required") Integer revisionCount,
    String comments,
    String overrideHarvestTotalPop,
    @Valid List<CostLineInput> lineItems,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}")
    @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}")
    BigDecimal popTimberVolume,
    @DecimalMin(value = "0", message = "{volumeValidatorErrorMsg}")
    @DecimalMax(value = "9999999", message = "{volumeValidatorErrorMsg}")
    BigDecimal crownTimberVolume) {

  /**
   * One fixed line's entered amounts, keyed by its Harvest cost-item code (27–37). Both amounts are
   * whole-dollar costs in the default range (±99,999,999, FLD-001). {@code pop} is ignored on write for
   * the Harvest-only lines (Annual Rents 29, Scaling 33, Silviculture Admin 37).
   */
  public record CostLineInput(
      Integer costItemCode,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer harvest,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer pop) {
  }
}
