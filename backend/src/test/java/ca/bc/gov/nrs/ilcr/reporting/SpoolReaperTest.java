package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the stranded-spool sweeper.
 *
 * <p>The two properties worth pinning pull in opposite directions, and either one alone is easy to
 * satisfy wrongly: the sweep must delete a stranded download, and it must not delete anything else
 * in a directory it SHARES with the Jasper virtualizer's live swap files. A reaper that deleted
 * nothing would pass the second on its own; one that deleted everything older than the threshold
 * would pass the first.
 */
class SpoolReaperTest {

  @TempDir Path spoolDir;

  private static final Instant NOW = Instant.parse("2026-09-15T18:00:00Z");

  private SpoolReaper reaper(Duration maxAge, boolean enabled) {
    return new SpoolReaper(
        new FileSpooler(spoolDir.toString()), maxAge, enabled, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /** A file in the spool directory whose last write was {@code age} ago. */
  private Path aged(String name, Duration age) throws IOException {
    Path file = Files.createFile(spoolDir.resolve(name));
    Files.setLastModifiedTime(file, FileTime.from(NOW.minus(age)));
    return file;
  }

  @Test
  @DisplayName("reaps a stranded download of each declared shape, and only those")
  void sweep_reapsStrandedDownloads_andLeavesEverythingElse() throws IOException {
    Path oldPdf = aged("ilcr-report-123456.pdf", Duration.ofHours(3));
    Path oldCsv = aged("dataExtract987654.csv", Duration.ofHours(3));
    // A Jasper virtualizer swap file, on the SAME volume by default, belonging to a render that may
    // still be running — the reason the sweep matches names rather than ages alone.
    Path swap = aged("6d1f2c3b4a59.jrpxxx", Duration.ofHours(3));
    // Right age, wrong shape: our prefix but not our extension, and vice versa.
    Path wrongSuffix = aged("dataExtract987654.tmp", Duration.ofHours(3));
    Path wrongPrefix = aged("someone-elses987654.csv", Duration.ofHours(3));
    // Our shape, but young enough to be a download in flight.
    Path freshCsv = aged("dataExtract111111.csv", Duration.ofMinutes(10));

    reaper(Duration.ofHours(1), true).sweep();

    assertThat(oldPdf).doesNotExist();
    assertThat(oldCsv).doesNotExist();
    assertThat(swap).exists();
    assertThat(wrongSuffix).exists();
    assertThat(wrongPrefix).exists();
    assertThat(freshCsv).exists();
  }

  @Test
  @DisplayName("a file exactly at the threshold is left alone")
  void sweep_leavesAFileExactlyAtTheThreshold() throws IOException {
    // The comparison is strictly-before, so the boundary itself survives. Which way the boundary
    // falls barely matters in production; that it is DECIDED does, since the alternative is a file
    // that is reaped or not depending on clock granularity.
    Path boundary = aged("dataExtract222222.csv", Duration.ofHours(1));

    reaper(Duration.ofHours(1), true).sweep();

    assertThat(boundary).exists();
  }

  @Test
  @DisplayName("disabled sweeps nothing")
  void sweep_disabled_reapsNothing() throws IOException {
    Path oldCsv = aged("dataExtract333333.csv", Duration.ofDays(5));

    reaper(Duration.ofHours(1), false).sweep();

    assertThat(oldCsv).exists();
  }

  @Test
  @DisplayName("a spool directory that does not exist yet is not an error")
  void sweep_missingDirectory_isSilent() {
    SpoolReaper reaper =
        new SpoolReaper(
            new FileSpooler(spoolDir.resolve("not-created-yet").toString()),
            Duration.ofHours(1),
            true,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // Before the first download the spooler has not created the directory. Sweeping must be a
    // no-op, not a warning every fifteen minutes on a freshly deployed pod.
    reaper.sweep();
  }

  @Test
  @DisplayName("a subdirectory is never descended into, whatever it is named")
  void sweep_doesNotDescend() throws IOException {
    // The sweep is non-recursive by construction (Files.list, not walk). Named like a spool file so
    // that a regular-file check is what saves it, not the shape match.
    Path directory = Files.createDirectory(spoolDir.resolve("dataExtract444444.csv"));
    Files.setLastModifiedTime(directory, FileTime.from(NOW.minus(Duration.ofDays(5))));
    Path nested = aged("dataExtract444444.csv/dataExtract555555.csv", Duration.ofDays(5));

    reaper(Duration.ofHours(1), true).sweep();

    assertThat(directory).exists();
    assertThat(nested).exists();
  }

  @Test
  @DisplayName("a real spooled file survives its own response window and is reaped once stranded")
  void sweep_reapsAFileTheSpoolerActuallyMade() throws IOException {
    // Built by the spooler rather than by hand, so the shapes in SpoolShape are checked against the
    // names the spooler really produces — a constant that drifted from its call site would leak
    // silently, and a hand-written name could not catch it.
    FileSpooler spooler = new FileSpooler(spoolDir.toString());
    SpooledFile spooled = spooler.spool(SpoolShape.DATA_EXTRACT, out -> out.write(1));

    // Still in its response window: nothing is reaped.
    new SpoolReaper(spooler, Duration.ofHours(1), true, Clock.fixed(NOW, ZoneOffset.UTC)).sweep();
    assertThat(spooled.size()).isEqualTo(1);
    assertThat(spoolFileCount()).isEqualTo(1);

    // A day later, with no response having ever claimed it.
    new SpoolReaper(
            spooler,
            Duration.ofHours(1),
            true,
            Clock.fixed(Instant.now().plus(Duration.ofDays(1)), ZoneOffset.UTC))
        .sweep();
    assertThat(spoolFileCount()).isZero();
  }

  /** How many entries the spool directory holds, with the directory handle closed. */
  private long spoolFileCount() throws IOException {
    try (var entries = Files.list(spoolDir)) {
      return entries.count();
    }
  }
}
