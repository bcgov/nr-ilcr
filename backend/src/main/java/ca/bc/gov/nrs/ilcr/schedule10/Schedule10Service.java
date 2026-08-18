package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.BecClassificationRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CodeRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CostLineRow;
import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CodeLists;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 10 aggregate document and owns every derived value.
 *
 * <p>The document body is assembled from THREE queries — pages, all road details joined up to their
 * pages, and all cost lines joined up through the details to the pages — then grouped in memory, so
 * nesting never multiplies round-trips. The full request additionally issues the track-status
 * lookup, two BEC queries and five code-list queries; the three-query property is about the nested
 * body, not the request as a whole.
 *
 * <p>Runs in one read-only transaction so those queries observe a single consistent snapshot.
 * Without it a concurrent write between the page and detail reads yields a silently torn document —
 * details belonging to a page that is not in the result are dropped with no error.
 *
 * <p>Derivation rules that matter:
 * <ul>
 *   <li><strong>Road Group</strong> is derived per page from the TSA/TSB or TFL tables and is never
 *       stored. Unmapped combinations serve {@code null} with no error (S12).</li>
 *   <li><strong>Costs</strong> are keyed rows, not columns: each is routed to its substructure
 *       field by legacy cost-item ordinal (BR-08).</li>
 *   <li><strong>Totals</strong> come from {@link Schedule10Amounts} only. An absent cost line
 *       counts as ZERO in a total (legacy {@code getCostValue}) while rendering blank on its
 *       own.</li>
 *   <li><strong>{@code editable}</strong> is {@code callerMayEdit && "D".equals(trackStatus)} — the
 *       SUBMITTER row only. Legacy also grants edit on {@code S}+non-Licensee and {@code V}+Admin,
 *       but no shipped schedule implements those paths; they belong to the AD-9/AR14 remediation.
 *       Do not add a second code path here.</li>
 * </ul>
 */
@Service
public class Schedule10Service {

  // Legacy cost-item ordinals (Constant.REPORT_COST_ITEMS :371-376), all verified against the
  // delivery ILCR_REPORT_COST_ITEM rows. The six "Less" lines span THREE subcategories, so routing
  // must be by item id — scanning a single subcategory would silently under-count the deductions.
  private static final int SUB_GRADE_TRANSFER = 3;        // cat 10 / sub 1
  private static final int LESS_OTHER_ENGINEERING = 4;    // cat 10 / sub 3
  private static final int OTHER_TT_TRANSFER = 5;         // cat 10 / sub 3
  private static final int LESS_CULVERT = 6;              // cat 10 / sub 1
  private static final int LESS_BRIDGE = 7;               // cat 10 / sub 1
  private static final int LESS_LANDING = 8;              // cat 10 / sub 1
  private static final int STABILIZING_OTHER_TRANSFER = 9; // cat 10 / sub 4
  private static final int STABILIZING_TRANSFER = 10;     // cat 10 / sub 2
  private static final int LESS_OVERLAND = 11;            // cat 10 / sub 1
  private static final int SUB_GRADE_ACTUAL = 20;         // cat 10 / sub 1
  private static final int LESS_END_HAUL = 21;            // cat 10 / sub 1
  private static final int STABILIZING_ACTUAL = 22;       // cat 10 / sub 2

  /** The 1–10 track Draft code; the only status at which a SUBMITTER may edit (AD-9). */
  private static final String DRAFT = "D";

  // G8 — Oracle does not preserve trailing zeros, so a NUMBER(6,3) holding 3.000 comes back as 3
  // and serialises as the integer 3 while its 12.500 neighbour serialises as 12.5. Stored
  // dimensions are normalised to their column's declared scale so the served document matches the
  // pinned contract regardless of the value (code review 2026-08-17 — caught by a new assertion).
  private static final int LENGTH_SCALE = 3;   // SUB_GRADE_LENGTH / STABILIZING_LENGTH NUMBER(6,3)
  private static final int MEASURE_SCALE = 1;  // widths, depth, distances              NUMBER(x,1)
  private static final int VOLUME_SCALE = 0;   // END_HAUL_VOLUME / OVERLAND_VOLUME      NUMBER(7,0)

