package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No schedule context could be resolved — the mill/reporting-year is unknown, has no report-status
 * row, or the requested schedule summary does not exist (UC-SCH1-001 S21). Maps to HTTP 404 with
 * legacy message {@code scheduleNotFoundErrorMsg} (ERR-003).
 */
public class ScheduleNotFoundException extends BusinessException {

  public ScheduleNotFoundException() {
    super(HttpStatus.NOT_FOUND, "scheduleNotFoundErrorMsg");
  }
}
