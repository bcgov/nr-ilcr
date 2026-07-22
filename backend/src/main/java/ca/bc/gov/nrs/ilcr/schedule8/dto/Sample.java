package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One Schedule 8 Tree-to-Truck sample (AD-12) — a {@code TREE_TO_TRUCK_DETAIL_REPORT} row under a
 * page, with its additions/deductions and every server-computed roll-up. The pinned wire shape,
 * frozen across the later Schedule 8 stories.
 *
 * <p>{@code id}/{@code revisionCount} are the row's PK + optimistic-lock token (the write stories
 * target this level). The six skidding percentages are stored as entered; {@code percentTotal} is
 * their server-side sum (AD-6). {@code actualHarvested} is {@code coniferousVolume + deciduousVolume}.
 * {@code skidTypeCode} is {@code ILCR_SKID_TYPE_CODE} and {@code skidTypeDescription} its resolved
 * label (§Decision 3). {@code additionsTotal}/{@code deductionsTotal} are the sums of the respective
 * rate rows' costing rates and {@code finalRate = originalRate + additionsTotal − deductionsTotal} —
 * all computed server-side, read-only, never accepted on write (AD-5). {@code additionCount}/
 * {@code deductionCount} mirror the list sizes. {@code uphillDirection}/{@code waterDumpDestination}
 * are the legacy Y/N indicators as booleans. Money/volume BigDecimals are normalized to natural form.
 */
public record Sample(
    Integer id,
    Integer revisionCount,
    String contractId,
    String cutBlock,
    Integer groundBasePct,
    Integer grapplePct,
    Integer skylinePct,
    Integer highleadPct,
    Integer helicopterPct,
    Integer otherSkiddingPct,
    Integer percentTotal,
    Integer skylineSlopeDistance,
    Integer skylineSupportNumber,
    BigDecimal supportAvgDistance,
    BigDecimal distance,
    BigDecimal cycleTime,
    boolean uphillDirection,
    boolean waterDumpDestination,
    String skidTypeCode,
    String skidTypeDescription,
    Integer coniferousVolume,
    Integer deciduousVolume,
    Integer actualHarvested,
    BigDecimal originalRate,
    BigDecimal additionsTotal,
    BigDecimal deductionsTotal,
    BigDecimal finalRate,
    int additionCount,
    int deductionCount,
    List<RateRow> additions,
    List<RateRow> deductions) {
}
