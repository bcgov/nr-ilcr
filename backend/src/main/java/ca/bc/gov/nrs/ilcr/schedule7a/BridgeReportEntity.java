package ca.bc.gov.nrs.ilcr.schedule7a;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one Schedule 7A bridge (AD-3): a {@code THE.BRIDGE_REPORT} row
 * keyed {@code (ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID = '7')} (legacy {@code
 * Constant.CATEGORIES.Schedule7}). The five code columns store the selected code string as an FK
 * into the corresponding {@code THE.*_CODE} table (the code IS that table's PK). Costs are NOT
 * columns here — each bridge's ten costs are {@code THE.ILCR_COST_REPORT_DETAIL} rows keyed by
 * {@code BRIDGE_REPORT_ID} + {@code ILCR_REPORT_COST_ITEM_ID} (items 70-76/79-81). Never crosses
 * the service boundary — {@link Schedule7aService} maps it to {@code dto/Bridge}.
 *
 * <p>Legacy source: {@code model/BridgeReport.java:40-130}. {@code EXPECTED_BRIDGE_LIFE_SPAN}
 * ({@code NUMBER(3)}) and {@code DISTANCE_FROM_STORAGE} ({@code NUMBER(4)}) are whole numbers
 * mapped to {@code Integer}; {@code HEIGHT}/{@code LENGTH}/{@code DECK_WIDTH} ({@code NUMBER(5,1)})
 * are one-decimal measurements mapped to {@code BigDecimal}.
 *
 * @param bridgeReportId the bridge PK ({@code BRIDGE_REPORT_ID}; seq {@code
 *     ILCR_REPORT_COMMON_SEQ})
 * @param locationName the bridge name/location (max 30)
 * @param builtDate the completion date (stored {@code DATE}; served/entered as {@code yyyy-MM})
 * @param lifeSpan the expected life span (0-999)
 * @param abutmentHeight the abutment height in metres (0.0-9,999.9)
 * @param length the bridge length in metres (0.0-9,999.9)
 * @param deckWidth the deck width in metres (0.0-9,999.9)
 * @param distance the distance from storage in km (0-9,999)
 * @param constructionTypeCode the New/Used construction type code FK
 * @param superstructureTypeCode the superstructure type code FK
 * @param deckTypeCode the decking type code FK
 * @param abutmentTypeCode the abutment type code FK
 * @param loadRatingCode the load rating code FK
 * @param comments the row comments (col 4000; UI cap 3,500); nullable
 * @param revisionCount the per-row optimistic-lock token
 */
@Table(name = "BRIDGE_REPORT", schema = "THE")
public record BridgeReportEntity(
    @Id @Column("BRIDGE_REPORT_ID") long bridgeReportId,
    @Column("LOCATION_NAME") String locationName,
    @Column("BUILT_DATE") LocalDate builtDate,
    @Column("EXPECTED_BRIDGE_LIFE_SPAN") Integer lifeSpan,
    @Column("HEIGHT") BigDecimal abutmentHeight,
    @Column("LENGTH") BigDecimal length,
    @Column("DECK_WIDTH") BigDecimal deckWidth,
    @Column("DISTANCE_FROM_STORAGE") Integer distance,
    @Column("ILCR_BRIDGE_CNSTRCTN_TYPE_CODE") String constructionTypeCode,
    @Column("ILCR_BRIDGE_SUPERSTRUCTR_CODE") String superstructureTypeCode,
    @Column("ILCR_DECK_CODE") String deckTypeCode,
    @Column("ILCR_BRIDGE_ABUTMENT_TYPE_CODE") String abutmentTypeCode,
    @Column("ILCR_BRIDGE_LOAD_RATING_CODE") String loadRatingCode,
    @Column("COMMENTS") String comments,
    @Column("REVISION_COUNT") int revisionCount) {}
