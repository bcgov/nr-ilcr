package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC aggregate root for the legacy {@code THE.ILCR_MILL_STATUS_XREF} table (AD-3).
 * Exists only to give {@link MillContextRepository} a mapped root; all mill/year context access goes
 * through explicit {@code @Query} methods, and the service maps results to decisions (entities never
 * cross the service boundary — AD-3). Never persisted via the CRUD API.
 */
@Table(schema = "THE", name = "ILCR_MILL_STATUS_XREF")
public record MillStatusXref(
    @Id @Column("ILCR_MILL_STATUS_XREF_ID") Long id,
    @Column("ILCR_MILL_STATUS_CODE") String statusCode) {
}
