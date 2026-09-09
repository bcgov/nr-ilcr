package ca.bc.gov.nrs.ilcr.millmaintenance;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The refusals the mill administration surface can answer with, each carrying its legacy message
 * key. Static factories rather than a class per case: every one of these is a single message and a
 * single status, and naming them here keeps the whole refusal surface readable in one place.
 */
public class MillMaintenanceException extends BusinessException {

  private MillMaintenanceException(HttpStatus status, String messageKey) {
    super(status, messageKey);
  }

  private MillMaintenanceException(HttpStatus status, String messageKey, Object[] args) {
    super(status, messageKey, args);
  }

  /**
   * No ministry mill with this id. Distinct from {@link #alreadyTracked()}: this is an unknown
   * mill, not one that has already been imported.
   */
  public static MillMaintenanceException millNotFound() {
    return new MillMaintenanceException(HttpStatus.NOT_FOUND, "mill.not.found.by.id");
  }

  /**
   * The mill already has an ILCR cross-reference, so it is not importable (BR-04). Legacy could not
   * reach this: its import list only ever offered untracked mills, and nothing re-checked on
   * submit, so a stale list would have produced a primary-key violation surfaced as the generic
   * import failure.
   */
  public static MillMaintenanceException alreadyTracked() {
    return new MillMaintenanceException(HttpStatus.CONFLICT, "mill.already.tracked");
  }

  /** The import transaction failed and rolled back; no cross-reference exists (S14). */
  public static MillMaintenanceException importFailed() {
    return new MillMaintenanceException(HttpStatus.INTERNAL_SERVER_ERROR, "failImportingMillMsg");
  }

  /**
   * Activation found the current year's report records in a partial state — the status row absent
   * but per-category rows present — so re-creating the set collided and rolled back. Legacy reached
   * the same state and surfaced it as its generic unhandled 500; naming it as a conflict is the D7
   * ruling (a business error, not a 500) and the AD-8 house rule.
   */
  public static MillMaintenanceException partialReportRecords() {
    return new MillMaintenanceException(HttpStatus.CONFLICT, "error.mill.activate.partialrecords");
  }

  /**
   * The status criterion of a search is not a known mill status. Legacy's fixed dropdown made an
   * unknown code unreachable; over the open wire it is a malformed request, refused rather than
   * silently matching nothing and advising an import.
   */
  public static MillMaintenanceException statusNotSearchable(String value) {
    return new MillMaintenanceException(
        HttpStatus.BAD_REQUEST, "error.mill.search.status.unknown", new Object[] {value});
  }

  /** Deactivation refused because the mill still has active user assignments (BR-01/S12). */
  public static MillMaintenanceException hasActiveUsers() {
    return new MillMaintenanceException(
        HttpStatus.CONFLICT, "error.mill.deactivate.hasactiveusers");
  }

  /**
   * A posted contact does not belong to the mill's client location (BR-09). Not a 400: the id is
   * well-formed and the contact exists, it just is not selectable for this mill, and the screen's
   * own option list would never have offered it.
   */
  public static MillMaintenanceException contactNotSelectable() {
    return new MillMaintenanceException(HttpStatus.CONFLICT, "error.mill.contact.notselectable");
  }

  /**
   * No reporting year has been opened, so there is no current year to create report records for.
   * Legacy's behaviour here was undefined — it read the current year and used it unguarded, which
   * would have failed somewhere inside the transaction as the generic unhandled error.
   */
  public static MillMaintenanceException noReportingYear() {
    return new MillMaintenanceException(HttpStatus.CONFLICT, "reportingPeriodNotFoundMsg");
  }

  /**
   * The mill number in a search was not a whole number. Legacy bound the field to an Integer and
   * let the framework's converter refuse the postback; that converter's message was overridden in
   * the legacy bundle, and this carries the same text with the same label argument.
   */
  public static MillMaintenanceException millNumberNotNumeric(String label, String value) {
    return new MillMaintenanceException(
        HttpStatus.BAD_REQUEST,
        "javax.faces.converter.IntegerConverter.INTEGER",
        new Object[] {value, "9999", label});
  }
}
