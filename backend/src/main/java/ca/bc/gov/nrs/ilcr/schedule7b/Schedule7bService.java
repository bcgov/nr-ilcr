package ca.bc.gov.nrs.ilcr.schedule7b;

import static ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bRepository.ITEM_INSTALL;
import static ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bRepository.ITEM_MATERIAL;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.schedule1.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.schedule1.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertCodeLists;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.CulvertSaveAllRequest;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bCheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the Schedule 7B (Culvert Costs) document and owns every write (record/correct/delete)
 * plus the type-conditional Check Status (Stories 13.1/13.2). The mill/year context is already
 * validated by {@code MillContextService} (AD-4) — an empty culvert list is a valid state, never
 * re-checked here. The track status is the Schedules 1–10 track code (BR-01/AD-9). Each culvert's
 * total cost is computed server-side (BR-05, AD-5/AD-6) from the legacy {@code CulvertReportType}
 * arithmetic, never read from storage nor accepted from a client. Costs and comments are never
 * logged (AD-11).
 *
 * <p>Schedule 7B is the twin of Schedule 7A over the same category-{@code '7'} storage, so this
 * service mirrors {@code Schedule7aService}'s structure. The three behaviours that genuinely
 * differ, and that a reader coming from 7A will otherwise get wrong:
 *
 * <ol>
 *   <li><strong>Only Type and Piece Count are required at Save.</strong> Span, rise, length, both
 *       costs and comments are optional — legacy set {@code required="true"} on those two fields
 *       alone ({@code schedule7B.xhtml:87,153}). A partially-measured culvert must be savable.
 *   <li><strong>Check Status is type-conditional.</strong> Span is required only for {@code R}
 *       (Round), comments only for {@code O} (Others), and <strong>rise is never checked for any
 *       type</strong> ({@code service/Schedule7bCheckStatus.java:10-23}).
 *   <li><strong>There is no per-culvert all-met message.</strong> 7A emits one per passing bridge;
 *       7B emits only the schedule-wide line ({@code Schedule7bMB.java:162-164}).
 * </ol>
 */
@Service
@Slf4j
public class Schedule7bService {

  private static final String STATUS_DRAFT = "D";

  /** Legacy {@code Constant.CULVERT_TYPE_CODES.R} — the only type that requires a span (BR-07). */
  private static final String TYPE_ROUND = "R";

  /**
   * Legacy {@code Constant.CULVERT_TYPE_CODES.O} — the only type that requires comments (BR-07).
   */
  private static final String TYPE_OTHERS = "O";

  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";
  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";

  private final Schedule7bRepository repository;
  private final MessageSource messageSource;

  public Schedule7bService(Schedule7bRepository repository, MessageSource messageSource) {
    this.repository = repository;
    this.messageSource = messageSource;
  }

  // ===============================================================================================
  // Read (Story 13.1)
  // ===============================================================================================

