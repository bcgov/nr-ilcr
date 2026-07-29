package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * Validates BR-04 (bidirectional required) for the 3 distance-based codes on a {@link CategoryInput}
 * (Story 4.2, S22/S23). Fixed codes and a fully-empty distance category pass; a partially-filled
 * distance category fails with a {@code missingRequiredFieldMsg} violation bound to each missing
 * field (so the 400 ProblemDetail names {@code distance}/{@code volume}/{@code cost}).
 */
public class DistanceCategoryCompleteValidator
    implements ConstraintValidator<DistanceCategoryComplete, CategoryInput> {

  /** 47 Truck Barge/Ferry, 48 Crew Barge/Ferry, 52 Rail Haul. */
  private static final Set<Integer> DISTANCE_CODES = Set.of(47, 48, 52);

  @Override
  public boolean isValid(CategoryInput input, ConstraintValidatorContext context) {
    if (input == null || input.code() == null || !DISTANCE_CODES.contains(input.code())) {
      return true; // not a distance category — BR-04 does not apply
    }
    boolean hasVolume = input.volume() != null;
    boolean hasCost = input.cost() != null;
    boolean hasDistance = input.distance() != null;

    // A fully-empty distance category is allowed (the category is simply not entered).
    if (!hasVolume && !hasCost && !hasDistance) {
      return true;
    }

    boolean valid = true;
    context.disableDefaultConstraintViolation();
    // volume|cost entered ⇒ distance required
    if ((hasVolume || hasCost) && !hasDistance) {
      addViolation(context, "distance");
      valid = false;
    }
    // distance entered ⇒ volume + cost required
    if (hasDistance && !hasVolume) {
      addViolation(context, "volume");
      valid = false;
    }
    if (hasDistance && !hasCost) {
      addViolation(context, "cost");
      valid = false;
    }
    return valid;
  }

  private static void addViolation(ConstraintValidatorContext context, String property) {
    context.buildConstraintViolationWithTemplate("{missingRequiredFieldMsg}")
        .addPropertyNode(property)
        .addConstraintViolation();
  }
}
