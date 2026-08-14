package ca.bc.gov.nrs.ilcr.reporting;

/**
 * A report could not be compiled, filled, or exported — an engine/infrastructure failure, not a
 * business condition (the empty-schedule case is a {@code ScheduleNotFoundException} 404 instead).
 * Unchecked so it propagates to the default 500 handling; the message names the stage only, never
 * the report data (AD-11).
 */
public class ReportGenerationException extends RuntimeException {

  public ReportGenerationException(String message, Throwable cause) {
    super(message, cause);
  }
}
