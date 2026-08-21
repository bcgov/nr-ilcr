package ca.bc.gov.nrs.ilcr.schedule9;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.exception.FieldValuesRequiredException;
import ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotEditableException;
import ca.bc.gov.nrs.ilcr.exception.ScheduleNotSavedException;
import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.CostRow;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository.RecordRow;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecord;
import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecordRequest;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedule 9 document assembly + server-side derivation (AD-5/AD-6) AND the write half (Story 9.2):
 * add/edit/delete one contractual-work record (a {@code CONTRACTUAL_WORK_REPORT} master + its one
 * keyed {@code ILCR_COST_REPORT_DETAIL} cost line) plus the per-record Check Status. {@code
 * costPerUnit} ($/Unit) is derived here and never accepted from a client.
 *
 * <p>{@code editable} = the caller holds {@code EDIT_SCHEDULE} AND the 1–10 track is Draft,
 * computed here and server-authoritative (AD-9, S30). A non-Draft mill still lists every record.
 *
 * <p><strong>The write half hardens what legacy left open.</strong> Legacy had no concurrency
 * control (it never incremented {@code REVISION_COUNT}), no server-side edit gate (only the
 * disabled buttons), and routed Save and Delete through one list transaction. Here each write is
 * one transaction whose first statement is the {@code FOR UPDATE} Draft gate, the optimistic lock
 * keys on the master's {@code REVISION_COUNT}, and Save/Delete are separate endpoints (recorded
 * deviation). The Save-vs-Check asymmetry — blank units/cost and a side slope of exactly 100 SAVE
 * but Check flags them — is preserved verbatim, not repaired. Costs/units are never logged (AD-11).
 */
@Service
@Slf4j
public class Schedule9Service {

  private static final String STATUS_DRAFT = "D";

  // Contractual Item cost-item ids (BR-09; legacy Constant.REPORT_COST_ITEMS Schedule9_*).
  private static final int ITEM_MIN = 108;
  private static final int ITEM_OTHER = 114;
  private static final int ITEM_ROAD_DEACTIVATE_SEMI = 111;
  private static final int ITEM_ROAD_DEACTIVATE_PERM = 112;

  // The conditional-description driving code values (legacy Schedule9DO enablement).
  private static final String UNIT_OTHER = "O";
  private static final String SOURCE_OTHER = "O";
  private static final String SOURCE_SUBCONTRACT = "S";

  // FLD-001 field labels — the {0} the JSF required template ("{0}: Value is required.") fills, in
  // screen order (schedule9.xhtml). NOT prefixed with the record id: that prefix is Check Status'.
  // Only the FIVE select-one/Company ID fields legacy marks required="true"; the three "Other"
  // descriptions are NOT required at Save (see validateRequired).
  private static final String LABEL_COMPANY_ID = "Company ID";
  private static final String LABEL_CONTRACTUAL_ITEM = "Contractual Item";
  private static final String LABEL_UNIT_TYPE = "Unit Type";
  private static final String LABEL_BEC_ZONE = "Biogeoclimatic Zone";
  private static final String LABEL_SOURCE = "Source";

  // Check Status field-title segments (leading space) — the exact legacy titles
  // Schedule9CheckStatus.validateSchedule builds: "Contractual Work Report Id : {row}" + segment.
  static final String CHECK_COMPANY_ID = " Company ID";
  static final String CHECK_CONTRACTUAL_ITEM = " Contractual Item";
  static final String CHECK_SIDE_SLOPE = " Side Slope %";
  static final String CHECK_NUMBER_OF_UNITS = " Number of Units";
  static final String CHECK_UNIT_TYPE = " Unit Type";
  static final String CHECK_BEC_ZONE = " Biogeoclimatic Zone";
  static final String CHECK_COST = " Cost$";
  static final String CHECK_SOURCE = " Source";
  static final String CHECK_TITLE_PREFIX = "Contractual Work Report Id : ";

  private static final String MSG_VALUE_REQUIRED = "missingRequiredFieldMsg";
  private static final String MSG_INVALID_RANGE = "invalidRangeErrorMsg";
  private static final String MSG_REQUIREMENTS_MET = "scheduleRequirementsMetMsg";

