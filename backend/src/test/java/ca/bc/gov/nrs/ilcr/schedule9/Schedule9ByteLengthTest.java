package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.schedule9.dto.ContractualWorkRecordRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for {@code @MaxByteLength} on {@link ContractualWorkRecordRequest} — pure Jakarta
 * Validator.
 */
@DisplayName("ContractualWorkRecordRequest byte-length bounds (the BYTE-declared delivery columns)")
class Schedule9ByteLengthTest {

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

  /** A minimal valid request varying only the length-capped fields. */
  private static ContractualWorkRecordRequest request(
      String contractorId,
      String itemDescription,
      String unitDescription,
      String sourceDescription,
      String comments) {
    return new ContractualWorkRecordRequest(
        contractorId,
        108,
        itemDescription,
        "U",
        unitDescription,
        null,
        "BEC",
        null,
        null,
        "S",
        sourceDescription,
        comments,
        null);
  }

  private static Set<String> violatedProperties(ContractualWorkRecordRequest request) {
    Set<ConstraintViolation<ContractualWorkRecordRequest>> violations = validator.validate(request);
    return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
  }

  private static int utf8Bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  @Test
  @DisplayName("exactly 30 bytes of multibyte contractorId is ACCEPTED")
  void contractorIdAtExactlyThirtyBytesIsAccepted() {
    String val = "é".repeat(15);
    assertEquals(30, utf8Bytes(val));
    assertTrue(violatedProperties(request(val, null, null, null, null)).isEmpty());
  }

  @Test
  @DisplayName("31 bytes across 16 characters is REJECTED on contractorId")
  void contractorIdOverThirtyBytesIsRejected() {
    String val = "é".repeat(15) + "x";
    assertEquals(16, val.length());
    assertEquals(31, utf8Bytes(val));
    assertTrue(violatedProperties(request(val, null, null, null, null)).contains("contractorId"));
  }

  @Test
  @DisplayName("exactly 30 bytes of multibyte itemDescription is ACCEPTED")
  void itemDescriptionAtExactlyThirtyBytesIsAccepted() {
    String val = "é".repeat(15);
    assertEquals(30, utf8Bytes(val));
    assertTrue(violatedProperties(request(null, val, null, null, null)).isEmpty());
  }

  @Test
  @DisplayName("exactly 120 bytes of multibyte unitDescription is ACCEPTED")
  void unitDescriptionAtExactlyOneHundredTwentyBytesIsAccepted() {
    String val = "é".repeat(60);
    assertEquals(120, utf8Bytes(val));
    assertTrue(violatedProperties(request(null, null, val, null, null)).isEmpty());
  }

  @Test
  @DisplayName("121 bytes is REJECTED on unitDescription")
  void unitDescriptionOverLimitIsRejected() {
    String val = "é".repeat(60) + "x";
    assertEquals(121, utf8Bytes(val));
    assertTrue(
        violatedProperties(request(null, null, val, null, null)).contains("unitDescription"));
  }

  @Test
  @DisplayName("exactly 120 bytes of multibyte sourceDescription is ACCEPTED")
  void sourceDescriptionAtExactlyOneHundredTwentyBytesIsAccepted() {
    String val = "é".repeat(60);
    assertEquals(120, utf8Bytes(val));
    assertTrue(violatedProperties(request(null, null, null, val, null)).isEmpty());
  }

  @Test
  @DisplayName("exactly 2000 bytes of multibyte comments is ACCEPTED")
  void commentsAtExactlyTwoThousandBytesIsAccepted() {
    String val = "é".repeat(1000);
    assertEquals(2000, utf8Bytes(val));
    assertTrue(violatedProperties(request(null, null, null, null, val)).isEmpty());
  }

  @Test
  @DisplayName("2001 bytes is REJECTED on comments")
  void commentsOverLimitIsRejected() {
    String val = "é".repeat(1000) + "x";
    assertEquals(2001, utf8Bytes(val));
    assertTrue(violatedProperties(request(null, null, null, null, val)).contains("comments"));
  }
}
