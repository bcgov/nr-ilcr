package ca.bc.gov.nrs.ilcr.schedule9;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC aggregate for the legacy {@code THE.CONTRACTUAL_WORK_REPORT} table
 * (AD-3), the Schedule 9 master record — one miscellaneous/unique logging cost item keyed by
 * {@code ILCR_MILL_ID} + {@code REPORT_YEAR} + {@code ILCR_CATEGORY_ID = '9'}. Its Contractual Item
 * + Cost hang off it as a keyed row in {@code THE.ILCR_COST_REPORT_DETAIL}, joined by
 * {@code CONTRACTUAL_WORK_REPORT_ID} (legacy {@code ILCRCostReportDetail} FK).
 *
 * <p>Present only as the {@link Schedule9Repository} aggregate type; the read {@code @Query} methods
 * return explicit projections (descriptions come from JOINs the entity cannot carry), so the entity
 * itself stays a bare identity — SQL is explicit because the model is a legacy projection, not a
 * {@code save()}-driven aggregate.
 */
@Table(name = "CONTRACTUAL_WORK_REPORT", schema = "THE")
public record ContractualWorkReportEntity(
    @Id @Column("CONTRACTUAL_WORK_REPORT_ID") Integer contractualWorkReportId) {
}
