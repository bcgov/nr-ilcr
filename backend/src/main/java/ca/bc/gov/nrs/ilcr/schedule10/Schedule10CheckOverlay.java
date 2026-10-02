package ca.bc.gov.nrs.ilcr.schedule10;

import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_BRIDGE;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_CULVERT;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_END_HAUL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_LANDING;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_OTHER_ENGINEERING;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.LESS_OVERLAND;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.OTHER_TT_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_OTHER_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_TRANSFER;

import ca.bc.gov.nrs.ilcr.schedule10.Schedule10DocumentAssembler.MaterialPercentages;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10DocumentAssembler.StabilizingMeasures;
import ca.bc.gov.nrs.ilcr.schedule10.dto.BecClassification;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPage;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetail;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.MaterialCompositionEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.PageEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.RoadEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.StabilizingEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CheckRequest.SubGradeEntry;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10CodeLists;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Overlays the Schedule 10 editor currently on screen onto the stored document, so Check Status
 * judges what the user is LOOKING AT (bcgov/nr-ilcr#359, group C).
 *
 * <p>The overlaid document goes through the one evaluator, {@link Schedule10CheckStatus#evaluate}
 * (AD-5), so rules, message bytes and emission order are those of the stored path. Only the INPUTS
 * change, and every derived value is rebuilt through the code that builds it on read:
 *
 * <ul>
 *   <li><strong>Page.</strong> Division, Period Surveyed and the TSA/TFL location replace the
 *       stored ones. The TSA/TFL selector maps through {@link Schedule10Service#locate}, the write
 *       path's own rule, so an unsaved switch to TFL with the TFL number blank reports {@code TFL
 *       #}, not {@code Supply Block}. The page label is rebuilt by {@link
 *       Schedule10DocumentAssembler#pageLabel} from the on-screen values.
 *   <li><strong>Road.</strong> Every field the rules read replaces the stored one. The totals,
 *       material type total and $/km are recomputed by the assembler's own substructure builders
 *       ({@link Schedule10Amounts} underneath); the BEC row is re-resolved by the on-screen id, so
 *       Sub Zone follows the on-screen BEC (spec D3); and the road label is rebuilt by {@link
 *       Schedule10DocumentAssembler#roadDetailLabel} with the on-screen name.
 * </ul>
 *
 * <p>Null stays null: a blank on-screen value is judged as blank, never coerced to zero. A page or
 * road with a null or unknown id is ignored — a new row is never evaluated, as in legacy.
 */
final class Schedule10CheckOverlay {

  private static final SubGradeEntry NO_SUB_GRADE =
      new SubGradeEntry(null, null, null, null, null, null, null, null, null, null, null);

  private static final StabilizingEntry NO_STABILIZING =
      new StabilizingEntry(null, null, null, null, null, null, null, null, null);

  private static final MaterialCompositionEntry NO_MATERIAL =
      new MaterialCompositionEntry(null, null, null, null, null);

  private Schedule10CheckOverlay() {}

  /**
   * The stored document with the open page panel and/or road editor overlaid.
   *
   * @param document the stored document, as assembled for the GET
   * @param request the Check Status body; a null member means no such editor is open
   * @return the document to evaluate; the same instance when nothing applies
   */
  static Schedule10Response apply(Schedule10Response document, Schedule10CheckRequest request) {
    if (request == null || (request.page() == null && request.road() == null)) {
      return document;
    }
    PageEntry pageEntry = request.page();
    RoadEntry roadEntry = request.road();
    List<ConstructionPage> pages = new ArrayList<>(document.pages().size());
    for (ConstructionPage page : document.pages()) {
      ConstructionPage overlaid = page;
      if (roadEntry != null && Objects.equals(roadEntry.pageId(), page.pageId())) {
        overlaid = overlayRoad(overlaid, roadEntry, document.codeLists());
      }
      if (pageEntry != null && Objects.equals(pageEntry.pageId(), page.pageId())) {
        overlaid = overlayPage(overlaid, pageEntry);
      }
      pages.add(overlaid);
    }
    return new Schedule10Response(
        document.millId(),
        document.year(),
        document.trackStatus(),
        document.editable(),
        pages,
        document.codeLists(),
        document.message());
  }

  /** The page with its checked fields, derived location, Road Group and label taken from screen. */
  private static ConstructionPage overlayPage(ConstructionPage page, PageEntry entry) {
    Schedule10Service.Location location =
        Schedule10Service.locate(entry.tsaOrTfl(), entry.supplyBlock(), entry.tflNumberCode());
    String period = Schedule10Service.blankToNull(entry.constructionPeriod());
    return new ConstructionPage(
        page.pageId(),
        page.pageNumber(),
        Schedule10DocumentAssembler.pageLabel(
            page.pageNumber(),
            period,
            location.tsaNumber(),
            location.tsbNumberCode(),
            location.tflNumberCode()),
        page.forestRegionCode(),
        location.tsaNumber(),
        location.tsbNumberCode(),
        location.tflNumberCode(),
        RoadGroup10Lookup.rmgFor(
            location.tsaNumber(), location.tsbNumberCode(), location.tflNumberCode()),
        Schedule10Service.blankToNull(entry.divisionName()),
        period,
        page.roadDetailCount(),
        page.revisionCount(),
        page.roadDetails(),
        page.originalValues());
  }

  /** The page with the matching road rebuilt from screen; unchanged when no road matches. */
  private static ConstructionPage overlayRoad(
      ConstructionPage page, RoadEntry entry, Schedule10CodeLists codeLists) {
    if (entry.roadDetailId() == null) {
      return page;
    }
    List<RoadDetail> details = new ArrayList<>(page.roadDetails().size());
    boolean matched = false;
    for (RoadDetail detail : page.roadDetails()) {
      if (detail.roadDetailId() == entry.roadDetailId()) {
        details.add(overlayRoadDetail(detail, entry, codeLists));
        matched = true;
      } else {
        details.add(detail);
      }
    }
    if (!matched) {
      return page;
    }
    return new ConstructionPage(
        page.pageId(),
        page.pageNumber(),
        page.pageLabel(),
        page.forestRegionCode(),
        page.tsaNumber(),
        page.tsbNumberCode(),
        page.tflNumberCode(),
        page.roadGroup(),
        page.divisionName(),
        page.constructionPeriod(),
        page.roadDetailCount(),
        page.revisionCount(),
        details,
        page.originalValues());
  }

  /**
   * One road rebuilt from its on-screen inputs. The fields the rules do not read (Road Type, the
   * engineering indicator, End Haul, Overland, Comments) keep their stored values; original values
   * are not carried, because the evaluator does not read them.
   */
  private static RoadDetail overlayRoadDetail(
      RoadDetail stored, RoadEntry entry, Schedule10CodeLists codeLists) {
    SubGradeEntry subGrade = entry.subGrade() != null ? entry.subGrade() : NO_SUB_GRADE;
    StabilizingEntry stabilizing =
        entry.stabilizing() != null ? entry.stabilizing() : NO_STABILIZING;

    // Keyed by the same legacy cost-item ordinals the read path routes stored cost rows by, so the
    // assembler's builders see the on-screen figures exactly as they would see saved ones. HashMap,
    // because a blank figure is a present-but-null entry.
    Map<Integer, BigDecimal> costs = new HashMap<>();
    costs.put(SUB_GRADE_ACTUAL, subGrade.actualCost());
    costs.put(SUB_GRADE_TRANSFER, subGrade.ttTransfer());
    costs.put(OTHER_TT_TRANSFER, subGrade.otherTransfer());
    costs.put(LESS_BRIDGE, subGrade.lessBridges());
    costs.put(LESS_CULVERT, subGrade.lessCulverts());
    costs.put(LESS_LANDING, subGrade.lessLandings());
    costs.put(LESS_OVERLAND, subGrade.lessOverland());
    costs.put(LESS_OTHER_ENGINEERING, subGrade.lessOtherEng());
    costs.put(LESS_END_HAUL, subGrade.lessEndHaul());
    costs.put(STABILIZING_ACTUAL, stabilizing.actualCost());
    costs.put(STABILIZING_TRANSFER, stabilizing.ttTransfer());
    costs.put(STABILIZING_OTHER_TRANSFER, stabilizing.otherTransfer());

    MaterialCompositionEntry material =
        entry.materialComposition() != null ? entry.materialComposition() : NO_MATERIAL;
    return new RoadDetail(
        stored.roadDetailId(),
        stored.rowNumber(),
        Schedule10DocumentAssembler.roadDetailLabel(stored.rowNumber(), entry.roadName()),
        entry.roadName(),
        stored.roadLifetimeCode(),
        resolveBec(entry.becbiogeoCatalogueId(), stored, codeLists),
        entry.relSoilMoistRgmClsCode(),
        entry.sideSlopePct(),
        Schedule10DocumentAssembler.subGrade(
            subGrade.length(), subGrade.surfaceWidth(), costs, null),
        Schedule10DocumentAssembler.stabilizing(
            stabilizing.ballastMethodCode(),
            stabilizing.ballastMaterialCode(),
            new StabilizingMeasures(
                stabilizing.length(),
                stabilizing.surfaceWidth(),
                stabilizing.depth(),
                stabilizing.distanceToSource()),
            costs,
            null),
        Schedule10DocumentAssembler.materialComposition(
            new MaterialPercentages(
                material.solidRockPct(),
                material.rippableRockPct(),
                material.coarsePct(),
                material.finePct(),
                material.organicPct()),
            null),
        stored.detailedEngineeringCostInd(),
        stored.endHaulDistance(),
        stored.endHaulVolume(),
        stored.overlandDistance(),
        stored.overlandVolume(),
        stored.comments(),
        stored.revisionCount());
  }

  /**
   * The catalogue row for the on-screen BEC id: the stored road's own row when the id is unchanged
   * (it may be a de-listed classification the document still resolves), otherwise the offerable row
   * the dropdown served. Null when the id is blank or matches neither, which the rules report as a
   * missing Sub Zone and an invalid BEC Zone.
   */
  private static BecClassification resolveBec(
      Integer becId, RoadDetail stored, Schedule10CodeLists codeLists) {
    if (becId == null) {
      return null;
    }
    BecClassification storedBec = stored.becClassification();
    if (storedBec != null && storedBec.biogeoclimaticCatalogueId() == becId) {
      return storedBec;
    }
    if (codeLists == null) {
      return null;
    }
    return codeLists.becClassifications().stream()
        .filter(bec -> bec.biogeoclimaticCatalogueId() == becId)
        .findFirst()
        .orElse(null);
  }
}
