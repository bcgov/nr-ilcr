package ca.bc.gov.nrs.ilcr.reporting;

import java.io.OutputStream;
import java.util.List;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;

/**
 * One filled report ready to export (Story 29.2): the filled sections plus the {@link
 * JRSwapFileVirtualizer} their fill spilled to. {@link #writeTo(OutputStream)} exports the sections
 * straight to a caller-supplied stream, so the PDF is never accumulated as a {@code byte[]} on the
 * heap; {@link #close()} disposes the virtualizer's swap file.
 *
 * <p><b>Not the response body.</b> {@link PdfSpooler} consumes this — exporting it to a temp file
 * and closing it — while the controller is still on the synchronous path, and the controller sends
 * the resulting {@link ExportedPdf} instead. That ordering is the point: exporting straight to the
 * servlet stream, as this used to, put the export's failures AFTER the response commit, where they
 * could only ever reach the user as a truncated PDF. So {@code writeTo} takes any stream and knows
 * nothing about responses.
 *
 * <p>AutoCloseable so its consumer cleans up on BOTH success and failure: a render that throws
 * mid-export must not leak a swap file. The fill that produced these sections has already happened
 * (and may have thrown the empty-schedule 404) BEFORE this holder exists, so virtualization stays a
 * pure memory concern and never moves a business outcome after the response is committed.
 */
class RenderedReport implements AutoCloseable {

  private final List<JasperPrint> sections;
  private final JRSwapFileVirtualizer virtualizer;

  RenderedReport(List<JasperPrint> sections, JRSwapFileVirtualizer virtualizer) {
    this.sections = sections;
    this.virtualizer = virtualizer;
  }

  /**
   * Export the filled sections to ONE PDF written directly to {@code out} (BR-08). Each section's
   * top-level bookmark is an in-template outline ANCHOR keyed to its {@code bookmarkTitle} fill
   * parameter, NOT JasperReports' batch-mode document bookmarks: the latter only emit a bookmark
   * when the export batch holds MORE THAN ONE JasperPrint (JRPdfExporter gates {@code
   * addBookmark(getName())} on {@code items.size() > 1}), so a single-schedule {@code /print} would
   * silently get an empty outline. The anchor renders one bookmark per section for a single-section
   * PDF just as for a combined one; the caller gates it by passing a null bookmark title (the
   * standalone Schedule 9 path) to suppress the anchor.
   *
   * <p>Streams rather than buffers: the exporter output wraps {@code out} directly, so a big "all
   * schedules" print never pins the whole PDF as a {@code byte[]} on the JVM heap. That still holds
   * under spooling, because {@code out} is then the spool file rather than the heap. {@link
   * SimpleOutputStreamExporterOutput} does not own {@code out} (it did not open it), so closing it
   * stays the caller's business.
   */
  void writeTo(OutputStream out) {
    JRPdfExporter exporter = new JRPdfExporter();
    exporter.setExporterInput(SimpleExporterInput.getInstance(sections));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    try {
      exporter.exportReport();
    } catch (JRException e) {
      throw new ReportGenerationException("Failed to export the combined report to PDF", e);
    }
  }

  /**
   * Dispose the virtualizer, deleting its swap file. {@link PdfSpooler#spool} wraps this in
   * try-with-resources, so it runs on both the success and error paths and a swap file is never
   * leaked — and it now runs as soon as the export finishes, rather than being held for the whole
   * network transfer. The virtualizer owns its swap file (swapOwner=true in {@link
   * ReportVirtualizerFactory}), so {@code cleanup()} removes the on-disk file, not just the
   * in-memory page cache.
   */
  @Override
  public void close() {
    if (virtualizer != null) {
      virtualizer.cleanup();
    }
  }
}
