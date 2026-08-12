package ca.bc.gov.nrs.ilcr.schedule5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 7.4 acceptance — sub-page field validation (AC4, AC5, AC6; slices S21/S22/S23).
 *
 * <p><strong>The cost bound is per PAGE, not per control</strong> (deviation (A)). Every cost input
 * on the Other Camp sub-page carries {@code costSize="7"} ({@code schedule5CampExpenses.xhtml:45}
 * add-form, {@code :79} grid) → &plusmn;9,999,999; neither Other Access input carries one
 * ({@code schedule5AccessExpenses.xhtml:36-38}, {@code :71-76}) → &plusmn;99,999,999. The committed
 * AC and all three UC documents record this incorrectly; the legacy source is what this suite
 * follows.
 *
 * <p><strong>Nothing here mutates.</strong> Every method targets camp 8708 / year 2023, whose single
 * seeded row (cost 9999999, description {@code 'Boundary Row'}) is the nothing-persisted
 * fingerprint — except the two ACCEPTANCE cases, which target their own camp because they must
 * commit to prove acceptance.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("Schedule 5 sub-pages — validation (AC4, AC5, AC6)")
class Schedule5SubPageValidationIT extends AbstractOracleIT {

  private static final String CAMP_ROWS = "/api/v1/schedule5/camps/8708/other-camp-expenses";
  private static final String ACCESS_ROWS = "/api/v1/schedule5/camps/8708/other-access-expenses";
  private static final long MILL = 690L;
  private static final int YEAR = 2023;

  private static final String CAMP_COST_MSG =
      "Entered cost must be between -9,999,999 and 9,999,999.";
  private static final String ACCESS_COST_MSG =
      "Entered cost must be between -99,999,999 and 99,999,999.";
  private static final String DESCRIPTION_MSG = "Description must be 30 characters or fewer.";

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private static String row(String description, String cost) {
    return "{\"rows\":[{\"rowId\":null,\"description\":"
        + (description == null ? "null" : "\"" + description + "\"")
        + ",\"cost\":" + cost + "}]}";
  }

