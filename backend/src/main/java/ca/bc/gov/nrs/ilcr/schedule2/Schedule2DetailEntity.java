package ca.bc.gov.nrs.ilcr.schedule2;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/** Minimal Spring Data JDBC row shape for a Schedule 2 {@code THE.ILCR_COST_REPORT_DETAIL} row (AD-3). */
@Table(name = "ILCR_COST_REPORT_DETAIL", schema = "THE")
public record Schedule2DetailEntity(
    @Id @Column("ILCR_COST_REPORT_DETAIL_ID") Integer detailId,
    @Column("ILCR_REPORT_COST_ITEM_ID") Integer costItemCode,
    @Column("VOLUME") BigDecimal volume,
    @Column("COST") Integer cost) {
}
