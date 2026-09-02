package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * The reporting year is open, but the requested mill has no report-status row for it, so there is
 * no section to render.
 *
 * <p>The single-mill counterpart of {@link MillInformationNoMillsException}, and distinct from it
 * because the two say different things: that one means the YEAR is empty, this one means the year
 * holds mills and this particular one is not among them. Answering "No mill has a report status for
 * the selected Report Year" for a drill-down would be false on its face — the administrator is
 * looking at a table of the year's mills while reading it.
 *
 * <p>Also distinct from {@link MillInformationReportException} for the same reason that one is:
 * reaching here means an opened year genuinely has no row for this mill — a data condition, not a
 * fault — so it must not carry the catch-all {@code undefinedError} or raise the 5xx rate.
 *
 * <p><b>Normally unreachable from the UI</b>, and deliberately still implemented. The mill id the
 * frontend sends comes from a row of the very table this year's read produced, so a mill that is on
 * screen has a row by construction. What makes this reachable is a stale table (the year's data
 * changed since Apply) or a hand-built request — and legacy's answer to both was worse than a 404:
 * its drill-down passed the already-loaded row object straight to the renderer and never re-read
 * the mill, so the question could not arise there and no message exists to port. A deliberate
 * deviation (AD-8), on the same reasoning as {@link ReportYearNotOpenException}.
 */
public class MillInformationMillNotFoundException extends BusinessException {

  public MillInformationMillNotFoundException() {
    super(HttpStatus.NOT_FOUND, "millInformationMillNotFoundErrorMsg");
  }
}
