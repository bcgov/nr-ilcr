package ca.bc.gov.nrs.ilcr.schedule11;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one {@code THE.BIOGEOCLIMATIC_CATALOGUE} entry (AD-3), read by the
 * global BEC type-ahead lookup (BR-09). Never crosses the service boundary: {@code Schedule11Service}
 * maps it to {@code dto/BiogeoclimaticOption}, deriving the label from the four raw parts with the
 * same {@code becLabel} concat the served location rows use (legacy
 * {@code getBiogeoSubZoneVariantPase()}, nulls → {@code ""}).
 *
 * @param id the catalogue PK ({@code BIOGEOCLIMATIC_CATALOGUE_ID})
 * @param becZoneCode the catalogue zone code (NOT NULL in delivery)
 * @param subzone the catalogue subzone (NOT NULL in delivery)
 * @param variant the catalogue variant; commonly null
 * @param phase the catalogue phase; commonly null
 */
@Table(name = "BIOGEOCLIMATIC_CATALOGUE", schema = "THE")
public record BiogeoclimaticCatalogueEntity(
    @Id @Column("BIOGEOCLIMATIC_CATALOGUE_ID") long id,
    @Column("BEC_ZONE_CODE") String becZoneCode,
    @Column("SUBZONE") String subzone,
    @Column("VARIANT") String variant,
    @Column("PHASE") String phase) {
}