  // Check-Status numeric bounds + the legacy DecimalFormat patterns fed to invalidRangeErrorMsg.
  // The
  // side-slope Check bound is 99 (not the Save bound 100) and units/cost are range-checked even
  // though blank — both halves of the preserved Save-vs-Check asymmetry.
  private static final double SIDE_SLOPE_CHECK_MAX = 99.0;
  private static final double UNITS_MAX = 99999.9;
  private static final double COST_MAX = 9999999.0;
  private static final String FORMAT_SIDE_SLOPE = "##";
  private static final String FORMAT_UNITS = "##,###.#";
  private static final String FORMAT_COST = "#,###,###";

  // The bounds are formatted with an EXPLICIT locale (comma grouping / period decimal), not the JVM
  // default, so the composed range line matches the legacy delivery output regardless of the
  // server's
  // default locale — legacy's DecimalFormat happened to run on a comma-locale JVM.
  private static final DecimalFormatSymbols NUMBER_SYMBOLS =
      DecimalFormatSymbols.getInstance(Locale.CANADA);

  private final Schedule9Repository repository;
  private final MessageSource messageSource;

  public Schedule9Service(Schedule9Repository repository, MessageSource messageSource) {
    this.repository = repository;
    this.messageSource = messageSource;
  }

  // ===============================================================================================
  // Read (Story 9.1)
  // ===============================================================================================

  /**
   * The number of Schedule 9 records for a mill/year — the reporting empty-schedule pre-check reads
   * this through the service seam (Story 29.10) rather than reaching into {@code
   * Schedule9Repository}.
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @return the record count (0 when the schedule is empty)
   */
  @Transactional(readOnly = true)
  public int countRecords(long millId, int year) {
    return repository.countRecords(millId, year);
  }

  /**
   * The Schedule 9 document for a mill/year. A valid, active mill/year with no records returns an
   * empty {@code records} list (never a 404 — the guards live in {@code MillContextService}).
   *
   * @param millId the validated mill id
   * @param year the validated reporting year
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the pinned document
   */
  @Transactional(readOnly = true)
  public Schedule9Response getSchedule9(long millId, int year, boolean callerMayEdit) {
    String trackStatus = repository.findTrackStatus(millId, year).orElse(null);
    return buildDocument(millId, year, trackStatus, callerMayEdit, true);
  }

  /**
   * Assemble the served document for a KNOWN track status. The write methods reuse this with the
   * {@code D} their Draft gate just proved (same transaction) rather than re-running the track
   * query.
   */
  private Schedule9Response buildDocument(
      long millId, int year, String trackStatus, boolean callerMayEdit, boolean includeCodeLists) {
    boolean editable = callerMayEdit && STATUS_DRAFT.equals(trackStatus);

    // One cost line per record; lowest ILCR_COST_REPORT_DETAIL_ID wins if delivery ever holds more
    // (no unique constraint on the FK) — the repository ORDER BY makes that deterministic, and the
    // write path's updateCostLine narrows to the same MIN row, so read and write agree.
    Map<Integer, CostRow> costByRecord =
        repository.findCostLines(millId, year).stream()
            .collect(
                Collectors.toMap(CostRow::reportId, Function.identity(), (first, dup) -> first));

    List<ContractualWorkRecord> records =
        repository.findRecords(millId, year).stream()
            .map(row -> toRecord(row, costByRecord.get(row.id())))
            .toList();

    return new Schedule9Response(
        millId,
        year,
        trackStatus,
        editable,
        records,
        includeCodeLists ? repository.codeLists() : null,
        null);
  }

  private static ContractualWorkRecord toRecord(RecordRow row, CostRow cost) {
    Integer costValue = cost == null ? null : cost.cost();
    CodeDescriptionDto contractualItem =
        cost == null || cost.itemCode() == null
            ? null
            : new CodeDescriptionDto(String.valueOf(cost.itemCode()), cost.itemName());
    String itemDescription = cost == null ? null : cost.itemDescription();

    return new ContractualWorkRecord(
        row.id(),
        row.revisionCount(),
        row.contractorId(),
        contractualItem,
        itemDescription,
        code(row.unitCode(), row.unitCodeDescription()),
        row.unitDescription(),
        row.numberOfUnits(),
        code(row.becCode(), row.becDescription()),
        costValue,
        costPerUnit(costValue, row.numberOfUnits()),
        row.sideSlopePct(),
        code(row.sourceCode(), row.sourceCodeDescription()),
        row.sourceDescription(),
        row.comments());
  }

  /** A code/description pair, or null when the code itself is absent. */
  private static CodeDescriptionDto code(String code, String description) {
    return code == null ? null : new CodeDescriptionDto(code, description);
  }

