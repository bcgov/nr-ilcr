package ca.bc.gov.nrs.ilcr.schedule7a;

import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeCodeLists;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.BridgeSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 7A (Bridge Costs) document and owns every write (add/correct/delete) plus
 * the per-bridge Check Status (Stories 12.1/12.2). The mill/year context is already validated by
 * {@code MillContextService} (AD-4) — an empty bridge list is a valid state, never re-checked here.
 * The track status is the Schedules 1–10 track code (BR-01/AD-9). All four bridge totals are
 * computed server-side (BR-06, AD-5/AD-6) from the legacy {@code BridgeReportType} arithmetic,
 * never read from storage nor accepted from a client. Costs/comments/location values are never
 * logged (AD-11).
 */
@Service
@Slf4j
public class Schedule7aService {

  private static final String STATUS_DRAFT = "D";

  // Legacy Constant.REPORT_COST_ITEMS.Schedule7_* ids (category '7').
  private static final int ITEM_SITE_PLAN = 70;
  private static final int ITEM_APPROACH = 71;
  private static final int ITEM_AFTER_INSTALL = 72;
  private static final int ITEM_OTHER = 73;
  private static final int ITEM_ABUT_MATERIAL = 74;
  private static final int ITEM_ABUT_DELIVER = 75;
  private static final int ITEM_ABUT_INSTALL = 76;
  private static final int ITEM_SS_MATERIAL = 79;
  private static final int ITEM_SS_DELIVER = 80;
  private static final int ITEM_SS_INSTALL = 81;

  private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_BRIDGE_MET = "bridgeRequirementsMetMsg";
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";

  private final Schedule7aRepository repository;
  private final MessageSource messageSource;

  public Schedule7aService(Schedule7aRepository repository, MessageSource messageSource) {
    this.repository = repository;
    this.messageSource = messageSource;
  }

  // ===============================================================================================
  // Read (Story 12.1)
  // ===============================================================================================

