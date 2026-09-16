package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The submit gate refused: at least one of the eleven Schedules 1&ndash;10 validations is not met
 * (UC-CHK-002 S03, BR-02). Maps to HTTP 409 with the verbatim legacy {@code
 * reportNotSubmittedErrorMsg} &mdash; the text {@code CheckStatusMB.submitReport():294} showed when
 * its eleven-term {@code &&} was false. Nothing has been written when this is raised: the gate runs
 * inside the write transaction, after the status row is locked and before any UPDATE.
 */
public class ReportNotSubmittedException extends BusinessException {

  public ReportNotSubmittedException() {
    super(HttpStatus.CONFLICT, "reportNotSubmittedErrorMsg");
  }
}
