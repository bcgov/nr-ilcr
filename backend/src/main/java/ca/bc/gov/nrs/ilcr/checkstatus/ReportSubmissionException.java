package ca.bc.gov.nrs.ilcr.checkstatus;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The transition could not be persisted after the guard and the gate had passed &mdash; a
 * repository {@code DataAccessException}, or a status or category UPDATE that affected no row
 * (UC-CHK-002 S06). Maps to HTTP 500 with the verbatim legacy {@code reportSubmissionErrorMsg}, the
 * text legacy showed when {@code SubmitReportDAO.submitReport()} rolled back on a {@code
 * HibernateException} ({@code :127-141}). The {@code @Transactional} boundary rolls everything back
 * before this surfaces, so the report is still in Draft and a retried request can succeed.
 */
public class ReportSubmissionException extends BusinessException {

  public ReportSubmissionException() {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "reportSubmissionErrorMsg");
  }
}
