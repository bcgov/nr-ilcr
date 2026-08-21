package ca.bc.gov.nrs.ilcr.schedule5;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 7.2 acceptance — server-side field validation on the Schedule 5 writes (AC6; slices
 * S12/S15; BR-05/BR-07).
 *
 * <p>Every bound in the story's § VALIDATION table is exercised on BOTH sides, and every expected
 * string is the LEGACY bundle text, byte-for-byte. Three of the ranges are deliberate oddities that
 * a uniform implementation would smooth over, and each is asserted here rather than described:
 *
 * <ul>
 *   <li>{@code wagesAndBenefits.cost} validates at &plusmn;99,999,999 while its siblings validate
 *       at &plusmn;9,999,999 — the {@code costSize} attribute is missing from that one input in
 *       BOTH legacy pages (deviation (F), an Open Question for the Ministry).
 *   <li>{@code recoveries.cost} is 0-FLOORED and capped at the legacy MESSAGE's 9,999,999, not at
 *       the wider {@code NUMBER(8,0)} column (deviation (G) — the call {@code deferred-work.md:245}
 *       handed this story, resolved as "the legacy message wins").
 *   <li>{@code roadDistanceToOperatingArea} is enforced at 999999.9 while its message SAYS
 *       "999,999" (deviation (H)). Real delivery data sits exactly on 999999.9, so clamping to the
 *       message would reject stored rows.
 * </ul>
 *
 * <p>Every test in this class asserts a REJECTION, so nothing here mutates — which is what lets it
 * share mill 670/2023 with the duplicate-name probes. The {@code @BeforeEach}/{@code @AfterEach}
 * fingerprint proves that claim per row rather than assuming it: count- and sum-based fingerprints
 * have passed while real writes slipped through ({@code 8-2-…md:112}).
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule5/camps — field validation (AC6)")
class Schedule5WriteValidationIT extends AbstractOracleIT {

  private static final String CAMPS = "/api/v1/schedule5/camps";
  private static final String PROBLEM_JSON = "application/problem+json";

  @Autowired private DataSource dataSource;

  private List<Map<String, Object>> before;

  @BeforeEach
  void snapshot() {
    before = fingerprint();
  }

  @AfterEach
  void nothingPersisted() {
    assertEquals(before, fingerprint(), "a rejected write must not touch a single column");
  }

  /**
   * The body as explicit UTF-8 bytes. {@code content(String)} encodes with the platform default
   * charset, which silently corrupts the multibyte input the byte-length tests exist to send.
   */
  private static byte[] utf8(String json) {
    return json.getBytes(StandardCharsets.UTF_8);
  }

  /** A body whose named field is replaced by the given raw JSON fragment. */
  private static String bodyWith(String fieldJson) {
    return """
        {
          "campName": "Validation Camp",
          "isolatedCamp": false,
          %s
        }
        """
        .formatted(fieldJson);
  }

