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
 * Writes a generated download to a temp file BEFORE the response is built, so that a failure during
 * generation is still an ordinary error rather than a corrupt download.
 *
 * <p>The report endpoints used to hand their filled report to a {@code StreamingResponseBody} and
 * export inside it. That put the one genuinely failure-prone step on the far side of the response
 * commit: the 200 and the content headers were already written, so no {@code @ExceptionHandler}
 * could turn a failure into {@code problem+json}, and the client received a short body it had no
 * reliable way to tell from a whole one. Detecting that after the fact is not solvable at the
 * client, because a truncated file can still carry a plausible header and trailer.
 *
 * <p>So the write moves in front of the commit. {@link #spool} runs the caller's writer to
 * completion against a temp file while the controller is still on the synchronous path, where a
 * throw becomes an ordinary error with no body written and no file offered to the user. What comes
 * back is a {@link SpooledFile} of known length, which lets the response be {@code
 * Content-Length}-delimited so even an interrupted TRANSFER is rejected by the browser instead of
 * saved. See {@link SpooledFile} for that second half.
 *
 * <p>A file, not a {@code byte[]}: the body never lands on the JVM heap (Story 29.2). It goes to
 * {@code ilcr.reporting.spool-directory}, which defaults to the virtualizer's swap directory so
 * that ops has ONE scratch volume to size and mount for generated downloads rather than two — the
 * two are the same kind of load and are held over the same window. Set it explicitly to split them.
 *
 * <p>The spooled file's lifetime is the response: {@link SpooledFile#close()} deletes it, and the
 * streaming caller closes in try-with-resources. A spool that fails part-way deletes its own
 * partial file here, so nothing survives a failed generation either.
 *
 * <p>Format-agnostic on purpose. The PDF path ({@link PdfSpooler}) and the CSV data extract share
 * the same whole-file-or-no-file contract and the same directory; each supplies its own declared
 * {@link SpoolShape} and its own writer, and translates {@link SpoolFailedException} into the error
 * its endpoint answers with.
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class FileSpooler {

  private static final Logger log = LoggerFactory.getLogger(FileSpooler.class);

  /** Writes the complete body to the spool file's stream. */
  @FunctionalInterface
  public interface SpoolWriter {
    void writeTo(OutputStream out) throws IOException;
  }

  private final Path directory;

  /**
   * Creates the spooler.
   *
   * @param spoolDirectory the directory spool files are written to; blank falls back to the JVM
   *     temp directory
   */
  public FileSpooler(
      @Value("${ilcr.reporting.spool-directory:${ilcr.reporting.virtualizer.swap-directory:}}")
          String spoolDirectory) {
    this.directory =
        Paths.get(
            StringUtils.hasText(spoolDirectory)
                ? spoolDirectory
                : System.getProperty("java.io.tmpdir"));
  }

  /**
   * Run {@code writer} to completion against a fresh temp file and hand back the finished file.
   *
   * <p>Any failure — the writer, the write, the final flush on close — deletes the partial file and
   * throws {@link SpoolFailedException} (or rethrows the writer's own unchecked exception). Because
   * this runs before the response is committed, that throw reaches the global handler and the
   * caller gets an error, not a file.
   *
   * @param shape the declared name shape for this kind of download, which is also what {@link
   *     SpoolReaper} recognises it by
   * @param writer writes the whole body
   * @return the complete file on disk, whose {@code close()} deletes it
   */
  public SpooledFile spool(SpoolShape shape, SpoolWriter writer) {
    Path file = createSpoolFile(shape.prefix(), shape.suffix());
    try {
      // The stream closes BEFORE size() is read, so the write is flushed and the file complete.
      try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
        writer.writeTo(out);
      }
      long size = Files.size(file);
      log.debug("Spooled {} bytes to {}", size, file);
      return new SpooledFile(file, size);
    } catch (IOException e) {
      delete(file);
      throw new SpoolFailedException("Failed to spool the generated file to disk", e);
    } catch (RuntimeException | Error e) {
      // Includes whatever the writer raises for its own generation failure — the failure this whole
      // class exists to keep in front of the response commit — and an Error such as
      // OutOfMemoryError, which a wide extract can raise mid-write and which would otherwise leave
      // the partial file behind on the shared swap volume.
      delete(file);
      throw e;
    }
  }

  /**
   * A fresh empty spool file, creating the spool directory if it is not there yet.
   *
   * <p>{@code createDirectories} is here rather than in the constructor deliberately. A configured
   * {@code ilcr.reporting.spool-directory} that does not exist at runtime — an unmounted volume, a
   * fresh container — would otherwise fail every download with a {@code NoSuchFileException}; but
   * doing it at construction would fail BEAN CREATION, taking the whole application down over a
   * directory only the download endpoints need. Per-request keeps the blast radius to downloads,
   * and it is idempotent and one stat call when the directory already exists.
   */
  private Path createSpoolFile(String prefix, String suffix) {
    try {
      Files.createDirectories(directory);
      return Files.createTempFile(directory, prefix, suffix);
    } catch (IOException e) {
      throw new SpoolFailedException(
          "Failed to create a spool file in " + directory + " for the generated download", e);
    }
  }

  /**
   * The directory spool files are written to, for {@link SpoolReaper}.
   *
   * <p>Taken from the spooler rather than re-reading the property, so the sweeper and the writer
   * can never be pointed at two different directories.
   *
   * @return the resolved spool directory, which may not exist yet
   */
  Path directory() {
    return directory;
  }

  /** Best-effort cleanup of a partial spool; the original failure is what the caller must see. */
  private static void delete(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not delete the partial spool file {} after a failed write", file, e);
    }
  }
}
