package ca.bc.gov.nrs.ilcr.millinformation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Spring Data JDBC row shape for one {@code THE.ISP_SELL_PRICE_ZONE_CODE} row — the selling-price
 * zone code and the description the Mill Information report prints as a mill's Region.
 *
 * @param code the zone code ({@code ISP_SELL_PRICE_ZONE_CODE})
 * @param description the display description
 */
@Table(name = "ISP_SELL_PRICE_ZONE_CODE", schema = "THE")
public record ZoneDescriptionEntity(
    @Id @Column("ISP_SELL_PRICE_ZONE_CODE") String code,
    @Column("DESCRIPTION") String description) {}
