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
 * <p><strong>Legacy's bug.</strong> On Truck Barge/Ferry, Crew Barge/Ferry and Rail Haul, a Volume
 * or Cost entered first revealed the other two required fields in steps — first the Distance, then,
 * once the Distance was in, the remaining amount — while a Distance (km) entered first showed both
 * at once (conditional {@code required=} at {@code schedule4ExistingLocation.xhtml:524,550,572};
 * Crew Barge/Ferry {@code :607,632,654}; Rail Haul {@code :881,905,925}).
 *
 * <p><strong>The decision.</strong> A deliberate fix of that legacy behaviour, by BA decision
 * (2026-09-29): once any one of Distance/Volume/Cost has a value, all three are required and every
 * missing one is reported at once, so Check Status (or Save) shows every required field of the row
 * in one go instead of making the user discover them in several steps.
 *
 * <p>The set of VALID rows is unchanged: both rules accept only an all-empty or an all-filled row.
 * Only which missing fields are reported, and when, changes.
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
