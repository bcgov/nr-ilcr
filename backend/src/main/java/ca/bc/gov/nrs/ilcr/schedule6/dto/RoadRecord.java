package ca.bc.gov.nrs.ilcr.schedule6.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

/**
 * One Schedule 6 road-maintenance record (AD-12). A record is classified either as a Timber Supply
 * Area with a Supply Block ({@code areaType} = the TSA code, {@code supplyBlock} = the TSB code) or
 * as a Tree Farm Licence ({@code areaType} = {@code "TFL"}, {@code tflNumber} = the TFL code) —
 * BR-02, mutually exclusive.
 *
 * <p>{@code rmg} (Resource Management Grouping) and {@code costPerVolume} ($/m&sup3;) are DERIVED
 * server-side (BR-04/BR-07) and read-only — never accepted from a client on write (8.2).
 * {@code revisionCount} is this row's own optimistic-lock token (each road record is independently
 * editable; there is no schedule-level summary row — the AR11 keying delta recorded in Story 8.1).
 * {@code costPerVolume} is null when volume is zero/absent (no divide-by-zero); Jackson
 * {@code non_null} omits every null field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RoadRecord(
    int recordId,
    Integer revisionCount,
    String areaType,
    String tflNumber,
    String supplyBlock,
    String rmg,
    BigDecimal volume,
    Integer cost,
    BigDecimal costPerVolume,
    String comments) {
}
