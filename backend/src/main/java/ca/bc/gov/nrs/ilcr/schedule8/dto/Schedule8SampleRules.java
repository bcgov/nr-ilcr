package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level cross-field constraint for a Schedule 8 sample (Story 14.3, BR-06). Enforces the three
 * legacy Save-time rules that span multiple fields:
 * <ul>
 *   <li>the six skidding/yarding %s sum to <b>at most</b> 100 — {@code > 100} is rejected at Save
 *       ({@code skiddingYardingEqualsCentPercent}, S15); a sum {@code < 100} SAVES and is flagged only
 *       at Check Status (S16) — so this validator never enforces exact-100;</li>
 *   <li>Helicopter conditional (S11/S23): when Helicopter % ≠ 0, Distance / Cycle Time / Direction /
 *       Dump Destination are required ({@code Value Required});</li>
 *   <li>Other conditional (S12/S24): when Other % ≠ 0, the skid-type code must be a valid selection —
 *       not blank, not {@code "NA"} ({@code notApplicableValidatorErrorMsg}).</li>
 * </ul>
 * Per-% individual 0–100 range (S17) is a field-level constraint on each percentage.
 */
@Documented
@Constraint(validatedBy = Schedule8SampleRulesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Schedule8SampleRules {

  String message() default "{skiddingYardingEqualsCentPercent}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