  /**
   * The Schedule 7A aggregate document for a mill/year (S01 serve half). Context is already
   * validated by {@code MillContextService} in the controller (AD-4).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (never inlined, AC5)
   * @return the document with server-computed totals and Draft-gated editability
   */
  @Transactional(readOnly = true)
  public Schedule7aResponse getSchedule7a(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit);
  }

  /**
   * Assemble the served document for a known track status (writes reuse it with their proven "D").
   */
  private Schedule7aResponse buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit) {
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<BridgeReportEntity> bridgeRows = repository.findBridges(millId, year);
    Map<Long, Map<Integer, Integer>> costs =
        costsByBridge(repository.findCostDetails(millId, year));

    List<Bridge> bridges = new ArrayList<>(bridgeRows.size());
    int rowCounter = 1;
    for (BridgeReportEntity row : bridgeRows) {
      bridges.add(toBridge(row, rowCounter++, costs.getOrDefault(row.bridgeReportId(), Map.of())));
    }
    return new Schedule7aResponse(
        millId, year, trackStatus, editable, bridges, codeLists(year), null);
  }

  /**
   * The five bridge code lists as offered for THIS reporting year. Legacy filtered every list to
   * the codes effective on January 1 of the year ({@code LookupCache.getCacheList(year)}), so a
   * retired code disappears from an older year's form rather than being offered indefinitely.
   */
  private BridgeCodeLists codeLists(int year) {
    return new BridgeCodeLists(
        repository.constructionTypeOptions(year),
        repository.superstructureTypeOptions(year),
        repository.deckTypeOptions(year),
        repository.abutmentTypeOptions(year),
        repository.loadRatingOptions(year));
  }

  // ===============================================================================================
  // Writes (Story 12.2). Each method is one transaction: a persistence failure rolls back and
  // surfaces as 500/ERR-004. Draft-gated on the 1–10 track (BR-01/AD-9).
  // ===============================================================================================

  /**
   * Add one bridge and return the recomputed document + the recalculated totals (S01/S02). Costs
   * are optional, but every one of the ten detail rows is written either way — an absent cost
   * stores a NULL row, never no row (see {@link #writeCosts}). Draft-gated; unknown code → 400;
   * malformed date → 400.
   */
  @Transactional
  public Schedule7aResponse addBridge(
      long millId, int year, BridgeRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    LocalDate builtDate = parseBuiltDate(request.builtDate());
    validateCodes(codeSets(year), request);
    try {
      long bridgeId = repository.nextBridgeReportId();
      repository.insertBridge(toEntity(bridgeId, request, builtDate), millId, year, user);
      writeCosts(bridgeId, request, user);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 7A add failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Correct one existing bridge and return the recomputed document (S03). Optimistic-lock on the
   * row's {@code REVISION_COUNT}: a stale token → 409, an unknown id → 404. Cost edits upsert their
   * row; clearing a cost stores NULL in place rather than removing the row (see {@link
   * #writeCosts}).
   */
  @Transactional
  public Schedule7aResponse updateBridge(
      long millId,
      int year,
      long bridgeId,
      BridgeRequest request,
      boolean callerMayEdit,
      String user) {
    requireDraft(millId, year);
    applyBridgeUpdate(millId, year, bridgeId, request, user, codeSets(year));
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Save EVERY bridge of the schedule in one transaction — the page-level Save (legacy {@code
   * Schedule7aMB.save()} → {@code saveSchedule7a}, which persisted the whole schedule from a single
   * button). Each entry goes through the same per-row path as {@link #updateBridge}, so the
   * validation, optimistic lock and cost upsert/clear rules are identical.
   *
   * <p>Atomic by construction: one entry failing its Draft gate, revision check, code check or date
   * parse rolls the WHOLE batch back. That is the legacy guarantee — a partial save would leave the
   * reporter unable to tell which rows persisted.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the bridges to save, each with its id and {@code revisionCount}
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the audit user
   * @return the recomputed document with refreshed totals
   */
  @Transactional
  public Schedule7aResponse saveAllBridges(
      long millId, int year, BridgeSaveAllRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    rejectDuplicateIds(request);
    // Read the five code tables ONCE for the batch rather than once per bridge: the lists are
    // year-scoped, not row-scoped, so N bridges would otherwise issue 5N identical queries inside
    // this transaction.
    CodeSets codes = codeSets(year);
    for (BridgeSaveAllRequest.Item item : request.bridges()) {
      applyBridgeUpdate(millId, year, item.bridgeReportId(), item.bridge(), user, codes);
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Reject a batch naming the same bridge twice. Left to run, the second pass would meet the
   * revision its own first pass had just bumped and surface as a 409 stale-edit — telling the
   * caller someone else changed the row when the request was simply malformed.
   */
  private static void rejectDuplicateIds(BridgeSaveAllRequest request) {
    Set<Long> seen = new java.util.HashSet<>();
    for (BridgeSaveAllRequest.Item item : request.bridges()) {
      if (!seen.add(item.bridgeReportId())) {
        throw new DuplicateBridgeException();
      }
    }
  }

  /**
   * Correct one bridge row and its costs. Shared by the per-row PUT and the save-all so a bridge is
   * persisted by exactly one code path. Assumes the Draft gate has already run for the request.
   */
  private void applyBridgeUpdate(
      long millId, int year, long bridgeId, BridgeRequest request, String user, CodeSets codes) {
    LocalDate builtDate = parseBuiltDate(request.builtDate());
    validateCodes(codes, request);
    try {
      int updated =
          repository.updateBridge(
              toEntity(bridgeId, request, builtDate), millId, year, request.revisionCount(), user);
      if (updated == 0) {
        // 0 rows = the id is absent (404) OR the revision is stale (409) — disambiguate.
        if (repository.countBridge(bridgeId, millId, year) == 0) {
          throw new BridgeNotFoundException();
        }
        throw new StaleRevisionException();
      }
      writeCosts(bridgeId, request, user);
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 7A update failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * Delete one bridge and ALL its cost children (S04/S05 — legacy whole-row removal). Draft-gated;
   * an unknown id → 404.
   *
   * <p>CHILDREN FIRST, then the parent — the order legacy used (Schedule7aDAO:566-570). Delivery
   * carries an FK from {@code ILCR_COST_REPORT_DETAIL.BRIDGE_REPORT_ID} without {@code ON DELETE
   * CASCADE}, so deleting the bridge while its ten cost rows still reference it raises ORA-02292
   * ("child record found") and the whole delete fails. The ownership/404 check therefore cannot be
   * the parent delete's row count; it is a scoped {@code countBridge} taken BEFORE either delete,
   * so another mill's id still removes nothing.
   */
  @Transactional
  public Schedule7aResponse deleteBridge(
      long millId, int year, long bridgeId, boolean callerMayEdit) {
    requireDraft(millId, year);
    try {
      if (repository.countBridge(bridgeId, millId, year) == 0) {
        throw new BridgeNotFoundException();
      }
      repository.deleteCostsForBridge(bridgeId);
      if (repository.deleteBridge(bridgeId, millId, year) == 0) {
        // The probe above passed, so a zero here means a concurrent delete won the race. Acting on
        // the count rather than assuming success is Schedule 5's 8.2 lesson: a delete whose result
        // is
        // discarded reported "Data deleted successfully" while the row survived — and here it would
        // have committed the cost deletes, so the row would re-render stripped of its costs.
        throw new BridgeNotFoundException();
      }
    } catch (DataAccessException ex) {
      log.warn(
          "Schedule 7A delete failed for mill {} year {} [{}]",
          millId,
          year,
          ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * The entered bridge columns as one {@link BridgeReportEntity} for the repository write (the
   * column-shaped param object the insert/update bind by accessor). {@code revisionCount} is unused
   * by the writes (insert forces 0; update locks on the separate {@code expectedRevision}), so it
   * is carried as 0 here.
   */
  private static BridgeReportEntity toEntity(long bridgeId, BridgeRequest r, LocalDate builtDate) {
    return new BridgeReportEntity(
        bridgeId,
        r.locationName(),
        builtDate,
        r.lifeSpan(),
        r.abutmentHeight(),
        r.length(),
        r.width(),
        r.distance(),
        r.constructionTypeCode(),
        r.superstructureTypeCode(),
        r.deckTypeCode(),
        r.abutmentTypeCode(),
        r.loadRatingCode(),
        r.comments(),
        0);
  }

  /**
   * Write ALL TEN cost children of a bridge, a cleared cost included as a row with {@code COST}
   * NULL — never as a missing row.
   *
   * <p>This is storage-shape parity that MATTERS, because the legacy app runs against the same
   * delivery database. Legacy's add branch inserts all ten detail rows even when a cost is null
   * ({@code Schedule7aDAO.saveItemCostDetail}), and its update branch then iterates only the rows
   * that already exist — it has no insert path. So a bridge this service wrote with a row MISSING
   * would have that cost permanently uneditable from the legacy screen: a reporter could type a
   * value there and legacy would silently discard it, having no row to update.
   *
   * <p>Reads are unaffected either way — an absent key and a key mapped to null both resolve to "no
   * cost" in {@code toBridge} and in Check Status.
   */
  private void writeCosts(long bridgeId, BridgeRequest r, String user) {
    writeCost(bridgeId, ITEM_SITE_PLAN, r.sitePlanCost(), user);
    writeCost(bridgeId, ITEM_APPROACH, r.approachCost(), user);
    writeCost(bridgeId, ITEM_AFTER_INSTALL, r.afterInstallCost(), user);
    writeCost(bridgeId, ITEM_OTHER, r.otherCost(), user);
    writeCost(bridgeId, ITEM_ABUT_MATERIAL, r.abutmentMaterialCost(), user);
    writeCost(bridgeId, ITEM_ABUT_DELIVER, r.abutmentDeliverCost(), user);
    writeCost(bridgeId, ITEM_ABUT_INSTALL, r.abutmentInstallCost(), user);
    writeCost(bridgeId, ITEM_SS_MATERIAL, r.superstructureMaterialCost(), user);
    writeCost(bridgeId, ITEM_SS_DELIVER, r.superstructureDeliverCost(), user);
    writeCost(bridgeId, ITEM_SS_INSTALL, r.superstructureInstallCost(), user);
  }

  /** Upsert one cost row; a null {@code cost} stores NULL in place rather than removing the row. */
  private void writeCost(long bridgeId, int costItemId, Integer cost, String user) {
    repository.upsertCost(bridgeId, costItemId, cost, user);
  }

  /** The Draft gate for every write: the 1–10 track must be {@code D} (else 409, BR-01/AD-9). */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /** The five code-value sets a write is validated against, read once for a reporting year. */
  private record CodeSets(
      Set<String> construction,
      Set<String> superstructure,
      Set<String> deck,
      Set<String> abutment,
      Set<String> loadRating) {}

  private CodeSets codeSets(int year) {
    return new CodeSets(
        codeSet(repository.constructionTypeOptions(year)),
        codeSet(repository.superstructureTypeOptions(year)),
        codeSet(repository.deckTypeOptions(year)),
        codeSet(repository.abutmentTypeOptions(year)),
        codeSet(repository.loadRatingOptions(year)));
  }

  /**
   * Reject a code value that resolves to no {@code *_CODE} row EFFECTIVE for the reporting year
   * (force-selection enforcement, S15). Year-scoped for the same reason the served lists are:
   * legacy only ever offered the year's effective codes, so accepting one outside that window here
   * would let a write store a value the form could never have produced.
   */
  private static void validateCodes(CodeSets codes, BridgeRequest r) {
    if (!codes.construction().contains(r.constructionTypeCode())
        || !codes.superstructure().contains(r.superstructureTypeCode())
        || !codes.deck().contains(r.deckTypeCode())
        || !codes.abutment().contains(r.abutmentTypeCode())
        || !codes.loadRating().contains(r.loadRatingCode())) {
      throw new InvalidBridgeCodeException();
    }
  }

  private static Set<String> codeSet(List<ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto> options) {
    return options.stream()
        .map(ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto::code)
        .collect(java.util.stream.Collectors.toSet());
  }

  /**
   * Parse a {@code yyyy-MM} completion date, non-lenient (legacy {@code SimpleDateFormat} with
   * {@code setLenient(false)}). Stored as the first day of the month. A malformed value → 400
   * {@code bridgeDateformatErrorMsg} (S07).
   */
  private static LocalDate parseBuiltDate(String value) {
    try {
      return YearMonth.parse(value, YEAR_MONTH).atDay(1);
    } catch (DateTimeParseException ex) {
      throw new BridgeDateFormatException();
    }
  }

  // ===============================================================================================
  // Check Status (Story 12.2, BR-08, S29) — read-only, mutates nothing, VIEW-gated (not Draft).
  // ===============================================================================================

  /**
   * Walk every stored bridge in the exact legacy field order ({@code Schedule7aMB.java:206-289}),
   * flagging each missing required value with {@code missingRequiredFieldMsg} = "Value Required".
   * When at least one bridge fails, each bridge that passes gets an SUC-005 all-met line; when
   * EVERY bridge passes the response carries the SUC-004 schedule-wide message alone and no
   * per-bridge lines (legacy's per-bridge loop ran only in the schedule-failed branch).
   *
   * <p>Recorded deviation: the legacy abutment-height check ({@code Schedule7aCheckStatus.java:23}
   * {@code getBridgeAbutHtM().equals(null)}) never fires and NPEs on null; here it is implemented
   * correctly as {@code abutmentHeight == null}.
   */
  @Transactional(readOnly = true)
  public Schedule7aCheckStatusResponse checkStatus(long millId, int year) {
    List<BridgeReportEntity> bridgeRows = repository.findBridges(millId, year);
    Map<Long, Map<Integer, Integer>> costs =
        costsByBridge(repository.findCostDetails(millId, year));

    List<MessageInfo> errors = new ArrayList<>();
    List<Integer> passingRows = new ArrayList<>();
    boolean allMet = true;

    int rowCounter = 1;
    for (BridgeReportEntity row : bridgeRows) {
      Map<Integer, Integer> cost = costs.getOrDefault(row.bridgeReportId(), Map.of());
      List<String> missing = missingLabels(row, cost);
      if (missing.isEmpty()) {
        passingRows.add(rowCounter);
      } else {
        allMet = false;
        for (String label : missing) {
          errors.add(new MessageInfo(MSG_VALUE_REQUIRED, missingText(rowCounter, label)));
        }
      }
      rowCounter++;
    }

    // The per-bridge all-met lines are emitted ONLY when the schedule as a whole failed. Legacy put
    // the whole per-bridge loop in the else branch of its schedule-passed test
    // (Schedule7aMB.java:197-296), so a fully complete schedule showed the ONE schedule-wide line
    // and
    // nothing per bridge — emitting both would stack N+1 success banners where legacy showed one.
    List<MessageInfo> bridgeMessages =
        allMet
            ? List.of()
            : passingRows.stream()
                .map(counter -> new MessageInfo(MSG_BRIDGE_MET, bridgeMetText(counter)))
                .toList();

    MessageInfo requirementsMetMessage =
        allMet ? new MessageInfo(MSG_REQUIREMENTS_MET, resolveText(MSG_REQUIREMENTS_MET)) : null;
    return new Schedule7aCheckStatusResponse(
        allMet, errors, bridgeMessages, requirementsMetMessage);
  }

  /** One required-value check: the "is this value missing?" test paired with its verbatim label. */
  private record RequiredCheck(
      BiPredicate<BridgeReportEntity, Map<Integer, Integer>> missing, String label) {}

  /** A required-attribute check on the bridge row itself. */
  private static RequiredCheck attrCheck(Predicate<BridgeReportEntity> missing, String label) {
    return new RequiredCheck((r, c) -> missing.test(r), label);
  }

  /** A required-cost check: the cost item has no stored detail row for the bridge. */
  private static RequiredCheck costCheck(int itemId, String label) {
    return new RequiredCheck((r, c) -> c.get(itemId) == null, label);
  }

  /**
   * The 17 Check-Status required values in the exact legacy order with the verbatim legacy labels
   * ({@code Schedule7aMB.java:206-289}), as an ordered table so {@link #missingLabels} stays a flat
   * walk rather than 17 branches. The abutment-height entry is a recorded deviation: legacy {@code
   * getBridgeAbutHtM().equals(null)} never flags and NPEs; here it is a correct null check.
   */
  private static final List<RequiredCheck> REQUIRED_CHECKS =
      List.of(
          attrCheck(
              r -> r.locationName() == null || r.locationName().isBlank(),
              " - Name / Location of Bridge "),
          attrCheck(r -> r.builtDate() == null, " - Built Date "),
          attrCheck(r -> r.lifeSpan() == null, " - Expected Life Span "),
          attrCheck(r -> r.abutmentHeight() == null, " - Abutments heigth value "),
          attrCheck(r -> r.length() == null, " - Length (m) "),
          attrCheck(r -> r.deckWidth() == null, " - Width (m) "),
          attrCheck(r -> r.distance() == null, " - Distance (km) "),
          costCheck(ITEM_SS_MATERIAL, " - Superstructure - Materil Cost "),
          costCheck(ITEM_SS_DELIVER, " - Superstructure - Deliver Cost "),
          costCheck(ITEM_SS_INSTALL, " - Superstructure - Install Cost "),
          costCheck(ITEM_ABUT_MATERIAL, " - Abutments Material Cost "),
          costCheck(ITEM_ABUT_DELIVER, " - Abutments Deliver Cost "),
          costCheck(ITEM_ABUT_INSTALL, " - Abutments Install Cost "),
          costCheck(ITEM_SITE_PLAN, " - Site Plan / Gen. Arr.  Cost "),
          costCheck(ITEM_APPROACH, " - Approach works Cost "),
          costCheck(ITEM_AFTER_INSTALL, " - Certification After install Cost "),
          costCheck(ITEM_OTHER, " - Other Costs "));

  /**
   * The missing required-field labels for one bridge, in the exact legacy order (verbatim text).
   */
  private static List<String> missingLabels(BridgeReportEntity row, Map<Integer, Integer> cost) {
    List<String> missing = new ArrayList<>();
    for (RequiredCheck check : REQUIRED_CHECKS) {
      if (check.missing().test(row, cost)) {
        missing.add(check.label());
      }
    }
    return missing;
  }

  /**
   * {@code "Bridge Report Id : {rowCounter}{label}: Value Required"} — the legacy composition.
   *
   * <p>The {@code ": "} is NOT part of the label: legacy built the label as {@code "Bridge Report
   * Id : " + rowCounter + fieldMissing} ({@code Schedule7aMB.java:191}) and handed it to {@code
   * FacesUtil.addCheckStatusErrorMessage}, which appends {@code ": "} before the bundle text for
   * EVERY schedule ({@code util/FacesUtil.java:134}). The labels already end in a space, so the
   * rendered line reads {@code "... - Length (m) : Value Required"} — the same shape Schedule 7B
   * composes ({@code Schedule7bService.missingText}).
   */
  private String missingText(int rowCounter, String label) {
    return "Bridge Report Id : " + rowCounter + label + ": " + resolveText(MSG_VALUE_REQUIRED);
  }

  private String bridgeMetText(int rowCounter) {
    return messageSource.getMessage(
        MSG_BRIDGE_MET, new Object[] {rowCounter}, MSG_BRIDGE_MET, LocaleContextHolder.getLocale());
  }

  private String resolveText(String key) {
    return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
  }

  // ===============================================================================================
  // Assembly + derivation helpers
  // ===============================================================================================

  /** Group the flat cost rows by bridge id into an item-id → cost map (last row wins on a dup). */
  private static Map<Long, Map<Integer, Integer>> costsByBridge(List<BridgeCostEntity> rows) {
    Map<Long, Map<Integer, Integer>> byBridge = new HashMap<>();
    for (BridgeCostEntity row : rows) {
      byBridge
          .computeIfAbsent(row.bridgeReportId(), k -> new HashMap<>())
          .put(row.costItemId(), row.cost());
    }
    return byBridge;
  }

  /** Map one bridge row + its cost map to the wire shape, computing the four totals (BR-06). */
  private Bridge toBridge(BridgeReportEntity row, int rowCounter, Map<Integer, Integer> cost) {
    Integer sitePlan = cost.get(ITEM_SITE_PLAN);
    Integer ssMaterial = cost.get(ITEM_SS_MATERIAL);
    Integer ssDeliver = cost.get(ITEM_SS_DELIVER);
    Integer ssInstall = cost.get(ITEM_SS_INSTALL);
    Integer abutMaterial = cost.get(ITEM_ABUT_MATERIAL);
    Integer abutDeliver = cost.get(ITEM_ABUT_DELIVER);
    Integer abutInstall = cost.get(ITEM_ABUT_INSTALL);
    Integer approach = cost.get(ITEM_APPROACH);
    Integer afterInstall = cost.get(ITEM_AFTER_INSTALL);
    Integer other = cost.get(ITEM_OTHER);

    Integer totalMaterial = add(ssMaterial, abutMaterial);
    Integer totalDeliver = add(ssDeliver, abutDeliver);
    Integer totalInstall = add(ssInstall, abutInstall);
    Integer grandTotal =
        sum(sitePlan, totalMaterial, totalDeliver, totalInstall, approach, afterInstall, other);

    return new Bridge(
        row.bridgeReportId(),
        rowCounter,
        row.locationName(),
        formatBuiltDate(row.builtDate()),
        row.constructionTypeCode(),
        row.superstructureTypeCode(),
        row.deckTypeCode(),
        row.abutmentTypeCode(),
        row.loadRatingCode(),
        row.lifeSpan(),
        oneDecimal(row.abutmentHeight()),
        oneDecimal(row.length()),
        oneDecimal(row.deckWidth()),
        row.distance(),
        sitePlan,
        ssMaterial,
        ssDeliver,
        ssInstall,
        abutMaterial,
        abutDeliver,
        abutInstall,
        approach,
        afterInstall,
        other,
        row.comments(),
        totalMaterial,
        totalDeliver,
        totalInstall,
        grandTotal,
        row.revisionCount());
  }

  private static String formatBuiltDate(LocalDate date) {
    return date == null ? null : YEAR_MONTH.format(date);
  }

  /**
   * Normalize a measurement to scale 1 (the legacy {@code NUMBER(5,1)} shape), so a whole value
   * serializes as {@code 5.0} rather than {@code 5} — Oracle collapses {@code 5.0} to scale 0 on
   * read, which would otherwise emit an inconsistent integer for some rows.
   */
  private static BigDecimal oneDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(1, java.math.RoundingMode.HALF_UP);
  }

  /**
   * Legacy {@code CoreUtil.bigDecimalCostAddition}: null-tolerant addition of two whole-dollar
   * costs. null+null=null, null+x=x. Each operand is bounded by the ±99,999,999 validation, so
   * their sum stays in {@code int} range.
   */
  private static Integer add(Integer a, Integer b) {
    if (a == null && b == null) {
      return null;
    }
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }
    return a + b;
  }

  /**
   * Legacy {@code CoreUtil.sumBigDecimalCosts}: null-tolerant sum of the grand-total operands.
   * Nulls are skipped; a sum with no non-null operand is null (never 0). Accumulates in {@code
   * long} — the grand total of seven ±99,999,999 terms can exceed {@code Integer.MAX_VALUE} — then
   * narrows to {@code Integer} (the DB {@code COST} range on any single item makes the realistic
   * total fit, and this matches the legacy {@code BigDecimal} width for the derived figure).
   */
  private static Integer sum(Integer... values) {
    boolean any = false;
    long total = 0;
    for (Integer v : values) {
      if (v != null) {
        any = true;
        total += v;
      }
    }
    return any ? Math.toIntExact(total) : null;
  }
}
