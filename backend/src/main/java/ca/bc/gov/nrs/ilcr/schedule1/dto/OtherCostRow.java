package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;

/**
 * One itemized Subtotal Other Costs row (AD-12). Keyed by the legacy detail id
 * ({@code ILCR_COST_REPORT_DETAIL_ID}) for edit/delete. {@code perUnit} ($/m³) is derived
 * server-side from the row cost and the shared Other-Costs volume (read-only).
 */
public record OtherCostRow(
    Integer id,
    String description,
    Integer cost,
    BigDecimal perUnit) {
}
