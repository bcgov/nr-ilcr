package ca.bc.gov.nrs.ilcr.millmaintenance;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A ministry mill offered for import: a {@code THE.MILL} row with no {@code
 * THE.ILCR_MILL_STATUS_XREF} row yet (AD-3).
 *
 * <p>It carries no status because it has none — status lives on the cross-reference, which is
 * exactly what importing creates. That absence is the whole selection rule (BR-04).
 */
@Table(schema = "THE", name = "MILL")
public record ImportableMillEntity(
    @Id @Column("MILL_ID") long millId,
    @Column("MILL_NUMBER") String millNumber,
    @Column("MILL_NAME") String millName) {}
