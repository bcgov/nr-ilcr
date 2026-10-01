package ca.bc.gov.nrs.ilcr.reporting;

import java.nio.file.Path;
import java.util.List;

/**
 * The name shape of one kind of spooled download — the {@code prefix} and {@code suffix} {@link
 * FileSpooler} hands to {@code createTempFile}, with the random middle the JDK supplies.
 *
 * <p>A declared type rather than two loose strings at each call site, because the shapes are the
 * ONLY thing that tells a stranded download apart from the other files in the spool directory. That
 * directory defaults to the Jasper virtualizer's swap directory ({@link FileSpooler}), which holds
 * live {@code .jrpxxx} swap files belonging to renders still in flight; {@link SpoolReaper} sweeps
 * by these shapes and so can never reach one. Declaring them in one place also means a third kind
 * of download cannot be added without appearing in {@link #ALL} — a new shape the reaper did not
 * know about would leak silently, and silently is the failure mode this whole class prevents.
 *
 * @param prefix the temp file name prefix
 * @param suffix the temp file name suffix, the extension
 */
public record SpoolShape(String prefix, String suffix) {

  /** A printed schedule set or mill information report. */
  public static final SpoolShape PDF_REPORT = new SpoolShape("ilcr-report-", ".pdf");

  /** The Data Extract CSV. Legacy's own spool prefix, less the leading slash. */
  public static final SpoolShape DATA_EXTRACT = new SpoolShape("dataExtract", ".csv");

  /** Every shape this application spools. What {@link SpoolReaper} is allowed to delete. */
  public static final List<SpoolShape> ALL = List.of(PDF_REPORT, DATA_EXTRACT);

  /**
   * Whether {@code file}'s NAME was made by this shape.
   *
   * <p>Name only — the path is not consulted, so a caller must already have established that the
   * file sits in the spool directory.
   *
   * @param file the candidate file
   * @return whether its file name both starts with {@link #prefix} and ends with {@link #suffix}
   */
  public boolean matches(Path file) {
    String name = file.getFileName().toString();
    return name.startsWith(prefix) && name.endsWith(suffix);
  }

  /**
   * Whether {@code file} was made by ANY declared shape.
   *
   * @param file the candidate file
   * @return whether some shape in {@link #ALL} matches it
   */
  public static boolean anyMatches(Path file) {
    return ALL.stream().anyMatch(shape -> shape.matches(file));
  }
}
