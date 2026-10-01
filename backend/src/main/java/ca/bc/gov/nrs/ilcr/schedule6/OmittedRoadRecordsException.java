package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a whole-document {@code PUT} omits a served (non-placeholder) road record. Legacy's
 * Save posted its entire in-memory record list ({@code Schedule6DAO.saveSchedule} :236-346), so a
 * partial payload could never arise there; silently skipping an absent row here would discard the
 * user's data behind a success message — the same house guard Schedules 5 and 7.4 pinned for their
 * own whole-document saves. A general-comment placeholder is never "served" (excluded from {@code
 * roadRecords[]} on the read side, same as {@link RoadRecordNotFoundException}'s guard), so its
 * absence from {@code records} never trips this — otherwise the lone-comment state would become
 * unsavable.
 *
 * <p>Maps to 400 {@code invalidCodeValueErrorMsg}'s sibling key, following the shape of {@link
 * InvalidClassificationCodeException} in this same package (a house 400 with no legacy literal).
 */
public class OmittedRoadRecordsException extends BusinessException {

  public OmittedRoadRecordsException() {
    super(HttpStatus.BAD_REQUEST, "omittedRoadRecordsErrorMsg");
  }
}
