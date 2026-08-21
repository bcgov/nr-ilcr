package ca.bc.gov.nrs.ilcr.schedule11.dto;

/**
 * One type-ahead option for the Schedule 11 Biogeo/Subzone/Variant field (BR-09 forced selection).
 * The {@code label} is the SAME zone+subzone+variant+phase concatenation (nulls → {@code ""}) that
 * {@code SilvicultureLocation.becLabel} serves, so the value a user picks here reads identically to
 * the value shown on a saved row (legacy {@code getBiogeoSubZoneVariantPase()}). Backs the global
 * catalogue lookup — no mill/year scope.
 *
 * @param id the catalogue id ({@code BIOGEOCLIMATIC_CATALOGUE_ID}); the value submitted as {@code
 *     biogeoclimaticCatalogueId} on a resolved selection
 * @param label the concatenated BEC label the field displays and filters on
 */
public record BiogeoclimaticOption(long id, String label) {}
