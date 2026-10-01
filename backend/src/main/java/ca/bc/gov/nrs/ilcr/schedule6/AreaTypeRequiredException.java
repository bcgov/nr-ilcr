package ca.bc.gov.nrs.ilcr.schedule6;

import ca.bc.gov.nrs.ilcr.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a write reaches {@code Schedule6Service.classify} without an area type (FLD-001). The
 * HTTP path never gets here — {@code areaType} is {@code @NotBlank} on BOTH write DTOs ({@link
 * ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordRequest} and {@link
 * ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordEntry}), and both endpoints bind them {@code @Valid} —
 * but {@code classify} dereferences the value ({@code areaType.length()} on the TSA branch), so a
 * direct service caller that bypasses Bean Validation would turn a missing area type into an
 * NPE-driven 500. Exactly the {@link ca.bc.gov.nrs.ilcr.exception.RevisionCountRequiredException}
 * precedent: a validation constraint protects one entry point, not the method (code review
 * 2026-08-24, defence in depth).
 *
 * <p>Maps to 400 with the same {@code tsaOrTflRequiredErrorMsg} the {@code @NotBlank} path
 * produces, so the two routes are indistinguishable to a client. Blank-aware rather than null-only
 * so it matches {@code @NotBlank} exactly — an empty or whitespace area type would otherwise clear
 * the width guard below it and store an empty {@code TSA_NUMBER}.
 */
public class AreaTypeRequiredException extends BusinessException {

  public AreaTypeRequiredException() {
    super(HttpStatus.BAD_REQUEST, "tsaOrTflRequiredErrorMsg");
  }
}
