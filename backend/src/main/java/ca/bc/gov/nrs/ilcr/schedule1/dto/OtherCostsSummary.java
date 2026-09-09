package ca.bc.gov.nrs.ilcr.schedule1.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Subtotal Other Costs summary (AD-5/AD-12). {@code volume} is the shared item-19 volume; {@code
 * costSubtotal}/{@code perUnit}/{@code count} are derived server-side from the itemized item-19
 * rows (rows with a non-empty description).
 *
 * <p>{@code originalValues} carries the licensee's submitted shared {@code volume} once the track
 * has left Draft (Story 16.2, BR-04). {@code costSubtotal} and {@code perUnit} get none: they are
 * derived here and were derived in legacy too, so no snapshot column exists for either.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtherCostsSummary(
    BigDecimal volume,
    Long costSubtotal,
    BigDecimal perUnit,
    int count,
    Map<String, OriginalValue> originalValues) {}