  /**
   * Every cost-item ordinal this service routes. A cost row outside this set contributes to no
   * substructure and would silently vanish from the totals, so it is logged instead.
   */
  private static final Set<Integer> ROUTED_COST_ITEMS = Set.of(
      SUB_GRADE_TRANSFER, LESS_OTHER_ENGINEERING, OTHER_TT_TRANSFER, LESS_CULVERT, LESS_BRIDGE,
      LESS_LANDING, STABILIZING_OTHER_TRANSFER, STABILIZING_TRANSFER, LESS_OVERLAND,
      SUB_GRADE_ACTUAL, LESS_END_HAUL, STABILIZING_ACTUAL);

  private static final Logger LOG = LoggerFactory.getLogger(Schedule10Service.class);

  private final Schedule10Repository repository;

  /**
   * Wires the Schedule 10 repository. Mill/year validation happens in the controller via
   * {@code MillContextService} (AD-4) before this service is ever reached.
   *
   * @param repository the Schedule 10 data access
   */
  public Schedule10Service(Schedule10Repository repository) {
    this.repository = repository;
  }

  /**
   * Builds the Schedule 10 document for a mill and reporting year.
   *
   * <p>A mill/year with no construction pages returns a document with an empty {@code pages} list —
   * that is a valid 200 state, not a 404 (deviation (a)).
   *
   * @param millId the mill, already validated by the caller
   * @param year the reporting year, already validated by the caller
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the assembled document
   */
  @Transactional(readOnly = true)
  public Schedule10Response getSchedule10(long millId, int year, boolean callerMayEdit) {
    List<RoadConstructionReportEntity> pageRows = repository.findPages(millId, year);
    List<RoadConstructionReportDetailEntity> detailRows = repository.findRoadDetails(millId, year);
    List<CostLineRow> costRows = repository.findCostLines(millId, year);

    Map<Integer, List<RoadConstructionReportDetailEntity>> detailsByPage = detailRows.stream()
        .collect(Collectors.groupingBy(
            RoadConstructionReportDetailEntity::roadConstructionReprtId,
            LinkedHashMap::new,
            Collectors.toList()));

    // costItemId -> cost, per road detail. A null value is a legitimate entry, not a defect:
    // ILCR_COST_REPORT_DETAIL.COST is nullable and legacy stores NULL for a cost the licensee left
    // blank (Schedule10DAO:722 writes intValueExact() or null). A stored NULL must therefore be
    // indistinguishable from an absent row — the individual field renders blank while the derived
    // totals coerce it to zero (see Schedule10Amounts).
    //
    // Map.merge CANNOT be used to build this map: it is specified to throw NullPointerException on a
    // null value, which turns an ordinary blank cost into a 500 during document assembly.
    //
    // Three hazards, all handled by logging rather than by failing the read — this is a report
    // screen, and refusing to render is worse for the licensee than rendering with a warning.
    //
    // (1) NULL COSTS are stored as-is, so absent and blank behave identically downstream.
    // (2) DUPLICATES. Nothing enforces one row per (detail, item): there is no unique constraint,
    //     and delivery holds zero Schedule 10 cost rows so the invariant has never been observed
    //     against data. A blind put() would let the last-read row win and silently discard the
    //     other, understating the total. Values are SUMMED instead, which at least conserves the
    //     money, and the collision is logged. Summing keeps legacy's null rule: one non-null term
    //     survives, and two nulls stay null rather than collapsing to zero.
    // (3) UNMAPPED ORDINALS. A cost row whose item id is outside the twelve routes nowhere and
    //     vanishes from the totals. Legacy throws on this (Schedule10DAO:559-561 switches on
    //     REPORT_COST_ITEMS.valueOfByValue); we log instead, for the same reason.
    Map<Integer, Map<Integer, BigDecimal>> costsByDetail = new LinkedHashMap<>();
    for (CostLineRow row : costRows) {
      if (!ROUTED_COST_ITEMS.contains(row.costItemId())) {
        LOG.warn("Schedule 10 road detail {} carries unrouted cost item {} — excluded from every"
            + " derived total (mill {}, year {})",
            row.roadDetailId(), row.costItemId(), millId, year);
        continue;
      }
      Map<Integer, BigDecimal> byItem =
          costsByDetail.computeIfAbsent(row.roadDetailId(), key -> new LinkedHashMap<>());
      // containsKey, not get() != null — a present-but-null entry is a real cost row that must be
      // recognised as a duplicate when a second row for the same item arrives.
      if (byItem.containsKey(row.costItemId())) {
        LOG.warn("Schedule 10 road detail {} has MORE THAN ONE cost row for item {} — summing them"
            + " (mill {}, year {})", row.roadDetailId(), row.costItemId(), millId, year);
        byItem.put(
            row.costItemId(), Schedule10Amounts.sum(byItem.get(row.costItemId()), row.cost()));
      } else {
        byItem.put(row.costItemId(), row.cost());
      }
    }

    // Two distinct sets: what the dropdown may OFFER (xref-gated) and what this document must be
    // able to RESOLVE (offerable + already-referenced). Keeping them apart stops a de-listed
    // classification leaking back into the dropdown.
    Map<Integer, BecClassification> offerableBec = offerableBecById();
    Map<Integer, BecClassification> resolvableBec = resolvableBecById(offerableBec, millId, year);

    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && DRAFT.equals(trackStatus);

    List<ConstructionPage> pages = new ArrayList<>(pageRows.size());
    int pageNumber = 0;
    for (RoadConstructionReportEntity page : pageRows) {
      pageNumber++;
      List<RoadConstructionReportDetailEntity> owned =
          detailsByPage.getOrDefault(page.roadConstructionReprtId(), List.of());
      pages.add(toPage(page, pageNumber, owned, costsByDetail, resolvableBec));
    }

    return new Schedule10Response(
        millId, year, trackStatus, editable, pages, codeLists(millId, year, offerableBec), null);
  }

