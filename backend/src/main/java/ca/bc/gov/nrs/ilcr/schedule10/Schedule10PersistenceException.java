package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 10 write fails at the persistence layer, naming the resource that failed.
 *
 * <p>Maps to 500, exactly as {@code ScheduleNotSavedException} does, but carries one of the four
 * resource-specific legacy keys instead of the generic {@code scheduleNotSavedErrorMsg}: §
 * Validation rules directs the write path to use them "where the failing resource is known", which
 * is what resolves the UC's otherwise-unattached ERR-004. Those four keys were declared but
 * unreferenced until code review 2026-08-18, so every failure — page insert, detail insert, cost
 * upsert, cascade delete — flattened into one message the reporter could not act on.
 *
 * <p>The {@code @Transactional} write boundary rolls back before this surfaces.
 */
public class Schedule10PersistenceException extends BusinessException {

  /** The page could not be saved. */
  public static final String PAGE_NOT_SAVED = "roadConstructionReportNotSavedErrorMsg";

  /** The road detail could not be saved. */
  public static final String DETAIL_NOT_SAVED = "roadConstructionReportDetailNotSavedErrorMsg";

  /** The page could not be deleted. */
  public static final String PAGE_NOT_DELETED = "roadConstructionReportNotDeletedErrorMsg";

  /** The road detail could not be deleted. */
  public static final String DETAIL_NOT_DELETED = "roadConstructionReportDetailNotDeletedErrorMsg";

  /**
   * Wraps a persistence failure against a named resource.
   *
   * @param messageKey one of the four constants on this class
   */
  public Schedule10PersistenceException(String messageKey) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, messageKey);
  }
}