  /** $/Unit = cost ÷ units (S14: null when units are zero/blank), scale 2 HALF_UP. */
  private static BigDecimal costPerUnit(Integer cost, BigDecimal units) {
    if (cost == null || units == null || units.signum() == 0) {
      return null;
    }
    return BigDecimal.valueOf(cost).divide(units, 2, RoundingMode.HALF_UP);
  }

  // ===============================================================================================
  // Writes (Story 9.2). Each is ONE transaction: FOR UPDATE Draft gate first, then validate, then
  // persist, then return the recomputed document built from the "D" the gate proved. The success
  // message is attached by the controller (AD-8), so the service stays message-free on the write
  // path. A persistence failure rolls back and surfaces as ScheduleNotSavedException.
  // ===============================================================================================

  /**
   * Add one contractual-work record and return the recomputed document (S01). Persists one master
   * plus its single keyed cost line, in that order (T1 gate (i)).
   *
   * @param millId the mill id (context already validated by the controller, AD-4)
   * @param year the reporting year
   * @param request the entered fields
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE} (for the echoed
   *     editability)
   * @param user the acting user id (audit columns)
   * @return the recomputed document, the new record included
   */
  @Transactional
  public Schedule9Response addRecord(
      long millId,
      int year,
      ContractualWorkRecordRequest request,
      boolean callerMayEdit,
      String user) {
    requireDraft(millId, year);
    validateWrite(request);
    int itemCode = request.contractualItemCode();
    try {
      int recordId = repository.nextContractualWorkReportId();
      repository.insertRecord(
          recordId,
          millId,
          year,
          request.contractorId(),
          sideSlopeToStore(itemCode, request.sideSlopePct()),
          request.numberOfUnits(),
          request.unitCode(),
          unitDescriptionToStore(request.unitCode(), request.unitDescription()),
          request.sourceCode(),
          sourceDescriptionToStore(request.sourceCode(), request.sourceDescription()),
          request.biogeoclimaticZone(),
          request.comments(),
          user);
      repository.insertCostLine(
          repository.nextCostDetailId(),
          recordId,
          itemCode,
          request.cost(),
          itemDescriptionToStore(itemCode, request.itemDescription()),
          user);
    } catch (DataAccessException ex) {
      logWriteFailure("add", millId, year, null, ex);
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit, false);
  }

  /**
   * Edit one record in place and return the recomputed document (S02, S07). Optimistic-locked on
   * the master's own {@code REVISION_COUNT}: a stale token → 409, an unknown or foreign id → 404.
   * The cost line is updated as the child in the same transaction.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param recordId the record to edit
   * @param request the entered fields plus the required {@code revisionCount} token
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @param user the acting user id (audit columns)
   * @return the recomputed document
   */
  @Transactional
  public Schedule9Response updateRecord(
      long millId,
      int year,
      int recordId,
      ContractualWorkRecordRequest request,
      boolean callerMayEdit,
      String user) {
    requireDraft(millId, year);
    // Defence in depth for the AR11 token: the API's OnUpdate group already rejects a null
    // revisionCount as a clean 400, but this method unboxes it, so a direct caller that bypassed
    // the
    // group would otherwise NPE into a 500. Never a coerced 409 (the 2.1 lesson).
    if (request.revisionCount() == null) {
      throw new RevisionCountRequiredException();
    }
    validateWrite(request);
    int itemCode = request.contractualItemCode();
    try {
      int updated =
          repository.updateRecord(
              recordId,
              millId,
              year,
              request.revisionCount(),
              request.contractorId(),
              sideSlopeToStore(itemCode, request.sideSlopePct()),
              request.numberOfUnits(),
              request.unitCode(),
              unitDescriptionToStore(request.unitCode(), request.unitDescription()),
              request.sourceCode(),
              sourceDescriptionToStore(request.sourceCode(), request.sourceDescription()),
              request.biogeoclimaticZone(),
              request.comments(),
              user);
      if (updated == 0) {
        // Zero rows means the id is absent/foreign OR the token is stale; the guarded UPDATE cannot
        // tell which. Only the scoped probe can.
        if (repository.countRecord(recordId, millId, year) == 0) {
          throw new ContractualWorkRecordNotFoundException();
        }
        throw new StaleRevisionException();
      }
      repository.upsertCostLine(
          recordId,
          itemCode,
          request.cost(),
          itemDescriptionToStore(itemCode, request.itemDescription()),
          user);
    } catch (DataAccessException ex) {
      logWriteFailure("update", millId, year, recordId, ex);
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit, false);
  }

