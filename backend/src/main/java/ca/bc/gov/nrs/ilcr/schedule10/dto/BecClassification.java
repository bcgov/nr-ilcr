package ca.bc.gov.nrs.ilcr.schedule10.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A BEC classification, served STRUCTURALLY rather than as a flat display string.
 *
 * <p>That choice is deliberate. LD-4 would reduce BEC Zone to base zone only, dropping subzone and
 * variant — but it is PROVISIONAL, pending the Ministry's updated list and source-table
 * confirmation, so this story builds to legacy (BR-07). Serving the components separately makes
 * that
 * later reduction a projection change rather than a breaking wire-format change.
 *
 * <p>{@code label} is the legacy concatenation {@code zone + subzone + variant + phase} with nulls
 * rendered as empty strings ({@code BiogeoclimaticCatalogue.getBiogeoSubZoneVariantPase} :208-212)
 * —
 * e.g. {@code "ICHdw1"}. It is provided so consumers need not re-implement the rule.
 *
 * <p>Note the legacy method name misspells "Phase" as "Pase"; the field here is spelled correctly
 * because it is not user-facing text.
 *
 * @param biogeoclimaticCatalogueId the catalogue row id
 * @param becZoneCode the base zone code, the only part LD-4 would retain
 * @param subzone the subzone
 * @param variant the variant, or absent
 * @param phase the phase, or absent
 * @param label the legacy concatenated display label
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BecClassification(
    int biogeoclimaticCatalogueId,
    String becZoneCode,
    String subzone,
    String variant,
    String phase,
    String label) {
}
