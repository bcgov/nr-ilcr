package ca.bc.gov.nrs.ilcr.schedule1.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One Schedule 1 fixed line item, keyed by the legacy cost-item identifier (AD-12). {@code perUnit}
 * ($/m³) is derived server-side and is read-only; {@code cost} is whole dollars, {@code volume} may
 * be fractional.
 *
 * <p>{@code originalValues} carries the licensee's submitted {@code volume} and {@code cost} for
 * audit comparison once the track has left Draft, and is null at Draft (Story 16.2, BR-04). Which
 * of the two keys appears is per-item legacy parity, not symmetry: legacy populated a volume
 * original for every line item including the subtotals, but a cost original only for the nine
 * entered items (see {@code Schedule1DAO.java:147-218}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LineItem(
    Integer costItemCode,
    BigDecimal volume,
    Integer cost,
    BigDecimal perUnit,
    Map<String, OriginalValue> originalValues) {}
