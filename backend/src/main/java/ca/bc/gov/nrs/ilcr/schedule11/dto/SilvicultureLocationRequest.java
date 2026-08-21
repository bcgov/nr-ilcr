package ca.bc.gov.nrs.ilcr.schedule11.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 11 (Basic Silviculture) location (AD-12). Entered fields only —
 * derived figures ({@code totalCost}, {@code costPerNetArea}, {@code becLabel}) and read-only
 * document fields are never client-supplied. Range/required messages resolve the LEGACY bundle keys
 * (AD-8) via the wired {@code MessageSource} ({@code ValidationConfiguration}).
 *
 * <p>Required (legacy JSF {@code required="true"} on schedule11.xhtml): {@code location}, {@code
 * enhancedIndicator}, {@code biogeoclimaticCatalogueId}, {@code netArea} (slices S14–S17). Costs
 * are OPTIONAL at entry (legacy). {@code netArea} range is 0–999,999.9 (S18, BR-05); each cost is
 * &plusmn;99,999,999 (S19, BR-06, legacy {@code ILCRCostValidator} default branch).
 *
 * <p>{@code revisionCount} is the per-row optimistic-lock token (AR11 keying delta) — required only
 * on UPDATE (the {@link OnUpdate} group), ignored on create.
 *
 * @param location the location text (required, &le; 30 chars)
 * @param enhancedIndicator the Enhanced Silviculture flag (required — must be selected, S15)
 * @param biogeoclimaticCatalogueId the BEC catalogue id (required; must resolve to a catalogue row
 *     — force-selection backend enforcement, S16 — else 400 {@code invalidBiogeoCode})
 * @param netArea the reforested net area / NAR(ha) (required, 0–999,999.9, at most one decimal)
 * @param actualCost the item-24 Actual cost (optional; whole dollars, &plusmn;99,999,999); null
 *     clears the row
 * @param plannedCost the item-23 Planned cost (optional; whole dollars, &plusmn;99,999,999); null
 *     clears the row
 * @param comments the row comments (optional, &le; 3500)
 * @param revisionCount the optimistic-lock token echoed from the served row (required on UPDATE)
 */
public record SilvicultureLocationRequest(
    @NotBlank(message = "{locationRequiredErrorMsg}") @Size(max = 30, message = "{locationMaxLengthErrorMsg}") String location,
    @NotNull(message = "{enhancedIndicatorRequiredErrorMsg}") Boolean enhancedIndicator,
    @NotNull(message = "{biogeoRequiredErrorMsg}") Long biogeoclimaticCatalogueId,
    @NotNull(message = "{netAreaRequiredErrorMsg}") @DecimalMin(value = "0", message = "{netAreaRangeErrorMsg}") @DecimalMax(value = "999999.9", message = "{netAreaRangeErrorMsg}")
        // One-decimal NAR (S18/BR-05): without the fraction cap Oracle would silently round a
        // finer-grained value to the column scale and every re-GET would differ from what was
        // entered.
        // fraction=1 is the load-bearing bound; integer=7 (not 6) leaves magnitude to @DecimalMax
        // so an
        // over-range value trips ONE constraint — the shared handler joins duplicate messages with
        // ";".
        @Digits(integer = 7, fraction = 1, message = "{netAreaRangeErrorMsg}") BigDecimal netArea,

    // Whole-dollar costs: legacy Oracle COST NUMBER(15) ROUNDED fractional input on insert, while
    // an Integer wire type would let Jackson silently TRUNCATE it at deserialization. BigDecimal +
    // fraction=0 turns a fractional cost into a clean 400 instead (the client rounds before send).
    // fraction=0 is the load-bearing bound; integer=9 (not 8) leaves magnitude to @DecimalMin/Max
    // so a just-over-range value trips ONE constraint (same idiom as netArea above).
    @DecimalMin(value = "-99999999", message = "{costValidatorErrorMsg}") @DecimalMax(value = "99999999", message = "{costValidatorErrorMsg}") @Digits(integer = 9, fraction = 0, message = "{costValidatorErrorMsg}") BigDecimal actualCost,
    @DecimalMin(value = "-99999999", message = "{costValidatorErrorMsg}") @DecimalMax(value = "99999999", message = "{costValidatorErrorMsg}") @Digits(integer = 9, fraction = 0, message = "{costValidatorErrorMsg}") BigDecimal plannedCost,
    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}") String comments,
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}") Integer revisionCount) {}
