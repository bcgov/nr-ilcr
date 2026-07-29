package ca.bc.gov.nrs.ilcr.schedule8;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8PageRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRequest;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Schedule8SampleRulesValidator;
import ca.bc.gov.nrs.ilcr.schedule8.dto.TsaOrTflRequiredValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the two Schedule 8 bean validators — the cross-field rules that the Jakarta
 * annotations can't express. The {@link ConstraintValidatorContext} is a deep-stub mock (the
 * build-violation fluent chain is exercised but its wiring isn't the unit under test); only the
 * boolean verdict is asserted.
 */
class Schedule8ValidatorsTest {

  private static ConstraintValidatorContext ctx() {
    return mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
  }

  // ---- Schedule8SampleRulesValidator (S15 / S23 / S24) -------------------------------------------

  private final Schedule8SampleRulesValidator sampleRules = new Schedule8SampleRulesValidator();

  private static Schedule8SampleRequest sample(Integer ground, Integer grapple, Integer skyline,
      Integer highlead, Integer helicopter, Integer other, BigDecimal distance, BigDecimal cycle,
      Boolean uphill, Boolean waterDump, String skidType) {
    return new Schedule8SampleRequest(null, null, "C", null, ground, grapple, skyline, highlead,
        helicopter, other, null, null, null, cycle, distance, uphill, waterDump, skidType, null,
        null, null);
  }

  @Test
  void sample_null_isValid() {
    assertTrue(sampleRules.isValid(null, ctx()));
  }

  @Test
  void sample_sumWithinHundred_noConditionals_isValid() {
    assertTrue(sampleRules.isValid(
        sample(40, 20, 10, 0, 0, 0, null, null, null, null, null), ctx()));
  }

  @Test
  void sample_sumOverHundred_isInvalid() {
    assertFalse(sampleRules.isValid(
        sample(60, 30, 20, 0, 0, 0, null, null, null, null, null), ctx()));
  }

  @Test
  void sample_helicopterNonZero_missingConditionalFields_isInvalid() {
    assertFalse(sampleRules.isValid(
        sample(0, 0, 0, 0, 50, 0, null, null, null, null, null), ctx()));
  }

  @Test
  void sample_helicopterNonZero_allConditionalFieldsPresent_isValid() {
    assertTrue(sampleRules.isValid(
        sample(0, 0, 0, 0, 50, 0, new BigDecimal("5"), new BigDecimal("2"), true, false, null),
        ctx()));
  }

  @Test
  void sample_otherNonZero_naSkidType_isInvalid() {
    assertFalse(sampleRules.isValid(
        sample(0, 0, 0, 0, 0, 20, null, null, null, null, "NA"), ctx()));
  }

  @Test
  void sample_otherNonZero_blankSkidType_isInvalid() {
    assertFalse(sampleRules.isValid(
        sample(0, 0, 0, 0, 0, 20, null, null, null, null, "  "), ctx()));
  }

  @Test
  void sample_otherNonZero_validSkidType_isValid() {
    assertTrue(sampleRules.isValid(
        sample(0, 0, 0, 0, 0, 20, null, null, null, null, "GR"), ctx()));
  }

  // ---- TsaOrTflRequiredValidator (S/BR-03 context) -----------------------------------------------

  private final TsaOrTflRequiredValidator tsaOrTfl = new TsaOrTflRequiredValidator();

  private static Schedule8PageRequest page(String tsa, String tfl) {
    return new Schedule8PageRequest(null, null, "LIC", "SC", "RG", "BZ", tsa, tfl, null, null, null,
        null, null, null);
  }

  @Test
  void page_null_isValid() {
    assertTrue(tsaOrTfl.isValid(null, ctx()));
  }

  @Test
  void page_tsaPresent_isValid() {
    assertTrue(tsaOrTfl.isValid(page("TSA25", null), ctx()));
  }

  @Test
  void page_tflPresent_isValid() {
    assertTrue(tsaOrTfl.isValid(page(null, "TFL48"), ctx()));
  }

  @Test
  void page_neitherTsaNorTfl_isInvalid() {
    assertFalse(tsaOrTfl.isValid(page("  ", null), ctx()));
  }
}
