package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The Print Schedules selection is invalid (UC-PRT-001, ERR-002/003/004) — a 400 carrying one of the
 * three verbatim legacy selection messages, resolved from the backend bundle by {@code
 * GlobalExceptionHandler} (AD-8). The three cases are validated in the fixed legacy order
 * (first-match-wins) before any report is filled; the static factories name each so the controller
 * reads as the legacy {@code PrintSchedulesMB.print()} validation ladder.
 */
public class PrintSelectionException extends BusinessException {

  private PrintSelectionException(String messageKey) {
    super(HttpStatus.BAD_REQUEST, messageKey);
  }

  /** ERR-002: a content option is on but no schedule is selected. */
  public static PrintSelectionException noScheduleSelected() {
    return new PrintSelectionException("printScheduleNotSelectedMsg");
  }

  /** ERR-003: a schedule is selected but neither content print option is. */
  public static PrintSelectionException noContentOption() {
    return new PrintSelectionException("printOptionsErrorMsg");
  }

  /** ERR-004: no print option at all is selected. */
  public static PrintSelectionException noPrintOption() {
    return new PrintSelectionException("printOptionsRequiredErrorMsg");
  }
}
