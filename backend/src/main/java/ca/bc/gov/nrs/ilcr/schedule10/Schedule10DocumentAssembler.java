package ca.bc.gov.nrs.ilcr.schedule10;

import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LENGTH_SCALE;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_BRIDGE;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_CULVERT;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_END_HAUL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_LANDING;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_OTHER_ENGINEERING;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_OVERLAND;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.MEASURE_SCALE;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.OTHER_TT_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.ROUTED;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_OTHER_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.VOLUME_SCALE;

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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the Schedule 10 aggregate document from stored rows, and owns every derived value on
 * the read side.
 *
 * <p>The body comes from THREE queries — pages, all road details joined up to their pages, and all
 * cost lines joined up through the details to the pages — then grouped in memory, so nesting never
 * multiplies round-trips. The caller additionally supplies the track status it has already read.
 *
 * <p>Split out of {@code Schedule10Service} at code review follow-up 2026-08-19. That class had
 * grown to 1137 lines doing two genuinely separate jobs — assembling the read document and
 * executing the seven writes — and carried 21 dependencies against a limit of 20. The seam was
 * clean: nothing in the assembly touches a request DTO, and nothing in the write path touches a
 * response DTO.
 *
 * <p>NOT transactional. Every caller is already inside a transaction the entry point opened —
 * read-only for the GET and Check Status, read-write for a save — so the queries observe a single
 * consistent snapshot. Annotating it here would be misleading rather than useful, because a call
 * from within the same bean never passes through the Spring proxy.
 *
 * <p>Derivation rules that matter:
 *
 * <ul>
 *   <li><strong>Road Group</strong> is derived per page from the TSA/TSB or TFL tables and is never
 *       stored. Unmapped combinations serve {@code null} with no error (S12).
 *   <li><strong>Costs</strong> are keyed rows, not columns: each is routed to its substructure
 *       field by legacy cost-item ordinal (BR-08).
 *   <li><strong>Totals</strong> come from {@link Schedule10Amounts} only. An absent cost line
 *       counts as ZERO in a total (legacy {@code getCostValue}) while rendering blank on its own.
 * </ul>
 */
class Schedule10DocumentAssembler {

  private static final Logger LOG = LoggerFactory.getLogger(Schedule10DocumentAssembler.class);

  private final Schedule10Repository repository;

  Schedule10DocumentAssembler(Schedule10Repository repository) {
    this.repository = repository;
  }

