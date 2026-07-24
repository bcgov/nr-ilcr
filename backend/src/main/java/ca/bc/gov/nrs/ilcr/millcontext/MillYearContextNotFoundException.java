package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The requested Home working context cannot be resolved: the mill is not a selectable mill (unknown
 * id, no status xref, or never enrolled in reporting — legacy {@code getMills()} parity) or the
 * reporting year is not opened (Story 1.2 AC6). Maps to HTTP 404 with bundle key
 * {@code millYearContextNotFoundErrorMsg} — a recorded deviation: no legacy text exists because the
 * server-controlled dropdowns made this path unreachable in the legacy UI.
 */
public class MillYearContextNotFoundException extends BusinessException {

  public MillYearContextNotFoundException() {
    super(HttpStatus.NOT_FOUND, "millYearContextNotFoundErrorMsg");
  }
}