  /**
   * The classifications the BEC control may OFFER — the xref-gated set, and nothing else.
   *
   * <p>This is what {@code codeLists.becClassifications} serves. It must never include a
   * classification the xref has de-listed: that would re-admit a code the surviving BR-06 gate
   * exists to withhold, and Story 11.2's write path would then accept it (code review 2026-08-17).
   */
  private Map<Integer, BecClassification> offerableBecById() {
    Map<Integer, BecClassification> byId = new LinkedHashMap<>();
    for (BecClassificationRow row : repository.findOfferableBecClassifications()) {
      byId.put(row.biogeoclimaticCatalogueId(), toBec(row));
    }
    return byId;
  }

  /**
   * The classifications this document must be able to RESOLVE — the offerable set plus any
   * classification a stored road detail already references.
   *
   * <p>Deliberately wider than {@link #offerableBecById()}: a detail saved before its catalogue row
   * left the xref must still render its stored classification rather than silently losing it. The
   * two sets are kept separate so that this widening can never leak into the dropdown.
   */
  private Map<Integer, BecClassification> resolvableBecById(
      Map<Integer, BecClassification> offerable, long millId, int year) {
    Map<Integer, BecClassification> byId = new LinkedHashMap<>(offerable);
    for (BecClassificationRow row : repository.findReferencedBecClassifications(millId, year)) {
      byId.putIfAbsent(row.biogeoclimaticCatalogueId(), toBec(row));
    }
    return byId;
  }

  private static BecClassification toBec(BecClassificationRow row) {
    return new BecClassification(
        row.biogeoclimaticCatalogueId(), row.becZoneCode(), row.subzone(),
        row.variant(), row.phase(), row.label());
  }

