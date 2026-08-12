package ca.bc.gov.nrs.ilcr.schedule5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.schedule5.dto.CampRequest;
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
 * Unit test for {@code @MaxByteLength} on {@link CampRequest} — pure Jakarta Validator, no Spring and
 * no database (the Schedule 8 {@code Schedule8SampleRulesTest} idiom).
 *
 * <p><strong>Why the ACCEPTED cases live here rather than in {@code Schedule5WriteValidationIT}.</strong>
 * Every test in that IT asserts a rejection and its {@code @AfterEach} fingerprint proves nothing was
 * written; a case that must be ACCEPTED would either persist a camp (mill 670/2023 is Draft, so the
 * write would succeed) or have to be aimed at a non-Draft mill, where a 409 from the Draft gate proves
 * only that validation ran — not which bound it applied. Validating the record directly asserts the
 * constraint itself with nothing in between.
 *
 * <p>The bounds under test are the delivery columns, re-verified against the seeded image on
 * 2026-08-10: {@code CAMP_REPORT.CAMP_NAME VARCHAR2(30)} and {@code COMMENTS VARCHAR2(4000)}, both
 * {@code CHAR_USED = 'B'}, on an {@code NLS_CHARACTERSET = AL32UTF8} database.
 */
@DisplayName("CampRequest byte-length bounds (the BYTE-declared delivery columns)")
class Schedule5ByteLengthTest {

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

  /** A minimal valid request varying only the two length-capped fields. */
  private static CampRequest request(String campName, String comments) {
    return new CampRequest(campName, null, null, null, false, comments,
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static Set<String> violatedProperties(CampRequest request) {
    Set<ConstraintViolation<CampRequest>> violations = validator.validate(request);
    return violations.stream().map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  private static int utf8Bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8).length;
  }

  @Test
  @DisplayName("exactly 30 bytes of multibyte camp name is ACCEPTED — the cap is bytes, not a ban")
  void campNameAtExactlyThirtyBytesIsAccepted() {
    // 15 two-byte characters. On the limit, not over it. Pinned so a future "just restrict to ASCII"
    // or "measure code points" simplification of the validator fails here rather than silently
    // narrowing what a licensee may type.
    String name = "é".repeat(15);
    assertEquals(30, utf8Bytes(name));
    assertTrue(violatedProperties(request(name, null)).isEmpty(),
        "30 bytes is within a VARCHAR2(30 BYTE) column");
  }

  @Test
  @DisplayName("31 bytes across 16 characters is REJECTED on campName, though @Size alone passes it")
  void campNameOverThirtyBytesIsRejected() {
    // The precise gap the constraint exists to close: 16 characters satisfies @Size(max = 30), and
    // 31 bytes does not fit the column. Before this constraint the value reached Oracle and came back
    // as ORA-12899 -> ScheduleNotSavedException -> 500.
    String name = "é".repeat(15) + "x";
    assertEquals(16, name.length());
    assertEquals(31, utf8Bytes(name));
    assertTrue(violatedProperties(request(name, null)).contains("campName"));
  }

  @Test
  @DisplayName("the widest single character still fits when the rest of the name leaves room")
  void fourByteCharacterCountsAsFourBytes() {
    // A supplementary-plane emoji is ONE code point, TWO Java chars, FOUR UTF-8 bytes. Asserting the
    // accepted and rejected sides on the same character proves the validator counts bytes rather than
    // either of the two lengths Java would hand it for free.
    String fits = "C".repeat(26) + "🌲";
    String overflows = "C".repeat(27) + "🌲";
    assertEquals(30, utf8Bytes(fits));
    assertEquals(31, utf8Bytes(overflows));
    assertTrue(violatedProperties(request(fits, null)).isEmpty());
    assertTrue(violatedProperties(request(overflows, null)).contains("campName"));
  }

  @Test
  @DisplayName("comments: the CHARACTER cap (3500) and the BYTE cap (4000) are independent bounds")
  void commentsHoldsBothCaps() {
    // 3501 ASCII characters — over the legacy screen cap, well under the column's 4000 bytes.
    assertTrue(violatedProperties(request("Camp", "c".repeat(3501))).contains("comments"),
        "the legacy 3500-character screen cap still applies to ASCII");

    // 2001 two-byte characters — 2001 characters is far under the screen cap, 4002 bytes is over the
    // column. Neither bound implies the other, which is why both annotations are present.
    String multibyte = "é".repeat(2001);
    assertTrue(multibyte.length() < 3500);
    assertEquals(4002, utf8Bytes(multibyte));
    assertTrue(violatedProperties(request("Camp", multibyte)).contains("comments"));

    // 2000 two-byte characters = exactly 4000 bytes: the widest accepted comment.
    String atLimit = "é".repeat(2000);
    assertEquals(4000, utf8Bytes(atLimit));
    assertTrue(violatedProperties(request("Camp", atLimit)).isEmpty());
  }

  @Test
  @DisplayName("a null comment passes — the byte cap must not make an optional field required")
  void nullCommentsPasses() {
    assertTrue(violatedProperties(request("Camp", null)).isEmpty());
  }

  @Test
  @DisplayName("over-long ASCII trips EXACTLY ONE constraint per field, never both")
  void theTwoLengthCapsDoNotDoubleReport() {
    // The two annotations share a message key, and GlobalExceptionHandler joins violations with "; ",
    // so a value tripping both handed the licensee the same sentence twice:
    //   "Camp Name must be 30 characters or fewer.; Camp Name must be 30 characters or fewer."
    // On campName that is the COMMON case (both caps are 30 and a byte is never narrower than a
    // character, so every over-long ASCII name trips both), which is why MaxByteLength defers to @Size
    // via charMax. Counting violations rather than checking the property is present is the whole point.
    assertEquals(1, validator.validate(request("C".repeat(31), null)).size(),
        "31 ASCII characters is over BOTH caps and must still report once");

    // 4001 ASCII characters is the comments equivalent: over the 3500-character screen cap AND over the
    // 4000-byte column.
    assertEquals(1, validator.validate(request("Camp", "c".repeat(4001))).size(),
        "4001 ASCII characters is over BOTH caps and must still report once");

    // And the deferral must not swallow the multibyte case it exists to catch.
    assertEquals(1, validator.validate(request("é".repeat(16), null)).size());
  }
}
