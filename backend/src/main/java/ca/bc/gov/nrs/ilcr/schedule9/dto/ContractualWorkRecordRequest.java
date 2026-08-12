package ca.bc.gov.nrs.ilcr.schedule9.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 9 contractual-work record (AD-12) — CLIENT-SETTABLE fields only.
 * It REALIZES the document Story 9.1 pinned rather than re-pinning it: {@code costPerUnit} ($/Unit,
 * derived cost ÷ units, AD-5) never appears here and is ignored if sent, so a client that {@code
 * PUT}s the served record straight back is correct, not in error.
 *
 * <p><strong>Required fields are checked in the service, not declaratively.</strong> Legacy renders
 * one {@code javax.faces.component.UIInput.REQUIRED} ({@code "{0}: Value is required."}) line per
 * missing field in screen order (FLD-001) — Company ID, Contractual Item, Unit Type, Biogeoclimatic
 * Zone, Source — which a per-property {@code @NotBlank} cannot reproduce (Schedule 7A conflates this
 * with the Check-Status {@code "Value Required"}; Schedule 9's legacy save path does not). {@link
 * ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service} collects the missing labels and throws {@code
 * FieldValuesRequiredException}, and rejects a code outside its reference list (FLD-005, {@code
 * invalidCodeValueErrorMsg}). The three code fields therefore carry NO {@code @Size} — an over-length
 * code simply matches no list row and is a clean FLD-005 400.
 *
 * <p>The three "Other" free-text descriptions are NOT required at Save — legacy leaves them
 * un-required ({@code itemDescription} has no {@code required} attribute, {@code unitDescription} is
 * {@code required="false"}, and {@code sourceDescription} carries a misspelled {@code require=} JSF
 * ignores). They are only conditionally STORED — nulled when their driving select is not "Other" —
 * which the service applies; a blank "Other" description saves.
 *
 * <p><strong>Only the three numeric RANGES are declarative</strong> (FLD-002/003/004), because they
 * ARE per-property and their boundaries accept (S13): cost {@code 0..9,999,999}
 * ({@code costValidatorSchedule9ErrorMsg}); side slope {@code 0..100}
 * ({@code sideSlopePercentageValidatorErrorMsg} — the legacy SAVE bound is 100, unlike Check Status'
 * 99, the preserved Save-vs-Check gap); number of units {@code 0.0..99,999.9}
 * ({@code unitValidatorErrorMsg}). Units/cost are NOT required at Save — a blank saves and only Check
 * Status flags it. Each free-text {@code @Size} is the delivery column width so an over-length value
 * is a clean 400 rather than an ORA-12899 the service could only turn into a 500.
 *
 * <p>{@code revisionCount} is the record's optimistic-lock token ({@code
 * CONTRACTUAL_WORK_REPORT.REVISION_COUNT}, surfaced by 9.1) — required on UPDATE only ({@link
 * OnUpdate}; omitting it is a clean 400, never a coerced 409), ignored on create.
 *
 * @param contractorId the contractor Company ID — required (FLD-001), &le; 30 chars
 * @param contractualItemCode the Contractual Item cost-item code — required, must be 108–114 (BR-09)
 * @param itemDescription the "Other" item free text — required only when the item is 114, &le; 30
 * @param unitCode the Unit Type code — required, must resolve to an {@code ILCR_UNIT_CODE} row
 * @param unitDescription the "Other" unit free text — required only when the unit is {@code O}
 * @param numberOfUnits units performed (optional at Save; 0.0–99,999.9)
 * @param biogeoclimaticZone the BEC zone code — required, must resolve to a {@code BEC_ZONE_CODE} row
 * @param cost the work item cost (optional at Save; 0–9,999,999 whole dollars)
 * @param sideSlopePct side slope percentage (0–100; only meaningful for road-deactivation items)
 * @param sourceCode the cost Source code — required, must resolve to an
 *     {@code ILCR_CONTRACTUAL_SOURCE_CODE} row
 * @param sourceDescription the "Other" source free text — required only when the source is {@code O}
 *     or {@code S}
 * @param comments per-record comments (optional, &le; 2000)
 * @param revisionCount the optimistic-lock token — required on UPDATE only
 */
public record ContractualWorkRecordRequest(

    @MaxByteLength(value = 30, charMax = 30, message = "{contractorIdMaxLengthErrorMsg}")
    @Size(max = 30, message = "{contractorIdMaxLengthErrorMsg}")
    String contractorId,

    Integer contractualItemCode,

    @MaxByteLength(value = 30, charMax = 30, message = "{itemDescriptionMaxLengthErrorMsg}")
    @Size(max = 30, message = "{itemDescriptionMaxLengthErrorMsg}")
    String itemDescription,

    String unitCode,

    @MaxByteLength(value = 120, charMax = 120, message = "{unitDescriptionMaxLengthErrorMsg}")
    @Size(max = 120, message = "{unitDescriptionMaxLengthErrorMsg}")
    String unitDescription,

    @DecimalMin(value = "0.0", message = "{unitValidatorErrorMsg}")
    @DecimalMax(value = "99999.9", message = "{unitValidatorErrorMsg}")
    @Digits(integer = 6, fraction = 1, message = "{unitValidatorErrorMsg}")
    BigDecimal numberOfUnits,

    String biogeoclimaticZone,

    @Min(value = 0, message = "{costValidatorSchedule9ErrorMsg}")
    @Max(value = 9999999, message = "{costValidatorSchedule9ErrorMsg}")
    Integer cost,

    @Min(value = 0, message = "{sideSlopePercentageValidatorErrorMsg}")
    @Max(value = 100, message = "{sideSlopePercentageValidatorErrorMsg}")
    Integer sideSlopePct,

    String sourceCode,

    @MaxByteLength(value = 120, charMax = 120, message = "{sourceDescriptionMaxLengthErrorMsg}")
    @Size(max = 120, message = "{sourceDescriptionMaxLengthErrorMsg}")
    String sourceDescription,

    @MaxByteLength(value = 2000, charMax = 2000, message = "{contractualCommentsMaxLengthErrorMsg}")
    @Size(max = 2000, message = "{contractualCommentsMaxLengthErrorMsg}")
    String comments,

    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}")
    Integer revisionCount) {
}