  /**
   * Delete one record and its cost line, then return the recomputed document (S10). Children go
   * FIRST (the parent FK is {@code ON DELETE NO ACTION} in delivery). Carries no revision token, so
   * a delete cannot be rejected as stale; the scoped existence probe runs first so a foreign or
   * unknown id is a 404 before anything is removed.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @param recordId the record to delete
   * @param callerMayEdit whether the caller holds {@code EDIT_SCHEDULE}
   * @return the recomputed document without the deleted record
   */
  @Transactional
  public Schedule9Response deleteRecord(
      long millId, int year, int recordId, boolean callerMayEdit) {
    requireDraft(millId, year);
    try {
      if (repository.countRecord(recordId, millId, year) == 0) {
        throw new ContractualWorkRecordNotFoundException();
      }
      repository.deleteCostLinesForRecord(recordId, millId, year);
      if (repository.deleteRecord(recordId, millId, year) == 0) {
        // The probe above passed, so a zero here means a concurrent delete won the race. Acting on
        // the count rather than assuming success is the 8.2 lesson.
        throw new ContractualWorkRecordNotFoundException();
      }
    } catch (DataAccessException ex) {
      logWriteFailure("delete", millId, year, recordId, ex);
      throw new ScheduleNotSavedException();
    }
    return buildDocument(millId, year, STATUS_DRAFT, callerMayEdit, false);
  }

  // ===============================================================================================
  // Write validation (BR-03/BR-04) — required (FLD-001) + conditional descriptions + code lists
  // (FLD-005). The three numeric RANGES (FLD-002/003/004) are declarative on the request DTO, so a
  // range failure is a 400 before this runs. Nothing persists on rejection (the try block never
  // starts).
  // ===============================================================================================

  private void validateWrite(ContractualWorkRecordRequest request) {
    validateRequired(request);
    validateCodes(request);
  }

  /**
   * The FLD-001 required check: one {@code javax.faces.component.UIInput.REQUIRED} line per missing
   * field in screen order. Only the FIVE fields legacy marks {@code required="true"} in {@code
   * schedule9.xhtml} are enforced — Company ID, Contractual Item, Unit Type, Biogeoclimatic Zone,
   * Source. All missing fields report together on one 400 (the FieldValuesRequiredException
   * contract).
   *
   * <p><strong>The three "Other" descriptions are NOT required at Save</strong> — legacy leaves
   * {@code itemDescription} with no {@code required} attribute ({@code schedule9.xhtml:117}), marks
   * {@code unitDescription} {@code required="false"} (:183), and guards {@code sourceDescription}
   * with a MISSPELLED {@code require=} that JSF silently ignores (:253). So a blank "Other"
   * description SAVES; the field is only conditionally STORED (nulled when its driver is not
   * "Other"), never required. Dev Note (D) flagged exactly this typo to verify — confirmed at dev,
   * and the epics' "required only when Other" reading of AC3 is the recorded deviation.
   */
  private static void validateRequired(ContractualWorkRecordRequest request) {
    List<String> missing = new ArrayList<>();
    if (StringUtils.isBlank(request.contractorId())) {
      missing.add(LABEL_COMPANY_ID);
    }
    if (request.contractualItemCode() == null) {
      missing.add(LABEL_CONTRACTUAL_ITEM);
    }
    if (StringUtils.isBlank(request.unitCode())) {
      missing.add(LABEL_UNIT_TYPE);
    }
    if (StringUtils.isBlank(request.biogeoclimaticZone())) {
      missing.add(LABEL_BEC_ZONE);
    }
    if (StringUtils.isBlank(request.sourceCode())) {
      missing.add(LABEL_SOURCE);
    }
    if (!missing.isEmpty()) {
      throw new FieldValuesRequiredException(missing);
    }
  }

  /**
   * The FLD-005 force-selection check: a code that is present but resolves to no reference-list row
   * → 400 {@code invalidCodeValueErrorMsg}. Runs after {@link #validateRequired}, so every code is
   * present here. Checked in screen order.
   */
  private void validateCodes(ContractualWorkRecordRequest request) {
    int itemCode = request.contractualItemCode();
    if (itemCode < ITEM_MIN || itemCode > ITEM_OTHER) {
      throw new InvalidContractualCodeException();
    }
    if (repository.countUnitCode(request.unitCode()) == 0) {
      throw new InvalidContractualCodeException();
    }
    if (repository.countBecZoneCode(request.biogeoclimaticZone()) == 0) {
      throw new InvalidContractualCodeException();
    }
    if (repository.countSourceCode(request.sourceCode()) == 0) {
      throw new InvalidContractualCodeException();
    }
  }

