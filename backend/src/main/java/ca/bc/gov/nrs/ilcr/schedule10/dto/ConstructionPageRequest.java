package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Add/edit request for one Schedule 10 construction page. Entered fields only — the derived Road
 * Group, page number, page label and road-detail count are deliberately ABSENT so a client cannot
 * supply them (they are computed on every read and there is no Road Group column at all). Messages
 * resolve LEGACY bundle keys via the wired {@code MessageSource}.
 *
 * <p><strong>{@code tsaOrTfl} is one field carrying two meanings</strong>, exactly as the legacy
 * screen models it: the single {@code pageTSATFL} dropdown holds either a TSA number or the literal
 * sentinel {@code "TFL"} ({@code Constant.TFL}). The location is one or the other, never both
 * (BR-05), and the server clears the counterpart before persisting rather than trusting the client
 * — legacy enforced the exclusion in the UI only, and its DAO would happily store an inconsistent
 * combination from a crafted post.
 *
 * <p><strong>{@code divisionName} is capped at 20, not the screen's 30.</strong> {@code
 * CONSTRUCTION_DIVISION_NAME} is {@code VARCHAR2(20)} in delivery while {@code
 * schedule10.xhtml:140} sets {@code maxlength="30"}, so legacy raises {@code ORA-12899} on 21 or
 * more characters today. A 400 naming the field beats an opaque 500; widening the column is a
 * Ministry decision recorded against this schedule.
 *
 * <p><strong>{@code constructionPeriod} is pattern-enforced.</strong> Legacy's converter returns
 * the raw String when no {@code dateType} attribute is set (Schedule 10 sets none), the field
 * carries no {@code maxlength}, and {@code SimpleDateFormat("yyyy-MM")} accepts {@code 2024-1} —
 * which then persists into a {@code VARCHAR2} column and throws {@code
 * StringIndexOutOfBoundsException} when re-rendered. The strict pattern closes that.
 *
 * @param forestRegionCode the forest region (required — FLD-001)
 * @param tsaOrTfl a TSA number, or the literal {@code "TFL"} for a TFL-located page (required —
 *     FLD-002)
 * @param supplyBlock the supply block; applies only on the TSA branch and is cleared server-side on
 *     the TFL branch (BR-05)
 * @param tflNumberCode the TFL number; required and validated only on the TFL branch, and cleared
 *     server-side on the TSA branch (BR-05)
 * @param divisionName the construction division (optional, ≤ 20 characters — the column width, not
 *     the screen's 30)
 * @param constructionPeriod the period surveyed as {@code YYYY-MM} (optional; FLD-010 on a bad
 *     format)
 * @param revisionCount the optimistic-lock token echoed from the served page (required on UPDATE)
 */
public record ConstructionPageRequest(
    @NotBlank(message = "{regionRequiredErrorMsg}")
    @Size(max = 10, message = "{invalidCodeValueErrorMsg}")
    String forestRegionCode,

    // max 3 accommodates the literal "TFL" sentinel. The TSA branch is additionally checked against
    // TSA_NUMBER's VARCHAR2(2) in the service, because a 3-character non-TFL code would otherwise
    // reach Oracle and raise ORA-12899 as an opaque 500 instead of a 400 naming the field.
    @NotBlank(message = "{schedule10TsaOrTflRequiredErrorMsg}")
    @Size(max = 3, message = "{invalidCodeValueErrorMsg}")
    String tsaOrTfl,

    @Size(max = 3, message = "{invalidCodeValueErrorMsg}")
    String supplyBlock,

    @Size(max = 2, message = "{tflNumberValidatorErrorMsg}")
    String tflNumberCode,

    // Both units. The column is VARCHAR2(20) with BYTE semantics, so 20 accented characters clear
    // @Size and then raise ORA-12899 — the opaque 500 this field's own message exists to replace
    // (code review 2026-08-18).
    @Size(max = 20, message = "{divisionNameMaxLengthErrorMsg}")
    @MaxByteLength(value = 20, charMax = 20, message = "{divisionNameMaxLengthErrorMsg}")
    String divisionName,

    // The month is range-checked, not just shaped. Legacy stores the raw string (its converter has
    // no dateType attribute), so "2024-99" persists there and then flows into every page label and
    // into the print reports. This extends the strict-pattern deviation already recorded for this
    // field rather than adding a new one: the rationale is identical — keep an unrenderable value
    // out of a VARCHAR2 column (code review 2026-08-18).
    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "{bridgeDateformatErrorMsg}")
    String constructionPeriod,

    // @Min(0) as well as @NotNull: a never-issued token like -1 matches no row, so without the
    // floor it reaches the optimistic-lock UPDATE, misses, and surfaces as a 409 "changed by
    // another user" for what is simply a malformed body.
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}")
    @Min(value = 0, message = "{revisionCountRequiredErrorMsg}")
    Integer revisionCount) {
}
