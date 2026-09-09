package ca.bc.gov.nrs.ilcr.millmaintenance;

import ca.bc.gov.nrs.ilcr.exception.StaleRevisionException;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.AdminMill;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ContactOption;
import ca.bc.gov.nrs.ilcr.millmaintenance.dto.ImportableMill;
import ca.bc.gov.nrs.ilcr.reportingyear.ReportingYearService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The mill lifecycle: search, import from the ministry mill table, activate, deactivate, and save
 * the head-office and contact details (UC-MILL-001).
 *
 * <p>Entities never leave this class — every method returns a wire record. Write methods return an
 * {@link Outcome} so the service names what happened and the controller resolves the text.
 *
 * <p>Report records are not created here. Importing a mill and activating a closed one both need
 * the current year's report-status and per-category rows, which is the same unit of work opening a
 * reporting year repeats per mill, so both call the reporting-year domain that owns those three
 * tables rather than reproducing its inserts (AD-14).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class MillMaintenanceService {

  static final String MSG_NOT_FOUND = "mill.not.found";
  static final String MSG_SAVED = "mill.updated";
  static final String MSG_ACTIVATED = "mill.activated";
  static final String MSG_DEACTIVATED = "mill.expired";

  /**
   * Stamped on every imported cross-reference, verbatim from legacy (MillDAO.java:189). It is the
   * only record of how a cross-reference came to exist, and the delivery audit trigger copies it
   * into the audit shadow table, so it is worth keeping rather than quietly dropping.
   */
  static final String IMPORT_COMMENT = "Imported from ISP Mill table";

  private final MillMaintenanceRepository repository;
  private final ReportingYearService reportingYears;

  /**
   * Constructs the service.
   *
   * @param repository the mill administration repository
   * @param reportingYears the reporting-year domain, which owns the report-record tables
   */
  public MillMaintenanceService(
      MillMaintenanceRepository repository, ReportingYearService reportingYears) {
    this.repository = repository;
    this.reportingYears = reportingYears;
  }

  /**
   * Search the tracked ILCR mills (S11). All three criteria are optional and an absent one is not a
   * filter, which is how legacy's blank fields and its empty "all" status option behaved.
   *
   * @param millNumber the mill number as typed, or null/blank for any
   * @param millName a name fragment, or null/blank for any
   * @param statusCode {@code ACT} or {@code CLS}, or null/blank for any
   * @return the matching mills, ordered by mill number; empty when nothing matched
   * @throws MillMaintenanceException 400 when the mill number is not numeric or the status code is
   *     not a known mill status
   */
  @Transactional(readOnly = true)
  public List<AdminMill> search(String millNumber, String millName, String statusCode) {
    List<AdminMill> results =
        repository
            .search(
                parseMillNumber(millNumber),
                escapeLike(StringUtils.trimToNull(millName)),
                parseStatusCode(statusCode))
            .stream()
            .map(MillMaintenanceService::toAdminMill)
            .toList();
    // The search is unbounded like legacy's (D8); the count — never the criteria or the rows — is
    // logged so the Mills page story can decide on paging with real numbers.
    log.info("Mill search returned {} rows", results.size());
    return results;
  }

  /**
   * The ministry mills that can still be imported (BR-04).
   *
   * @param millNumber the mill number as typed, or null/blank for any
   * @param millName a name fragment, or null/blank for any
   * @return the importable mills, ordered by mill number
   */
  @Transactional(readOnly = true)
  public List<ImportableMill> findImportable(String millNumber, String millName) {
    List<ImportableMill> results =
        repository
            .findImportable(
                parseMillNumber(millNumber), escapeLike(StringUtils.trimToNull(millName)))
            .stream()
            .map(e -> new ImportableMill(e.millId(), e.millNumber(), e.millName()))
            .toList();
    // Unbounded anti-join over all of THE.MILL (D8) — the count is the paging evidence for 22.3.
    log.info("Importable-mill search returned {} rows", results.size());
    return results;
  }

  /**
   * One tracked mill's administration detail.
   *
   * @param millId the mill id
   * @return the mill
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   */
  @Transactional(readOnly = true)
  public AdminMill findById(long millId) {
    return requireTrackedMill(millId);
  }

  /**
   * The contacts selectable for a mill's head-office and division slots (BR-09).
   *
   * @param millId the mill id
   * @return the selectable contacts, ordered by name; empty when the mill has no client linkage
   * @throws MillMaintenanceException 404 when the mill is unknown or not yet imported
   */
  @Transactional(readOnly = true)
  public List<ContactOption> contactOptions(long millId) {
    requireTrackedMill(millId);
    return repository.findContactOptions(millId).stream()
        .map(e -> new ContactOption(e.clientContactId(), e.contactName()))
        .toList();
  }

  /**
   * Bring a ministry mill into ILCR (S02, BR-03): create its cross-reference initialized closed
   * with the head-office indicator set, then give it the current year's report records.
   *
   * <p>One transaction covers both. Legacy's did too, and its rollback semantics are the reason the
   * failure message promises no partial state: any exception rolls the whole thing back so no
   * cross-reference survives (S14).
   *
   * @param millId the ministry mill to import
   * @param user the acting administrator
   * @return the imported mill, whose details the screen then displays
   * @throws MillMaintenanceException 404 unknown mill, 409 already tracked or no open reporting
   *     year, 500 on a failed import
   */
  @Transactional
  public Outcome importMill(long millId, String user) {
    if (!repository.millExists(millId)) {
      throw MillMaintenanceException.millNotFound();
    }
    if (repository.findById(millId).isPresent()) {
      throw MillMaintenanceException.alreadyTracked();
    }
    int year = requireCurrentReportingYear();

    try {
      repository.insertStatusXref(millId, AdminMill.CLOSED, "Y", IMPORT_COMMENT, user);
      reportingYears.enrolMillInYear(millId, year, user);
    } catch (DataIntegrityViolationException failed) {
      // Any integrity failure past the existence checks -- a concurrent import losing the
      // cross-reference primary key, leftover report rows from an old partial state colliding, an
      // overflow -- ends the same way: the transaction rolls back, no cross-reference survives, and
      // the caller gets legacy's own import-failure answer (S14). The causes are deliberately not
      // told apart on the wire: guessing from constraint names would misname some of them, and the
      // message already sends the reader to the log, where the real cause is.
      log.warn("Mill {} import failed and rolled back", millId, failed);
      throw MillMaintenanceException.importFailed();
    }

    // No legacy success message exists for import: the screen's only confirmation was the imported
    // mill's own details appearing (S02), so the record is returned with no message rather than
    // with a sentence the administrator never saw.
    log.info("Mill {} imported into ILCR for reporting year {} by {}", millId, year, user);
    return new Outcome(requireTrackedMill(millId), null);
  }

  /**
   * Reopen a closed mill for reporting (S04) and guarantee its current-year report records (BR-07).
   *
   * <p>The records check is legacy's, and so is its depth: it asks only whether a report-status row
   * exists for the year. A mill whose status row is present but whose per-category rows are missing
   * is not repaired, because nothing in legacy repaired it and inventing that here would be a new
   * behaviour on a shared table.
   *
   * <p>No precondition on the current status. Legacy's activate had no guard of any kind, unlike
   * its deactivate, and re-activating an already-active mill is harmless.
   *
   * @param millId the mill id
   * @param revisionCount the revision the caller read
   * @param user the acting administrator
   * @return the activated mill
   * @throws MillMaintenanceException 404 unknown, 409 no open reporting year or partial
   *     current-year report records
   * @throws StaleRevisionException 409 when another administrator wrote the row first
   */
  @Transactional
  public Outcome activate(long millId, int revisionCount, String user) {
    requireTrackedMill(millId);
    int year = requireCurrentReportingYear();

    applyStatus(millId, AdminMill.ACTIVE, revisionCount, user);

    if (!reportingYears.isMillEnrolled(millId, year)) {
      try {
        reportingYears.enrolMillInYear(millId, year, user);
      } catch (DataIntegrityViolationException partial) {
        // The status row was absent but some per-category rows were not, so re-creating the set
        // collides. Legacy reached the same state and surfaced it as its generic unhandled error,
        // sending the administrator to the logs; this names the inconsistency as a conflict instead
        // (D7: a business error, not a 500). The status change above is rolled back with it, so the
        // mill is left exactly as it was found.
        log.warn(
            "Mill {} has partial report records for year {}; activation rolled back",
            millId,
            year,
            partial);
        throw MillMaintenanceException.partialReportRecords();
      }
    }

    log.info("Mill {} activated by {}", millId, user);
    return new Outcome(requireTrackedMill(millId), MSG_ACTIVATED);
  }

  /**
   * Close a mill (S03). Refused while any user assignment is still active (BR-01/S12), which is the
   * one precondition legacy checked on this screen — and the mill's status is left untouched when
   * it refuses.
   *
   * <p>Closing has reach beyond this table: a closed mill is not viewable for schedule workflows
   * (BR-06, enforced by the mill/year context owner), cannot have an assignment revived, and is
   * excluded from the next reporting year that is opened.
   *
   * @param millId the mill id
   * @param revisionCount the revision the caller read
   * @param user the acting administrator
   * @return the deactivated mill
   * @throws MillMaintenanceException 404 unknown, 409 when active assignments remain
   * @throws StaleRevisionException 409 when another administrator wrote the row first
   */
  @Transactional
  public Outcome deactivate(long millId, int revisionCount, String user) {
    requireTrackedMill(millId);
    if (repository.hasActiveAssignment(millId)) {
      throw MillMaintenanceException.hasActiveUsers();
    }

    applyStatus(millId, AdminMill.CLOSED, revisionCount, user);

    log.info("Mill {} deactivated by {}", millId, user);
    return new Outcome(requireTrackedMill(millId), MSG_DEACTIVATED);
  }

  /**
   * Save the head-office indicator and the two contact selections (S01, BR-09).
   *
   * <p>Both contacts are checked against the mill's own client location. Legacy stated that rule
   * and enforced it nowhere on the server: it resolved the posted id against every contact in the
   * database, so a contact belonging to another client persisted silently. The check is added here
   * because the backend is authoritative for business rules; a null selection still clears the
   * column, exactly as before.
   *
   * @param millId the mill id
   * @param headOfficeContactInd the indicator
   * @param headOfficeContactId the head-office contact, or null to clear
   * @param divisionContactId the division contact, or null to clear
   * @param revisionCount the revision the caller read
   * @param user the acting administrator
   * @return the saved mill
   * @throws MillMaintenanceException 404 unknown, 409 when a contact is not selectable for the mill
   * @throws StaleRevisionException 409 when another administrator wrote the row first
   */
  @Transactional
  public Outcome saveContacts(
      long millId,
      String headOfficeContactInd,
      Long headOfficeContactId,
      Long divisionContactId,
      int revisionCount,
      String user) {
    requireTrackedMill(millId);
    requireSelectableContact(millId, headOfficeContactId);
    requireSelectableContact(millId, divisionContactId);

    int updated =
        repository.updateContacts(
            millId,
            headOfficeContactInd,
            headOfficeContactId,
            divisionContactId,
            revisionCount,
            user);
    if (updated == 0) {
      throw new StaleRevisionException();
    }

    log.info("Mill {} head-office and contact details saved by {}", millId, user);
    return new Outcome(requireTrackedMill(millId), MSG_SAVED);
  }

  private void applyStatus(long millId, String statusCode, int revisionCount, String user) {
    if (repository.updateStatusCode(millId, statusCode, revisionCount, user) == 0) {
      throw new StaleRevisionException();
    }
  }

  private void requireSelectableContact(long millId, Long contactId) {
    if (contactId != null && !repository.contactBelongsToMill(millId, contactId)) {
      throw MillMaintenanceException.contactNotSelectable();
    }
  }

  private AdminMill requireTrackedMill(long millId) {
    return repository
        .findById(millId)
        .map(MillMaintenanceService::toAdminMill)
        .orElseThrow(MillMaintenanceException::millNotFound);
  }

  private int requireCurrentReportingYear() {
    Integer year = reportingYears.currentReportingYear();
    if (year == null) {
      throw MillMaintenanceException.noReportingYear();
    }
    return year;
  }

  /**
   * Read the mill number a user typed. Legacy bound this field to an Integer and let the framework
   * refuse a non-numeric postback before the search ever ran; the refusal carries that converter's
   * own message, whose text the legacy bundle overrode.
   *
   * <p>Parsed here rather than bound as a numeric request parameter so the refusal is this message
   * and not the framework's generic type-mismatch response.
   */
  private static Long parseMillNumber(String millNumber) {
    String trimmed = StringUtils.trimToNull(millNumber);
    if (trimmed == null) {
      return null;
    }
    try {
      return Long.valueOf(trimmed);
    } catch (NumberFormatException notNumeric) {
      throw MillMaintenanceException.millNumberNotNumeric("Number", trimmed);
    }
  }

  /**
   * Read the status criterion. Legacy's screen offered a fixed dropdown (blank, Active, Close), so
   * an unknown code was unreachable; over the open wire it is a malformed request, refused rather
   * than silently matching nothing — a zero-match 200 would advise "try importing the mill" for
   * what is actually a caller bug.
   */
  private static String parseStatusCode(String statusCode) {
    String trimmed = StringUtils.trimToNull(statusCode);
    if (trimmed != null && !AdminMill.ACTIVE.equals(trimmed) && !AdminMill.CLOSED.equals(trimmed)) {
      throw MillMaintenanceException.statusNotSearchable(trimmed);
    }
    return trimmed;
  }

  /**
   * Escape Oracle {@code LIKE} metacharacters so the name fragment matches literally. Legacy passed
   * the raw fragment into a LIKE, where a typed {@code %} silently became a wildcard. Pairs with
   * the {@code ESCAPE '\'} clauses in the repository.
   */
  private static String escapeLike(String term) {
    return term == null ? null : term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static AdminMill toAdminMill(AdminMillEntity e) {
    return new AdminMill(
        e.millId(),
        e.millNumber(),
        e.millName(),
        e.statusCode(),
        e.statusDescription(),
        e.headOfficeContactInd(),
        e.headOfficeContactId(),
        e.divisionContactId(),
        e.revisionCount());
  }

  /**
   * A completed write and the key of the message that names it.
   *
   * @param mill the mill after the write
   * @param messageKey the legacy message key, or null when legacy showed no sentence (import)
   */
  public record Outcome(AdminMill mill, String messageKey) {}
}
