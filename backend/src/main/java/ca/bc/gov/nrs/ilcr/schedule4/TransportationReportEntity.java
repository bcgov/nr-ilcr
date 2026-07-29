package ca.bc.gov.nrs.ilcr.schedule4;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.TRANSPORTATION_REPORT} table (AD-3).
 * A Schedule 4 location is a family of these rows sharing {@code LOCATION_DESCRIPTION}: one primary
 * (distance null) + one per distance code (47/48/52) + one per sub-page list row (43/46/55). The
 * aggregate type for {@link Schedule4Repository}; write SQL stays explicit ({@code @Query}) because
 * the model is a legacy-projection, not a save()-driven aggregate.
 */
@Table(name = "TRANSPORTATION_REPORT", schema = "THE")
public record TransportationReportEntity(
    @Id @Column("TRANSPORTATION_REPORT_ID") Integer transportationReportId,
    @Column("LOCATION_DESCRIPTION") String locationDescription,
    @Column("DISTANCE") BigDecimal distance,
    @Column("TRANSPORTATION_CYCLE_TIME") Integer transportationCycleTime,
    @Column("REVISION_COUNT") Integer revisionCount) {
}
