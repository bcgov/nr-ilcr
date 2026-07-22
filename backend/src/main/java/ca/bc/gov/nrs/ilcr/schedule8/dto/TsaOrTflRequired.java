package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint (Story 14.2, S19/FLD-006): a Tree-to-Truck page must supply a TSA-or-TFL
 * context — at least one of {@code tsaNumber} / {@code tflNumber} is required at Save. The violation
 * is bound to {@code tsaNumber} with the standard {@code Value Required} message.
 */
@Documented
@Constraint(validatedBy = TsaOrTflRequiredValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TsaOrTflRequired {

  String message() default "{missingRequiredFieldMsg}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
