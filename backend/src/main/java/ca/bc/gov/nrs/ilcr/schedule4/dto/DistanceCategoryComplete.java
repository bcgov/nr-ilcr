package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level constraint enforcing BR-04 (bidirectional required) on a {@link CategoryInput} for the
 * 3 distance-based codes (47 Truck Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul), transcribed from
 * legacy {@code schedule4NewLocation.xhtml} {@code required} expressions (Story 4.2, S22/S23):
 *
 * <ul>
 *   <li>a Distance entered obliges both Volume and Cost (distance ⇒ volume + cost), and</li>
 *   <li>a Volume or Cost entered obliges a Distance (volume|cost ⇒ distance).</li>
 * </ul>
 *
 * <p>Non-distance (fixed) codes are unaffected. Each violation carries the verbatim
 * {@code missingRequiredFieldMsg} ("Value Required") on the specific missing field (AD-8), so the
 * 400 ProblemDetail names {@code volume}/{@code cost}/{@code distance} like the legacy per-field
 * "Value Required".
 */
@Documented
@Constraint(validatedBy = DistanceCategoryCompleteValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistanceCategoryComplete {

  String message() default "{missingRequiredFieldMsg}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
