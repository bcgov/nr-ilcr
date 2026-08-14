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
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Assembles the Schedule 10 aggregate document and owns every derived value.
 *
 * <p>The read is three repository queries — pages, all road details joined up to their pages, all
 * cost lines joined up through the details to the pages — then grouped in memory. Depth never
 * multiplies round-trips.
 *
 * <p>Derivation rules that matter:
 * <ul>
 *   <li><strong>Road Group</strong> is derived per page from the TSA/TSB or TFL tables and is never
 *       stored. Unmapped combinations serve {@code null} with no error (S12).</li>
 *  <li><strong>Costs</strong> are keyed rows, not columns: each is routed to its substructure field
 *       by legacy cost-item ordinal (BR-08).</li>
 *   <li><strong>Totals</strong> come from {@link Schedule10Amounts} only. Null is not zero.</li>
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
  public Schedule10Response getSchedule10(long millId, int year, boolean callerMayEdit) {
    List<RoadConstructionReportEntity> pageRows = repository.findPages(millId, year);
    List<RoadConstructionReportDetailEntity> detailRows = repository.findRoadDetails(millId, year);
    List<CostLineRow> costRows = repository.findCostLines(millId, year);

    Map<Integer, List<RoadConstructionReportDetailEntity>> detailsByPage = detailRows.stream()
        .collect(Collectors.groupingBy(
            RoadConstructionReportDetailEntity::roadConstructionReprtId,
            LinkedHashMap::new,
            Collectors.toList()));

    // costItemId -> cost, per road detail. Legacy stores at most one row per item per detail.
    Map<Integer, Map<Integer, BigDecimal>> costsByDetail = new LinkedHashMap<>();
    for (CostLineRow row : costRows) {
      costsByDetail
          .computeIfAbsent(row.roadDetailId(), key -> new LinkedHashMap<>())
          .put(row.costItemId(), row.cost());
    }

    Map<Integer, BecClassification> becById = becByIdFor(millId, year);

    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && DRAFT.equals(trackStatus);

    List<ConstructionPage> pages = new ArrayList<>(pageRows.size());
    int pageNumber = 0;
    for (RoadConstructionReportEntity page : pageRows) {
      pageNumber++;
      List<RoadConstructionReportDetailEntity> owned =
          detailsByPage.getOrDefault(page.roadConstructionReprtId(), List.of());
      pages.add(toPage(page, pageNumber, owned, costsByDetail, becById));
    }

    return new Schedule10Response(
        millId, year, trackStatus, editable, pages, codeLists(year, becById), null);
  }

  /**
   * The BEC classifications this document may need, keyed by id: the xref-offerable set plus any
   * classification a stored road detail already references. A row saved before the xref changed
   * must
   * still render its stored classification.
   */
  private Map<Integer, BecClassification> becByIdFor(long millId, int year) {
    Map<Integer, BecClassification> byId = new LinkedHashMap<>();
    for (BecClassificationRow row : repository.findOfferableBecClassifications()) {
      byId.put(row.biogeoclimaticCatalogueId(), toBec(row));
    }
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

    // Normalize the classification codes ONCE so the TSA-vs-TFL split and the Road Group lookup
    // decide from identical values; rmgFor's TFL-first routing depends on a blank being null.
    String tsaNumber = StringUtils.trimToNull(page.tsaNumber());
    String tsbNumberCode = StringUtils.trimToNull(page.tsbNumberCode());
    String tflNumberCode = StringUtils.trimToNull(page.tflNumberCode());

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
        detail.subGradeLength(),
        detail.subGradeSurfaceWidth(),
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
        detail.stabilizingLength(),
        detail.stabilizingSurfaceWidth(),
        detail.stabilizingDepth(),
        detail.stabilizingDistanceToSource(),
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
        detail.endHaulDistance(),
        detail.endHaulVolume(),
        detail.overlandDistance(),
        detail.overlandVolume(),
        detail.comments(),
        detail.revisionCount());
  }

  private Schedule10CodeLists codeLists(int year, Map<Integer, BecClassification> becById) {
    return new Schedule10CodeLists(
        toCodes(repository.findForestRegions(year)),
        toCodes(repository.findRoadLifetimes(year)),
        toCodes(repository.findBallastMethods(year)),
        toCodes(repository.findBallastMaterials(year)),
        toCodes(repository.findRsmrClasses(year)),
        List.copyOf(becById.values()));
  }

  private static List<CodeDescriptionDto> toCodes(List<CodeRow> rows) {
    return rows.stream()
        .map(row -> new CodeDescriptionDto(row.code(), row.description()))
        .toList();
  }
}
