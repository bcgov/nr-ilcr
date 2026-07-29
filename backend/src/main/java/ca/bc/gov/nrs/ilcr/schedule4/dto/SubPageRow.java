package ca.bc.gov.nrs.ilcr.schedule4.dto;

import java.math.BigDecimal;

/**
 * One Schedule 4 sub-page transportation-list row within a location (AD-12, Story 4.3). The pinned
 * wire shape for the three list-based sub-pages: Towing Total (code 43), Truck Rehaul (code 46, the
 * only one with a {@code cycle}), and Other Transportation (code 55). Code 54 is dead (never
 * persisted) and never appears here.
 *
 * <p>Unlike the fixed/distance {@link CategoryAmount category amounts} (which live on the location's
 * primary and per-distance-code reports), each sub-page row is its OWN {@code TRANSPORTATION_REPORT}
 * sharing the location's {@code LOCATION_DESCRIPTION}, with a free-text {@code description}
 * ({@code ITEM_DESCRIPTION}), its own {@code distance} (and {@code cycle} for Truck Rehaul) on the
 * report, and a single {@code ILCR_COST_REPORT_DETAIL} row. {@code id} is that row's
 * {@code TRANSPORTATION_REPORT_ID} — the stable handle the write path deletes by.
 *
 * <p>{@code perUnit} ($/m³) is derived server-side (AD-6, cost ÷ volume); null when volume is null or
 * zero. Running totals per sub-page are derivable by summing the rows. All nullable fields are omitted
 * from the JSON when null (app-wide Jackson {@code non_null}); {@code cycle} is null except on 46.
 */
public record SubPageRow(
    Integer id,
    int code,
    String description,
    BigDecimal distance,
    BigDecimal volume,
    Integer cost,
    Integer cycle,
    BigDecimal perUnit) {
}
