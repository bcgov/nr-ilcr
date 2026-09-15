package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

import java.util.List;

/**
 * A mill search result set, carrying the zero-match message rather than an error status.
 *
 * <p>A search that matches nothing is not a failed request: the screen keeps its criteria and lets
 * the administrator try again (S11), so the message rides a 200 beside an empty list. {@code
 * messageKey} and {@code message} are null whenever results were found, and Jackson omits them.
 *
 * @param results the matching mills, ordered by mill number
 * @param messageKey the zero-match message key, or null
 * @param message the resolved zero-match text, or null
 */
public record MillSearchResponse(List<AdminMill> results, String messageKey, String message) {

  /** A result set with matches and no message. */
  public static MillSearchResponse of(List<AdminMill> results) {
    return new MillSearchResponse(results, null, null);
  }
}
