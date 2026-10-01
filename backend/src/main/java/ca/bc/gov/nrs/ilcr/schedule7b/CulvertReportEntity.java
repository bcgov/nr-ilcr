package ca.bc.gov.nrs.ilcr.schedule7b;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 7B culvert (AD-3): a {@code THE.CULVERT_REPORT} row
 * keyed {@code (ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '7')} — the SAME category-{@code '7'}
 * storage Schedule 7A's bridges use (legacy {@code Constant.CATEGORIES.Schedule7}), which is why 7A
 * and 7B are twin capabilities over one category rather than one merged page (FR4). {@code
 * ILCR_CULVERT_TYPE_CODE} stores the selected code string as an FK into {@code
 * THE.ILCR_CULVERT_TYPE_CODE} (the code IS that table's PK). Costs are NOT columns here — each
 * culvert's two costs are {@code THE.ILCR_COST_REPORT_DETAIL} rows keyed by {@code
 * CULVERT_REPORT_ID} + {@code ILCR_REPORT_COST_ITEM_ID} (77 material / 78 install). Never crosses
 * the service boundary — {@link Schedule7bService} maps it to {@code dto/Culvert}.
 *
 * <p>Legacy source: {@code model/CulvertReport.java:36-99}. {@code SPAN_SIZE}/{@code RISE_SIZE}
 * (mm) and {@code CULVERT_PIECE_COUNT} are whole numbers mapped to {@code Integer}; {@code LENGTH}
 * (m) is a one-decimal measurement mapped to {@code BigDecimal}.
 *
 * @param culvertReportId the culvert PK ({@code CULVERT_REPORT_ID}; seq {@code
 *     ILCR_REPORT_COMMON_SEQ})
 * @param culvertTypeCode the culvert type code FK (legacy values {@code A, ABL, HE, O, PA, R, VE,
 *     WBL}, plus {@code RP} added 2026-08-11 as Table-Maintenance data)
 * @param spanSize the span in millimetres (0-9,999,999); nullable — optional at Save
 * @param riseSize the rise in millimetres (0-9,999,999); nullable — optional at Save, and never
 *     checked by Check Status for any type (BR-07)
 * @param length the culvert length in metres (0.0-999,999.9); nullable — optional at Save
 * @param culvertPieceCount the number of pieces (1-9,999)
 * @param comments the row comments (col 4000; UI cap 3,500); nullable
 * @param revisionCount the per-row optimistic-lock token
 */
@Table(name = "CULVERT_REPORT", schema = "THE")
public record CulvertReportEntity(
    @Id @Column("CULVERT_REPORT_ID") long culvertReportId,
    @Column("ILCR_CULVERT_TYPE_CODE") String culvertTypeCode,
    @Column("SPAN_SIZE") Integer spanSize,
    @Column("RISE_SIZE") Integer riseSize,
    @Column("LENGTH") BigDecimal length,
    @Column("CULVERT_PIECE_COUNT") Integer culvertPieceCount,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") int revisionCount) {}
