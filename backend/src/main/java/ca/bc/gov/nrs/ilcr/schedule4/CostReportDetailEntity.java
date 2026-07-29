package ca.bc.gov.nrs.ilcr.schedule4;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Minimal Spring Data JDBC row shape for the legacy {@code THE.ILCR_COST_REPORT_DETAIL} table, as
 * used by Schedule 4 (AD-3): each row hangs off a {@code TRANSPORTATION_REPORT} via
 * {@code TRANSPORTATION_REPORT_ID} (not a summary), carries one category/sub-page code, and — for
 * sub-page rows — a free-text {@code ITEM_DESCRIPTION}.
 */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record CostReportDetailEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Integer detailId,
    @Column("TRANSPORTATION_REPORT_ID") Integer transportationReportId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemCode,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost,
    @Column("ITEM_DESCRIPTION") String itemDescription) {
}