  /**
   * The Schedule 7B aggregate document for a mill/year (S01 serve half). Context is already
   * validated by {@code MillContextService} in the controller (AD-4).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (never inlined)
   * @return the document with server-computed totals and Draft-gated editability
   */
  @Transactional(readOnly = true)
  public Schedule7bResponse getSchedule7b(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit);
  }

  /**
   * Assemble the served document for a known track status (writes reuse it with their proven "D").
   */
  private Schedule7bResponse buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit) {
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    List<CulvertReportEntity> rows = repository.findCulverts(millId, year);
    Map<Long, Map<Integer, Integer>> costs =
        costsByCulvert(repository.findCostDetails(millId, year));

    List<Culvert> culverts = new ArrayList<>(rows.size());
    int rowCounter = 1;
    for (CulvertReportEntity row : rows) {
      culverts.add(
          toCulvert(row, rowCounter++, costs.getOrDefault(row.culvertReportId(), Map.of())));
    }
    return new Schedule7bResponse(
        millId, year, trackStatus, editable, culverts,
        new CulvertCodeLists(repository.culvertTypeOptions(year)), null);
  }

  // ===============================================================================================
  // Writes (Story 13.2). Each method is one transaction: a persistence failure rolls back and
  // surfaces as 500/ERR-001. Draft-gated on the 1–10 track (BR-01/AD-9).
  // ===============================================================================================

  /**
   * Record one culvert and return the recomputed document + the recalculated total (S01/S02). Both
   * costs are optional, but both detail rows are written either way — an absent cost stores a NULL
   * row, never no row (see {@link #writeCosts}). Draft-gated; a type outside the year's effective
   * codes → 400.
   */
  @Transactional
  public Schedule7bResponse addCulvert(
      long millId, int year, CulvertRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    // No stored type on a create, so the year-effective check always applies.
    validateCulvertType(culvertTypeCodes(year), request, null);
    try {
      long culvertId = repository.nextCulvertReportId();
      repository.insertCulvert(toEntity(culvertId, request), millId, year, user);
      writeCosts(culvertId, request, user);
    } catch (DataAccessException ex) {
      log.warn("Schedule 7B add failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Correct one existing culvert and return the recomputed document (S03). Optimistic-lock on the
   * row's {@code REVISION_COUNT}: a stale token → 409, an unknown id → 404. Clearing a cost stores
   * NULL in place rather than removing the row (see {@link #writeCosts}).
   */
  @Transactional
  public Schedule7bResponse updateCulvert(
      long millId, int year, long culvertId, CulvertRequest request, boolean callerMayEdit,
      String user) {
    requireDraft(millId, year);
    applyCulvertUpdate(
        millId, year, culvertId, request, user, culvertTypeCodes(year), storedTypes(millId, year));
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Save EVERY culvert of the schedule in one transaction — the page-level Save (legacy {@code
   * Schedule7bMB.save()} → {@code saveSchedule}, which persisted the whole schedule from a single
   * button). Each entry goes through the same per-row path as {@link #updateCulvert}, so the
   * validation, optimistic lock and cost upsert rules are identical.
   *
   * <p>Atomic by construction: one entry failing its Draft gate, revision check or type check rolls
   * the WHOLE batch back. That is the legacy guarantee — a partial save would leave the reporter
   * unable to tell which rows persisted.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param request the culverts to save, each with its id and {@code revisionCount}
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the audit user
   * @return the recomputed document with refreshed totals
   */
  @Transactional
  public Schedule7bResponse saveAllCulverts(
      long millId, int year, CulvertSaveAllRequest request, boolean callerMayEdit, String user) {
    requireDraft(millId, year);
    rejectDuplicateIds(request);
    // Read the code table ONCE for the batch rather than once per culvert: the list is year-scoped,
    // not row-scoped, so N culverts would otherwise issue N identical queries inside this
    // transaction.
    Set<String> codes = culvertTypeCodes(year);
    Map<Long, String> storedTypes = storedTypes(millId, year);
    for (CulvertSaveAllRequest.Item item : request.culverts()) {
      applyCulvertUpdate(
          millId, year, item.culvertReportId(), item.culvert(), user, codes, storedTypes);
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * Reject a batch naming the same culvert twice. Left to run, the second pass would meet the
   * revision its own first pass had just bumped and surface as a 409 stale-edit — telling the
   * caller someone else changed the row when the request was simply malformed.
   */
  private static void rejectDuplicateIds(CulvertSaveAllRequest request) {
    Set<Long> seen = new HashSet<>();
    for (CulvertSaveAllRequest.Item item : request.culverts()) {
      if (!seen.add(item.culvertReportId())) {
        throw new DuplicateCulvertException();
      }
    }
  }

  /**
   * Correct one culvert and its costs. Shared by the per-row PUT and the save-all so a culvert is
   * persisted by exactly one code path. Assumes the Draft gate has already run for the request.
   *
   * <p>{@code storedTypes} carries the currently-stored type of every culvert in the mill/year so
   * an UNCHANGED type is exempt from the year-effective check — see {@link #validateCulvertType}.
   *
   * <p><strong>Existence is resolved BEFORE any request-specific validation</strong> (PR #266
   * review). {@code storedTypes} holds an entry for every culvert of the mill/year, so its key set
   * is an existence oracle that costs no extra query — and {@code get} alone could not serve as
   * one, because a culvert with a NULL stored type and an absent culvert both answer null.
   * Validating the
   * submitted type first made the status depend on the BODY: an unknown id carrying a retired type
   * answered 400 (invalid type) where the endpoint contract and the write tests both say 404. The
   * same missing resource must report the same status whatever the body says, on the single PUT and
   * on every entry of a page-level Save alike.
   *
   * <p>The {@code updated == 0} disambiguation further down stays as the concurrency backstop: this
   * check reads committed state, so a culvert deleted by another transaction between that read and
   * the UPDATE is still caught there.
   */
  private void applyCulvertUpdate(
      long millId, int year, long culvertId, CulvertRequest request, String user,
      Set<String> codes, Map<Long, String> storedTypes) {
    if (!storedTypes.containsKey(culvertId)) {
      throw new CulvertNotFoundException();
    }
    validateCulvertType(codes, request, storedTypes.get(culvertId));
    try {
      int updated = repository.updateCulvert(
          toEntity(culvertId, request), millId, year, request.revisionCount(), user);
      if (updated == 0) {
        // 0 rows = the id is absent (404) OR the revision is stale (409) — disambiguate.
        if (repository.countCulvert(culvertId, millId, year) == 0) {
          throw new CulvertNotFoundException();
        }
        throw new StaleRevisionException();
      }
      writeCosts(culvertId, request, user);
    } catch (DataAccessException ex) {
      log.warn("Schedule 7B update failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
  }

  /**
   * Delete one culvert and BOTH its cost children (S04 — legacy whole-row removal via Hibernate
   * {@code CascadeType.ALL}, {@code model/CulvertReport.java:231}). Draft-gated; an unknown id →
   * 404.
   *
   * <p>CHILDREN FIRST, then the parent — the order Hibernate's cascade gave legacy. Delivery carries
   * an FK from {@code ILCR_COST_REPORT_DETAIL.CULVERT_REPORT_ID} without {@code ON DELETE CASCADE},
   * so deleting the culvert while its cost rows still reference it raises ORA-02292 ("child record
   * found") and the whole delete fails. The ownership/404 check therefore cannot be the parent
   * delete's row count; it is a scoped {@code countCulvert} taken BEFORE either delete, so another
   * mill's id still removes nothing.
   *
   * <p>The Yes/No confirmation (ALT-001) is an in-page dialog with no backend contract — a
   * cancelled delete (S05) simply never reaches here.
   */
  @Transactional
  public Schedule7bResponse deleteCulvert(
      long millId, int year, long culvertId, boolean callerMayEdit) {
    requireDraft(millId, year);
    try {
      if (repository.countCulvert(culvertId, millId, year) == 0) {
        throw new CulvertNotFoundException();
      }
      repository.deleteCostsForCulvert(culvertId);
      if (repository.deleteCulvert(culvertId, millId, year) == 0) {
        // The probe above passed, so a zero here means a concurrent delete won the race. Acting on
        // the count rather than assuming success is Schedule 5's 8.2 lesson: a delete whose result is
        // discarded reported "Data deleted successfully" while the row survived — and here it would
        // have committed the cost deletes, so the row would re-render stripped of its costs.
        throw new CulvertNotFoundException();
      }
    } catch (DataAccessException ex) {
      log.warn("Schedule 7B delete failed for mill {} year {} [{}]",
          millId, year, ex.getClass().getSimpleName());
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit);
  }

  /**
   * The entered culvert columns as one {@link CulvertReportEntity} for the repository write (the
   * column-shaped param object the insert/update bind by accessor). {@code revisionCount} is unused
   * by the writes (insert forces 0; update locks on the separate {@code expectedRevision}), so it
   * is carried as 0 here.
   *
   * <p>The length is ROUNDED to scale 1 rather than rejected for carrying more decimals. Legacy
   * validated range only ({@code f:validateDoubleRange}, {@code schedule7B.xhtml:378-379}) and let
   * the {@code NUMBER(7,1)} column round whatever the converter parsed, so {@code 12.55} was
   * accepted and stored as {@code 12.6}. Doing the rounding here keeps that behaviour AND removes
   * the trap that a Bean-Validation {@code @Digits(fraction = 1)} created: it reads {@code
   * BigDecimal.scale()}, so {@code 12.50} — the same number as the accepted {@code 12.5} — failed
   * with a range message.
   */
  private static CulvertReportEntity toEntity(long culvertId, CulvertRequest r) {
    return new CulvertReportEntity(
        culvertId, r.culvertTypeCode(), r.spanSize(), r.riseSize(), oneDecimal(r.length()),
        r.culvertPieceCount(), r.comments(), 0);
  }

  /**
   * Write BOTH cost children of a culvert (material 77, install 78), a cleared cost included as a
   * row with {@code COST} NULL — never as a missing row.
   *
   * <p>This is storage-shape parity that MATTERS, because the legacy app runs against the same
   * delivery database. Legacy's add branch inserts both detail rows even when a cost is null
   * ({@code Schedule7bDAO.java:222-232} → {@code saveItemCostDetail}), and its update branch then
   * iterates only the rows that already exist ({@code :234-248}) — it has no insert path. So a
   * culvert this service wrote with a row MISSING would have that cost permanently uneditable from
   * the legacy screen: a reporter could type a value there and legacy would silently discard it,
   * having no row to update.
   *
   * <p>Reads are unaffected either way — an absent key and a key mapped to null both resolve to "no
   * cost" in {@link #toCulvert} and in Check Status.
   */
  private void writeCosts(long culvertId, CulvertRequest r, String user) {
    repository.upsertCost(culvertId, ITEM_MATERIAL, r.materialCost(), user);
    repository.upsertCost(culvertId, ITEM_INSTALL, r.installCost(), user);
  }

  /** The Draft gate for every write: the 1–10 track must be {@code D} (else 409, BR-01/AD-9). */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /**
   * The currently-stored type of every culvert in the mill/year, keyed by id — read ONCE per
   * request (not once per row, so a page-level Save of N culverts costs one query, not N). Feeds
   * {@link #validateCulvertType}'s unchanged-type exemption.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return culvert id → stored type code; empty when the schedule has no culverts
   */
  private Map<Long, String> storedTypes(long millId, int year) {
    Map<Long, String> byId = new HashMap<>();
    for (CulvertReportEntity row : repository.findCulverts(millId, year)) {
      byId.put(row.culvertReportId(), row.culvertTypeCode());
    }
    return byId;
  }

  /** The culvert type code values effective for a reporting year, read once per request/batch. */
  private Set<String> culvertTypeCodes(int year) {
    return repository.culvertTypeOptions(year).stream()
        .map(CodeDescriptionDto::code)
        .collect(Collectors.toSet());
  }

  /**
   * Reject a culvert type that resolves to no {@code ILCR_CULVERT_TYPE_CODE} row EFFECTIVE for the
   * reporting year (force-selection enforcement). Year-scoped for the same reason the served list
   * is: legacy only ever offered the year's effective codes, so accepting one outside that window
   * here would let a write store a value the form could never have produced.
   *
   * <p><strong>An UNCHANGED type is exempt.</strong> {@code storedType} is what the row currently
   * holds ({@code null} on a create). Without this exemption a single culvert stored with a code
   * that has since expired would 400 the whole page-level Save — which resubmits every row with its
   * existing type — so no culvert on that page could ever be corrected again. Legacy did not block
   * that save: it resolved the code through the year-scoped {@code LookUpCaches} cache and, on a
   * miss, stored {@code null} — silently WIPING the type. Neither legacy behaviour is worth
   * reproducing; exempting a value the reporter did not touch preserves legacy's "the page still
   * saves" without its data loss, and a genuine type CHANGE is still held to the year's list.
   *
   * @param codes the type codes effective for the reporting year
   * @param r the incoming request
   * @param storedType the type currently stored on this row, or {@code null} on a create
   */
  private static void validateCulvertType(
      Set<String> codes, CulvertRequest r, String storedType) {
    if (r.culvertTypeCode().equals(storedType)) {
      return;
    }
    if (!codes.contains(r.culvertTypeCode())) {
      throw new InvalidCulvertTypeException();
    }
  }

  // ===============================================================================================
  // Check Status (Story 13.2, BR-07) — read-only, mutates nothing, VIEW-gated (not Draft-gated).
  // ===============================================================================================

  /**
   * Walk every stored culvert in the exact legacy field order ({@code Schedule7bMB.java:130-158}),
   * flagging each missing required value with {@code missingRequiredFieldMsg} = "Value Required".
   * When every culvert passes, the response also carries the SUC-003 schedule-wide all-met message.
   *
   * <p>Unlike Schedule 7A there is NO per-culvert all-met message — legacy emits only the
   * schedule-wide line ({@code Schedule7bMB.java:162-164}).
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the flags and verbatim messages; nothing is mutated
   */
  @Transactional(readOnly = true)
  public Schedule7bCheckStatusResponse checkStatus(long millId, int year) {
    List<CulvertReportEntity> rows = repository.findCulverts(millId, year);
    Map<Long, Map<Integer, Integer>> costs =
        costsByCulvert(repository.findCostDetails(millId, year));

    List<MessageInfo> errors = new ArrayList<>();
    boolean allMet = true;

    int rowCounter = 1;
    for (CulvertReportEntity row : rows) {
      Map<Integer, Integer> cost = costs.getOrDefault(row.culvertReportId(), Map.of());
      List<String> missing = missingLabels(row, cost);
      if (!missing.isEmpty()) {
        allMet = false;
        for (String label : missing) {
          errors.add(new MessageInfo(MSG_VALUE_REQUIRED, missingText(rowCounter, label)));
        }
      }
      rowCounter++;
    }

    MessageInfo requirementsMetMessage = allMet
        ? new MessageInfo(MSG_REQUIREMENTS_MET, resolveText(MSG_REQUIREMENTS_MET))
        : null;
    return new Schedule7bCheckStatusResponse(allMet, errors, requirementsMetMessage);
  }

  /**
   * One required-value check: the "is this value missing?" test paired with its verbatim legacy
   * label. The predicate sees both the culvert row and its cost map, so an attribute check and a
   * cost check share one shape.
   *
   * @param missing whether the value this check guards is absent
   * @param label the verbatim legacy label fragment, spacing included
   */
  private record RequiredCheck(
      java.util.function.BiPredicate<CulvertReportEntity, Map<Integer, Integer>> missing,
      String label) {
  }

  /** A required-attribute check on the culvert row itself. */
  private static RequiredCheck attrCheck(Predicate<CulvertReportEntity> missing, String label) {
    return new RequiredCheck((r, c) -> missing.test(r), label);
  }

  /** A required-cost check: the cost item has no stored value for the culvert. */
  private static RequiredCheck costCheck(int itemId, String label) {
    return new RequiredCheck((r, c) -> c.get(itemId) == null, label);
  }

  /**
   * The Check Status required values in the exact legacy emission order with the verbatim legacy
   * labels ({@code Schedule7bMB.java:130-158}), as an ordered table so {@link #missingLabels} stays
   * a flat walk rather than six branches.
   *
   * <p><strong>Two entries are type-conditional</strong> and read as "not applicable unless the
   * type matches", which is why they are expressed as {@code type-matches AND value-absent} rather
   * than as a plain null check: legacy only ever SET those flags inside their type branch ({@code
   * Schedule7bCheckStatus.java:11-17}), so a non-matching type leaves the flag false and the
   * culvert passes (S26/S27).
   *
   * <p><strong>Rise is deliberately absent from this table.</strong> Legacy sets no rise flag for
   * any type, so a culvert with a blank rise passes Check Status (S28). Adding a rise entry here
   * would be the single easiest way to break parity on this schedule.
   *
   * <p><strong>Comments use {@code isEmpty()}, not {@code isBlank()}.</strong> Legacy tested {@code
   * CoreUtil.isNullOrEmptyString(comments)}, which delegates with {@code doTrim = false} ({@code
   * util/CoreUtil.java:166-176}) — so a whitespace-only comment is NOT empty to legacy and an
   * "Others" culvert holding {@code " "} PASSES Check Status. Trimming here would flag a culvert
   * legacy passed.
   *
   * <p><strong>Recorded deviation — a NULL type.</strong> The column is nullable, and legacy called
   * {@code item.getCulvertTypeCode().equals(...)} with no null guard ({@code
   * Schedule7bCheckStatus.java:11,15}), so a null type threw an NPE and the whole Check Status
   * failed. Here {@code TYPE_ROUND.equals(...)}/{@code TYPE_OTHERS.equals(...)} are null-safe, so
   * both conditional checks simply do not apply and the culvert is judged on the four unconditional
   * values alone. Same class of fix the 7A twin records for its abutment-height check ({@code
   * Schedule7aService.java:374-376}); a write cannot produce this state ({@code culvertTypeCode} is
   * {@code @NotBlank}), so it is reachable only through legacy-written or migrated data.
   *
   * <p>The label spacing is copied byte-for-byte, inconsistencies included: the two
   * type-conditional labels use {@code "Id : "} while the four unconditional ones use {@code "Id:
   * "}, and the latter carry a trailing space that composes into {@code " : Value Required"}. These
   * are legacy string literals, not typos to tidy — the Gherkin assertions and the users' saved
   * screenshots expect them.
   */
  private static final List<RequiredCheck> REQUIRED_CHECKS = List.of(
      attrCheck(r -> TYPE_ROUND.equals(r.culvertTypeCode()) && r.spanSize() == null,
          " - Culvert Type Round - Span size"),
      attrCheck(r -> TYPE_OTHERS.equals(r.culvertTypeCode())
              && (r.comments() == null || r.comments().isEmpty()),
          " - Culvert Type Others - Comments"),
      attrCheck(r -> r.length() == null, " - Length "),
      attrCheck(r -> r.culvertPieceCount() == null, " - Piece Count "),
      costCheck(ITEM_MATERIAL, " - Material Cost "),
      costCheck(ITEM_INSTALL, " - Install Cost "));

  /**
   * The two type-conditional labels take {@code "Culvert Report Id : "} (space before the colon);
   * the four unconditional ones take {@code "Culvert Report Id: "}. Legacy hardcoded both spellings
   * ({@code Schedule7bMB.java:132,137} vs {@code :142,147,152,157}).
   */
  private static final Set<String> SPACED_PREFIX_LABELS = Set.of(
      " - Culvert Type Round - Span size", " - Culvert Type Others - Comments");

  /**
   * The missing required-field labels for one culvert, in the exact legacy order (verbatim text).
   */
  private static List<String> missingLabels(
      CulvertReportEntity row, Map<Integer, Integer> cost) {
    List<String> missing = new ArrayList<>();
    for (RequiredCheck check : REQUIRED_CHECKS) {
      if (check.missing().test(row, cost)) {
        missing.add(check.label());
      }
    }
    return missing;
  }

  /**
   * {@code "Culvert Report Id[ ]: {rowCounter}{label}: Value Required"} — the legacy composition
   * ({@code FacesUtil.addCheckStatusErrorMessage} concatenates {@code label + ": " + bundleText},
   * {@code util/FacesUtil.java:134}).
   */
  private String missingText(int rowCounter, String label) {
    String prefix = SPACED_PREFIX_LABELS.contains(label)
        ? "Culvert Report Id : "
        : "Culvert Report Id: ";
    return prefix + rowCounter + label + ": " + resolveText(MSG_VALUE_REQUIRED);
  }

  private String resolveText(String key) {
    return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
  }

  // ===============================================================================================
  // Assembly + derivation helpers
  // ===============================================================================================

  /** Group the flat cost rows by culvert id into an item-id → cost map (last row wins on a dup). */
  private static Map<Long, Map<Integer, Integer>> costsByCulvert(List<CulvertCostEntity> rows) {
    Map<Long, Map<Integer, Integer>> byCulvert = new HashMap<>();
    for (CulvertCostEntity row : rows) {
      byCulvert.computeIfAbsent(row.culvertReportId(), k -> new HashMap<>())
          .put(row.costItemId(), row.cost());
    }
    return byCulvert;
  }

  /** Map one culvert row + its cost map to the wire shape, computing the total (BR-05). */
  private static Culvert toCulvert(
      CulvertReportEntity row, int rowCounter, Map<Integer, Integer> cost) {
    Integer material = cost.get(ITEM_MATERIAL);
    Integer install = cost.get(ITEM_INSTALL);

    return new Culvert(
        row.culvertReportId(), rowCounter, row.culvertTypeCode(), row.spanSize(), row.riseSize(),
        oneDecimal(row.length()), row.culvertPieceCount(), material, install,
        totalCost(material, install), row.comments(), row.revisionCount());
  }

  /**
   * Normalize the length to scale 1 (the legacy {@code NUMBER(7,1)} shape), so a whole value
   * serializes as {@code 12.0} rather than {@code 12} — Oracle collapses {@code 12.0} to scale 0 on
   * read, which would otherwise emit an inconsistent integer for some rows.
   */
  private static BigDecimal oneDecimal(BigDecimal value) {
    return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
  }

  /**
   * The system-calculated Total costs (BR-05): legacy {@code CulvertReportType.getTotalCost()}
   * ({@code service/domain/type/CulvertReportType.java:238-242}) summing install + material through
   * {@code CoreUtil.sumBigDecimalAreas} ({@code util/CoreUtil.java:363-377}) — null operands
   * skipped, and a sum with NO non-null operand is null, never {@code 0}.
   *
   * <p>Legacy routes this through the AREA helper rather than the cost helper, which additionally
   * rounds to one decimal. That rounding is a no-op here and the distinction is not a port error:
   * both operands are whole-dollar integers on write ({@code Schedule7bDAO.java:225,230} store
   * {@code .intValue()}) and on read ({@code :337,342}), so integer addition is exact parity. Each
   * operand is bounded by the ±99,999,999 validation, so their sum stays in {@code int} range.
   */
  private static Integer totalCost(Integer material, Integer install) {
    if (material == null && install == null) {
      return null;
    }
    if (material == null) {
      return install;
    }
    if (install == null) {
      return material;
    }
    return material + install;
  }
}
