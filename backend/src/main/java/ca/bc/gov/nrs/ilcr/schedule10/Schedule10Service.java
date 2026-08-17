package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.BecClassificationRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CodeRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CostLineRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.MoistureCodePair;
import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPageRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialComposition;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialCompositionRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CodeLists;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Stabilizing;
import ca.bc.gov.nrs.ilcr.schedule10.dto.StabilizingRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGrade;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGradeRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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

  // ===============================================================================================
  // WRITES
  // ===============================================================================================

  /** The legacy sentinel the TSA/TFL dropdown carries for a TFL-located page ({@code Constant.TFL}). */
  private static final String TFL = "TFL";

  /** The schedule's legacy category id, on every page row and every scoped predicate. */
  private static final String CATEGORY = "10";

  /** {@code TSA_NUMBER} is {@code VARCHAR2(2)}; the dropdown's 3-char sentinel never reaches it. */
  private static final int TSA_NUMBER_MAX = 2;

  /** Ballast method "non-required": legacy zeroes the stabilizing figures for this one. */
  private static final String BALLAST_NOT_REQUIRED = "N";

  /** Ballast method for which legacy forces the material code but leaves the figures alone. */
  private static final String BALLAST_DEFERRED = "D";

  /** Ballast method requiring a material type. */
  private static final String BALLAST_CRUSHED = "C";

  /** The material code legacy forces when no ballast material applies. */
  private static final String BALLAST_MATERIAL_NOT_APPLICABLE = "NA";

  /**
   * The ASM moisture gradient, driest to wettest, used only to break a tie deterministically.
   *
   * <p>The cross-reference resolves a BEC classification plus RSMR class to exactly one moisture pair
   * for the overwhelming majority of combinations. Where it offers more than one, legacy declined to
   * auto-select and left the choice to a dropdown the business has since removed — so a rule is
   * needed, and the driest candidate is chosen because it is the conservative end of the scale and,
   * being ordered by the Ministry's own code semantics, is stable rather than incidental.
   */
  private static final List<String> ASM_MOISTURE_GRADIENT =
      List.of("ED", "VD", "MD", "SD", "F", "M", "VM", "W");

  /** Stand-ins so an omitted optional substructure needs no null checks at every use site. */
  private static final SubGradeRequest NO_SUB_GRADE = new SubGradeRequest(
      null, null, null, null, null, null, null, null, null, null, null);

  private static final MaterialCompositionRequest NO_MATERIAL =
      new MaterialCompositionRequest(null, null, null, null, null);

  /** A page's location after the mutual-exclusion rule has been applied. */
  private record Location(String tsaNumber, String tsbNumberCode, String tflNumberCode) {
  }

  /**
   * Creates a construction page.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param request the entered page fields
   * @param user the actor stamped into the audit columns
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}, for the echoed document
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response addPage(
      long millId, int year, ConstructionPageRequest request, String user, boolean callerMayEdit) {
    requireDraft(millId, year);
    requireOfferedForestRegion(millId, year, request.forestRegionCode());
    Location location = classify(request);
    int pageId = repository.nextPageId();
    persist(() -> repository.insertPage(
        toPageEntity(pageId, millId, year, request, location), millId, year, user));
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Edits a construction page under its optimistic lock. Changing the location re-derives the Road
   * Group on the next read; nothing about it is stored.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the page to edit
   * @param request the entered page fields, carrying the last-read revision
   * @param user the actor stamped into the audit columns
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response updatePage(
      long millId, int year, int pageId, ConstructionPageRequest request, String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    int expectedRevision = requireRevision(request.revisionCount());
    // Existence first, and deliberately before any body validation: an unknown or foreign id must
    // answer 404 regardless of what the body contains, not 400 for a field the caller cannot reach.
    requirePage(pageId, millId, year);
    requireOfferedForestRegion(millId, year, request.forestRegionCode());
    Location location = classify(request);
    persist(() -> {
      int updated = repository.updatePage(
          toPageEntity(pageId, millId, year, request, location), millId, year, expectedRevision,
          user);
      if (updated == 0) {
        // Zero rows means the id is gone or the revision moved. Re-probe to say which.
        requirePage(pageId, millId, year);
        throw new StaleRevisionException();
      }
    });
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Copies a construction page and saves the copy immediately.
   *
   * <p><strong>The copy carries no road details.</strong> Legacy's copy constructor nulls both detail
   * collections and then saves without cascading, so only the page header is duplicated. Reproduced
   * as-is; a copy that silently duplicated every road would be a different feature.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the page to copy
   * @param user the actor stamped into the audit columns
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response copyPage(
      long millId, int year, int pageId, String user, boolean callerMayEdit) {
    requireDraft(millId, year);
    RoadConstructionReportEntity source = repository.findPages(millId, year).stream()
        .filter(page -> page.roadConstructionReprtId() == pageId)
        .findFirst()
        .orElseThrow(ConstructionPageNotFoundException::new);

    int copyId = repository.nextPageId();
    RoadConstructionReportEntity copy = new RoadConstructionReportEntity(
        copyId, year, millId, CATEGORY, source.constructionPeriod(),
        source.constructionDivisionName(), source.ilcrForestRegionCode(), source.tsbNumberCode(),
        source.tsaNumber(), source.tflNumberCode(), 0);
    persist(() -> repository.insertPage(copy, millId, year, user));
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Deletes a page and everything beneath it.
   *
   * <p>Order is grandchildren, children, parent. Neither delete-path foreign key cascades in delivery,
   * so the reverse order is rejected outright. Legacy reached the same order through its ORM cascade.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the page to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response deletePage(
      long millId, int year, int pageId, boolean callerMayEdit) {
    requireDraft(millId, year);
    requirePage(pageId, millId, year);
    persist(() -> {
      repository.deleteCostsForPage(pageId);
      repository.deleteRoadDetailsForPage(pageId);
      // The result is checked rather than discarded: a silently zero-row delete would answer
      // "deleted successfully" while the row survived.
      if (repository.deletePage(pageId, millId, year) == 0) {
        throw new ConstructionPageNotFoundException();
      }
    });
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Creates a road detail under a page.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the owning page
   * @param request the entered road-detail fields
   * @param user the actor stamped into the audit columns
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response addRoadDetail(
      long millId, int year, int pageId, RoadDetailRequest request, String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    requirePage(pageId, millId, year);
    requireOfferedDetailCodes(millId, year, request);
    MoistureCodePair moisture = deriveMoistureCodes(request);
    RoadDetailRequest coupled = applyBallastCoupling(request);

    int roadDetailId = repository.nextRoadDetailId();
    persist(() -> {
      repository.insertRoadDetail(
          toDetailEntity(roadDetailId, pageId, coupled), moisture.soilMoistureCode(),
          moisture.asmCode(), user);
      writeCostLines(roadDetailId, coupled, user);
    });
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Edits a road detail under its own optimistic lock — the detail's revision, not its page's. A
   * road-detail write deliberately does not bump the page: cross-bumping would make every sibling edit
   * conflict against a page token the client legitimately holds, and legacy has no lock at all to be
   * faithful to.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the owning page, part of the identity check
   * @param roadDetailId the road detail to edit
   * @param request the entered fields, carrying the last-read revision
   * @param user the actor stamped into the audit columns
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response updateRoadDetail(
      long millId, int year, int pageId, int roadDetailId, RoadDetailRequest request, String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    int expectedRevision = requireRevision(request.revisionCount());
    requireRoadDetail(roadDetailId, pageId, millId, year);
    requireOfferedDetailCodes(millId, year, request);
    MoistureCodePair moisture = deriveMoistureCodes(request);
    RoadDetailRequest coupled = applyBallastCoupling(request);

    persist(() -> {
      int updated = repository.updateRoadDetail(
          toDetailEntity(roadDetailId, pageId, coupled), moisture.soilMoistureCode(),
          moisture.asmCode(), millId, year, expectedRevision, user);
      if (updated == 0) {
        requireRoadDetail(roadDetailId, pageId, millId, year);
        throw new StaleRevisionException();
      }
      writeCostLines(roadDetailId, coupled, user);
    });
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Deletes one road detail and its cost lines, leaving the page and its other details untouched.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the owning page
   * @param roadDetailId the road detail to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response deleteRoadDetail(
      long millId, int year, int pageId, int roadDetailId, boolean callerMayEdit) {
    requireDraft(millId, year);
    requireRoadDetail(roadDetailId, pageId, millId, year);
    persist(() -> {
      repository.deleteCostsForRoadDetail(roadDetailId);
      if (repository.deleteRoadDetail(roadDetailId, pageId) == 0) {
        throw new RoadDetailNotFoundException();
      }
    });
    return getSchedule10(millId, year, callerMayEdit);
  }

  /**
   * Runs the Schedule 10 readiness rules over the current document.
   *
   * <p>Mutates nothing and is deliberately NOT Draft-gated: a submitted or verified schedule can still
   * be checked, which is why the endpoint asks only for view rights. Scope is always the whole
   * schedule — legacy has no per-page mode, and neither does any other schedule here.
   *
   * <p>Evaluating the assembled document rather than the tables means Check Status and the GET can
   * never disagree, and it puts the derived totals that several rules check within reach.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @return the unresolved outcome; the controller composes the verbatim text
   */
  @Transactional(readOnly = true)
  public Schedule10CheckStatus.Outcome checkStatus(long millId, int year) {
    // callerMayEdit is irrelevant to the rules, and passing false keeps this read from implying any
    // edit authority in the document it evaluates.
    return Schedule10CheckStatus.evaluate(getSchedule10(millId, year, false));
  }

  // -----------------------------------------------------------------------------------------------
  // Gates and rules
  // -----------------------------------------------------------------------------------------------

  /**
   * The write gate: only a Draft 1–10 track may be written.
   *
   * <p>Legacy has no server-side check at all — its gate is the rendered {@code disabled} attribute —
   * so a crafted post reaches its DAO unimpeded. This is the house hardening rather than parity.
   *
   * <p>The status read is deliberately not locked. A concurrent transition between this check and the
   * write is theoretically possible, but no endpoint in the application can move a track today, and
   * the locked variant belongs to the status-transition work where it can be applied consistently.
   */
  private void requireDraft(long millId, int year) {
    if (!DRAFT.equals(repository.findTrackStatus(millId, year).orElse(null))) {
      throw new ScheduleNotEditableException();
    }
  }

  private static int requireRevision(Integer revisionCount) {
    if (revisionCount == null) {
      throw new RevisionCountRequiredException();
    }
    return revisionCount;
  }

  private void requirePage(int pageId, long millId, int year) {
    if (repository.countPage(pageId, millId, year) == 0) {
      throw new ConstructionPageNotFoundException();
    }
  }

  private void requireRoadDetail(int roadDetailId, int pageId, long millId, int year) {
    if (repository.countRoadDetail(roadDetailId, pageId, millId, year) == 0) {
      throw new RoadDetailNotFoundException();
    }
  }

  /**
   * Applies the mutual-exclusion rule before anything is persisted: a page is TSA-located or
   * TFL-located, never both.
   *
   * <p>The server clears the counterpart rather than trusting the client. Legacy enforced the
   * exclusion only in the browser, and its DAO set the supply block unconditionally before the TFL
   * branch and never nulled the other leg — so a crafted post could store an inconsistent
   * combination. No real page in delivery carries both, which is the intent this makes enforceable.
   */
  private Location classify(ConstructionPageRequest request) {
    if (TFL.equals(request.tsaOrTfl())) {
      String canonical = RoadGroup10Lookup.canonicalTfl(blankToNull(request.tflNumberCode()));
      if (canonical == null) {
        throw new InvalidTflNumberException();
      }
      return new Location(null, null, canonical);
    }
    String tsaNumber = blankToNull(request.tsaOrTfl());
    if (tsaNumber != null && tsaNumber.length() > TSA_NUMBER_MAX) {
      // The 3-character allowance on the field exists only for the "TFL" sentinel. A wider TSA code
      // would reach a VARCHAR2(2) column and surface as an opaque 500 instead of naming the field.
      throw new InvalidClassificationCodeException();
    }
    return new Location(tsaNumber, blankToNull(request.supplyBlock()), null);
  }

  /**
   * Derives the two moisture codes the business removed from the screen but the schema still demands.
   *
   * <p>This is the port of legacy's own filter-and-auto-select: the cross-reference resolves a BEC
   * classification plus RSMR class to the moisture pair, and legacy selected it automatically whenever
   * the filtered list held exactly one entry. Both inputs are mandatory on every write, so the
   * derivation always has what it needs.
   *
   * <p>No sentinel is ever written. These columns carry a real moisture classification that the legacy
   * print reports consume, and every road detail in delivery holds genuine values.
   *
   * <p>Zero candidates is a rejection, not a fallback: it means the classification is not offered at
   * all, or is offered but has no pair for the submitted RSMR class. Letting it through would reach two
   * NOT NULL columns with enabled foreign keys and return an opaque constraint violation.
   */
  private MoistureCodePair deriveMoistureCodes(RoadDetailRequest request) {
    List<MoistureCodePair> candidates =
        repository.findMoistureCodes(request.becbiogeoCatalogueId(), request.relSoilMoistRgmClsCode());
    if (candidates.isEmpty()) {
      throw new InvalidBecClassificationException();
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    return candidates.stream()
        .min(Comparator.comparingInt((MoistureCodePair pair) -> gradientRank(pair.asmCode()))
            .thenComparing(MoistureCodePair::asmCode)
            .thenComparing(MoistureCodePair::soilMoistureCode))
        .orElseThrow(InvalidBecClassificationException::new);
  }

  /** An ASM code's place on the moisture gradient; anything unrecognised sorts last, deterministically. */
  private static int gradientRank(String asmCode) {
    int rank = ASM_MOISTURE_GRADIENT.indexOf(asmCode);
    return rank >= 0 ? rank : ASM_MOISTURE_GRADIENT.size();
  }

  /**
   * Reproduces legacy's coupling between the ballast method and the stabilizing figures, as its DAO
   * applies it at save time.
   *
   * <ul>
   *   <li>{@code N} — the four dimensions, the actual cost and the other transfer are forced to zero,
   *       and the material code to {@code "NA"}. Note the tree-to-truck transfer is deliberately NOT
   *       zeroed: legacy re-converts only the actual-cost and other-transfer items.</li>
   *   <li>{@code D} — only the material code is forced to {@code "NA"}; the figures are stored as
   *       submitted. The asymmetry with {@code N} is legacy's, not an oversight here.</li>
   *   <li>{@code C} — nothing is forced, and the material code is required.</li>
   * </ul>
   *
   * <p>Legacy additionally cleared these fields from the browser when the method changed. That is view
   * state rather than save behaviour, and a stateless API cannot observe a change — so only the
   * save-time rules are reproduced.
   */
  private RoadDetailRequest applyBallastCoupling(RoadDetailRequest request) {
    StabilizingRequest stabilizing = request.stabilizing();
    String method = stabilizing.ballastMethodCode();

    if (BALLAST_CRUSHED.equals(method)) {
      if (blankToNull(stabilizing.ballastMaterialCode()) == null) {
        throw new InvalidClassificationCodeException();
      }
      return request;
    }

    boolean notRequired = BALLAST_NOT_REQUIRED.equals(method);
    boolean deferred = BALLAST_DEFERRED.equals(method);
    if (!notRequired && !deferred) {
      return request;
    }

    StabilizingRequest coupled = new StabilizingRequest(
        method,
        BALLAST_MATERIAL_NOT_APPLICABLE,
        notRequired ? BigDecimal.ZERO : stabilizing.length(),
        notRequired ? BigDecimal.ZERO : stabilizing.surfaceWidth(),
        notRequired ? BigDecimal.ZERO : stabilizing.depth(),
        notRequired ? BigDecimal.ZERO : stabilizing.distanceToSource(),
        notRequired ? 0 : stabilizing.actualCost(),
        stabilizing.ttTransfer(),
        notRequired ? 0 : stabilizing.otherTransfer());

    return new RoadDetailRequest(
        request.roadName(), request.roadLifetimeCode(), request.becbiogeoCatalogueId(),
        request.relSoilMoistRgmClsCode(), request.sideSlopePct(),
        request.detailedEngineeringCostInd(), request.subGrade(), coupled,
        request.materialComposition(), request.endHaulDistance(), request.endHaulVolume(),
        request.overlandDistance(), request.overlandVolume(), request.comments(),
        request.revisionCount());
  }

  /**
   * Rejects a forest region the year-filtered list does not offer.
   *
   * <p>The list query already includes any code a stored row in this mill/year still references, which
   * is what lets a code survive its own expiry rather than permanently blocking a re-save.
   */
  private void requireOfferedForestRegion(long millId, int year, String code) {
    requireOffered(repository.findForestRegions(millId, year), code);
  }

  private void requireOfferedDetailCodes(long millId, int year, RoadDetailRequest request) {
    requireOffered(repository.findRoadLifetimes(millId, year), request.roadLifetimeCode());
    requireOffered(repository.findRsmrClasses(millId, year), request.relSoilMoistRgmClsCode());
    StabilizingRequest stabilizing = request.stabilizing();
    requireOffered(repository.findBallastMethods(millId, year), stabilizing.ballastMethodCode());
    String material = blankToNull(stabilizing.ballastMaterialCode());
    // "NA" is the code legacy forces for the methods that take no material; it is a real row, but the
    // coupling can substitute it after this check, so only a client-supplied value is validated.
    if (material != null) {
      requireOffered(repository.findBallastMaterials(millId, year), material);
    }
  }

  private static void requireOffered(List<CodeRow> offered, String code) {
    if (code == null) {
      return;
    }
    boolean known = offered.stream().anyMatch(row -> code.equals(row.code()));
    if (!known) {
      // Legacy resolved codes through a cache and silently stored NULL on a miss. That is not
      // reproducible: these columns carry enabled foreign keys, so an unknown code would raise a
      // constraint violation and surface as an opaque 500 rather than naming the field.
      throw new InvalidClassificationCodeException();
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Mapping and persistence
  // -----------------------------------------------------------------------------------------------

  private static RoadConstructionReportEntity toPageEntity(
      int pageId, long millId, int year, ConstructionPageRequest request, Location location) {
    return new RoadConstructionReportEntity(
        pageId, year, millId, CATEGORY, blankToNull(request.constructionPeriod()),
        blankToNull(request.divisionName()), request.forestRegionCode(), location.tsbNumberCode(),
        location.tsaNumber(), location.tflNumberCode(), 0);
  }

  /**
   * Maps the request onto the detail entity, normalising every scaled measurement to its column's
   * declared scale on the way in.
   *
   * <p>Normalising on write as well as on read matters: Oracle does not preserve trailing zeros, so a
   * value stored at the wrong scale would round-trip differently from what was entered.
   */
  private static RoadConstructionReportDetailEntity toDetailEntity(
      int roadDetailId, int pageId, RoadDetailRequest request) {
    SubGradeRequest subGrade = request.subGrade() != null ? request.subGrade() : NO_SUB_GRADE;
    StabilizingRequest stabilizing = request.stabilizing();
    MaterialCompositionRequest material =
        request.materialComposition() != null ? request.materialComposition() : NO_MATERIAL;

    return new RoadConstructionReportDetailEntity(
        roadDetailId,
        pageId,
        request.roadName(),
        request.sideSlopePct(),
        request.roadLifetimeCode(),
        material.rippableRockPct(),
        material.solidRockPct(),
        material.coarsePct(),
        request.becbiogeoCatalogueId(),
        material.finePct(),
        material.organicPct(),
        Schedule10Amounts.atScale(subGrade.length(), LENGTH_SCALE),
        request.detailedEngineeringCostInd(),
        Schedule10Amounts.atScale(request.endHaulDistance(), MEASURE_SCALE),
        toBigDecimal(request.endHaulVolume()),
        Schedule10Amounts.atScale(request.overlandDistance(), MEASURE_SCALE),
        toBigDecimal(request.overlandVolume()),
        stabilizing.ballastMethodCode(),
        Schedule10Amounts.atScale(subGrade.surfaceWidth(), MEASURE_SCALE),
        blankToNull(stabilizing.ballastMaterialCode()),
        Schedule10Amounts.atScale(stabilizing.length(), LENGTH_SCALE),
        Schedule10Amounts.atScale(stabilizing.surfaceWidth(), MEASURE_SCALE),
        Schedule10Amounts.atScale(stabilizing.depth(), MEASURE_SCALE),
        Schedule10Amounts.atScale(stabilizing.distanceToSource(), MEASURE_SCALE),
        request.relSoilMoistRgmClsCode(),
        blankToNull(request.comments()),
        0);
  }

  /**
   * Upserts all twelve cost lines for a road detail.
   *
   * <p>A blank cost is stored as {@code COST = NULL} rather than deleting its row: legacy never
   * removes a cost row on save, and the read path treats a stored NULL exactly as it treats an absent
   * row — the field renders blank while the totals coerce it to zero.
   */
  private void writeCostLines(int roadDetailId, RoadDetailRequest request, String user) {
    SubGradeRequest subGrade = request.subGrade() != null ? request.subGrade() : NO_SUB_GRADE;
    StabilizingRequest stabilizing = request.stabilizing();

    repository.upsertCostLine(roadDetailId, SUB_GRADE_ACTUAL, subGrade.actualCost(), user);
    repository.upsertCostLine(roadDetailId, SUB_GRADE_TRANSFER, subGrade.ttTransfer(), user);
    repository.upsertCostLine(roadDetailId, OTHER_TT_TRANSFER, subGrade.otherTransfer(), user);
    repository.upsertCostLine(roadDetailId, LESS_BRIDGE, subGrade.lessBridges(), user);
    repository.upsertCostLine(roadDetailId, LESS_CULVERT, subGrade.lessCulverts(), user);
    repository.upsertCostLine(roadDetailId, LESS_LANDING, subGrade.lessLandings(), user);
    repository.upsertCostLine(roadDetailId, LESS_OVERLAND, subGrade.lessOverland(), user);
    repository.upsertCostLine(roadDetailId, LESS_OTHER_ENGINEERING, subGrade.lessOtherEng(), user);
    repository.upsertCostLine(roadDetailId, LESS_END_HAUL, subGrade.lessEndHaul(), user);
    repository.upsertCostLine(roadDetailId, STABILIZING_ACTUAL, stabilizing.actualCost(), user);
    repository.upsertCostLine(roadDetailId, STABILIZING_TRANSFER, stabilizing.ttTransfer(), user);
    repository.upsertCostLine(
        roadDetailId, STABILIZING_OTHER_TRANSFER, stabilizing.otherTransfer(), user);
  }

  /**
   * Runs a write, translating a data-access failure into the house save error.
   *
   * <p>Only the exception TYPE is logged. Never the values: a rejected write would otherwise put cost,
   * volume or comment content into the log, which the data-sensitivity rules forbid.
   */
  private void persist(Runnable write) {
    try {
      write.run();
    } catch (DataAccessException ex) {
      LOG.warn("Schedule 10 write failed [{}]", ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  private static BigDecimal toBigDecimal(Integer value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
