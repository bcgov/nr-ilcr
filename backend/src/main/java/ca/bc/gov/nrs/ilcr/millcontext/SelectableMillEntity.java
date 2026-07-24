package ca.bc.gov.nrs.ilcr.millcontext;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for a selectable Home-page mill (AD-3). Projected by explicit
 * {@code @Query} from {@code THE.MILL} joined to its one-to-one {@code THE.ILCR_MILL_STATUS_XREF},
 * so {@code ILCR_MILL_STATUS_CODE} comes from the XREF (not {@code MILL}); the {@code @Table} name is
 * the driving table. Mapped by {@code THE} column name — it never crosses the service boundary:
 * {@link MillContextRepository} maps it to {@link ca.bc.gov.nrs.ilcr.millcontext.dto.MillSummary}.
 *
 * <p>{@code MILL_NUMBER} ({@code NUMBER(15)}) is read as a String display identifier — never
 * arithmetic — honouring the pinned {@code MillSummary} contract; may be null.
 *
 * @param millId the mill id ({@code THE.MILL.MILL_ID})
 * @param millNumber the mill number as a display string ({@code THE.MILL.MILL_NUMBER}); may be null
 * @param millName the mill name ({@code THE.MILL.MILL_NAME}); may be null
 * @param millStatusCode the status code {@code "ACT"}/{@code "CLS"}
 *     ({@code THE.ILCR_MILL_STATUS_XREF.ILCR_MILL_STATUS_CODE})
 */
@Table(name = "MILL", schema = "THE")
public record SelectableMillEntity(
    @Id @Column("MILL_ID") long millId,
    @Column("MILL_NUMBER") String millNumber,
    @Column("MILL_NAME") String millName,
    @Column("ILCR_MILL_STATUS_CODE") String millStatusCode) {
}
