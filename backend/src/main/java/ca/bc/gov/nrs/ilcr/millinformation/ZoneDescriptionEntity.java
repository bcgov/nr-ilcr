package ca.bc.gov.nrs.ilcr.millinformation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one {@code THE.APPRAISAL_SELL_PRICE_ZONE_CODE} row — the
 * selling-price zone code and the description the Mill Information report and the Mill Status
 * Report table both print as a mill's Region.
 *
 * <p><b>The table name is not the column name, and that is not a typo.</b> {@code MILL} holds the
 * zone as a column called {@code ISP_SELL_PRICE_ZONE_CODE}, but the descriptions live in {@code
 * THE.APPRAISAL_SELL_PRICE_ZONE_CODE} — which is where legacy reads them: {@code Mill.java:59-61}
 * maps that FK column to the {@code AppraisalSellPrizeZoneCode} entity, whose {@code @Table} is
 * {@code THE.APPRAISAL_SELL_PRICE_ZONE_CODE} and which is the only zone entity registered in {@code
 * hibernate.cfg.xml:90}. Legacy's {@code IspSellPrizeZodeCode} class names {@code
 * ISP_SELL_PRICE_ZONE_CODE} but is mapped nowhere and referenced by nothing — dead code. Do not
 * "correct" this record to match the column name: {@code THE.ISP_SELL_PRICE_ZONE_CODE} does not
 * exist on the FTA database (only a dangling PUBLIC synonym does), and naming it is what made every
 * Region render as "-".
 *
 * @param code the zone code ({@code APPRAISAL_SELL_PRICE_ZONE_CODE}, {@code VARCHAR2(2)} — the same
 *     domain as {@code MILL.ISP_SELL_PRICE_ZONE_CODE}, with no padding on either side)
 * @param description the display description, e.g. {@code Northern Interior}
 */
@Table(name = "APPRAISAL_SELL_PRICE_ZONE_CODE", schema = "THE")
public record ZoneDescriptionEntity(
    @Id @Column("APPRAISAL_SELL_PRICE_ZONE_CODE") String code,
    @Column("DESCRIPTION") String description) {}
