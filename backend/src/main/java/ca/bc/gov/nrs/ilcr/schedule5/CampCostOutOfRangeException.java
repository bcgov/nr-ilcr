package ca.bc.gov.nrs.ilcr.schedule5;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a category cost falls outside the range LEGACY enforced for that specific category
 * (BR-05/BR-07, FLD-002). Maps to 400 with the verbatim text of whichever key the caller passes, so
 * the response is indistinguishable from the Bean Validation route that handles the widest bound.
 *
 * <p><strong>Why this exists rather than a third annotation.</strong> The eleven cost inputs do not
 * share one range. Eight carry {@code costSize="7"} → &plusmn;9,999,999 ({@code
 * costSize7ValidatorErrorMsg}); {@code recoveries} carries {@code costSize="0"} → 0–9,999,999
 * ({@code costValidatorSchedule9ErrorMsg}, deviation (G) — the legacy MESSAGE's range wins over the
 * wider {@code NUMBER(8,0)} column); and {@code wagesAndBenefits} carries NO {@code costSize}
 * attribute in EITHER page ({@code schedule5ExistingCamp.xhtml:160-162}, {@code
 * schedule5NewCamp.xhtml:99-101}), so it falls to {@code ILCRCostValidator}'s default {@code "8"} →
 * &plusmn;99,999,999 ({@code costValidatorErrorMsg}, deviation (F), an Open Question for the
 * Ministry). {@link ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryEntry} is ONE record type reused for
 * all twelve categories, and a Bean Validation constraint cannot vary by which property holds the
 * value — so the widest bound is declarative there and the two narrower ones are checked here,
 * where the category is known.
 *
 * <p>The key is a caller-supplied constant, never client input: the only call sites are {@code
 * Schedule5Service}'s two range checks.
 */
public class CampCostOutOfRangeException extends BusinessException {

  public CampCostOutOfRangeException(String messageKey) {
    super(HttpStatus.BAD_REQUEST, messageKey);
  }
}
