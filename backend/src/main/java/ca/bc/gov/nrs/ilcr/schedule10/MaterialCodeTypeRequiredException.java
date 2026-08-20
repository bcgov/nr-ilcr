package ca.bc.gov.nrs.ilcr.schedule10;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when the additional-stabilizing material type is missing while the ballast method requires
 * it.
 *
 * <p>Legacy makes this field conditionally required — {@code pageDtlASType} carries {@code
 * required="#{...typeMandatory}"}, and {@code typeMandatory} is true only for ballast method {@code
 * "C"} ({@code RoadConstructionReportDetailType:1129}). Because the requiredness depends on a
 * sibling field, Bean Validation on a single record component cannot express it, so the check lives
 * in the service and arrives here.
 *
 * <p>Maps to 400 {@code materialCodeTypeRequiredErrorMsg} — the resolved form of legacy's JSF
 * required template with {@code {0}} filled from that component's {@code label="Material Code
 * Type"}. It replaces {@code invalidCodeValueErrorMsg} ({@code A valid value must be selected from
 * the list.}), used here until code review 2026-08-18: a pick-from-the-list message for a
 * missing-required condition.
 */
public class MaterialCodeTypeRequiredException extends BusinessException {

  public MaterialCodeTypeRequiredException() {
    super(HttpStatus.BAD_REQUEST, "materialCodeTypeRequiredErrorMsg");
  }
}
