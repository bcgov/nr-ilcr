package ca.bc.gov.nrs.ilcr.schedule1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * The Schedule 1 PUT request (AD-12) — entered fields ONLY. Derived values ({@code perUnit},
 * subtotals, {@code total}/{@code subtotalCompanyLogging}), read-only metadata
 * ({@code trackStatus}/{@code editable}) and pulled fields ({@code crownVolume},
 * {@code forestMgmtAdminCost}, {@code lessSilvAdminCost}) are NOT part of the request and are
 * recomputed/preserved server-side (AD-5). The server is the sole writer of derived data.
 *
 * <p>Range validation is Jakarta Bean Validation whose messages resolve the LEGACY bundle keys
 * (AD-6/AD-8) — the application {@code MessageSource} is wired into the validator
 * ({@code ValidationConfiguration}). Null values are permitted (legacy accepts blank amounts at
 * Save; Check Status catches missing required fields — Story 2.6).
 *
 * @param revisionCount optimistic-lock token from the last GET (AR11); mismatch → 409
 * @param comments free-text schedule comments (nullable)
 * @param lineItems the writable fixed line items (codes 12–18); other codes are ignored
 * @param silviculture the writable silviculture entries (codes 1 &amp; 2)
 * @param otherCostsVolume the shared Other-Costs volume (code-19 null-description row) only
 */
public record Schedule1Request(
    @NotNull(message = "revisionCount is required") Integer revisionCount,
    String comments,
    @Valid List<LineItemInput> lineItems,
    @Valid SilvicultureInput silviculture,
    @DecimalMin(value = "-99999999", message = "{volume8DigitValidatorErrorMsg}")
    @DecimalMax(value = "99999999", message = "{volume8DigitValidatorErrorMsg}")
    BigDecimal otherCostsVolume,
    // Forest Management Administration (143) volume — user-entered 8-digit; its cost is pulled from
    // Schedule 3 (BR-04) and is not part of this request.
    @DecimalMin(value = "-99999999", message = "{volume8DigitValidatorErrorMsg}")
    @DecimalMax(value = "99999999", message = "{volume8DigitValidatorErrorMsg}")
    BigDecimal forestMgmtAdminVolume,
    // Subtotal Company Logging (144) volume — user-entered 8-digit; its cost is derived server-side.
    @DecimalMin(value = "-99999999", message = "{volume8DigitValidatorErrorMsg}")
    @DecimalMax(value = "99999999", message = "{volume8DigitValidatorErrorMsg}")
    BigDecimal subtotalCompanyLoggingVolume) {

  /**
   * One writable fixed line item. Volume is the 7-digit group (±9,999,999, FLD-002); cost is the
   * default range (±99,999,999, FLD-001).
   */
  public record LineItemInput(
      Integer costItemCode,
      @DecimalMin(value = "-9999999", message = "{volume7DigitValidatorErrorMsg}")
      @DecimalMax(value = "9999999", message = "{volume7DigitValidatorErrorMsg}")
      BigDecimal volume,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer cost) {
  }

  /**
   * The writable silviculture entries. {@code actualSpent} (1) and {@code accruedLessActual} (2) are
   * volume + cost; {@code lessAdminVolume} (139) and {@code totalVolume} (140) are VOLUME only (7-digit)
   * — their cost is pulled from Schedule 3 (139) or derived (140), not client-written.
   */
  public record SilvicultureInput(
      @Valid EntryAmount actualSpent,
      @Valid EntryAmount accruedLessActual,
      @DecimalMin(value = "-9999999", message = "{volume7DigitValidatorErrorMsg}")
      @DecimalMax(value = "9999999", message = "{volume7DigitValidatorErrorMsg}")
      BigDecimal lessAdminVolume,
      @DecimalMin(value = "-9999999", message = "{volume7DigitValidatorErrorMsg}")
      @DecimalMax(value = "9999999", message = "{volume7DigitValidatorErrorMsg}")
      BigDecimal totalVolume) {
  }

  /** A writable volume/cost pair (7-digit volume, default cost). */
  public record EntryAmount(
      @DecimalMin(value = "-9999999", message = "{volume7DigitValidatorErrorMsg}")
      @DecimalMax(value = "9999999", message = "{volume7DigitValidatorErrorMsg}")
      BigDecimal volume,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer cost) {
  }
}
