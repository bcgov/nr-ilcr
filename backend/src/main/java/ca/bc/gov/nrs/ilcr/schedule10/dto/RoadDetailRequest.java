package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.MaxByteLength;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Add/edit request for one Schedule 10 road detail. Entered fields only — every total, rate, label
 * and positional number is derived server-side and deliberately ABSENT.
 *
 * <p><strong>Two required legacy fields are NOT here, and that is the point.</strong> ASM Code and
 * Soil Moisture Code are removed by business direction. Their columns are nevertheless {@code NOT
 * NULL} with enabled foreign keys, so the service DERIVES both from {@code becbiogeoCatalogueId} +
 * {@code relSoilMoistRgmClsCode} through the surviving {@code ILCR_SOIL_MOISTURE_XREF}
 * cross-reference — the same lookup legacy used to filter and auto-select them. That is why both of
 * those fields are required here: they are the derivation's inputs, not merely descriptive. A
 * sentinel is never written; the codes carry a real moisture classification that the legacy print
 * reports consume.
 *
 * <p>Boulder Area % is likewise removed and absent from {@link MaterialCompositionRequest}.
 *
 * <p><strong>{@code becbiogeoCatalogueId} must be OFFERABLE.</strong> Removing the two moisture
 * fields killed the cross-reference's role as a runtime FILTER, but not its second role: it also
 * decides which catalogue rows may be chosen at all. The service validates against the xref-gated
 * set, never the wider set the read path uses to resolve already-stored rows.
 *
 * <p>Nested substructures are annotated {@code @Valid} so their constraints participate in the same
 * 400. The response nests the same way, which keeps the request and the document symmetrical for
 * the frontend.
 *
 * @param roadName the road name (required, ≤ 30 — FLD-003)
 * @param roadLifetimeCode the road type (required — FLD-005 template, label "Road Type")
 * @param becbiogeoCatalogueId the BEC classification id (required — FLD-005 template, label "BEC
 *     Zone"); must be in the offerable xref-gated set, and is a derivation input
 * @param relSoilMoistRgmClsCode the RSMR class (required — FLD-004); the second derivation input
 * @param sideSlopePct the side slope percentage (optional, 0–100)
 * @param detailedEngineeringCostInd whether detailed engineering costs are included, {@code "Y"} or
 *     {@code "N"}
 * @param subGrade the sub-grade dimensions, costs and deductions
 * @param stabilizing the additional-stabilizing codes, dimensions and costs
 * @param materialComposition the five material percentages
 * @param endHaulDistance the end-haul distance in km (optional, −9,999.9–9,999.9 — negatives are
 *     legal here, as in legacy)
 * @param endHaulVolume the end-haul volume in m³ (optional, 0–9,999,999)
 * @param overlandDistance the overland distance in km (optional, −9,999.9–9,999.9)
 * @param overlandVolume the overland volume in m³ (optional, 0–9,999,999)
 * @param comments the road-detail comments (optional, ≤ 3500 characters AND ≤ 4000 UTF-8 bytes)
 * @param revisionCount the optimistic-lock token echoed from the served road detail (required on
 *     UPDATE)
 */
public record RoadDetailRequest(
    // Both units, for the same reason as comments below: ROAD_NAME is VARCHAR2(30) with BYTE
    // semantics, so 30 accented or CJK characters clear @Size and then raise ORA-12899, which the
    // blanket DataAccessException catch can only surface as an opaque 500 (code review 2026-08-18).
    @NotBlank(message = "{roadNameRequiredErrorMsg}") @Size(max = 30, message = "{roadNameRequiredErrorMsg}") @MaxByteLength(value = 30, charMax = 30, message = "{roadNameRequiredErrorMsg}")
        String roadName,

    // The JSF template key is NOT used here. Bean Validation supplies no positional arguments, so
    // "{0}: Value is required." would reach the reporter with a literal {0} (code review
    // 2026-08-18). These keys carry legacy's resolved text, label included.
    @NotBlank(message = "{roadTypeRequiredErrorMsg}") @Size(max = 10, message = "{invalidCodeValueErrorMsg}") String roadLifetimeCode,
    @NotNull(message = "{becZoneRequiredErrorMsg}") Integer becbiogeoCatalogueId,
    @NotBlank(message = "{rsmrClassRequiredErrorMsg}") @Size(max = 2, message = "{rsmrClassRequiredErrorMsg}") String relSoilMoistRgmClsCode,
    @Min(value = 0, message = "{sideSlopePercentageValidatorErrorMsg}") @Max(value = 100, message = "{sideSlopePercentageValidatorErrorMsg}") Integer sideSlopePct,

    // Optional, and the service defaults a blank to "N". The column is NOT NULL, and legacy's
    // pageDtlECIncludeCosts is a two-item dropdown (No/N, Yes/Y) with no empty option and no
    // required flag — so legacy always submitted a value and its effective default was N. Requiring
    // it here would reject a body legacy's own screen could not have produced (code review
    // 2026-08-18: without the default this reached Oracle as ORA-01400 and surfaced as a 500).
    @Pattern(regexp = "[YN]", message = "{invalidCodeValueErrorMsg}") String detailedEngineeringCostInd,
    @Valid SubGradeRequest subGrade,

    // @NotNull as well as @Valid: Bean Validation skips a null nested object, so without this a
    // client could omit the whole substructure and slip past the required ballast method code.
    @NotNull(message = "{ballastMethodRequiredErrorMsg}") @Valid StabilizingRequest stabilizing,
    @Valid MaterialCompositionRequest materialComposition,

    // The only dimensions legacy permits to go negative. Their Check Status rules are commented out
    // in legacy, so nothing downstream catches a negative rate either — reproduced deliberately.
    @DecimalMin(value = "-9999.9", message = "{rangeHaulDistanceErrorMsg}") @DecimalMax(value = "9999.9", message = "{rangeHaulDistanceErrorMsg}") BigDecimal endHaulDistance,
    @Min(value = 0, message = "{volumeValidatorErrorMsg}") @Max(value = 9999999, message = "{volumeValidatorErrorMsg}") Integer endHaulVolume,
    @DecimalMin(value = "-9999.9", message = "{rangeHaulDistanceErrorMsg}") @DecimalMax(value = "9999.9", message = "{rangeHaulDistanceErrorMsg}") BigDecimal overlandDistance,
    @Min(value = 0, message = "{volumeValidatorErrorMsg}") @Max(value = 9999999, message = "{volumeValidatorErrorMsg}") Integer overlandVolume,

    // Two units, both enforced. 3,500 CHARACTERS is the legacy textarea's own maxlength
    // (schedule10.xhtml:1681); 4,000 BYTES is the column's real width. @Size alone would let 3,500
    // accented or CJK characters through into a VARCHAR2(4000 BYTE) column, where Oracle raises
    // ORA-12899 and the blanket DataAccessException catch could only surface it as an opaque 500.
    @Size(max = 3500, message = "{commentsMaxLengthErrorMsg}") @MaxByteLength(value = 4000, charMax = 3500, message = "{commentsMaxLengthErrorMsg}")
        String comments,
    @NotNull(groups = OnUpdate.class, message = "{revisionCountRequiredErrorMsg}") @Min(value = 0, message = "{revisionCountRequiredErrorMsg}") Integer revisionCount) {}
