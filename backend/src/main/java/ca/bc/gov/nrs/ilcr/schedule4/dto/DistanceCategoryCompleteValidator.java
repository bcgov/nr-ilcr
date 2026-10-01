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
 * <p>The client mirror is {@code validateLocationForm} in {@code schedule4/validation.ts}.
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
