package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a TFL-located page carries a TFL number the reference table does not hold, after the
 * missing-leading-zero aliases have been applied. Maps to 400 with legacy's verbatim validator
 * text.
 *
 * <p>The accept set and the Road-Group-derivable set are the SAME set, because a single table
 * answers both questions — {@code RoadGroup10Lookup.rg10ByTflNumberCode}. So a TFL that passes this
 * check always derives a Road Group, and the "unmapped TFL saves with a blank Road Group" state is
 * unreachable through a write. It exists only in stored data that predates or bypassed the screen.
 *
 * <p>An earlier version of this note put a number on that set — "the same 22 keys" — which was
 * wrong and then drifted again when {@code "52B"} was demoted. One table answering both questions
 * is the fact this class rests on, and it holds whatever the table's contents are; see {@link
 * RoadGroup10Lookup#canonicalTfl} for why the count is deliberately not quoted (code review
 * 2026-08-19).
 */
public class InvalidTflNumberException extends BusinessException {

  public InvalidTflNumberException() {
    super(HttpStatus.BAD_REQUEST, "tflNumberValidatorErrorMsg");
  }
}
