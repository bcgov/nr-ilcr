package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint enforcing BR-04 (all-or-nothing) on a {@link CategoryInput} for the 3
 * distance-based codes (47 Truck Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul) (Story 4.2,
 * S22/S23): once ANY of Distance, Volume or Cost is entered, all three are required, and every
 * missing one is reported at once.
 *
 * <p>A deliberate fix of legacy behaviour (Iman, 2026-09-29). Legacy's conditional {@code required}
 * expressions ({@code schedule4ExistingLocation.xhtml} / {@code schedule4NewLocation.xhtml}: a
 * Distance obliges Volume and Cost; a Volume or Cost obliges a Distance) were effectively
 * all-or-nothing but surfaced one missing field per attempt. The rebuild reports them all together.
 *
 * <p>Non-distance (fixed) codes are unaffected. Each violation carries the verbatim {@code
 * missingRequiredFieldMsg} ("Value Required") on the specific missing field (AD-8), so the 400
 * ProblemDetail names {@code volume}/{@code cost}/{@code distance} like the legacy per-field "Value
 * Required".
 */
@Documented
@Constraint(validatedBy = DistanceCategoryCompleteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistanceCategoryComplete {

  /**
   * The error message template.
   *
   * @return the error message template
   */
  String message() default "{missingRequiredFieldMsg}";

  /**
   * The groups the constraint belongs to.
   *
   * @return the groups
   */
  Class<?>[] groups() default {};

  /**
   * The payload associated to the constraint.
   *
   * @return the payload
   */
  Class<? extends Payload>[] payload() default {};
}
