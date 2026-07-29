package ca.bc.gov.nrs.ilcr.schedule8.dto;

import java.util.List;

/**
 * One Schedule 8 Tree-to-Truck report page (AD-12) — a category-{@code '8'} {@code TREE_TO_TRUCK_REPORT}
 * row with its samples. The pinned wire shape, frozen across the later Schedule 8 stories.
 *
 * <p>{@code id}/{@code revisionCount} are the row's PK + optimistic-lock token. The six code fields
 * (support centre, forest region, BEC zone, TSA, TFL, supply block) are surfaced as BOTH the stored
 * code AND its resolved label (the {@code *Label} companion), each label looked up from the code
 * table's {@code DESCRIPTION} column (§Decision 3); a code with no matching row leaves its label null.
 * The derived Road Group is deferred (§Decision 2). {@code sampleCount} mirrors {@code samples} size.
 * Nullable fields are omitted from the JSON when null.
 */
public record Page(
    Integer id,
    Integer revisionCount,
    String division,
    String license,
    String contact,
    String phone,
    String cuttingPermit,
    String supportCentre,
    String supportCentreLabel,
    String region,
    String regionLabel,
    String becZone,
    String becZoneLabel,
    String tsaNumber,
    String tsaNumberLabel,
    String tflNumber,
    String tflNumberLabel,
    String supplyBlock,
    String supplyBlockLabel,
    String comments,
    int sampleCount,
    List<Sample> samples) {
}