  private ConstructionPage toPage(
      RoadConstructionReportEntity page,
      int pageNumber,
      List<RoadConstructionReportDetailEntity> details,
      Map<Integer, Map<Integer, BigDecimal>> costsByDetail,
      Map<Integer, BecClassification> becById) {

    // The RAW stored values, deliberately un-normalized. Legacy tests `tflNumberCode != null`
    // against the column itself (RoadConstructionReportType.getRmg :455-464) and concatenates the
    // raw values into pageLabel (:138-145). Trimming here would silently reroute a whitespace-only
    // TFL — legal in VARCHAR2(2) — to the TSA table and yield a Road Group where legacy yields
    // blank, and would render "TFL:-" where legacy renders the stored spaces. Both are
    // user-visible derived values, so parity wins over tidiness (code review 2026-08-17).
    String tsaNumber = page.tsaNumber();
    String tsbNumberCode = page.tsbNumberCode();
    String tflNumberCode = page.tflNumberCode();

    List<RoadDetail> roadDetails = new ArrayList<>(details.size());
    int rowNumber = 0;
    for (RoadConstructionReportDetailEntity detail : details) {
      rowNumber++;
      roadDetails.add(toRoadDetail(
          detail,
          rowNumber,
          costsByDetail.getOrDefault(detail.roadConstructionReprtDtlId(), Map.of()),
          becById));
    }

    return new ConstructionPage(
        page.roadConstructionReprtId(),
        pageNumber,
        pageLabel(pageNumber, page.constructionPeriod(), tsaNumber, tsbNumberCode, tflNumberCode),
        page.ilcrForestRegionCode(),
        tsaNumber,
        tsbNumberCode,
        tflNumberCode,
        RoadGroup10Lookup.rmgFor(tsaNumber, tsbNumberCode, tflNumberCode),
        page.constructionDivisionName(),
        page.constructionPeriod(),
        roadDetails.size(),
        page.revisionCount(),
        roadDetails);
  }

  /**
   * The legacy page summary label, reproduced byte-for-byte from
   * {@code RoadConstructionReportType.getPageLabel} (:138-145).
   *
   * <pre>
   * String tsb = getTsbNumberCode() != null ? getTsbNumberCode() : "-";
   * String tfl = getTflNumberCode() != null ? getTflNumberCode() : "-";
   * "Page " + pageNumber + ", Period: " + period + ", TSA: " + tsa + ", SB: " + tsb + ", TFL:" +
   * tfl
   * </pre>
   *
   * <p>Three legacy quirks are preserved deliberately, all asserted byte-for-byte:
   * <ol>
   *   <li>There is NO space after {@code "TFL:"} — every other separator has one.</li>
   *   <li>Only TSB and TFL fall back to {@code "-"}.</li>
   *   <li><strong>TSA and Period are NOT null-guarded</strong>, so on a TFL-located page (where TSA
   *       is null by BR-05) legacy renders the literal text {@code "TSA: null"}. That is a real
   *  user-visible legacy defect, reproduced here rather than quietly corrected — see Story 11.1
   *       deviation (l) and the matching Ministry open question. Changing it is a product decision,
   *       not a developer one.</li>
   * </ol>
   */
  private static String pageLabel(
      int pageNumber, String period, String tsaNumber, String tsb, String tfl) {
    return "Page " + pageNumber
        + ", Period: " + period
        + ", TSA: " + tsaNumber
        + ", SB: " + (tsb != null ? tsb : "-")
        + ", TFL:" + (tfl != null ? tfl : "-");
  }

