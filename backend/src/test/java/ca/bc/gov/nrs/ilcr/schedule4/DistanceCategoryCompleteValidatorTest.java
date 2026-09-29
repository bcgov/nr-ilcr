package ca.bc.gov.nrs.ilcr.schedule4;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryInput;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * BR-04 on the three distance categories, ALL-OR-NOTHING (Iman, 2026-09-29): once any of distance,
 * volume or cost is present, every absent one is reported, all at once — a deliberate fix of
 * legacy's one-missing-field-per-attempt reveal. Each violation is {@code missingRequiredFieldMsg}
 * on the missing property.
 */
class DistanceCategoryCompleteValidatorTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void openValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  private static CategoryInput input(int code, Integer volume, Integer cost, String distance) {
    return new CategoryInput(
        code,
        volume == null ? null : BigDecimal.valueOf(volume),
        cost,
        distance == null ? null : new BigDecimal(distance));
  }

  /** The properties flagged with the BR-04 "Value Required" template. */
  private static Set<String> missing(CategoryInput input) {
    Set<ConstraintViolation<CategoryInput>> violations = validator.validate(input);
    assertThat(violations)
        .allSatisfy(v -> assertThat(v.getMessageTemplate()).isEqualTo("{missingRequiredFieldMsg}"));
    return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
  }

  @ParameterizedTest(name = "code {0}")
  @ValueSource(ints = {47, 48, 52})
  @DisplayName("Volume only -> Distance AND Cost required, together (the legacy fix)")
  void volumeOnly_flagsDistanceAndCost(int code) {
    assertThat(missing(input(code, 200, null, null))).containsExactlyInAnyOrder("distance", "cost");
  }

  @ParameterizedTest(name = "code {0}")
  @ValueSource(ints = {47, 48, 52})
  @DisplayName("Cost only -> Distance AND Volume required, together (the legacy fix)")
  void costOnly_flagsDistanceAndVolume(int code) {
    assertThat(missing(input(code, null, 8000, null)))
        .containsExactlyInAnyOrder("distance", "volume");
  }

  @Test
  @DisplayName("Distance only -> Volume AND Cost required (unchanged from legacy)")
  void distanceOnly_flagsVolumeAndCost() {
    assertThat(missing(input(47, null, null, "50.0"))).containsExactlyInAnyOrder("volume", "cost");
  }

  @Test
  @DisplayName("Two of three present -> the third alone is required")
  void twoPresent_flagsTheThird() {
    assertThat(missing(input(48, 200, null, "50.0"))).containsExactly("cost");
    assertThat(missing(input(48, null, 8000, "50.0"))).containsExactly("volume");
    assertThat(missing(input(48, 200, 8000, null))).containsExactly("distance");
  }

  @Test
  @DisplayName("All empty and all filled both pass; a typed 0 counts as present")
  void emptyOrComplete_passes() {
    assertThat(missing(input(52, null, null, null))).isEmpty();
    assertThat(missing(input(52, 200, 8000, "50.0"))).isEmpty();
    assertThat(missing(input(52, 0, 0, "0"))).isEmpty();
  }

  @Test
  @DisplayName("A fixed category is never subject to BR-04")
  void fixedCategory_isUnaffected() {
    assertThat(missing(input(40, 200, null, null))).isEmpty();
  }
}
