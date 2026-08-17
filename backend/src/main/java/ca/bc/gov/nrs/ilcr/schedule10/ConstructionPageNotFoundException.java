package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a Schedule 10 page write targets a {@code ROAD_CONSTRUCTION_REPRT_ID} that is not a
 * category-{@code '10'} page under the caller's mill and year — an unknown id, or an IDOR attempt
 * against another mill's page. Maps to 404.
 *
 * <p>Distinguishing this from a stale optimistic-lock token matters: a zero-row UPDATE means either
 * the id is absent or the revision is stale, and answering 409 for a missing id would tell the user
 * to reload when there is nothing to reload.
 */
public class ConstructionPageNotFoundException extends BusinessException {

  public ConstructionPageNotFoundException() {
    super(HttpStatus.NOT_FOUND, "constructionPageNotFoundErrorMsg");
  }
}
