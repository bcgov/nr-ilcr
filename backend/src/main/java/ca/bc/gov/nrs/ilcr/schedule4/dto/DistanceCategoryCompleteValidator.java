package ca.bc.gov.nrs.ilcr.schedule4.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;

/**
 * Validates BR-04 (all-or-nothing) for the 3 distance-based codes on a {@link CategoryInput} (Story
 * 4.2, S22/S23). Fixed codes and a fully-empty distance category pass; once ANY of
 * distance/volume/cost is present, EVERY absent one fails with a {@code missingRequiredFieldMsg}
 * violation bound to that field (so the 400 ProblemDetail names each of {@code distance}/{@code
 * volume}/{@code cost} that is missing, all at once).
 *
 * <p><strong>A deliberate fix of legacy behaviour (Iman, 2026-09-29).</strong> Legacy's conditional
 * {@code required=} in {@code schedule4ExistingLocation.xhtml} — Distance required iff Volume or
 * Cost is present; Volume and Cost required iff Distance is present — is effectively
 * all-or-nothing, but it surfaced one missing field per attempt (a Volume alone asked only for the
 * Distance, then the Cost once the Distance was in). The rebuild reports every missing field at
 * once. The client mirror is {@code validateLocationForm} in {@code schedule4/validation.ts}.
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

    // Any one present ⇒ every absent one is required, reported together (the legacy fix above).
    boolean valid = true;
    context.disableDefaultConstraintViolation();
    if (!hasDistance) {
      addViolation(context, "distance");
      valid = false;
    }
    if (!hasVolume) {
      addViolation(context, "volume");
      valid = false;
    }
    if (!hasCost) {
      addViolation(context, "cost");
      valid = false;
    }
    return valid;
  }

  private static void addViolation(ConstraintValidatorContext context, String property) {
    context
        .buildConstraintViolationWithTemplate("{missingRequiredFieldMsg}")
        .addPropertyNode(property)
        .addConstraintViolation();
  }
}
