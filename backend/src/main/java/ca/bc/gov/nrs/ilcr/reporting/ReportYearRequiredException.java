package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No usable report year reached the Mill Information endpoint — absent, blank or non-numeric.
 *
 * <p>Legacy enforced this in the JSF Process Validations phase, before its bean method ever ran, so
 * the year is the one input the report cannot proceed without. (Legacy's own server-side guard was
 * inert: {@code (reportYear != null || reportYear != 0)} is always true, leaving only the
 * non-empty-mills check. That is a defect, not a contract, and is not reproduced.)
 */
public class ReportYearRequiredException extends BusinessException {

  public ReportYearRequiredException() {
    super(HttpStatus.BAD_REQUEST, "reportYearRequiredErrorMsg");
  }
}
