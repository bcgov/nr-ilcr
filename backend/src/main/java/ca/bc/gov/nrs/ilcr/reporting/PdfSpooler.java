package ca.bc.gov.nrs.ilcr.reporting;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Exports a filled report to a temp file BEFORE the response is built, so that a failure during
 * export is still an ordinary error rather than a corrupt download.
 *
 * <p>The mechanics — the spool directory, the temp file, the delete-on-failure and the known-length
 * result — live in the format-agnostic {@link FileSpooler}; this class is the PDF-shaped entry that
 * consumes a {@link RenderedReport} and speaks the report path's own exception. See {@link
 * FileSpooler} and {@link SpooledFile} for why the export runs in front of the response commit at
 * all.
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
class PdfSpooler {

  private final FileSpooler spooler;

  PdfSpooler(FileSpooler spooler) {
    this.spooler = spooler;
  }

  /**
   * Export {@code report} to a temp file and hand back the finished PDF.
   *
   * <p>Consumes the report: it is closed here on every path, success or failure, which releases the
   * fill's virtualizer and its swap file as soon as the export is done rather than holding both for
   * the whole network transfer. Callers must not use the report afterwards.
   *
   * <p>Any failure — the export, the write, the final flush on close — deletes the partial file and
   * throws {@link ReportGenerationException}. Because this runs before the response is committed,
   * that throw reaches the global handler and the caller gets an error, not a PDF.
   *
   * @param report the filled report to export; closed by this call
   * @return the complete PDF on disk, whose {@code close()} deletes it
   */
  SpooledFile spool(RenderedReport report) {
    // The report is the OUTERMOST resource, so it is closed even when the spool file cannot be
    // created. It owns the fill's swap file, and that is the one thing here whose leak survives the
    // request: a spool that is never opened leaves nothing behind, but an unclosed virtualizer
    // leaves a swap file on the same volume, and a directory that fails once tends to fail for
    // every request after it. Acquiring the file inside this block rather than before it is the
    // whole reason it is shaped this way.
    try (report) {
      return spooler.spool("ilcr-report-", ".pdf", report::writeTo);
    } catch (SpoolFailedException e) {
      throw new ReportGenerationException(e.getMessage(), e);
    }
  }
}
