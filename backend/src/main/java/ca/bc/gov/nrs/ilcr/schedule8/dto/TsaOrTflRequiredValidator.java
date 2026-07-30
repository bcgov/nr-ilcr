package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces {@link TsaOrTflRequired} (Story 14.2): a page must carry a TSA-or-TFL context — at least
 * one of {@code tsaNumber} / {@code tflNumber} non-blank. The violation is bound to {@code tsaNumber}
 * so the 400 ProblemDetail names the field, with the standard {@code Value Required} message.
 */
public class TsaOrTflRequiredValidator
    implements ConstraintValidator<TsaOrTflRequired, Schedule8PageRequest> {

  @Override
  public boolean isValid(Schedule8PageRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true; // @NotNull on the body handles a null request
    }
    if (isNotBlank(request.tsaNumber()) || isNotBlank(request.tflNumber())) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate("{missingRequiredFieldMsg}")
        .addPropertyNode("tsaNumber")
        .addConstraintViolation();
    return false;
  }

  private static boolean isNotBlank(String value) {
    return value != null && !value.isBlank();
  }
}
