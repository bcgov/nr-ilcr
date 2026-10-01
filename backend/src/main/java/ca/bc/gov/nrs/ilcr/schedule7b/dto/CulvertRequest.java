package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 7B culvert (AD-12). Entered fields only — {@code totalCost} is
 * derived and is deliberately ABSENT from this record, so a client cannot supply it (BR-05; the
 * legacy field is {@code disabled="true"}, {@code schedule7B.xhtml:197}). Range/required messages
 * resolve the LEGACY bundle keys (AD-8) via the wired {@code MessageSource} ({@code
 * ValidationConfiguration}).
 *
 * <p><strong>Only TWO fields are required at Save.</strong> Legacy {@code required="true"} sits on
 * Type ({@code schedule7B.xhtml:87}) and No of Pieces ({@code :153}) alone. Span, Rise, Length,
 * both costs, and Comments are OPTIONAL at Save — their absence is flagged only by Check Status
 * (BR-07, S15–S20). Do not tighten these to match Schedule 7A, which required twelve fields: a
 * reporter must be able to save a partially-measured culvert and come back to it, exactly as in
 * legacy.
 *
 * <p>Ranges (BR-04/BR-05, from {@code schedule7B.xhtml} + legacy {@code messages.properties}): span
 * and rise 0–9,999,999 ({@code zero.minValue}/{@code schedule7b.size.maxValue}); length
 * 0.0–999,999.9 ({@code 6digits1decimal.maxValue}) — RANGE only, with any extra decimal places
 * rounded to scale 1 on write rather than rejected (see {@code length} below); piece count 1–9,999
 * ({@code one.minValue}/{@code 4digits.maxValue}); each cost ±99,999,999 ({@code ILCRCostValidator}
 * default {@code costSize} branch); comments ≤ 3,500 CHARACTERS and ≤ 4,000 BYTES (the textarea's
 * {@code maxlength} and the column's real width — both enforced, see {@code comments} below).
 *
 * <p>{@code revisionCount} is the per-row optimistic-lock token — required only on UPDATE (the
 * {@link OnUpdate} group), ignored on create.
 *
 * @param culvertTypeCode the culvert type code (required; must resolve to a code-table row
 *     effective for the reporting year)
 * @param spanSize the span in mm (optional, 0–9,999,999)
 * @param riseSize the rise in mm (optional, 0–9,999,999)
 * @param length the length in m (optional, 0.0–999,999.9; extra decimals are rounded, not rejected)
 * @param culvertPieceCount the number of pieces (required, 1–9,999)
 * @param materialCost the material cost (optional, ±99,999,999); null clears the cost in place
 * @param installCost the installation cost (optional, ±99,999,999); null clears the cost in place
 * @param comments the row comments (optional, ≤ 3500 characters AND ≤ 4000 UTF-8 bytes)
 * @param revisionCount the optimistic-lock token echoed from the served row (required on UPDATE)
 */
public record CulvertRequest(
    @NotBlank(message = "{missingRequiredFieldMsg}") String culvertTypeCode,
    @Min(value = 0, message = "{culvertSpanValidatorErrorMsg}") @Max(value = 9999999, message = "{culvertSpanValidatorErrorMsg}") Integer spanSize,
    @Min(value = 0, message = "{culvertRiseValidatorErrorMsg}") @Max(value = 9999999, message = "{culvertRiseValidatorErrorMsg}") Integer riseSize,

    // Range ONLY — deliberately no @Digits. Bean Validation's @Digits reads BigDecimal.scale(), so
    // `12.50` (numerically one decimal, scale 2) violated `fraction = 1` and was rejected with a
    // RANGE message for a value squarely inside the range, while the identical `12.5` passed. Any
    // client that formats to two decimals could not save a culvert at all. Legacy applied
    // f:validateDoubleRange (range only, schedule7B.xhtml:378-379) and pinned the scale in its
    // display
    // converter instead — `f:convertNumber pattern="###,##0.0"` (messages.properties:206), one
    // decimal
    // — so it never wrote more. The column is NUMBER(8,2) in delivery and so would ACCEPT two
    // decimals;
    // scale 1 is legacy's converter, not a column limit. Schedule7bService normalizes on write.
    @DecimalMin(value = "0.0", message = "{culvertLengthValidatorErrorMsg}") @DecimalMax(value = "999999.9", message = "{culvertLengthValidatorErrorMsg}") BigDecimal length,
    @NotNull(message = "{missingRequiredFieldMsg}") @Min(value = 1, message = "{culvertPieceCountValidatorErrorMsg}") @Max(value = 9999, message = "{culvertPieceCountValidatorErrorMsg}") Integer culvertPieceCount,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer materialCost,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer installCost,

    // Two units, both enforced. 3,500 CHARACTERS is the legacy screen's own maxlength
    // (schedule7B.xhtml:221,490); 4,000 BYTES is the column's real width. @Size alone let 3,500
    // accented or CJK characters through into a VARCHAR2(4000 BYTE) column, where Oracle raised
    // ORA-12899 and the service's blanket DataAccessException catch could only surface it as an
    // opaque 500 "Schedule could not be saved." — on an ordinary save, with a page-level Save
    // rolling the whole batch back and nothing pointing at the comment.
    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}") @MaxByteLength(value = 4000, charMax = 3500, message = "{commentsMaxLengthErrorMsg}")
        String comments,

    // @Min(0) as well as @NotNull: a never-issued token like -1 matches no row, so without the
    // floor it reached the optimistic-lock UPDATE, missed, and surfaced as a 409 "someone else
    // changed this row" for what is simply a malformed body — the phantom conflict OnUpdate exists
    // to prevent.
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}") @Min(value = 0, message = "{revisionCountRequiredErrorMsg}") Integer revisionCount) {}
