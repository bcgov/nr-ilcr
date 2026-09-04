package ca.bc.gov.nrs.ilcr.reporting;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * A COMPLETE PDF on disk, ready to be sent as the response body: the whole export has already
 * succeeded by the time this exists.
 *
 * <p>This type is the pivot that makes an export failure a normal error instead of a corrupt
 * download. {@link RenderedReport} exported straight to the servlet output stream, so the 200 and
 * the {@code application/pdf} headers were committed before the exporter had produced a single
 * byte; anything that went wrong from that point on — a {@code JRException} deep in the export, the
 * async request timing out — could no longer change the status code, and the browser saved whatever
 * prefix had arrived. {@link PdfSpooler} moves that export in front of the response instead, and
 * what reaches the controller is this: a file whose length is known.
 *
 * <p>A known length is the second half of the guarantee, and the reason spooling beats simply
 * catching more exceptions. The response carries a real {@code Content-Length}, so the body is
 * length-delimited rather than chunked, and a transfer that stops early is a protocol-level short
 * read: the browser fails the request and {@code fetch}/XHR reject, rather than handing the caller
 * a short body that looks like a success. Every failed export is therefore distinguishable from a
 * successful one — before the response commits it is a {@code problem+json} error, and after it
 * commits it is a failed request. Neither outcome saves a file.
 *
 * <p>Spooling to a file rather than a {@code byte[]} keeps Story 29.2's constraint intact: the PDF
 * is never accumulated on the JVM heap, so a big "all schedules" print or several concurrent prints
 * cost temp-file space (the same disk budget the fill's virtualizer already uses) instead of heap.
 *
 * <p>{@link #close()} deletes the file. The streaming caller closes it in try-with-resources, so it
 * runs on the success path, on an IO failure mid-send, and on a client disconnect alike.
 */
class ExportedPdf implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(ExportedPdf.class);

  private final Path file;
  private final long size;

  ExportedPdf(Path file, long size) {
    this.file = file;
    this.size = size;
  }

  /**
   * The exported PDF's exact length in bytes, for the response's {@code Content-Length}.
   *
   * <p>Measured from the finished file, never predicted: it is only meaningful because the export
   * has already completed, which is the whole point of spooling first.
   */
  long size() {
    return size;
  }

  /**
   * Copy the finished PDF to {@code out}. Used by tests and by any caller that owns the stream; the
   * response itself goes out through {@link #asResource()}.
   *
   * <p>{@link Files#copy} streams through a small buffer and does not close {@code out}.
   */
  void writeTo(OutputStream out) throws IOException {
    Files.copy(file, out);
  }

  /**
   * The finished PDF as a response body, deleting itself once the response has been written.
   *
   * <p>A {@link Resource} rather than a {@code StreamingResponseBody}, and that is a deliberate
   * reversal of how these endpoints used to answer. Streaming existed to keep the EXPORT off the
   * heap while it ran; now the export is over before the response exists, and what is left is a
   * finished file. Spring's resource converter serves that from disk in a small buffer — the heap
   * stays flat either way — and writes it on the REQUEST thread.
   *
   * <p>The request thread is the point. Writing from the async executor while the container thread
   * still held the response is what made the declared {@code Content-Length} dangerous: Spring
   * Security's {@code OnCommittedResponseWrapper} fires its response-committed callback the moment
   * the written byte count reaches that length, and that callback adds security headers — from the
   * async thread, into a header map the other thread was reading. Intermittent {@code
   * ConcurrentModificationException}, and only ever on the endpoints that declared a length. One
   * thread writing means the callback fires on that same thread, in order, and the race cannot
   * exist. Content-Length keeps its meaning; nothing about the failure contract changes.
   *
   * <p>Deletion rides on the stream's {@code close()}, which the converter always calls — on a
   * completed write and on a client disconnect alike. A response that never opens the stream at all
   * leaves the file for the spool directory's own reaping; {@link #close()} stays available for the
   * paths that never reach a response.
   */
  Resource asResource() {
    return new FileSystemResource(file) {
      @Override
      public InputStream getInputStream() throws IOException {
        InputStream in = super.getInputStream();
        return new FilterInputStream(in) {
          @Override
          public void close() throws IOException {
            try {
              super.close();
            } finally {
              ExportedPdf.this.close();
            }
          }
        };
      }
    };
  }

  /**
   * Delete the spooled file.
   *
   * <p>Safe to call more than once — {@code deleteIfExists} is idempotent — because the resource's
   * stream close and an explicit failure-path close can both reach it.
   *
   * <p>A failure to delete is logged and swallowed rather than thrown: by the time this runs the
   * response has either been sent in full or already failed, and turning a leftover temp file into
   * an exception would replace a successful download with an error, or mask the real cause of a
   * failed one. The leak is a disk-space concern for ops, which is what the log line is for.
   */
  @Override
  public void close() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException | UncheckedIOException e) {
      log.warn("Could not delete the spooled report file {} — it will need reaping", file, e);
    }
  }
}
