package ca.bc.gov.nrs.ilcr.millmaintenance;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A mill as the administration surface sees it: {@code THE.MILL} joined to its {@code
 * THE.ILCR_MILL_STATUS_XREF} row and that row's status description (AD-3).
 *
 * <p>A second projection over these tables rather than a widening of {@link
 * ca.bc.gov.nrs.ilcr.millcontext.MillStatusXref}, which carries two columns and is documented as
 * never persisted. That record serves the mill/year context check, where the extra nine columns
 * would be dead weight on every schedule request; this one serves an editing screen. Several
 * projections over one table is the established shape here — the report-status view already backs
 * three. The table is still written through exactly one repository, which is what the one-writer
 * rule protects.
 *
 * <p>{@code millNumber} is a String although the column is {@code NUMBER(15)}: it is a display
 * identifier that is never arithmetic, and the house contract carries it that way everywhere.
 */
@Table(schema = "THE", name = "ILCR_MILL_STATUS_XREF")
public record AdminMillEntity(
    @Id @Column("MILL_ID") long millId,
    @Column("MILL_NUMBER") String millNumber,
    @Column("MILL_NAME") String millName,
    @Column("ILCR_MILL_STATUS_CODE") String statusCode,
    @Column("STATUS_DESCRIPTION") String statusDescription,
    @Column("HEAD_OFFICE_CONTACT_IND") String headOfficeContactInd,
    @Column("HEAD_OFFICE_CONTACT_ID") Long headOfficeContactId,
    @Column("DIVISION_CONTACT_ID") Long divisionContactId,
    @Column("REVISION_COUNT") int revisionCount) {}