  // Conditional-null rules (BR-04): a dependent field is stored only while its driver enables it,
  // so
  // changing the driver clears the dependent (legacy nulls them in the on-change setters).

  private static String itemDescriptionToStore(int itemCode, String itemDescription) {
    return itemCode == ITEM_OTHER ? itemDescription : null;
  }

  private static Integer sideSlopeToStore(Integer itemCode, Integer sideSlopePct) {
    return itemCode != null
            && (itemCode == ITEM_ROAD_DEACTIVATE_SEMI || itemCode == ITEM_ROAD_DEACTIVATE_PERM)
        ? sideSlopePct
        : null;
  }

  private static String unitDescriptionToStore(String unitCode, String unitDescription) {
    return UNIT_OTHER.equals(unitCode) ? unitDescription : null;
  }

  private static String sourceDescriptionToStore(String sourceCode, String sourceDescription) {
    return sourceEnablesDescription(sourceCode) ? sourceDescription : null;
  }

  private static boolean sourceEnablesDescription(String sourceCode) {
    return SOURCE_OTHER.equals(sourceCode) || SOURCE_SUBCONTRACT.equals(sourceCode);
  }

  /**
   * The Draft gate for every write: the Schedules 1–10 track must be {@code D}, else 409 (BR-06,
   * AD-9). The {@code FOR UPDATE} lock is load-bearing — it holds the status for the whole
   * transaction so a transition cannot slip between the gate and the write it guards. Never reads
   * the silviculture track. The mill/year context (400/404/409) is already validated by the
   * controller.
   */
  private void requireDraft(long millId, int year) {
    String trackStatus = repository.findTrackStatusForUpdate(millId, year).orElse(null);
    if (!STATUS_DRAFT.equals(trackStatus)) {
      throw new ScheduleNotEditableException();
    }
  }

