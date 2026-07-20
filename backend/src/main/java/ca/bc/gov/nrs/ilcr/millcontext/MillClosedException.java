package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The mill is closed ({@code CLS}) for the reporting year — schedule not viewable (UC-SCH1-001 S20).
 * Maps to HTTP 409 with legacy message {@code millNotActiveForCurrentYearMsg} (ERR-002).
 */
public class MillClosedException extends BusinessException {

  public MillClosedException() {
    super(HttpStatus.CONFLICT, "millNotActiveForCurrentYearMsg");
  }
}
