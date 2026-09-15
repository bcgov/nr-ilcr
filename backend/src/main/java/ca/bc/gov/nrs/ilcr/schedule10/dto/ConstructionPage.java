package ca.bc.gov.nrs.ilcr.schedule10.dto;

import ca.bc.gov.nrs.ilcr.dto.base.OriginalValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * One Schedule 10 construction page, owning its road-detail rows.
 *
 * <p>The details are <strong>nested</strong> rather than served as a flat sibling list keyed by
 * page id — the page owns them, and flattening would push the grouping onto every consumer.
 *
 * @param pageId the {@code ROAD_CONSTRUCTION_REPRT_ID}
 * @param pageNumber positional 1-based number assigned on read; not stored ({@code
 *     Schedule10DAO:842-846})
 * @param pageLabel the legacy summary-list label, reproduced byte-for-byte
 * @param forestRegionCode the region code
 * @param tsaNumber the TSA number, or absent on a TFL-located page (BR-05)
 * @param tsbNumberCode the supply block, or absent
 * @param tflNumberCode the TFL number, or absent on a TSA-located page (BR-05)
 * @param roadGroup the DERIVED read-only Road Group (BR-04); absent when the combination is
 *     unmapped, with no error raised (S12)
 * @param divisionName the division name
 * @param constructionPeriod the period surveyed, {@code YYYY-MM}
 * @param roadDetailCount the count backing the legacy {@code Enter Road Data ({count})} link text
 *     (CNT-001); {@code 0} is served, not omitted
 * @param revisionCount the per-page optimistic-lock counter (no schedule-level counter exists)
 * @param roadDetails the road details, ordered by id; empty when the page has none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConstructionPage(
    int pageId,
    int pageNumber,
    String pageLabel,
    String forestRegionCode,
    String tsaNumber,
    String tsbNumberCode,
    String tflNumberCode,
    String roadGroup,
    String divisionName,
    String constructionPeriod,
    int roadDetailCount,
    Integer revisionCount,
    List<RoadDetail> roadDetails,
    Map<String, OriginalValue> originalValues) {

  /**
   * Without original values — the shape every caller that is not serving a stored, beyond-Draft
   * document uses (a print mapper, a check-status projection, a test fixture). The canonical
   * constructor is the one the read path uses (Story 16.2).
   */
  public ConstructionPage(
      int pageId,
      int pageNumber,
      String pageLabel,
      String forestRegionCode,
      String tsaNumber,
      String tsbNumberCode,
      String tflNumberCode,
      String roadGroup,
      String divisionName,
      String constructionPeriod,
      int roadDetailCount,
      Integer revisionCount,
      List<RoadDetail> roadDetails) {
    this(
        pageId,
        pageNumber,
        pageLabel,
        forestRegionCode,
        tsaNumber,
        tsbNumberCode,
        tflNumberCode,
        roadGroup,
        divisionName,
        constructionPeriod,
        roadDetailCount,
        revisionCount,
        roadDetails,
        null);
  }
}