  /** Class name plus most-specific cause only — an ORA code carries no cost/unit values (AD-11). */
  private static void logWriteFailure(
      String op, long millId, int year, Integer recordId, DataAccessException ex) {
    log.warn(
        "Schedule 9 {} failed for mill {} year {} record {} [{}]: {}",
        op,
        millId,
        year,
        recordId,
        ex.getClass().getSimpleName(),
        NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
  }

  // ===============================================================================================
  // Check Status (Story 9.2, BR-08) — read-only, mutates nothing, NOT Draft-gated (VIEW_SCHEDULE
  // only, so a Submitted mill can still be checked). Reproduces
  // Schedule9CheckStatus.validateSchedule
  // exactly: the eight fields, the 1-based row number in the title, the side-slope 0..99 bound, and
  // the base validator's invalidRangeErrorMsg (NOT the per-field save messages) for a range
  // failure.
  // ===============================================================================================

  /**
   * Check Status for Schedule 9 (S09). Walks every stored record in served (id) order; a record
   * passes iff all eight checks pass. On an all-met schedule the SUC-002 banner is returned and the
   * error list is empty; otherwise the per-field lines are returned verbatim and no banner.
   *
   * @param millId the mill id (context already validated)
   * @param year the reporting year
   * @return the check-status result with fully composed, resolved message text
   */
  @Transactional(readOnly = true)
  public Schedule9CheckStatusResponse checkStatus(long millId, int year) {
    Map<Integer, CostRow> costByRecord =
        repository.findCostLines(millId, year).stream()
            .collect(
                Collectors.toMap(CostRow::reportId, Function.identity(), (first, dup) -> first));
    List<RecordRow> records = repository.findRecords(millId, year);

    List<MessageInfo> errors = new ArrayList<>();
    int rowNumber = 1;
    for (RecordRow row : records) {
      evaluateRecord(rowNumber, row, costByRecord.get(row.id()), errors);
      rowNumber++;
    }

    if (errors.isEmpty()) {
      return new Schedule9CheckStatusResponse(
          true, List.of(), new MessageInfo(MSG_REQUIREMENTS_MET, resolve(MSG_REQUIREMENTS_MET)));
    }
    return new Schedule9CheckStatusResponse(false, errors, null);
  }

  /**
   * The eight checks for one record, in legacy validateSchedule order, appended to {@code errors}.
   */
  private void evaluateRecord(
      int rowNumber, RecordRow row, CostRow cost, List<MessageInfo> errors) {
    Integer itemCode = cost == null ? null : cost.itemCode();

    if (StringUtils.isBlank(row.contractorId())) {
      errors.add(valueRequired(rowNumber, CHECK_COMPANY_ID));
    }
    if (itemCode == null) {
      errors.add(valueRequired(rowNumber, CHECK_CONTRACTUAL_ITEM));
    }
    // Side Slope is checked ONLY when enabled (item 111/112); required-when-enabled AND range
    // 0..99.
    boolean sideSlopeEnabled =
        itemCode != null
            && (itemCode == ITEM_ROAD_DEACTIVATE_SEMI || itemCode == ITEM_ROAD_DEACTIVATE_PERM);
    if (sideSlopeEnabled) {
      if (row.sideSlopePct() == null) {
        errors.add(valueRequired(rowNumber, CHECK_SIDE_SLOPE));
      } else if (outOfRange(row.sideSlopePct(), SIDE_SLOPE_CHECK_MAX)) {
        errors.add(
            rangeError(rowNumber, CHECK_SIDE_SLOPE, SIDE_SLOPE_CHECK_MAX, FORMAT_SIDE_SLOPE));
      }
    }
    // Number of Units — always checked; blank is flagged (the Save-vs-Check gap), range
    // 0..99,999.9.
    if (row.numberOfUnits() == null) {
      errors.add(valueRequired(rowNumber, CHECK_NUMBER_OF_UNITS));
    } else if (outOfRange(row.numberOfUnits(), UNITS_MAX)) {
      errors.add(rangeError(rowNumber, CHECK_NUMBER_OF_UNITS, UNITS_MAX, FORMAT_UNITS));
    }
    if (StringUtils.isBlank(row.unitCode())) {
      errors.add(valueRequired(rowNumber, CHECK_UNIT_TYPE));
    }
    if (StringUtils.isBlank(row.becCode())) {
      errors.add(valueRequired(rowNumber, CHECK_BEC_ZONE));
    }
    // Cost$ — always checked; blank is flagged (the Save-vs-Check gap), range 0..9,999,999.
    Integer costValue = cost == null ? null : cost.cost();
    if (costValue == null) {
      errors.add(valueRequired(rowNumber, CHECK_COST));
    } else if (outOfRange(BigDecimal.valueOf(costValue), COST_MAX)) {
      errors.add(rangeError(rowNumber, CHECK_COST, COST_MAX, FORMAT_COST));
    }
    if (StringUtils.isBlank(row.sourceCode())) {
      errors.add(valueRequired(rowNumber, CHECK_SOURCE));
    }
  }

  private static boolean outOfRange(Number value, double max) {
    double d = value.doubleValue();
    return d < 0.0 || d > max;
  }

  /**
   * {@code "Contractual Work Report Id : {row}{segment}: Value Required"} — the legacy composition.
   */
  private MessageInfo valueRequired(int rowNumber, String segment) {
    String text = CHECK_TITLE_PREFIX + rowNumber + segment + ": " + resolve(MSG_VALUE_REQUIRED);
    return new MessageInfo(MSG_VALUE_REQUIRED, text);
  }

  /**
   * The range line, byte-for-byte with legacy: title + {@code ": "} + {@code invalidRangeErrorMsg}
   * resolved with the field's own bounds. Both bounds are formatted with the SAME pattern (the
   * legacy base validator passes {@code lowerLimitFormat} for both), so the output matches the
   * running app.
   */
  private MessageInfo rangeError(int rowNumber, String segment, double max, String pattern) {
    String lower = new DecimalFormat(pattern, NUMBER_SYMBOLS).format(0.0);
    String upper = new DecimalFormat(pattern, NUMBER_SYMBOLS).format(max);
    String range = resolve(MSG_INVALID_RANGE, lower, upper);
    String text = CHECK_TITLE_PREFIX + rowNumber + segment + ": " + range;
    return new MessageInfo(MSG_INVALID_RANGE, text);
  }

  private String resolve(String key, Object... args) {
    return messageSource.getMessage(
        key, args.length == 0 ? null : args, LocaleContextHolder.getLocale());
  }
}