  private org.springframework.test.web.servlet.ResultActions save(String path, String body)
      throws Exception {
    return mockMvc.perform(put(path).with(csrf())
        .param("millId", String.valueOf(MILL)).param("year", String.valueOf(YEAR))
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  /** Camp 8708 still holds exactly its seeded row — the nothing-persisted proof. */
  private void assertNothingPersisted() {
    assertThat(jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE CAMP_REPORT_ID = 8708 AND ILCR_REPORT_COST_ITEM_ID IN (62, 68)",
        Integer.class)).isEqualTo(1);
    assertThat(jdbc().queryForObject(
        "SELECT ITEM_DESCRIPTION FROM THE.ILCR_COST_REPORT_DETAIL "
            + "WHERE ILCR_COST_REPORT_DETAIL_ID = 8742", String.class)).isEqualTo("Boundary Row");
  }

  @Nested
  @DisplayName("AC5 — the two cost bands, at their boundaries")
  class CostBands {

    @Test
    @DisplayName("CAMP: 9,999,999 and -9,999,999 are accepted")
    void campBoundsAccepted() throws Exception {
      // Targets its own camp/year: acceptance must COMMIT, so it cannot use the fingerprint camp.
      mockMvc.perform(put("/api/v1/schedule5/camps/8717/other-camp-expenses").with(csrf())
              .param("millId", String.valueOf(MILL)).param("year", "2027")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"rows\":[{\"rowId\":null,\"description\":\"Max\",\"cost\":9999999},"
                  + "{\"rowId\":null,\"description\":\"Min\",\"cost\":-9999999}]}"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CAMP: 10,000,000 is rejected with costSize7ValidatorErrorMsg")
    void campOverMaxRejected() throws Exception {
      save(CAMP_ROWS, row("Too Big", "10000000"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(CAMP_COST_MSG));
      assertNothingPersisted();
    }

    @Test
    @DisplayName("CAMP: -10,000,000 is rejected")
    void campUnderMinRejected() throws Exception {
      save(CAMP_ROWS, row("Too Small", "-10000000"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(CAMP_COST_MSG));
      assertNothingPersisted();
    }

    @Test
    @DisplayName("CAMP: 100,000,000 still gets the CAMP message, not the Access one")
    void campBeyondWideBoundGetsCampMessage() throws Exception {
      // The review patch that moved the wide bound out of the DTO: a declarative ±99,999,999 on
      // SubPageRowRequest fired BEFORE the service's per-page narrowing, so a Camp cost past the
      // WIDE bound was rejected with the ACCESS message. Both bounds now live in the service, each
      // paired with its own key (AD-8).
      save(CAMP_ROWS, row("Way Too Big", "100000000"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(CAMP_COST_MSG));
      assertNothingPersisted();
    }

    @Test
    @DisplayName("ACCESS: 10,000,000 is ACCEPTED — the camp bound does not apply here")
    void accessAcceptsCampOverMax() throws Exception {
      // The sharp case: the same value the camp page rejects is legal on the access page. This is
      // the assertion that would fail if someone "tidied up" the two bounds into one.
      mockMvc.perform(put("/api/v1/schedule5/camps/8717/other-access-expenses").with(csrf())
              .param("millId", String.valueOf(MILL)).param("year", "2027")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"rows\":[{\"rowId\":null,\"description\":\"Wide\",\"cost\":10000000}]}"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ACCESS: 100,000,000 is rejected with costValidatorErrorMsg")
    void accessOverMaxRejected() throws Exception {
      save(ACCESS_ROWS, row("Too Big", "100000000"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(ACCESS_COST_MSG));
      assertNothingPersisted();
    }

    @Test
    @DisplayName("a fractional cost is REJECTED, not truncated (deviation (M))")
    void fractionalCostRejected() throws Exception {
      // accept-float-as-int: false, shipped app-wide by 7.2. Legacy is itself inconsistent here:
      // getNewCostReportDetail calls intValueExact() (:622) and throws inside the transaction.
      save(CAMP_ROWS, row("Fractional", "12.5")).andExpect(status().isBadRequest());
      assertNothingPersisted();
    }
  }

  @Nested
  @DisplayName("AC6 — description length")
  class DescriptionLength {

    private static String repeat(char c, int count) {
      return String.valueOf(c).repeat(count);
    }

    @Test
    @DisplayName("30 characters is accepted")
    void thirtyCharsAccepted() throws Exception {
      mockMvc.perform(put("/api/v1/schedule5/camps/8717/other-camp-expenses").with(csrf())
              .param("millId", String.valueOf(MILL)).param("year", "2027")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"rows\":[{\"rowId\":null,\"description\":\"" + repeat('A', 30)
                  + "\",\"cost\":1}]}"))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("31 characters is rejected with descriptionMaxLengthErrorMsg")
    void thirtyOneCharsRejected() throws Exception {
      save(CAMP_ROWS, row(repeat('A', 31), "1"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(DESCRIPTION_MSG));
      assertNothingPersisted();
    }

    @Test
    @DisplayName("30 FOUR-BYTE characters are accepted — the byte guard cannot fire")
    void thirtyMultibyteCharsAccepted() throws Exception {
      // A 30-CHARACTER string carries at most 90 UTF-8 bytes, well inside the delivery column
      // (VARCHAR2(120), CHAR_USED = 'B', Task 1 gate (i)), so @MaxByteLength(120) behind
      // @Size(max = 30) is provably non-binding and the honest assertion is that this is ACCEPTED.
      // It is only meaningful because the seed migration widened the local snapshot from
      // VARCHAR2(30): against 30 BYTES this row would fail with ORA-12899 despite being legal in
      // production, which is exactly the gap the widening closes.
      String fourByteChar = "🚀"; // U+1F680 — 4 UTF-8 bytes, but TWO Java chars
      String description = fourByteChar.repeat(15); // 30 Java chars, 60 bytes
      mockMvc.perform(put("/api/v1/schedule5/camps/8717/other-camp-expenses").with(csrf())
              .param("millId", String.valueOf(MILL)).param("year", "2027")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"rows\":[{\"rowId\":null,\"description\":\"" + description
                  + "\",\"cost\":1}]}"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("malformed bodies are 400s, never silent deletes or 500s")
  class MalformedBodies {

    @Test
    @DisplayName("an omitted rows field is 400 — absence must never mean delete-everything")
    void omittedRowsFieldRejected() throws Exception {
      // A typoed key, a truncated payload or a serializer bug produces {} — before the review
      // patch (@NotNull on rows, 2026-08-12) that body validated cleanly and CLEARED the list.
      // The intentional clear is always spelled "rows": [].
      save(CAMP_ROWS, "{}")
          .andExpect(status().isBadRequest());
      assertNothingPersisted();
    }

    @Test
    @DisplayName("a null rows field is 400 for the same reason")
    void nullRowsFieldRejected() throws Exception {
      save(CAMP_ROWS, "{\"rows\":null}")
          .andExpect(status().isBadRequest());
      assertNothingPersisted();
    }

    @Test
    @DisplayName("a null element inside rows is 400, not an NPE-in-transaction 500")
    void nullRowElementRejected() throws Exception {
      // Jackson deserializes {"rows":[null]} into a list containing null, and @Valid cascades skip
      // null elements — without the element-level @NotNull the first dereference NPEs into a 500.
      save(CAMP_ROWS, "{\"rows\":[null]}")
          .andExpect(status().isBadRequest());
      assertNothingPersisted();
    }
  }

  @Nested
  @DisplayName("AC4 — a blank description is STORABLE (deviation (F))")
  class BlankDescription {

    @Test
    @DisplayName("null, empty and whitespace-only descriptions all persist rather than 400")
    void blankDescriptionsAccepted() throws Exception {
      // A @NotBlank here would make legacy-stored rows un-re-saveable AND make four already-shipped
      // Check Status conditions unreachable. Nothing server-side has ever checked this field.
      mockMvc.perform(put("/api/v1/schedule5/camps/8717/other-camp-expenses").with(csrf())
              .param("millId", String.valueOf(MILL)).param("year", "2027")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"rows\":[{\"rowId\":null,\"description\":null,\"cost\":1},"
                  + "{\"rowId\":null,\"description\":\"\",\"cost\":2},"
                  + "{\"rowId\":null,\"description\":\" \",\"cost\":3}]}"))
          .andExpect(status().isOk());

      String body = mockMvc.perform(
              get("/api/v1/schedule5/camps/8717/other-camp-expenses")
                  .param("millId", String.valueOf(MILL)).param("year", "2027"))
          .andExpect(status().isOk())
          .andReturn().getResponse().getContentAsString();
      JsonNode doc = mapper.readTree(body);
      assertThat(doc.get("rows")).hasSize(3);
      // The single-space row keeps its space — it is NOT trimmed away, which is what makes the
      // shipped Check Status "isEmpty, not isBlank" behaviour observable.
      assertThat(doc.get("rows").get(2).get("description").asText()).isEqualTo(" ");
    }
  }
}
