package ca.bc.gov.nrs.ilcr.schedule11;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 11 location (AD-3): a {@code
 * THE.BASIC_SILVICULTURE_REPORT} row LEFT-joined to its {@code THE.BIOGEOCLIMATIC_CATALOGUE} row
 * (the join column is the delivery's {@code BEC}-prefixed {@code BECBIOGEOCLIMATIC_CATALOGUE_ID} —
 * AC9 finding). Never crosses the service boundary: {@code Schedule11Service} maps it to {@code
 * dto/SilvicultureLocation}, deriving {@code becLabel} from the four raw catalogue parts (legacy
 * {@code getBiogeoSubZoneVariantPase()}, nulls → {@code ""}).
 *
 * @param locationId the location PK ({@code BASIC_SILVICULTURE_REPORT_ID})
 * @param location the location text (label "Location", max 30)
 * @param enhancedInd {@code "Y"}/{@code "N"} (label "ES"); mapped to boolean by the service
 * @param biogeoclimaticCatalogueId the BEC catalogue FK ({@code BECBIOGEOCLIMATIC_CATALOGUE_ID})
 * @param becZoneCode the catalogue zone code; null only if the catalogue row is missing
 * @param subzone the catalogue subzone; null only if the catalogue row is missing
 * @param variant the catalogue variant; commonly null (real data: 14/40 used rows)
 * @param phase the catalogue phase; commonly null (real data: 38/40 used rows)
 * @param netArea {@code REFORESTED_NET_AREA} (label "NAR (ha)"); NOT NULL in delivery, kept
 *     nullable here so the service's legacy-faithful null tolerance stays honest
 * @param comments the row comments (max 3500); nullable
 * @param revisionCount the per-row optimistic-lock token 25.2 will key on (AR11 delta)
 */
@Table(name = "BASIC_SILVICULTURE_REPORT", schema = "THE")
public record SilvicultureLocationEntity(
    @Id @Column("BASIC_SILVICULTURE_REPORT_ID") long locationId,
    @Column("LOCATION") String location,
    @Column("ENHANCED_IND") String enhancedInd,
    @Column("BECBIOGEOCLIMATIC_CATALOGUE_ID") long biogeoclimaticCatalogueId,
    @Column("BEC_ZONE_CODE") String becZoneCode,
    @Column("SUBZONE") String subzone,
    @Column("VARIANT") String variant,
    @Column("PHASE") String phase,
    @Column("REFORESTED_NET_AREA") BigDecimal netArea,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") int revisionCount) {}
