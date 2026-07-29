package ca.bc.gov.nrs.ilcr.millcontext;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No mill/reporting-year selection reached a schedule endpoint — the {@code millId}/{@code year}
 * params are missing, blank, or non-numeric (UC-SCH11-001 S11). Maps to HTTP 400 with legacy
 * message {@code millYearNotSelectedErrorMsg} (ERR-001, trailing space verbatim). Legacy shows this
 * one combined message when the session context is absent ({@code schedule11.xhtml:11–26}) — never
 * per-field texts, which is why this is distinct from {@code FieldValuesRequiredException}.
 */
public class MillYearNotSelectedException extends BusinessException {

  public MillYearNotSelectedException() {
    super(HttpStatus.BAD_REQUEST, "millYearNotSelectedErrorMsg");
  }
}
