package ca.bc.gov.nrs.ilcr.reporting;

/**
 * The spool directory could not be used or the spooled write did not complete — an infrastructure
 * failure on the way to a file, never a business condition. Each consumer of {@link FileSpooler}
 * translates it into the error its own endpoint answers with (the PDF path's {@link
 * ReportGenerationException}, the CSV path's 500 business error), so this stays a plain unchecked
 * exception with no HTTP status of its own. The message names the stage only, never the data
 * (AD-11).
 */
public class SpoolFailedException extends RuntimeException {

  public SpoolFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
