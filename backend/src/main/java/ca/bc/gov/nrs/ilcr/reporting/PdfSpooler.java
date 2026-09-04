package ca.bc.gov.nrs.ilcr.reporting;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Exports a filled report to a temp file BEFORE the response is built, so that a failure during
 * export is still an ordinary error rather than a corrupt download.
 *
 * <p>Every report endpoint used to hand its {@link RenderedReport} to a {@code
 * StreamingResponseBody} and export inside it. That put the one genuinely failure-prone step — the
 * Jasper export — on the far side of the response commit: the 200 and the {@code application/pdf}
 * headers were already written, so no {@code @ExceptionHandler} could turn a failure into {@code
 * problem+json}, and the client received a short body it had no reliable way to tell from a whole
 * one. Detecting that after the fact is not solvable at the client, because a truncated PDF can
 * still carry a plausible header and trailer.
 *
 * <p>So the export moves in front of the commit. {@link #spool} runs it to completion against a
 * temp file while the controller is still on the synchronous path, where a throw becomes a 500
 * {@code undefinedError} with no body written and no file offered to the user — the same outcome
 * the pre-fill guards already produced. What comes back is an {@link ExportedPdf} of known length,
 * which lets the response be {@code Content-Length}-delimited so even an interrupted TRANSFER is
 * rejected by the browser instead of saved. See {@link ExportedPdf} for that second half.
 *
 * <p>A file, not a {@code byte[]}: the PDF still never lands on the JVM heap (Story 29.2). It goes
 * to {@code ilcr.reporting.spool-directory}, which defaults to the virtualizer's swap directory so
 * that ops has ONE scratch volume to size and mount for reporting rather than two — the two are the
 * same kind of load and are held over the same window. Set it explicitly to split them.
 *
 * <p>The spooled file's lifetime is the response: {@link ExportedPdf#close()} deletes it, and the
 * streaming caller closes in try-with-resources. A spool that fails part-way deletes its own
 * partial file here, so nothing survives a failed export either.
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
class PdfSpooler {

  private static final Logger log = LoggerFactory.getLogger(PdfSpooler.class);

  private final Path directory;

  PdfSpooler(
      @Value("${ilcr.reporting.spool-directory:${ilcr.reporting.virtualizer.swap-directory:}}")
          String spoolDirectory) {
    this.directory =
        Paths.get(
            StringUtils.hasText(spoolDirectory)
                ? spoolDirectory
                : System.getProperty("java.io.tmpdir"));
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
  ExportedPdf spool(RenderedReport report) {
    // The report is the OUTERMOST resource, so it is closed even when the spool file cannot be
    // created. It owns the fill's swap file, and that is the one thing here whose leak survives the
    // request: a spool that is never opened leaves nothing behind, but an unclosed virtualizer
    // leaves a swap file on the same volume, and a directory that fails once tends to fail for
    // every request after it. Acquiring the file inside this block rather than before it is the
    // whole reason it is shaped this way.
    try (report) {
      Path file = createSpoolFile();
      try {
        // The stream closes BEFORE size() is read, so the export is flushed and the file complete.
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
          report.writeTo(out);
        }
        long size = Files.size(file);
        log.debug("Spooled {} bytes of exported PDF to {}", size, file);
        return new ExportedPdf(file, size);
      } catch (IOException e) {
        delete(file);
        throw new ReportGenerationException("Failed to spool the exported report to disk", e);
      } catch (RuntimeException e) {
        // Includes the ReportGenerationException writeTo raises for a JRException — the export
        // failure this whole class exists to keep in front of the response commit.
        delete(file);
        throw e;
      }
    }
  }

  /**
   * A fresh empty spool file, creating the spool directory if it is not there yet.
   *
   * <p>{@code createDirectories} is here rather than in the constructor deliberately. A configured
   * {@code ilcr.reporting.spool-directory} that does not exist at runtime — an unmounted volume, a
   * fresh container — would otherwise fail every report with a {@code NoSuchFileException}; but
   * doing it at construction would fail BEAN CREATION, taking the whole application down over a
   * directory only the report endpoints need. Per-request keeps the blast radius to reports, and it
   * is idempotent and one stat call when the directory already exists.
   */
  private Path createSpoolFile() {
    try {
      Files.createDirectories(directory);
      return Files.createTempFile(directory, "ilcr-report-", ".pdf");
    } catch (IOException e) {
      throw new ReportGenerationException(
          "Failed to create a spool file in " + directory + " for the exported report", e);
    }
  }

  /** Best-effort cleanup of a partial spool; the original failure is what the caller must see. */
  private static void delete(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete the partial spool file {} after a failed export", file, e);
    }
  }
}
