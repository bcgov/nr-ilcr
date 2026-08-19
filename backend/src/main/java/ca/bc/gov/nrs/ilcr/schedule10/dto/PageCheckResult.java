package ca.bc.gov.nrs.ilcr.schedule10.dto;

import java.util.List;

/**
 * Check Status outcome for one construction page and the road details beneath it.
 *
 * <p>Legacy validates page-level fields first, then loops the page's road details, so both levels
 * appear here and the emission order is preserved within each.
 *
 * <p>{@code pageLabel} is carried because it is the literal prefix legacy puts in front of every
 * composed message for this page — including, on a TFL-located page, the text {@code "TSA: null"}
 * that the read path reproduces. The label the client sees in a Check Status line therefore matches
 * the label it sees on the page summary.
 *
 * @param pageId the database id, for UI correlation
 * @param pageNumber the 1-based positional ordinal, as used in the label
 * @param pageLabel the legacy page label, the prefix of every composed message for this page
 * @param met whether this page and all of its road details have no outstanding requirements
 * @param issues the page-level outstanding requirements, in legacy emission order
 * @param roadDetails the per-road-detail outcomes, ordered by road detail id
 */
public record PageCheckResult(
    int pageId,
    int pageNumber,
    String pageLabel,
    boolean met,
    List<FieldIssue> issues,
    List<RoadDetailCheckResult> roadDetails) {
}
