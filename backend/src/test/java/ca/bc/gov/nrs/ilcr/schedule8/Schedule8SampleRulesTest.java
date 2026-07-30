package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the Schedule 8 sample Bean Validation rules (Story 14.3) — pure Jakarta Validator, no
 * Spring/DB. Documents the intentional BR-06 Save-vs-Check asymmetry (S15 sum &gt; 100 rejected, S16
 * sum &lt; 100 allowed) and the Helicopter/Other conditionals + Contract-ID-required + per-% range.
 */
class Schedule8SampleRulesTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  private static boolean hasViolationOn(
      Set<ConstraintViolation<Schedule8SampleRequest>> violations, String property) {
    return violations.stream()
        .anyMatch(v -> v.getPropertyPath().toString().equals(property));
  }

  /** A sample request varying only the fields the rules key on; everything else null. */
  private static Schedule8SampleRequest sample(String contractId, Integer groundBase,
      Integer grapple, Integer helicopter, Integer other, BigDecimal distance, BigDecimal cycle,
      Boolean uphill, Boolean waterDump, String skidType) {
    return new Schedule8SampleRequest(null, null, contractId, null, groundBase, grapple, null, null,
        helicopter, other, null, null, null, cycle, distance, uphill, waterDump, skidType, null,
        null, null);
  }

  @Test
  void skiddingSumBelow100_isValid_theS16Half() {
    var violations = validator.validate(
        sample("C", 50, null, null, null, null, null, null, null, null));
    assertTrue(violations.isEmpty(), () -> "sum < 100 must SAVE at Save-time, got " + violations);
  }

  @Test
  void skiddingSumExactly100_isValid() {
    var violations = validator.validate(
        sample("C", 60, 40, null, null, null, null, null, null, null));
    assertTrue(violations.isEmpty());
  }

  @Test
  void skiddingSumAbove100_isRejected_theS15Half() {
    var violations = validator.validate(
        sample("C", 60, 60, null, null, null, null, null, null, null));
    assertFalse(violations.isEmpty());
    assertTrue(hasViolationOn(violations, "groundBasePct"));
  }

  @Test
  void individualPercentAbove100_isRejected() {
    var violations = validator.validate(
        sample("C", 150, null, null, null, null, null, null, null, null));
    assertTrue(hasViolationOn(violations, "groundBasePct"));
  }

  @Test
  void contractIdMissing_isRejected() {
    var violations = validator.validate(
        sample(null, 100, null, null, null, null, null, null, null, null));
    assertTrue(hasViolationOn(violations, "contractId"));
  }

  @Test
  void helicopterNonZeroWithoutRequiredFields_isRejected() {
    var violations = validator.validate(
        sample("C", 50, null, 50, null, null, null, null, null, null));
    assertTrue(hasViolationOn(violations, "distance"));
    assertTrue(hasViolationOn(violations, "cycleTime"));
    assertTrue(hasViolationOn(violations, "uphillDirection"));
    assertTrue(hasViolationOn(violations, "waterDumpDestination"));
  }

  @Test
  void helicopterNonZeroWithRequiredFields_isValid() {
    var violations = validator.validate(sample("C", 50, null, 50, null,
        new BigDecimal("12.5"), new BigDecimal("3.0"), true, false, null));
    assertTrue(violations.isEmpty());
  }

  @Test
  void otherNonZeroWithNaSkidType_isRejected() {
    var violations = validator.validate(
        sample("C", 50, null, null, 50, null, null, null, null, "NA"));
    assertTrue(hasViolationOn(violations, "skidTypeCode"));
  }

  @Test
  void otherNonZeroWithValidSkidType_isValid() {
    var violations = validator.validate(
        sample("C", 50, null, null, 50, null, null, null, null, "ST1"));
    assertTrue(violations.isEmpty());
  }
}
