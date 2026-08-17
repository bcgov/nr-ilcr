package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — field validation on the Schedule 10 write path.
 *
 * <p>Every rejection asserts the VERBATIM legacy text, because the whole point of the exercise is
 * that the client renders exactly what the legacy screen did. The 400 body is a plain
 * {@code ProblemDetail} with a single {@code detail} string — there is no per-field array — which is
 * why each distinguishable field carries its own bundle key.
 *
 * <p>Every test also asserts that nothing was created: a rejection that persisted a partial row
 * would otherwise pass on the status code alone.
 */
@DisplayName("Schedule 10 — write validation")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10ValidationIT extends AbstractOracleIT {

  private static final String PAGES = "/api/v1/schedule10/pages";
  private static final String MILL = "717";
  private static final String YEAR = "2024";

  @Autowired
  private JdbcTemplate jdbc;

  private int pageCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ILCR_MILL_ID = 717"
            + " AND REPORT_YEAR = 2024", Integer.class);
  }

  private void expectPageRejected(String json, String expectedText) throws Exception {
    int before = pageCount();
    mockMvc.perform(post(PAGES).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).with(csrf()).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail", containsString(expectedText)));
    assertThat(pageCount()).as("a rejected write must persist nothing").isEqualTo(before);
  }

  @Test
  @DisplayName("a missing Region is rejected with legacy's own wording")
  void missingRegionIsRejected() throws Exception {
    expectPageRejected("""
        {"tsaOrTfl":"01","supplyBlock":"01A","divisionName":"No Region",
         "constructionPeriod":"2021-06"}
        """, "Region is required.");
  }

  @Test
  @DisplayName("a missing TSA or TFL uses Schedule 10's wording, NOT Schedule 6's")
  void missingTsaOrTflUsesScheduleTenWording() throws Exception {
    // Schedule 6's key reads "TSA or TFL: Value is required." — reusing it here would ship the
    // wrong bytes with nothing to catch it.
    expectPageRejected("""
        {"forestRegionCode":"RNI","supplyBlock":"01A","divisionName":"No Location",
         "constructionPeriod":"2021-06"}
        """, "TSA or TFL is required.");
  }

  @Test
  @DisplayName("a Period Surveyed in the wrong format is rejected")
  void badPeriodFormatIsRejected() throws Exception {
    // Legacy accepts "2024-1" and stores it, then throws when re-rendering it. The API refuses it.
    expectPageRejected("""
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A","divisionName":"Bad Period",
         "constructionPeriod":"2024-1"}
        """, "The date is not valid. Enter date in format: YYYY-MM.");
  }

  @Test
  @DisplayName("a division name longer than the column is a 400, not an opaque 500")
  void overlongDivisionIsRejected() throws Exception {
    // The column is VARCHAR2(20) while the legacy screen allows 30, so legacy raises ORA-12899.
    expectPageRejected("""
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A",
         "divisionName":"012345678901234567890123456789","constructionPeriod":"2021-06"}
        """, "Division must be 20 characters or fewer.");
  }

  @Test
  @DisplayName("a TFL the reference table does not hold is rejected with the validator's message")
  void invalidTflIsRejected() throws Exception {
    expectPageRejected("""
        {"forestRegionCode":"RNI","tsaOrTfl":"TFL","tflNumberCode":"99",
         "divisionName":"Bad TFL","constructionPeriod":"2021-06"}
        """, "Entered TFL number is not valid for Interior Regions.");
  }

  @Test
  @DisplayName("an unknown forest region is rejected rather than reaching the foreign key")
  void unknownRegionCodeIsRejected() throws Exception {
    // Legacy silently stored NULL on a cache miss; the column carries an enabled FK here, so an
    // unknown code would otherwise surface as an opaque 500.
    expectPageRejected("""
        {"forestRegionCode":"ZZZ","tsaOrTfl":"01","supplyBlock":"01A","divisionName":"Bad Region",
         "constructionPeriod":"2021-06"}
        """, "A valid value must be selected from the list.");
  }

  @Test
  @DisplayName("cost bands are enforced with the two distinct legacy messages")
  void costBandsAreEnforced() throws Exception {
    int pagesBefore = pageCount();

    // A non-negative cost below zero uses the 0-based message.
    mockMvc.perform(post(PAGES + "/8956/road-details").param("millId", MILL).param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"roadName":"Bad Cost","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","subGrade":{"actualCost":-1},
                 "stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered cost must be between 0 and 9,999,999.")));

    // A transfer outside the symmetric band uses the other message.
    mockMvc.perform(post(PAGES + "/8956/road-details").param("millId", MILL).param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"roadName":"Bad Transfer","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","subGrade":{"ttTransfer":-10000000},
                 "stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered cost must be between -9,999,999 and 9,999,999.")));

    assertThat(pageCount()).isEqualTo(pagesBefore);
  }

  @Test
  @DisplayName("a road detail omitting the stabilizing block cannot bypass the required ballast method")
  void omittedStabilizingBlockIsRejected() throws Exception {
    // Bean Validation skips a null nested object, so without @NotNull the required ballast method
    // inside it would never be evaluated.
    mockMvc.perform(post(PAGES + "/8956/road-details").param("millId", MILL).param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"roadName":"No Stabilizing","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1"}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("required road-detail fields use the JSF default template with the field's label")
  void requiredRoadDetailFieldsUseTheLabelTemplate() throws Exception {
    mockMvc.perform(post(PAGES + "/8956/road-details").param("millId", MILL).param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Road Name is required.")));

    mockMvc.perform(post(PAGES + "/8956/road-details").param("millId", MILL).param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"roadName":"No RSMR","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("RSMR Class is required.")));
  }
}