  private void expectRejected(String fieldJson, String verbatimText) throws Exception {
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(fieldJson)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is(verbatimText)));
  }

  private void expectAccepted(String fieldJson) throws Exception {
    // Accepted by VALIDATION, then stopped by the duplicate-name check — so the bound is proven
    // inclusive without this class ever writing a row. Reusing the name mill 670/2023 already holds
    // is
    // what makes that possible.
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith(fieldJson).replace("Validation Camp", "Duplicate Name Camp")))
        .andExpect(status().isConflict());
  }

  // ---- FLD-001: the two required fields (S12) ---------------------------------------------------

  @Test
  @DisplayName("a blank, whitespace-only or missing camp name -> 400 (FLD-001, deviation (Q))")
  void campNameIsRequired() throws Exception {
    // FLD-001 has NO legacy text — it was the JSF container default, never overridden
    // (UC-SCH5-001-technical.md:279 records it as [UNKNOWN]) — so these keys are new, cited as new.
    for (String name : List.of("\"\"", "\"   \"", "null")) {
      mockMvc
          .perform(
              post(CAMPS)
                  .with(csrf())
                  .param("millId", "670")
                  .param("year", "2023")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"campName\":" + name + ",\"isolatedCamp\":false}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail", is("Camp Name is required.")));
    }
  }

  @Test
  @DisplayName("a camp name over 30 characters -> 400 (the column is VARCHAR2(30 BYTE) NOT NULL)")
  void campNameMaxLength() throws Exception {
    // 31 chars. Without this cap the value reaches Oracle and raises ORA-12899, which the service's
    // catch could only turn into an opaque 500 (the 8.2 lesson).
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campName\":\"" + "C".repeat(31) + "\",\"isolatedCamp\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Camp Name must be 30 characters or fewer.")));
  }

  @Test
  @DisplayName("a camp name within 30 CHARS but over 30 UTF-8 BYTES -> 400, not a 500")
  void campNameMaxByteLength() throws Exception {
    // The column is VARCHAR2(30 BYTE) on an AL32UTF8 database (ALL_TAB_COLUMNS CHAR_USED = 'B',
    // re-verified against the seeded image 2026-08-10), so @Size's CHARACTER count is the wrong
    // unit
    // and the two limits being equal makes the gap maximal: any multibyte character at all pushes a
    // 30-character name past 30 bytes. Each of these passes @Size and would previously have reached
    // Oracle, raised ORA-12899, and surfaced as ScheduleNotSavedException -> 500. The request body
    // is
    // sent as explicit UTF-8 bytes: the string would otherwise be encoded with the platform default
    // charset, which on a Windows dev box mangles the very characters under test.
    for (String name :
        List.of(
            "é".repeat(16), // 16 chars, 32 bytes -- accented Latin, 2 bytes each
            "Camp " + "ü".repeat(13), // 18 chars, 31 bytes -- the realistic mixed case
            "営".repeat(11), // 11 chars, 33 bytes -- CJK, 3 bytes each
            "Camp "
                + "🌲".repeat(7))) { // 12 chars, 33 bytes -- emoji, 4 bytes each (surrogate pairs)
      mockMvc
          .perform(
              post(CAMPS)
                  .with(csrf())
                  .param("millId", "670")
                  .param("year", "2023")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(utf8("{\"campName\":\"" + name + "\",\"isolatedCamp\":false}")))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
          .andExpect(jsonPath("$.detail", is("Camp Name must be 30 characters or fewer.")));
    }
  }

  @Test
  @DisplayName("comments within 3500 CHARS but over the 4000-BYTE column -> 400, not a 500")
  void commentsMaxByteLength() throws Exception {
    // Unlike campName the two caps differ (3500 chars vs 4000 bytes), so this needs sustained
    // multibyte text rather than a single character: 2001 two-byte characters is 4002 bytes while
    // comfortably inside the 3500-character screen cap legacy enforced.
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    utf8(
                        "{\"campName\":\"Validation Camp\",\"isolatedCamp\":false,"
                            + "\"comments\":\""
                            + "é".repeat(2001)
                            + "\"}")))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is("Comments must be 3500 characters or fewer.")));
  }

  @Test
  @DisplayName(
      "a missing isolatedCamp -> 400 (BR-05; legacy would NPE on it, Schedule5DAO.java:377)")
  void isolatedCampIsRequired() throws Exception {
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campName\":\"Validation Camp\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Isolated Camp is required.")));
  }

  // ---- FLD-002: the numeric ranges (S15) -------------------------------------------------------

  @Test
  @DisplayName("distance: 0.0 and 999999.9 are IN, -0.1 and 999999.91 are out (deviation (H))")
  void distanceRange() throws Exception {
    String text = "Entered distance must be between 0 and 999,999.";
    expectRejected("\"roadDistanceToOperatingArea\": -0.1", text);
    expectRejected("\"roadDistanceToOperatingArea\": 999999.91", text);
    // The message UNDERSTATES the bound by 0.9 and that is preserved:
    // ILCRDistanceValidator.java:16-17
    // enforces 999999.9, and Task 1 gate (vii) found real data sitting exactly on it, so clamping
    // to
    // the message's 999,999 would reject stored rows.
    expectAccepted("\"roadDistanceToOperatingArea\": 999999.9");
    expectAccepted("\"roadDistanceToOperatingArea\": 0.0");
  }

  @Test
  @DisplayName("distance: a THIRD decimal is rejected, not silently rounded by Oracle")
  void distanceScale() throws Exception {
    // The column is NUMBER(8,2). Without the @Digits fraction cap Oracle would round 42.555 to
    // 42.56
    // and report success with a number the licensee never typed (the 25.2 lesson).
    expectRejected(
        "\"roadDistanceToOperatingArea\": 42.555",
        "Entered distance must be between 0 and 999,999.");
  }

  @Test
  @DisplayName("sizeOfCamp: 1 and 999 are IN, 0 and 1000 are out")
  void sizeOfCampRange() throws Exception {
    String text = "Entered number of persons must be between 1 and 999.";
    // Note the floor is ONE, not zero — Constants.SIZE_OF_CAMP_MIN_VALUE is BigDecimal.ONE (:122).
    expectRejected("\"sizeOfCamp\": 0", text);
    expectRejected("\"sizeOfCamp\": 1000", text);
    expectAccepted("\"sizeOfCamp\": 1");
    expectAccepted("\"sizeOfCamp\": 999");
  }

  @Test
  @DisplayName("associatedCampVolume: 0 and 9,999,999 are IN, -1 and 10,000,000 are out")
  void campVolumeRange() throws Exception {
    String text = "Entered volume must be between 0 and 9,999,999.";
    expectRejected("\"associatedCampVolume\": -1", text);
    expectRejected("\"associatedCampVolume\": 10000000", text);
    expectAccepted("\"associatedCampVolume\": 0");
    expectAccepted("\"associatedCampVolume\": 9999999");
  }

  @Test
  @DisplayName("associatedCampVolume: a FRACTION is rejected, not truncated (legacy truncates)")
  void campVolumeIsIntegral() throws Exception {
    // ASSOCIATED_CAMP_VOLUME is NUMBER(7) with scale 0. Legacy called .intValue() and silently
    // truncated 1.9 to 1 (Schedule5DAO.java:376); the modern path rejects instead. Legacy is itself
    // inconsistent here — its sub-page helper uses intValueExact() and throws (:622).
    expectRejected(
        "\"associatedCampVolume\": 1.5", "Entered volume must be between 0 and 9,999,999.");
  }

  @Test
  @DisplayName("a category volume obeys the same 0-9,999,999 bound, on both sides")
  void categoryVolumeRange() throws Exception {
    String text = "Entered volume must be between 0 and 9,999,999.";
    expectRejected("\"cateringAndFood\": { \"volume\": -1 }", text);
    expectRejected("\"cateringAndFood\": { \"volume\": 10000000 }", text);
    // Two decimals ARE allowed here — the detail column is NUMBER(10,2), unlike the integral camp
    // volume. A third is not.
    expectAccepted("\"cateringAndFood\": { \"volume\": 999999.99 }");
    expectRejected("\"cateringAndFood\": { \"volume\": 100.123 }", text);
    // The magnitude bound still applies to the FRACTIONAL part: 9999999.99 exceeds 9,999,999 and is
    // rejected, exactly as legacy's compareTo against a scale-0 BigDecimal(9999999) rejects it
    // (ILCRVolumeValidator.java:48). The decimals do not buy extra magnitude.
    expectRejected("\"cateringAndFood\": { \"volume\": 9999999.99 }", text);
    expectAccepted("\"cateringAndFood\": { \"volume\": 9999999 }");
  }

  @Test
  @DisplayName("an ordinary category cost is capped at +/-9,999,999 (costSize=\"7\")")
  void standardCategoryCostRange() throws Exception {
    String text = "Entered cost must be between -9,999,999 and 9,999,999.";
    expectRejected("\"cateringAndFood\": { \"cost\": 10000000 }", text);
    expectRejected("\"equipAndSuppliesWater\": { \"cost\": -10000000 }", text);
    expectAccepted("\"cateringAndFood\": { \"cost\": 9999999 }");
    expectAccepted("\"cateringAndFood\": { \"cost\": -9999999 }");
  }

  @Test
  @DisplayName("wagesAndBenefits.cost is the +/-99,999,999 OUTLIER — deviation (F), preserved")
  void wagesCostIsTheOutlier() throws Exception {
    // Its input carries no costSize attribute in EITHER page (schedule5ExistingCamp.xhtml:160-162,
    // schedule5NewCamp.xhtml:99-101), so ILCRCostValidator falls through to its default "8". A tidy
    // implementation that treated all eleven categories alike would reject 50,000,000 and silently
    // narrow what legacy accepted.
    expectAccepted("\"wagesAndBenefits\": { \"cost\": 50000000 }");
    expectAccepted("\"wagesAndBenefits\": { \"cost\": 99999999 }");
    expectRejected(
        "\"wagesAndBenefits\": { \"cost\": 100000000 }",
        "Entered cost must be between -99,999,999 and 99,999,999.");
  }

  @Test
  @DisplayName("recoveries.cost is 0-FLOORED and capped at 9,999,999 — deviation (G)")
  void recoveriesCostIsZeroFloored() throws Exception {
    String text = "Entered cost must be between 0 and 9,999,999.";
    // The legacy MESSAGE's range wins over the wider NUMBER(8,0) column. Task 1 gate (vii) measured
    // the consequence: no stored Schedule 5 Recoveries exceeds it today, so this blocks no edit.
    expectRejected("\"recoveries\": { \"cost\": -1 }", text);
    expectRejected("\"recoveries\": { \"cost\": 10000000 }", text);
    expectAccepted("\"recoveries\": { \"cost\": 0 }");
    expectAccepted("\"recoveries\": { \"cost\": 9999999 }");
  }

  @Test
  @DisplayName("a FRACTIONAL cost is rejected, never truncated to whole dollars")
  void fractionalCostIsRejected() throws Exception {
    // Jackson enables ACCEPT_FLOAT_AS_INT by default, which would bind 1234.99 as 1234 and report a
    // successful save with a different number than the licensee typed (deferred-work.md:180). The
    // feature is disabled in application.yml, so this is a clean 400.
    mockMvc
        .perform(
            post(CAMPS)
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyWith("\"cateringAndFood\": { \"cost\": 1234.99 }")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an out-of-range value in a category's EXCLUDED half is rejected, not ignored")
  void outOfRangeExcludedHalfIsRejected() throws Exception {
    // The pinned contract's "the excluded half is ignored" holds for IN-RANGE values (the write map
    // hard-codes the null); Bean Validation still runs first, so an out-of-range excluded value is
    // a 400. Recorded as a deliberate divergence from legacy's never-validated disabled inputs —
    // review decision 2026-08-07: accepted and pinned rather than refactoring every declarative
    // bound into programmatic checks for a value the server was going to discard.
    expectRejected(
        "\"recoveries\": { \"volume\": -1 }", "Entered volume must be between 0 and 9,999,999.");
    expectRejected(
        "\"otherCampExpenses\": { \"cost\": 100000000 }",
        "Entered cost must be between -99,999,999 and 99,999,999.");
  }

  // ---- the SAME declarative and programmatic bounds hold on PUT (the edit path)
  // ------------------

  @Test
  @DisplayName("PUT enforces the Default-group bounds too — the groups are Default + OnUpdate")
  void putEnforcesTheDefaultGroupBounds() throws Exception {
    // Regressing @Validated({Default.class, OnUpdate.class}) to OnUpdate-only would silently drop
    // every declarative bound from the edit path while missingRevisionCount_400 stayed green (the
    // review's regression gap). Camp 8206 belongs to 670/2023; the rejection persists nothing, so
    // the class fingerprint holds.
    mockMvc
        .perform(
            put(CAMPS + "/8206")
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"campName\":\""
                        + "C".repeat(31)
                        + "\",\"isolatedCamp\":false,\"revisionCount\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("Camp Name must be 30 characters or fewer.")));
  }

  @Test
  @DisplayName("PUT enforces the two programmatic cost ranges too")
  void putEnforcesTheProgrammaticRanges() throws Exception {
    // validateCostRanges runs on updateCamp before the name check and the guarded UPDATE, so the
    // stale revisionCount 0 never matters — the 400 wins.
    mockMvc
        .perform(
            put(CAMPS + "/8206")
                .with(csrf())
                .param("millId", "670")
                .param("year", "2023")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"campName\":\"Validation Camp\",\"isolatedCamp\":false,"
                        + "\"revisionCount\":0,\"recoveries\":{\"cost\":-1}}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail", is("Entered cost must be between 0 and 9,999,999.")));
  }

  @Test
  @DisplayName("comments over 3500 characters -> 400 (the legacy textarea's own maxlength)")
  void commentsMaxLength() throws Exception {
    // The column is VARCHAR2(4000 BYTE) — WIDER than the screen cap — so unlike Schedule 6's
    // per-record comment there is no over-cap defect to inherit. Task 1 gate (vii) found the
    // longest
    // real stored comment is exactly 3500, so the cap is exactly right and is not widened.
    expectRejected(
        "\"comments\": \"" + "x".repeat(3501) + "\"", "Comments must be 3500 characters or fewer.");
    expectAccepted("\"comments\": \"" + "x".repeat(3500) + "\"");
  }

  private List<Map<String, Object>> fingerprint() {
    return new JdbcTemplate(dataSource)
        .queryForList(
            """
        SELECT c.CAMP_REPORT_ID, c.CAMP_NAME, c.DISTANCE_TO_OPERATING_AREA, c.CAMP_SIZE_CAPACITY,
               c.ASSOCIATED_CAMP_VOLUME, c.ISOLATED_CAMP_IND, c.COMMENTS, c.REVISION_COUNT,
               c.ENTRY_USERID, c.ENTRY_TIMESTAMP, c.UPDATE_USERID, c.UPDATE_TIMESTAMP,
               d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.VOLUME, d.COST,
               d.REVISION_COUNT AS DETAIL_REVISION, d.ENTRY_USERID AS DETAIL_ENTRY_USER,
               d.ENTRY_TIMESTAMP AS DETAIL_ENTRY_TS, d.UPDATE_USERID AS DETAIL_UPDATE_USER,
               d.UPDATE_TIMESTAMP AS DETAIL_UPDATE_TS
          FROM THE.CAMP_REPORT c
          LEFT JOIN THE.ILCR_COST_REPORT_DETAIL d ON d.CAMP_REPORT_ID = c.CAMP_REPORT_ID
         WHERE c.ILCR_MILL_ID = 670 AND c.REPORT_YEAR = 2023
         ORDER BY c.CAMP_REPORT_ID, d.ILCR_COST_REPORT_DETAIL_ID
        """);
  }
}
