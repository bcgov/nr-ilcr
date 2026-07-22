package ca.bc.gov.nrs.ilcr.schedule8.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces {@link Schedule8SampleRules} (Story 14.3): the skidding-sum ≤ 100 rule (S15, never
 * exact-100 at Save), the Helicopter-conditional required fields (S23), and the Other-conditional
 * skid-type-not-{@code NA} rule (S24). Each failure is bound to its field so the 400 ProblemDetail
 * names the offending property with the verbatim legacy message.
 */
public class Schedule8SampleRulesValidator
    implements ConstraintValidator<Schedule8SampleRules, Schedule8SampleRequest> {

  private static final String NOT_APPLICABLE = "NA";

  @Override
  public boolean isValid(Schedule8SampleRequest r, ConstraintValidatorContext context) {
    if (r == null) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    boolean valid = true;

    // S15: sum of the six skidding/yarding %s must not exceed 100 (a sum < 100 is allowed at Save).
    int sum = zero(r.groundBasePct()) + zero(r.grapplePct()) + zero(r.skylinePct())
        + zero(r.highleadPct()) + zero(r.helicopterPct()) + zero(r.otherSkiddingPct());
    if (sum > 100) {
      violation(context, "{skiddingYardingEqualsCentPercent}", "groundBasePct");
      valid = false;
    }

    // S23: Helicopter % ≠ 0 makes Distance / Cycle Time / Direction / Dump Destination required.
    if (isNonZero(r.helicopterPct())) {
      if (r.distance() == null) {
        valid = requiredViolation(context, "distance") && valid;
      }
      if (r.cycleTime() == null) {
        valid = requiredViolation(context, "cycleTime") && valid;
      }
      if (r.uphillDirection() == null) {
        valid = requiredViolation(context, "uphillDirection") && valid;
      }
      if (r.waterDumpDestination() == null) {
        valid = requiredViolation(context, "waterDumpDestination") && valid;
      }
    }

    // S24: Other % ≠ 0 requires a valid skid-type selection — not blank, not "NA".
    if (isNonZero(r.otherSkiddingPct())) {
      String skidType = r.skidTypeCode();
      if (skidType == null || skidType.isBlank() || NOT_APPLICABLE.equalsIgnoreCase(skidType.trim())) {
        violation(context, "{notApplicableValidatorErrorMsg}", "skidTypeCode");
        valid = false;
      }
    }
    return valid;
  }

  private static boolean requiredViolation(ConstraintValidatorContext context, String property) {
    violation(context, "{missingRequiredFieldMsg}", property);
    return false;
  }

  private static void violation(
      ConstraintValidatorContext context, String template, String property) {
    context.buildConstraintViolationWithTemplate(template)
        .addPropertyNode(property)
        .addConstraintViolation();
  }

  private static int zero(Integer value) {
    return value == null ? 0 : value;
  }

  private static boolean isNonZero(Integer value) {
    return value != null && value != 0;
  }
}