  /**
   * Assembles the document with NO transaction annotation of its own.
   *
   * <p>This exists so the seven write methods and {@link #checkStatus} can build their response
   * without self-invoking {@link #getSchedule10}. A {@code this.}-call never passes through the
   * Spring proxy, so the inner {@code @Transactional} was silently ignored — the reads simply
   * joined the caller's transaction, which is correct behaviour but not what the annotation
   * appeared to promise. Extracting the body states that directly rather than relying on a proxy
   * subtlety, and removes the self-invocation Sonar flags (code review follow-up 2026-08-18).
   *
   * <p>Every caller is already inside a transaction: {@code getSchedule10} and {@code checkStatus}
   * open a read-only one, and each write method opens a read-write one. Nothing calls this
   * unwrapped, so the single-snapshot guarantee is unchanged.
   */
  Schedule10Response assemble(long millId, int year, String trackStatus, boolean editable) {
    List<RoadConstructionReportEntity> pageRows = repository.findPages(millId, year);
    List<RoadConstructionReportDetailEntity> detailRows = repository.findRoadDetails(millId, year);
    List<CostLineRow> costRows = repository.findCostLines(millId, year);

    Map<Integer, List<RoadConstructionReportDetailEntity>> detailsByPage =
        detailRows.stream()
            .collect(
                Collectors.groupingBy(
                    RoadConstructionReportDetailEntity::roadConstructionReprtId,
                    LinkedHashMap::new,
                    Collectors.toList()));

    // costItemId -> cost, per road detail. A null value is a legitimate entry, not a defect:
    // ILCR_COST_REPORT_DETAIL.COST is nullable and legacy stores NULL for a cost the licensee left
    // blank (Schedule10DAO:722 writes intValueExact() or null). A stored NULL must therefore be
    // indistinguishable from an absent row — the individual field renders blank while the derived
    // totals coerce it to zero (see Schedule10Amounts).
    //
    // Map.merge CANNOT be used to build this map: it is specified to throw NullPointerException
    // on a null value, which turns an ordinary blank cost into a 500 during document assembly.
    //
    // Three hazards, all handled by logging rather than by failing the read — this is a report
    // screen, and refusing to render is worse for the licensee than rendering with a warning.
    //
    // (1) NULL COSTS are stored as-is, so absent and blank behave identically downstream.
    // (2) DUPLICATES. Nothing enforces one row per (detail, item): there is no unique constraint,
    //     and delivery holds zero Schedule 10 cost rows so the invariant has never been observed
    //     against data. LAST ROW WINS, matching legacy: Schedule10DAO:556-600 loops the cost-detail
    //     Set and ASSIGNS per item (setSubGradeTtTTransfer(...) and its eleven siblings), so a
    //     second row for the same item overwrites the first. Legacy does NOT sum.
    //
    //     Story 11.1 summed instead, which read as the safer choice — it conserves the money — but
    //     it is an unrecorded deviation, and it made a duplicated value UNFIXABLE through the API:
    //     the write path's UPDATE sets every duplicate row, so resubmitting the correct figure left
    //     the sum wrong for ever. Corrected to legacy at code review 2026-08-18 (legacy-first).
    //     Note legacy iterates a HashSet, so which row wins there is arbitrary; the ORDER BY on the
    //     cost query makes the choice deterministic here, which is strictly better than legacy.
    //     The collision is still logged, because it means the data violates an intended invariant.
    // (3) UNMAPPED ORDINALS. A cost row whose item id is outside the twelve routes nowhere and
    //     vanishes from the totals. Legacy throws on this (Schedule10DAO:559-561 switches on
    //     REPORT_COST_ITEMS.valueOfByValue); we log instead, for the same reason.
    Map<Integer, Map<Integer, BigDecimal>> costsByDetail = new LinkedHashMap<>();
    for (CostLineRow row : costRows) {
      if (!ROUTED.contains(row.costItemId())) {
        LOG.warn(
            "Schedule 10 road detail {} carries unrouted cost item {} — excluded from every"
                + " derived total (mill {}, year {})",
            row.roadDetailId(),
            row.costItemId(),
            millId,
            year);
        continue;
      }
      Map<Integer, BigDecimal> byItem =
          costsByDetail.computeIfAbsent(row.roadDetailId(), key -> new LinkedHashMap<>());
      // containsKey, not get() != null — a present-but-null entry is a real cost row that must be
      // recognised as a duplicate when a second row for the same item arrives.
      // containsKey, not get() != null — a present-but-null entry is a real cost row, and a second
      // row for the same item must still be recognised as a duplicate.
      if (byItem.containsKey(row.costItemId())) {
        LOG.warn(
            "Schedule 10 road detail {} has MORE THAN ONE cost row for item {} — the last row"
                + " wins, as in legacy (mill {}, year {})",
            row.roadDetailId(),
            row.costItemId(),
            millId,
            year);
      }
      byItem.put(row.costItemId(), row.cost());
    }

    // Two distinct sets: what the dropdown may OFFER (xref-gated) and what this document must be
    // able to RESOLVE (offerable + already-referenced). Keeping them apart stops a de-listed
    // classification leaking back into the dropdown.
    Map<Integer, BecClassification> offerableBec = offerableBecById();
    Map<Integer, BecClassification> resolvableBec = resolvableBecById(offerableBec, millId, year);

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
        row.biogeoclimaticCatalogueId(),
        row.becZoneCode(),
        row.subzone(),
        row.variant(),
        row.phase(),
        row.label());
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
      roadDetails.add(
          toRoadDetail(
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
   * The legacy page summary label, reproduced byte-for-byte from {@code
   * RoadConstructionReportType.getPageLabel} (:138-145).
   *
   * <pre>
   * String tsb = getTsbNumberCode() != null ? getTsbNumberCode() : "-";
   * String tfl = getTflNumberCode() != null ? getTflNumberCode() : "-";
   * "Page " + pageNumber + ", Period: " + period + ", TSA: " + tsa + ", SB: " + tsb + ", TFL:" +
   * tfl
   * </pre>
   *
   * <p>Three legacy quirks are preserved deliberately, all asserted byte-for-byte:
   *
   * <ol>
   *   <li>There is NO space after {@code "TFL:"} — every other separator has one.
   *   <li>Only TSB and TFL fall back to {@code "-"}.
   *   <li><strong>TSA and Period are NOT null-guarded</strong>, so on a TFL-located page (where TSA
   *       is null by BR-05) legacy renders the literal text {@code "TSA: null"}. That is a real
   *       user-visible legacy defect, reproduced here rather than quietly corrected — see Story
   *       11.1 deviation (l) and the matching Ministry open question. Changing it is a product
   *       decision, not a developer one.
   * </ol>
   */
  private static String pageLabel(
      int pageNumber, String period, String tsaNumber, String tsb, String tfl) {
    return "Page "
        + pageNumber
        + ", Period: "
        + period
        + ", TSA: "
        + tsaNumber
        + ", SB: "
        + (tsb != null ? tsb : "-")
        + ", TFL:"
        + (tfl != null ? tfl : "-");
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
    BigDecimal subGradeTotalDeductions =
        Schedule10Amounts.subGradeTotalDeductions(
            lessBridges, lessCulverts, lessLandings, lessEndHaul, lessOverland, lessOtherEng);
    BigDecimal subGradeTotal =
        Schedule10Amounts.subGradeTotal(subGradeTotalCosts, subGradeTotalDeductions);

    SubGrade subGrade =
        new SubGrade(
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

    Stabilizing stabilizing =
        new Stabilizing(
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

    MaterialComposition material =
        new MaterialComposition(
            detail.solidRockPct(),
            detail.rippableRockPct(),
            detail.coarseMaterialPct(),
            detail.fineMaterialPct(),
            detail.organicMaterialPct(),
            Schedule10Amounts.materialTypeTotal(
                detail.solidRockPct(),
                detail.rippableRockPct(),
                detail.coarseMaterialPct(),
                detail.fineMaterialPct(),
                detail.organicMaterialPct()));

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
        toCodes(repository.findTsaNumbers(millId, year)),
        toCodes(repository.findSupplyBlocks(millId, year)),
        toCodes(repository.findRoadLifetimes(millId, year)),
        toCodes(repository.findBallastMethods(millId, year)),
        toCodes(repository.findBallastMaterials(millId, year)),
        toCodes(repository.findRsmrClasses(millId, year)),
        List.copyOf(offerableBec.values()));
  }

  private static List<CodeDescriptionDto> toCodes(List<CodeRow> rows) {
    return rows.stream().map(row -> new CodeDescriptionDto(row.code(), row.description())).toList();
  }
}
