package ca.bc.gov.nrs.ilcr.reporting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes spooled downloads that no response ever claimed.
 *
 * <p>A spool file's normal lifetime is one response: {@link SpooledFile#close()} deletes it, and
 * that close rides on the body stream, which Spring's resource converter always closes — on a
 * completed write and on a client disconnect alike. One path escapes it. If the request fails
 * BETWEEN the build finishing and the converter opening the stream — content negotiation, a client
 * that has already gone, a filter that throws on the way out — the stream is never opened, so it is
 * never closed, and the finished file stays on disk with nothing left holding a reference to it.
 * Nothing swept the directory before this class, so such a file lived until the pod was replaced.
 *
 * <p>Rare per request and unbounded over time, which is the combination worth a sweeper: the spool
 * directory defaults to the report virtualizer's swap volume, sized for concurrent renders and not
 * for an indefinite accumulation of whole PDFs and CSVs.
 *
 * <h2>What it will not touch</h2>
 *
 * <p>That shared volume is exactly why the sweep is not "everything older than an hour". It also
 * holds Jasper's virtualizer swap files, and those belong to renders that may still be running; a
 * render of a large schedule set legitimately outlives the threshold. So the sweep is restricted
 * twice over — to the spool directory itself, non-recursively, and within it to the file names
 * {@link SpoolShape#ALL} declares this application creates. A file the application did not spool is
 * not ours to delete, whatever its age.
 *
 * <p>Age is the second guard, and it is generous on purpose. It is measured from last modification,
 * which for a spool file is the moment its write finished, so the window starts when the file
 * became a candidate for a response rather than when its build began. An hour is far longer than
 * any download this application produces takes to hand over, so a file that old is stranded rather
 * than in flight — and a wrong deletion here would fail a download a user is actively waiting on,
 * which is a worse outcome than the leak.
 *
 * <p>Best-effort throughout: a file that vanishes mid-sweep (the converter's close winning the
 * race) and a file that cannot be deleted are both ordinary, and neither is worth an exception on a
 * background thread. Set {@code ilcr.reporting.spool-reaper.enabled} to false to stop sweeping.
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class SpoolReaper {

  private static final Logger log = LoggerFactory.getLogger(SpoolReaper.class);

  private final Path directory;
  private final Duration maxAge;
  private final boolean enabled;
  private final Clock clock;

  /**
   * Creates the reaper.
   *
   * <p>The enabled flag is a FIELD rather than a second {@code @ConditionalOnProperty}: the one on
   * the class already has to carry {@code ilcr.datasource.enabled}, which is what gates {@link
   * FileSpooler} itself, and a bean that required a spooler that was not there would fail the
   * context rather than simply not sweeping.
   *
   * @param spooler the spooler whose directory is swept — taken from the bean rather than the
   *     property so the two can never diverge
   * @param maxAge how long after its last write a spool file is considered stranded
   * @param enabled whether to sweep at all; the off switch for an environment that reaps its own
   *     scratch volume
   */
  @Autowired
  public SpoolReaper(
      FileSpooler spooler,
      @Value("${ilcr.reporting.spool-reaper.max-age:PT1H}") Duration maxAge,
      @Value("${ilcr.reporting.spool-reaper.enabled:true}") boolean enabled) {
    this(spooler, maxAge, enabled, Clock.system(ZoneId.of("America/Vancouver")));
  }

  SpoolReaper(FileSpooler spooler, Duration maxAge, boolean enabled, Clock clock) {
    this.directory = spooler.directory();
    this.maxAge = maxAge;
    this.enabled = enabled;
    this.clock = clock;
  }

  /**
   * Sweep the spool directory once.
   *
   * <p>{@code fixedDelay}, not {@code fixedRate}: the next sweep is spaced from the end of the last
   * one, so a slow directory listing cannot queue sweeps behind each other.
   */
  @Scheduled(
      initialDelayString = "${ilcr.reporting.spool-reaper.initial-delay:PT5M}",
      fixedDelayString = "${ilcr.reporting.spool-reaper.interval:PT15M}")
  public void sweep() {
    if (!enabled) {
      return;
    }
    if (!Files.isDirectory(directory)) {
      // Not an error: the spooler creates the directory on its first download, so before any
      // download has happened there is genuinely nothing to sweep.
      return;
    }
    Instant cutoff = clock.instant().minus(maxAge);
    int reaped = 0;
    // In try-with-resources because Files.list holds an open directory handle — the one thing here
    // that must not leak while cleaning up a leak. The names are collected before any deletion so
    // the walk never observes the directory it is mutating; they are paths, not contents, so the
    // memory is bounded by the very growth this sweep exists to stop.
    try (Stream<Path> entries = Files.list(directory)) {
      for (Path file : entries.toList()) {
        if (stranded(file, cutoff) && delete(file)) {
          reaped++;
        }
      }
    } catch (IOException e) {
      log.warn("Could not list the spool directory {} to reap stranded downloads", directory, e);
      return;
    }
    if (reaped > 0) {
      // Count only. A spool file name carries no mill, year or figure, but the count is what ops
      // needs and the names would only be noise at INFO.
      log.info("Reaped {} stranded spool file(s) older than {} from {}", reaped, maxAge, directory);
    }
  }

  /** Whether {@code file} is one of ours, is a regular file, and has gone quiet past the cutoff. */
  private boolean stranded(Path file, Instant cutoff) {
    try {
      return Files.isRegularFile(file)
          && SpoolShape.anyMatches(file)
          && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
    } catch (IOException e) {
      // Including the file having been deleted under us by the response that owned it.
      return false;
    }
  }

  private boolean delete(Path file) {
    try {
      return Files.deleteIfExists(file);
    } catch (IOException e) {
      log.warn("Could not reap the stranded spool file {}", file, e);
      return false;
    }
  }
}
