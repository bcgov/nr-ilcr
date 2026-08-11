package ca.bc.gov.nrs.ilcr.schedule7b.dto;

import java.math.BigDecimal;

/**
 * One Schedule 7B culvert on the served document (AD-12). The nine entered values are as stored;
 * {@code totalCost} is SERVER-COMPUTED (BR-05) from the two costs and is read-only — never stored,
 * never client-supplied (the legacy Total field is {@code disabled="true"}, {@code
 * schedule7B.xhtml:197}). Jackson serializes non-null only, so an absent optional value — or a
 * total with no contributing cost — is omitted.
 *
 * <p>Legacy shape source: {@code service/domain/type/CulvertReportType.java}. Costs are
 * whole-dollar {@code Integer} (legacy {@code COST} is {@code Integer}, stored via {@code
 * intValue()} at {@code Schedule7bDAO.java:225,230}); {@code spanSize}/{@code riseSize} are
 * millimetres and {@code length} is one-decimal metres.
 *
 * @param culvertReportId the culvert id
 * @param rowCounter the 1-based list index (legacy display id, used in check-status messages)
 * @param culvertTypeCode the culvert type code ({@code R} = Round and {@code O} = Others carry the
 *     type-conditional Check Status meaning, BR-07)
 * @param spanSize the span (mm); required by Check Status only when the type is {@code R}
 * @param riseSize the rise (mm); never required by Check Status for any type
 * @param length the culvert length (m); always required by Check Status
 * @param culvertPieceCount the number of pieces; always required
 * @param materialCost the material cost (item 77); always required by Check Status
 * @param installCost the installation cost (item 78); always required by Check Status
 * @param totalCost read-only derived = {@code materialCost + installCost}; null when both are
 *     absent
 * @param comments the row comments; required by Check Status only when the type is {@code O}
 * @param revisionCount the per-row optimistic-lock token
 */
public record Culvert(
    long culvertReportId,
    int rowCounter,
    String culvertTypeCode,
    Integer spanSize,
    Integer riseSize,
    BigDecimal length,
    Integer culvertPieceCount,
    Integer materialCost,
    Integer installCost,
    Integer totalCost,
    String comments,
    int revisionCount) {
}
