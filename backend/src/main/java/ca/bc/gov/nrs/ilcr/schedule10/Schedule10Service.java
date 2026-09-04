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
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_OTHER_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.STABILIZING_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_ACTUAL;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10CostItems.SUB_GRADE_TRANSFER;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10PersistenceException.DETAIL_NOT_DELETED;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10PersistenceException.DETAIL_NOT_SAVED;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10PersistenceException.PAGE_NOT_DELETED;
import static ca.bc.gov.nrs.ilcr.schedule10.Schedule10PersistenceException.PAGE_NOT_SAVED;

import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.CodeRow;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository.MoistureCodePair;
import ca.bc.gov.nrs.ilcr.schedule10.dto.ConstructionPageRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.MaterialCompositionRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.RoadDetailRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.Schedule10Response;
import ca.bc.gov.nrs.ilcr.schedule10.dto.StabilizingRequest;
import ca.bc.gov.nrs.ilcr.schedule10.dto.SubGradeRequest;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Schedule 10 write path: create, edit, copy and delete construction pages and road details,
 * plus the read-only Check Status evaluation. Every method answers with the refreshed document.
 *
 * <p>Document ASSEMBLY lives in {@link Schedule10DocumentAssembler}, not here. The two were one
 * class until code review follow-up 2026-08-19, at which point it ran to 1137 lines doing two
 * genuinely separate jobs. The seam is clean: nothing in this class touches a response DTO beyond
 * returning one, and nothing in the assembler touches a request DTO.
 *
 * <p>Each public method opens the transaction — read-only for {@link #getSchedule10} and {@link
 * #checkStatus}, read-write for a save — and the assembly then runs inside it, so its queries
 * observe a single consistent snapshot. That ordering matters: a self-invoked
 * {@code @Transactional} method never passes through the Spring proxy, so annotating the assembly
 * would have looked like a guarantee without being one.
 *
 * <p>Write rules that matter:
 *
 * <ul>
 *   <li><strong>Draft only.</strong> Every write is gated on the 1–10 track status being {@code D},
 *       server-side. Legacy has no such gate at all.
 *   <li><strong>Costs</strong> are keyed rows, not columns: all twelve are maintained per road
 *       detail, update-in-place, and a blank stores {@code COST = NULL} rather than deleting the
 *       row (BR-08, AC5).
 *   <li><strong>Derived values are never accepted from a client.</strong> Totals, rates, labels and
 *       positional numbers are computed on read; the two LD-removed moisture columns are derived
 *       from BEC Zone plus RSMR class, and preserved untouched when neither input changes.
 * </ul>
 */
@Service
public class Schedule10Service {

  /** The 1–10 track Draft code; the only status at which a SUBMITTER may edit (AD-9). */
  private static final String DRAFT = "D";

  private static final Logger LOG = LoggerFactory.getLogger(Schedule10Service.class);

  private final Schedule10Repository repository;

  private final Schedule10DocumentAssembler assembler;

  /**
   * Wires the Schedule 10 repository. Mill/year validation happens in the controller via {@code
   * MillContextService} (AD-4) before this service is ever reached.
   *
   * <p>The document assembler is constructed here rather than injected: it is an implementation
   * detail of this service with no independent lifecycle, and constructing it keeps the existing
   * single-argument constructor that every test already uses.
   *
   * @param repository the Schedule 10 data access
   */
  public Schedule10Service(Schedule10Repository repository) {
    this.repository = repository;
    this.assembler = new Schedule10DocumentAssembler(repository);
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
    return document(millId, year, callerMayEdit);
  }

  /**
   * Reads the track status once, derives editability from it, and hands both to the assembler.
   *
   * <p>Reading the status HERE rather than inside the assembly also removes a second query the
   * write paths used to make: {@code requireDraft} read the status for its gate, and the assembly
   * then read it again to compute {@code editable} (code review 2026-08-18, Low). One read per
   * request now.
   *
   * <p>{@code editable} is {@code callerMayEdit && "D".equals(trackStatus)} — the SUBMITTER row
   * only. Legacy also grants edit on {@code S}+non-Licensee and {@code V}+Admin, but no shipped
   * schedule implements those paths; they belong to the AD-9/AR14 remediation. Do not add a second
   * code path.
   */
  private Schedule10Response document(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    boolean editable = callerMayEdit && DRAFT.equals(trackStatus);
    return assembler.assemble(millId, year, trackStatus, editable);
  }

  // ===============================================================================================
  // WRITES
  // ===============================================================================================

  /**
   * The legacy sentinel the TSA/TFL dropdown carries for a TFL-located page ({@code Constant.TFL}).
   */
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
   * <p>The cross-reference resolves a BEC classification plus RSMR class to exactly one moisture
   * pair for the overwhelming majority of combinations. Where it offers more than one, legacy
   * declined to auto-select and left the choice to a dropdown the business has since removed — so a
   * rule is needed, and the driest candidate is chosen because it is the conservative end of the
   * scale and, being ordered by the Ministry's own code semantics, is stable rather than
   * incidental.
   */
  private static final List<String> ASM_MOISTURE_GRADIENT =
      List.of("ED", "VD", "MD", "SD", "F", "M", "VM", "W");

  /** Stand-ins so an omitted optional substructure needs no null checks at every use site. */
  private static final SubGradeRequest NO_SUB_GRADE =
      new SubGradeRequest(null, null, null, null, null, null, null, null, null, null, null);

  private static final MaterialCompositionRequest NO_MATERIAL =
      new MaterialCompositionRequest(null, null, null, null, null);

  /** A page's location after the mutual-exclusion rule has been applied. */
  private record Location(String tsaNumber, String tsbNumberCode, String tflNumberCode) {}

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
    persist(
        () ->
            repository.insertPage(
                toPageEntity(pageId, millId, year, request, location), millId, year, user),
        PAGE_NOT_SAVED);
    return document(millId, year, callerMayEdit);
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
      long millId,
      int year,
      int pageId,
      ConstructionPageRequest request,
      String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    int expectedRevision = requireRevision(request.revisionCount());
    // Existence first, and deliberately before any body validation: an unknown or foreign id must
    // answer 404 regardless of what the body contains, not 400 for a field the caller cannot reach.
    requirePage(pageId, millId, year);
    requireOfferedForestRegion(millId, year, request.forestRegionCode());
    Location location = classify(request);
    persist(
        () -> {
          int updated =
              repository.updatePage(
                  toPageEntity(pageId, millId, year, request, location),
                  millId,
                  year,
                  expectedRevision,
                  user);
          if (updated == 0) {
            // Zero rows means the id is gone or the revision moved. Re-probe to say which.
            requirePage(pageId, millId, year);
            throw new StaleRevisionException();
          }
        },
        PAGE_NOT_SAVED);
    return document(millId, year, callerMayEdit);
  }

  /**
   * Copies a construction page and saves the copy immediately.
   *
   * <p><strong>The copy carries no road details.</strong> Legacy's copy constructor nulls both
   * detail collections and then saves without cascading, so only the page header is duplicated.
   * Reproduced as-is; a copy that silently duplicated every road would be a different feature.
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
    RoadConstructionReportEntity source =
        repository.findPages(millId, year).stream()
            .filter(page -> page.roadConstructionReprtId() == pageId)
            .findFirst()
            .orElseThrow(ConstructionPageNotFoundException::new);

    int copyId = repository.nextPageId();
    RoadConstructionReportEntity copy =
        new RoadConstructionReportEntity(
            copyId,
            year,
            millId,
            CATEGORY,
            source.constructionPeriod(),
            source.constructionDivisionName(),
            source.ilcrForestRegionCode(),
            source.tsbNumberCode(),
            source.tsaNumber(),
            source.tflNumberCode(),
            0);
    persist(() -> repository.insertPage(copy, millId, year, user), PAGE_NOT_SAVED);
    return document(millId, year, callerMayEdit);
  }

  /**
   * Deletes a page and everything beneath it.
   *
   * <p>Order is grandchildren, children, parent. Neither delete-path foreign key cascades in
   * delivery, so the reverse order is rejected outright. Legacy reached the same order through its
   * ORM cascade.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @param pageId the page to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the refreshed document
   */
  @Transactional
  public Schedule10Response deletePage(long millId, int year, int pageId, boolean callerMayEdit) {
    requireDraft(millId, year);
    requirePage(pageId, millId, year);
    persist(
        () -> {
          repository.deleteCostsForPage(pageId, millId, year);
          repository.deleteRoadDetailsForPage(pageId, millId, year);
          // The result is checked rather than discarded: a silently zero-row delete would answer
          // "deleted successfully" while the row survived.
          if (repository.deletePage(pageId, millId, year) == 0) {
            throw new ConstructionPageNotFoundException();
          }
        },
        PAGE_NOT_DELETED);
    return document(millId, year, callerMayEdit);
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
      long millId,
      int year,
      int pageId,
      RoadDetailRequest request,
      String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    requirePage(pageId, millId, year);
    requireOfferedDetailCodes(millId, year, request);
    MoistureCodePair moisture = deriveMoistureCodes(request);
    RoadDetailRequest coupled = applyBallastCoupling(request);

    int roadDetailId = repository.nextRoadDetailId();
    persist(
        () -> {
          repository.insertRoadDetail(
              toDetailEntity(roadDetailId, pageId, coupled),
              moisture.soilMoistureCode(),
              moisture.asmCode(),
              user);
          writeCostLines(roadDetailId, coupled, user, millId, year);
        },
        DETAIL_NOT_SAVED);
    return document(millId, year, callerMayEdit);
  }

  /**
   * Edits a road detail under its own optimistic lock — the detail's revision, not its page's. A
   * road-detail write deliberately does not bump the page: cross-bumping would make every sibling
   * edit conflict against a page token the client legitimately holds, and legacy has no lock at all
   * to be faithful to.
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
      long millId,
      int year,
      int pageId,
      int roadDetailId,
      RoadDetailRequest request,
      String user,
      boolean callerMayEdit) {
    requireDraft(millId, year);
    int expectedRevision = requireRevision(request.revisionCount());
    requireRoadDetail(roadDetailId, pageId, millId, year);
    requireOfferedDetailCodes(millId, year, request);
    MoistureCodePair moisture = moistureForEdit(roadDetailId, request);
    RoadDetailRequest coupled = applyBallastCoupling(request);

    persist(
        () -> {
          int updated =
              repository.updateRoadDetail(
                  toDetailEntity(roadDetailId, pageId, coupled),
                  moisture.soilMoistureCode(),
                  moisture.asmCode(),
                  millId,
                  year,
                  expectedRevision,
                  user);
          if (updated == 0) {
            requireRoadDetail(roadDetailId, pageId, millId, year);
            throw new StaleRevisionException();
          }
          writeCostLines(roadDetailId, coupled, user, millId, year);
        },
        DETAIL_NOT_SAVED);
    return document(millId, year, callerMayEdit);
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
    persist(
        () -> {
          repository.deleteCostsForRoadDetail(roadDetailId, millId, year);
          if (repository.deleteRoadDetail(roadDetailId, pageId, millId, year) == 0) {
            throw new RoadDetailNotFoundException();
          }
        },
        DETAIL_NOT_DELETED);
    return document(millId, year, callerMayEdit);
  }

  /**
   * Runs the Schedule 10 readiness rules over the current document.
   *
   * <p>Mutates nothing and is deliberately NOT Draft-gated: a submitted or verified schedule can
   * still be checked, which is why the endpoint asks only for view rights. Scope is always the
   * whole schedule — legacy has no per-page mode, and neither does any other schedule here.
   *
   * <p>Evaluating the assembled document rather than the tables means Check Status and the GET can
   * never disagree, and it puts the derived totals that several rules check within reach.
   *
   * @param millId the validated mill
   * @param year the validated reporting year
   * @return the unresolved outcome; {@link Schedule10CheckStatusResolver} composes the verbatim
   *     text
   */
  @Transactional(readOnly = true)
  public Schedule10CheckStatus.Outcome checkStatus(long millId, int year) {
    // callerMayEdit is irrelevant to the rules, and passing false keeps this read from implying any
    // edit authority in the document it evaluates.
    return Schedule10CheckStatus.evaluate(document(millId, year, false));
  }

  // -----------------------------------------------------------------------------------------------
  // Gates and rules
  // -----------------------------------------------------------------------------------------------

  /**
   * The write gate: only a Draft 1–10 track may be written.
   *
   * <p>Legacy has no server-side check at all — its gate is the rendered {@code disabled} attribute
   * — so a crafted post reaches its DAO unimpeded. This is the house hardening rather than parity.
   *
   * <p>The status read is deliberately not locked. A concurrent transition between this check and
   * the write is theoretically possible, but no endpoint in the application can move a track today,
   * and the locked variant belongs to the status-transition work where it can be applied
   * consistently.
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
   * Derives the two moisture codes the business removed from the screen but the schema still
   * demands.
   *
   * <p>This is the port of legacy's own filter-and-auto-select: the cross-reference resolves a BEC
   * classification plus RSMR class to the moisture pair, and legacy selected it automatically
   * whenever the filtered list held exactly one entry. Both inputs are mandatory on every write, so
   * the derivation always has what it needs.
   *
   * <p>No sentinel is ever written. These columns carry a real moisture classification that the
   * legacy print reports consume, and every road detail in delivery holds genuine values.
   *
   * <p>Zero candidates is a rejection, not a fallback: it means the classification is not offered
   * at all, or is offered but has no pair for the submitted RSMR class. Letting it through would
   * reach two NOT NULL columns with enabled foreign keys and return an opaque constraint violation.
   */
  private MoistureCodePair deriveMoistureCodes(RoadDetailRequest request) {
    List<MoistureCodePair> candidates =
        repository.findMoistureCodes(
            request.becbiogeoCatalogueId(), request.relSoilMoistRgmClsCode());
    if (candidates.isEmpty()) {
      throw new InvalidBecClassificationException();
    }
    if (candidates.size() == 1) {
      return candidates.get(0);
    }
    return candidates.stream()
        .min(
            Comparator.comparingInt((MoistureCodePair pair) -> gradientRank(pair.asmCode()))
                .thenComparing(MoistureCodePair::asmCode)
                .thenComparing(MoistureCodePair::soilMoistureCode))
        .orElseThrow(InvalidBecClassificationException::new);
  }

  /**
   * The moisture pair for an EDIT: preserved when the classification is unchanged, re-derived only
   * when an input actually moved.
   *
   * <p>Legacy never rewrites these two columns on a save. {@code filterMoistureCodeLists()} ({@code
   * Schedule10MB:665-689}) rebuilds the two dropdown LISTS and nothing else, so the stored ASM and
   * soil-moisture codes change only when the user selects new ones from the filtered list.
   * Re-deriving on every edit would let a request that changed only {@code comments} silently
   * rewrite a stored {@code (F, Moist)} to {@code (SD, Moist)} through the multi-candidate
   * tie-break — a rule invented for this port, because legacy asked the user (code review
   * 2026-08-18, decision D4).
   *
   * <p>It also delivers the unchanged-code exemption for the BEC classification (decision D5): a
   * stored id that has dropped out of the offerable xref-gated set no longer makes its road detail
   * permanently unsaveable, because an unchanged classification never re-enters the gate.
   */
  private MoistureCodePair moistureForEdit(int roadDetailId, RoadDetailRequest request) {
    return repository
        .findStoredClassification(roadDetailId)
        .filter(stored -> stored.asmCode() != null && stored.soilMoistureCode() != null)
        .filter(stored -> Objects.equals(stored.becId(), request.becbiogeoCatalogueId()))
        .filter(stored -> Objects.equals(stored.rsmrClassCode(), request.relSoilMoistRgmClsCode()))
        .map(stored -> new MoistureCodePair(stored.asmCode(), stored.soilMoistureCode()))
        .orElseGet(() -> deriveMoistureCodes(request));
  }

  /**
   * An ASM code's place on the moisture gradient; anything unrecognised sorts last,
   * deterministically.
   */
  private static int gradientRank(String asmCode) {
    int rank = ASM_MOISTURE_GRADIENT.indexOf(asmCode);
    return rank >= 0 ? rank : ASM_MOISTURE_GRADIENT.size();
  }

  /**
   * Reproduces legacy's coupling between the ballast method and the stabilizing figures, as its DAO
   * applies it at save time.
   *
   * <ul>
   *   <li>{@code N} — the four dimensions, the actual cost and the other transfer are forced to
   *       zero, and the material code to {@code "NA"}. Note the tree-to-truck transfer is
   *       deliberately NOT zeroed: legacy re-converts only the actual-cost and other-transfer
   *       items.
   *   <li>{@code D} — only the material code is forced to {@code "NA"}; the figures are stored as
   *       submitted. The asymmetry with {@code N} is legacy's, not an oversight here.
   *   <li>{@code C} — nothing is forced, and the material code is required.
   * </ul>
   *
   * <p>Legacy additionally cleared these fields from the browser when the method changed. That is
   * view state rather than save behaviour, and a stateless API cannot observe a change — so only
   * the save-time rules are reproduced.
   */
  private RoadDetailRequest applyBallastCoupling(RoadDetailRequest request) {
    StabilizingRequest stabilizing = request.stabilizing();
    String method = stabilizing.ballastMethodCode();

    if (BALLAST_CRUSHED.equals(method)) {
      if (blankToNull(stabilizing.ballastMaterialCode()) == null) {
        throw new MaterialCodeTypeRequiredException();
      }
      return request;
    }

    boolean notRequired = BALLAST_NOT_REQUIRED.equals(method);
    boolean deferred = BALLAST_DEFERRED.equals(method);

    // Any OTHER method keeps its figures — legacy's else branch stores them as submitted — but the
    // material code still cannot be null: ILCR_ROAD_BALLAST_MATERL_CODE is NOT NULL, and legacy's
    // dropdown carried an empty option, so a blank pick raised ORA-01400 there too. Defaulting to
    // "NA" follows what legacy already does for the other two non-crushed methods, and keeps a
    // Ministry-added method code (the reference table already holds XP and FU) from turning a save
    // into an opaque 500. Found at code review 2026-08-18: this branch previously returned the
    // request untouched, so a blank material reached Oracle.
    if (!notRequired && !deferred) {
      if (blankToNull(stabilizing.ballastMaterialCode()) != null) {
        return request;
      }
      return withStabilizing(
          request,
          new StabilizingRequest(
              method,
              BALLAST_MATERIAL_NOT_APPLICABLE,
              stabilizing.length(),
              stabilizing.surfaceWidth(),
              stabilizing.depth(),
              stabilizing.distanceToSource(),
              stabilizing.actualCost(),
              stabilizing.ttTransfer(),
              stabilizing.otherTransfer()));
    }

    StabilizingRequest coupled =
        new StabilizingRequest(
            method,
            BALLAST_MATERIAL_NOT_APPLICABLE,
            notRequired ? BigDecimal.ZERO : stabilizing.length(),
            notRequired ? BigDecimal.ZERO : stabilizing.surfaceWidth(),
            notRequired ? BigDecimal.ZERO : stabilizing.depth(),
            notRequired ? BigDecimal.ZERO : stabilizing.distanceToSource(),
            notRequired ? 0 : stabilizing.actualCost(),
            stabilizing.ttTransfer(),
            notRequired ? 0 : stabilizing.otherTransfer());

    return withStabilizing(request, coupled);
  }

  /**
   * Defaults the detailed-engineering-cost indicator to {@code "N"} when the client omits it.
   *
   * <p>{@code DETAIL_ENGINEERING_COST_IND} is {@code VARCHAR2(1) NOT NULL} with no default and no
   * trigger, while the field is optional on the request. Legacy's {@code pageDtlECIncludeCosts} is
   * a two-item dropdown ({@code No}/{@code N}, {@code Yes}/{@code Y}) with no empty option and no
   * required flag, so it always submitted a value and its effective default was {@code N}.
   * Reproducing that default is what keeps an omitted field from reaching Oracle as ORA-01400 and
   * surfacing as an opaque 500 (code review 2026-08-18).
   */
  private static String detailEngineeringCostIndOrDefault(String submitted) {
    String value = blankToNull(submitted);
    return value == null ? "N" : value;
  }

  /** Rebuilds a road-detail request around a replacement stabilizing substructure. */
  private RoadDetailRequest withStabilizing(
      RoadDetailRequest request, StabilizingRequest stabilizing) {
    return new RoadDetailRequest(
        request.roadName(),
        request.roadLifetimeCode(),
        request.becbiogeoCatalogueId(),
        request.relSoilMoistRgmClsCode(),
        request.sideSlopePct(),
        request.detailedEngineeringCostInd(),
        request.subGrade(),
        stabilizing,
        request.materialComposition(),
        request.endHaulDistance(),
        request.endHaulVolume(),
        request.overlandDistance(),
        request.overlandVolume(),
        request.comments(),
        request.revisionCount());
  }

  /**
   * Rejects a forest region the year-filtered list does not offer.
   *
   * <p>The list query already includes any code a stored row in this mill/year still references,
   * which is what lets a code survive its own expiry rather than permanently blocking a re-save.
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
    // "NA" is the code legacy forces for the methods that take no material; it is a real row, but
    // the coupling can substitute it after this check, so only a client-supplied value is
    // validated.
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
        pageId,
        year,
        millId,
        CATEGORY,
        blankToNull(request.constructionPeriod()),
        blankToNull(request.divisionName()),
        request.forestRegionCode(),
        location.tsbNumberCode(),
        location.tsaNumber(),
        location.tflNumberCode(),
        0);
  }

  /**
   * Maps the request onto the detail entity, normalising every scaled measurement to its column's
   * declared scale on the way in.
   *
   * <p>Normalising on write as well as on read matters: Oracle does not preserve trailing zeros, so
   * a value stored at the wrong scale would round-trip differently from what was entered.
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
        detailEngineeringCostIndOrDefault(request.detailedEngineeringCostInd()),
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
   * removes a cost row on save, and the read path treats a stored NULL exactly as it treats an
   * absent row — the field renders blank while the totals coerce it to zero.
   */
  private void writeCostLines(
      int roadDetailId, RoadDetailRequest request, String user, long millId, int year) {
    SubGradeRequest subGrade = request.subGrade() != null ? request.subGrade() : NO_SUB_GRADE;

    cost(roadDetailId, SUB_GRADE_ACTUAL, subGrade.actualCost(), user, millId, year);
    cost(roadDetailId, SUB_GRADE_TRANSFER, subGrade.ttTransfer(), user, millId, year);
    cost(roadDetailId, OTHER_TT_TRANSFER, subGrade.otherTransfer(), user, millId, year);
    cost(roadDetailId, LESS_BRIDGE, subGrade.lessBridges(), user, millId, year);
    cost(roadDetailId, LESS_CULVERT, subGrade.lessCulverts(), user, millId, year);
    cost(roadDetailId, LESS_LANDING, subGrade.lessLandings(), user, millId, year);
    cost(roadDetailId, LESS_OVERLAND, subGrade.lessOverland(), user, millId, year);
    cost(roadDetailId, LESS_OTHER_ENGINEERING, subGrade.lessOtherEng(), user, millId, year);
    cost(roadDetailId, LESS_END_HAUL, subGrade.lessEndHaul(), user, millId, year);

    StabilizingRequest stabilizing = request.stabilizing();
    cost(roadDetailId, STABILIZING_ACTUAL, stabilizing.actualCost(), user, millId, year);
    cost(roadDetailId, STABILIZING_TRANSFER, stabilizing.ttTransfer(), user, millId, year);
    cost(roadDetailId, STABILIZING_OTHER_TRANSFER, stabilizing.otherTransfer(), user, millId, year);
  }

  /** One cost-line upsert, mill/year-scoped. Named short so the twelve routings read as a table. */
  private void cost(
      int roadDetailId, int costItemId, Integer amount, String user, long millId, int year) {
    repository.upsertCostLine(roadDetailId, costItemId, amount, user, millId, year);
  }

  /**
   * Runs a write, translating a data-access failure into the resource-specific legacy save error.
   *
   * <p>Only the exception TYPE is logged. Never the values: a rejected write would otherwise put
   * cost, volume or comment content into the log, which the data-sensitivity rules forbid.
   *
   * <p>Every write names the resource that failed, which is what § Validation rules directs the
   * write path to do wherever that resource is known (code review 2026-08-18: the four keys were
   * declared but dead). A generic no-key overload existed alongside this one until all seven call
   * sites carried a key, at which point it was unreachable and was removed.
   *
   * @param write the write to run
   * @param messageKey one of the {@link Schedule10PersistenceException} constants
   */
  private void persist(Runnable write, String messageKey) {
    try {
      write.run();
    } catch (DataAccessException ex) {
      LOG.warn("Schedule 10 write failed [{}] for [{}]", ex.getClass().getSimpleName(), messageKey);
      throw new Schedule10PersistenceException(messageKey);
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
