package ca.bc.gov.nrs.ilcr.schedule2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.schedule2.dto.Schedule2Request;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bean-Validation boundary test for {@link Schedule2Request} — proves the @Min/@Max/@DecimalMin/
 * @DecimalMax ranges are correct at the EXACT inclusive edge (an off-by-one would otherwise slip
 * through, since the IT suite only exercises far-out values). Pure Jakarta validation — no Spring,
 * no DB. Asserts on the offending property path, not the (bundle-key) message text.
 */
class Schedule2RequestValidationTest {

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

  /** revisionCount 0 (valid new/update token) keeps these tests focused on the amount fields. */
  private Schedule2Request req(Integer cost25, BigDecimal vol26, Integer cost26) {
    return new Schedule2Request(0, null, cost25, vol26, cost26);
  }

  private Set<String> invalidProps(Schedule2Request r) {
    return validator.validate(r).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  @Test
  void allEdgesInclusive_areValid() {
    // Exact inclusive edges must all PASS: item 25 ±99,999,999; item 26 vol 0 and 9,999,999;
    // item 26 cost ±999,999,999.
    assertTrue(invalidProps(req(-99999999, BigDecimal.ZERO, -999999999)).isEmpty());
    assertTrue(invalidProps(
        req(99999999, new BigDecimal("9999999"), 999999999)).isEmpty());
  }

  @Test
  void purchasedLogCost_justOverEdge_isInvalid() {
    assertEquals(Set.of("purchasedLogCostCost"), invalidProps(req(100000000, null, null)));
    assertEquals(Set.of("purchasedLogCostCost"), invalidProps(req(-100000000, null, null)));
  }

  @Test
  void lessLogSalesVolume_outsideRange_isInvalid() {
    // min is 0 (NOT signed like Schedule 1) and max 9,999,999.
    assertEquals(Set.of("lessLogSalesVolume"), invalidProps(req(null, new BigDecimal("-1"), null)));
    assertEquals(Set.of("lessLogSalesVolume"),
        invalidProps(req(null, new BigDecimal("10000000"), null)));
  }

  @Test
  void lessLogSalesCost_justOverWidenedEdge_isInvalid() {
    assertEquals(Set.of("lessLogSalesCost"), invalidProps(req(null, null, 1000000000)));
    assertEquals(Set.of("lessLogSalesCost"), invalidProps(req(null, null, -1000000000)));
  }

  @Test
  void nullRevisionCount_isRejected() {
    // The create token is 0, never null (contract clarification from review).
    Schedule2Request r = new Schedule2Request(null, null, 1, null, null);
    assertEquals(Set.of("revisionCount"), invalidProps(r));
  }
}