  private RoadDetail toRoadDetail(
      RoadConstructionReportDetailEntity detail,
      int rowNumber,
      Map<Integer, BigDecimal> costs,
      Map<Integer, BecClassification> becById) {

    BigDecimal subGradeActual = costs.get(SUB_GRADE_ACTUAL);
    BigDecimal subGradeTt = costs.get(SUB_GRADE_TRANSFER);
    BigDecimal subGradeOther = costs.get(OTHER_TT_TRANSFER);
    BigDecimal lessBridges = costs.get(LESS_BRIDGE);
    BigDecimal lessCulverts = costs.get(LESS_CULVERT);
    BigDecimal lessLandings = costs.get(LESS_LANDING);
    BigDecimal lessOverland = costs.get(LESS_OVERLAND);
    BigDecimal lessOtherEng = costs.get(LESS_OTHER_ENGINEERING);
    BigDecimal lessEndHaul = costs.get(LESS_END_HAUL);

    BigDecimal subGradeTotalCosts =
        Schedule10Amounts.subGradeTotalCosts(subGradeActual, subGradeTt, subGradeOther);
    BigDecimal subGradeTotalDeductions = Schedule10Amounts.subGradeTotalDeductions(
        lessBridges, lessCulverts, lessLandings, lessEndHaul, lessOverland, lessOtherEng);
    BigDecimal subGradeTotal =
        Schedule10Amounts.subGradeTotal(subGradeTotalCosts, subGradeTotalDeductions);

    SubGrade subGrade = new SubGrade(
        Schedule10Amounts.atScale(detail.subGradeLength(), LENGTH_SCALE),
        Schedule10Amounts.atScale(detail.subGradeSurfaceWidth(), MEASURE_SCALE),
        subGradeActual,
        subGradeTt,
        subGradeOther,
        lessBridges,
        lessCulverts,
        lessLandings,
        lessOverland,
        lessOtherEng,
        lessEndHaul,
        subGradeTotalCosts,
        subGradeTotalDeductions,
        subGradeTotal,
        Schedule10Amounts.costPerLength(subGradeTotal, detail.subGradeLength()));

    BigDecimal stabilizingActual = costs.get(STABILIZING_ACTUAL);
    BigDecimal stabilizingTt = costs.get(STABILIZING_TRANSFER);
    BigDecimal stabilizingOther = costs.get(STABILIZING_OTHER_TRANSFER);
    BigDecimal stabilizingTotal =
        Schedule10Amounts.stabilizingTotal(stabilizingActual, stabilizingTt, stabilizingOther);

    Stabilizing stabilizing = new Stabilizing(
        detail.ilcrRoadBallastMethodCode(),
        detail.ilcrRoadBallastMaterlCode(),
        Schedule10Amounts.atScale(detail.stabilizingLength(), LENGTH_SCALE),
        Schedule10Amounts.atScale(detail.stabilizingSurfaceWidth(), MEASURE_SCALE),
        Schedule10Amounts.atScale(detail.stabilizingDepth(), MEASURE_SCALE),
        Schedule10Amounts.atScale(detail.stabilizingDistanceToSource(), MEASURE_SCALE),
        stabilizingActual,
        stabilizingTt,
        stabilizingOther,
        stabilizingTotal,
        Schedule10Amounts.costPerLength(stabilizingTotal, detail.stabilizingLength()));

    MaterialComposition material = new MaterialComposition(
        detail.solidRockPct(),
        detail.rippableRockPct(),
        detail.coarseMaterialPct(),
        detail.fineMaterialPct(),
        detail.organicMaterialPct(),
        Schedule10Amounts.materialTypeTotal(
            detail.solidRockPct(), detail.rippableRockPct(), detail.coarseMaterialPct(),
            detail.fineMaterialPct(), detail.organicMaterialPct()));

    return new RoadDetail(
        detail.roadConstructionReprtDtlId(),
        rowNumber,
        "Road #" + rowNumber + ", " + detail.roadName(),
        detail.roadName(),
        detail.ilcrRoadLifetimeCode(),
        becById.get(detail.becbiogeoCatalogueId()),
        detail.relSoilMoistRgmClsCode(),
        detail.sideSlopePct(),
        subGrade,
        stabilizing,
        material,
        detail.detailEngineeringCostInd(),
        Schedule10Amounts.atScale(detail.endHaulDistance(), MEASURE_SCALE),
        Schedule10Amounts.atScale(detail.endHaulVolume(), VOLUME_SCALE),
        Schedule10Amounts.atScale(detail.overlandDistance(), MEASURE_SCALE),
        Schedule10Amounts.atScale(detail.overlandVolume(), VOLUME_SCALE),
        detail.comments(),
        detail.revisionCount());
  }

  private Schedule10CodeLists codeLists(
      long millId, int year, Map<Integer, BecClassification> offerableBec) {
    return new Schedule10CodeLists(
        toCodes(repository.findForestRegions(millId, year)),
        toCodes(repository.findRoadLifetimes(millId, year)),
        toCodes(repository.findBallastMethods(millId, year)),
        toCodes(repository.findBallastMaterials(millId, year)),
        toCodes(repository.findRsmrClasses(millId, year)),
        List.copyOf(offerableBec.values()));
  }

  private static List<CodeDescriptionDto> toCodes(List<CodeRow> rows) {
    return rows.stream()
        .map(row -> new CodeDescriptionDto(row.code(), row.description()))
        .toList();
  }
}
