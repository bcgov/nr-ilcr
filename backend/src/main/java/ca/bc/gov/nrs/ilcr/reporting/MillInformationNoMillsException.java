package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The reporting year is open, but no mill has a report status for it, so there is nothing to
 * render.
 *
 * <p>Distinct from {@link MillInformationReportException} on purpose. By the time this is raised
 * the caller has already rejected any year that is not an open period, so reaching here means a
 * year was opened and no mill was initialised against it — a data condition, not a fault. Answering
 * the catch-all {@code undefinedError} would tell an administrator the system is broken and put an
 * ERROR line in the log for something nobody needs to fix in code.
 *
 * <p>Legacy had no message here at all: its {@code !mills.isEmpty()} guard produced no file and
 * said nothing (UC-MRPT-003 EF1). That silence is a gap worth improving on rather than reproducing
 * — the same argument {@link ReportYearNotOpenException} already makes one step earlier. Recorded
 * as a deliberate deviation (AD-8).
 */
public class MillInformationNoMillsException extends BusinessException {

  public MillInformationNoMillsException() {
    super(HttpStatus.NOT_FOUND, "millInformationNoMillsErrorMsg");
  }
}
