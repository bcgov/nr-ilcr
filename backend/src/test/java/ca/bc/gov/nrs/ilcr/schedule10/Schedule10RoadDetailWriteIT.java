package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 10 road-detail writes: the moisture-code derivation, the cost-line
 * routing, the ballast coupling and the per-row optimistic lock.
 *
 * <p>Each test creates its OWN page and asserts only against the ids it was handed, so the tests
 * share a Draft context without being able to interfere with one another.
 */
@DisplayName("Schedule 10 — road-detail writes")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10RoadDetailWriteIT extends AbstractOracleIT {

  private static final String PAGES = "/api/v1/schedule10/pages";
  private static final String MILL = "717";
  private static final String YEAR = "2024";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private JdbcTemplate jdbc;

  /**
   * Creates a page in the shared context and returns its id.
   *
   * <p>The division name is capped at 20 characters — the real {@code CONSTRUCTION_DIVISION_NAME}
   * column width, not the legacy screen's 30. Checked here rather than left to the endpoint: a
   * too-long name comes back as a bare 400 from {@code newPage}, which reads like a broken write
   * path and cost a full suite run to diagnose (2026-08-18).
   */
  private int newPage(String division) throws Exception {
    assertThat(division.length())
        .as("division name must fit CONSTRUCTION_DIVISION_NAME VARCHAR2(20): %s", division)
        .isLessThanOrEqualTo(20);
    String body =
        mockMvc
            .perform(
                post(PAGES)
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(
                        """
                {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A",
                 "divisionName":"%s","constructionPeriod":"2021-06"}
                """
                            .formatted(division)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (JsonNode page : MAPPER.readTree(body).get("pages")) {
      if (division.equals(page.path("divisionName").asText())) {
        return page.get("pageId").asInt();
      }
    }
    throw new AssertionError("page not created: " + division);
  }

  private static String detailJson(int becId, String rsmr, String ballast, String roadName) {
    return """
        {"roadName":"%s","roadLifetimeCode":"P","becbiogeoCatalogueId":%d,
         "relSoilMoistRgmClsCode":"%s","sideSlopePct":25,"detailedEngineeringCostInd":"N",
         "subGrade":{"length":12.5,"surfaceWidth":6.5,"actualCost":150000,"ttTransfer":-5000,
                     "otherTransfer":2000,"lessBridges":1000,"lessCulverts":2000,
                     "lessLandings":3000,"lessOverland":4000,"lessOtherEng":5000,
                     "lessEndHaul":6000},
         "stabilizing":{"ballastMethodCode":"%s","ballastMaterialCode":"GR","length":3.0,
                        "surfaceWidth":6.5,"depth":0.3,"distanceToSource":12.4,
                        "actualCost":40000,"ttTransfer":2500,"otherTransfer":-1500},
         "materialComposition":{"solidRockPct":10,"rippableRockPct":20,"coarsePct":40,
                                "finePct":20,"organicPct":10},
         "endHaulDistance":2.5,"endHaulVolume":1200,"overlandDistance":1.5,"overlandVolume":800,
         "comments":"Created road detail"}
        """
        .formatted(roadName, becId, rsmr, ballast);
  }

  private JsonNode detailOf(String body, int pageId) throws Exception {
    for (JsonNode page : MAPPER.readTree(body).get("pages")) {
      if (page.get("pageId").asInt() == pageId) {
        return page.get("roadDetails").get(0);
      }
    }
    throw new AssertionError("page " + pageId + " absent from the echo");
  }

  @Test
  @DisplayName(
      "creates a road detail, derives both moisture codes, and writes all twelve cost lines")
  void createsRoadDetailWithDerivedMoistureAndCosts() throws Exception {
    int pageId = newPage("Detail Create");

    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8801, "1", "C", "Derived Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode detail = detailOf(body, pageId);
    int detailId = detail.get("roadDetailId").asInt();

    // The two removed fields must never appear on the wire, in either direction.
    assertThat(detail.path("asmCode").isMissingNode()).isTrue();
    assertThat(detail.path("soilMoistureCode").isMissingNode()).isTrue();
    assertThat(detail.path("materialComposition").path("boulderAreaPct").isMissingNode()).isTrue();

    // BEC 8801 + RSMR '1' resolves to exactly one pair through the cross-reference.
    var stored =
        jdbc.queryForMap(
            "SELECT ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, ENTRY_USERID,"
                + " UPDATE_USERID, REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(stored)
        .containsEntry("ILCR_SOIL_MOISTURE_CODE", "Dry")
        .containsEntry("RELATIVE_SOIL_MOISTUR_RGM_CODE", "MD")
        .containsEntry("ENTRY_USERID", "dev-submitter")
        .containsEntry("UPDATE_USERID", "dev-submitter");

    // All twelve ordinals, routed across all four subcategories.
    Map<String, Object> costs =
        jdbc.queryForMap(
            "SELECT COUNT(*) AS TOTAL,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 20 THEN COST END) AS SG_ACTUAL,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 3  THEN COST END) AS SG_TT,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 5  THEN COST END) AS SG_OTHER,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 4  THEN COST END) AS LESS_OTHER_ENG,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 9  THEN COST END) AS ST_OTHER,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 10 THEN COST END) AS ST_TT,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 22 THEN COST END) AS ST_ACTUAL"
                + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(((Number) costs.get("TOTAL")).intValue()).isEqualTo(12);
    assertThat(((Number) costs.get("SG_ACTUAL")).intValue()).isEqualTo(150000);
    assertThat(((Number) costs.get("SG_TT")).intValue()).isEqualTo(-5000);
    // Item 5 lives under subcategory 3, not 1 — routing must be by item id.
    assertThat(((Number) costs.get("SG_OTHER")).intValue()).isEqualTo(2000);
    assertThat(((Number) costs.get("LESS_OTHER_ENG")).intValue()).isEqualTo(5000);
    // Item 9 is subcategory 4, item 10 subcategory 2 — a swap here would be invisible without this.
    assertThat(((Number) costs.get("ST_OTHER")).intValue()).isEqualTo(-1500);
    assertThat(((Number) costs.get("ST_TT")).intValue()).isEqualTo(2500);
    assertThat(((Number) costs.get("ST_ACTUAL")).intValue()).isEqualTo(40000);

    // Cost rows hang off the road detail, never off a summary row — Schedule 10 has no category
    // row.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?"
                    + " AND ILCR_REPORT_SUMMARY_ID IS NOT NULL",
                Integer.class,
                detailId))
        .isZero();
  }

  @Test
  @DisplayName("a multi-candidate classification resolves by the documented tie-break")
  void multipleCandidatesResolveByGradient() throws Exception {
    int pageId = newPage("Detail TieBreak");

    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8802, "2", "C", "TieBreak Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    // BEC 8802 + RSMR '2' offers SD/Moist and F/Moist. The gradient orders SD before F, so the
    // driest candidate wins. Legacy left this choice to a dropdown that no longer exists.
    var stored =
        jdbc.queryForMap(
            "SELECT ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(stored)
        .containsEntry("RELATIVE_SOIL_MOISTUR_RGM_CODE", "SD")
        .containsEntry("ILCR_SOIL_MOISTURE_CODE", "Moist");
  }

  @Test
  @DisplayName(
      "a classification the cross-reference cannot resolve is rejected, and nothing persists")
  void unresolvableClassificationIsRejected() throws Exception {
    int pageId = newPage("Detail Unresolvable");

    // BEC 8801 + RSMR '2' exists only through an INACTIVE link, so it resolves to nothing.
    mockMvc
        .perform(
            post(PAGES + "/" + pageId + "/road-details")
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(detailJson(8801, "2", "C", "Inactive Link Road")))
        .andExpect(status().isBadRequest());

    // BEC 8803 is in the catalogue but absent from the gate, so it is not offerable at all.
    mockMvc
        .perform(
            post(PAGES + "/" + pageId + "/road-details")
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(detailJson(8803, "1", "C", "Not Offerable Road")))
        .andExpect(status().isBadRequest());

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
                Integer.class,
                pageId))
        .isZero();
  }

  @Test
  @DisplayName("ballast method N zeroes the stabilizing figures and forces the material code")
  void ballastNotRequiredZeroesStabilizing() throws Exception {
    int pageId = newPage("Detail Ballast N");

    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8801, "1", "N", "Ballast N Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    var stored =
        jdbc.queryForMap(
            "SELECT ILCR_ROAD_BALLAST_MATERL_CODE, STABILIZING_LENGTH, STABILIZING_SURFACE_WIDTH,"
                + " STABILIZING_DEPTH, STABILIZING_DISTANCE_TO_SOURCE"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    // The client sent 'GR' and real dimensions; legacy's save-time rule overrides all of them.
    //
    // ALL FOUR dimensions are asserted, and by VALUE rather than through intValue(). Code review
    // 2026-08-18 found the old form vacuous: the request sends depth 0.3 into a NUMBER(3,1) column,
    // so if the zeroing were deleted the stored 0.3 still gave intValue() == 0 and the assertion
    // passed either way. Surface width and distance to source were selected and never asserted at
    // all, so three of the four dimensions were unpinned.
    assertThat(stored).containsEntry("ILCR_ROAD_BALLAST_MATERL_CODE", "NA");
    assertThat((BigDecimal) stored.get("STABILIZING_LENGTH")).isEqualByComparingTo("0");
    assertThat((BigDecimal) stored.get("STABILIZING_SURFACE_WIDTH")).isEqualByComparingTo("0");
    assertThat((BigDecimal) stored.get("STABILIZING_DEPTH")).isEqualByComparingTo("0");
    assertThat((BigDecimal) stored.get("STABILIZING_DISTANCE_TO_SOURCE")).isEqualByComparingTo("0");

    Map<String, Object> costs =
        jdbc.queryForMap(
            "SELECT SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 22 THEN COST END) AS ST_ACTUAL,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 9  THEN COST END) AS ST_OTHER,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 10 THEN COST END) AS ST_TT"
                + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(((Number) costs.get("ST_ACTUAL")).intValue()).isZero();
    assertThat(((Number) costs.get("ST_OTHER")).intValue()).isZero();
    // The tree-to-truck transfer is deliberately NOT zeroed — legacy re-converts only the other
    // two.
    assertThat(((Number) costs.get("ST_TT")).intValue()).isEqualTo(2500);
  }

  @Test
  @DisplayName("an edit updates cost lines IN PLACE rather than inserting a second row")
  void editUpdatesCostLinesInPlace() throws Exception {
    // Detail 8969 is seeded at revision 5 with ONE existing cost line, so the upsert's UPDATE
    // branch
    // runs. Asserting COUNT(*) = 1 for that item is what pins update-in-place over a second insert.
    String edit =
        """
        {"roadName":"Edited Road","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","sideSlopePct":30,"detailedEngineeringCostInd":"Y",
         "subGrade":{"actualCost":777000},
         "stabilizing":{"ballastMethodCode":"C","ballastMaterialCode":"GR"},
         "materialComposition":{"solidRockPct":10,"rippableRockPct":20,"coarsePct":40,
                                "finePct":20,"organicPct":10},
         "comments":"Edited","revisionCount":5}
        """;
    mockMvc
        .perform(
            put(PAGES + "/8956/road-details/8969")
                .param("millId", "717")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
                    + " AND ILCR_REPORT_COST_ITEM_ID = 20",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
                    + " AND ILCR_REPORT_COST_ITEM_ID = 20",
                Integer.class))
        .isEqualTo(777000);
    // A cost the client omitted is cleared IN PLACE, never deleted — legacy keeps the row.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
                    + " AND ILCR_REPORT_COST_ITEM_ID = 3 AND COST IS NULL",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969",
                Integer.class))
        .isEqualTo(6);
  }

  @Test
  @DisplayName("ballast method D forces the material code but KEEPS every figure, unlike N")
  void ballastDeferredKeepsFiguresButForcesMaterial() throws Exception {
    // The documented asymmetric branch had no test at all: only N and C appeared anywhere, so
    // folding
    // D into the N branch — silently zeroing four real dimensions and two real costs — was
    // invisible
    // to the suite (code review 2026-08-18). Legacy zeroes for N ONLY; its else branch stores D's
    // figures as submitted and forces only the material code (Schedule10DAO:188-208).
    int pageId = newPage("Detail Ballast D");

    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8801, "1", "D", "Ballast D Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    var stored =
        jdbc.queryForMap(
            "SELECT ILCR_ROAD_BALLAST_MATERL_CODE, STABILIZING_LENGTH, STABILIZING_SURFACE_WIDTH,"
                + " STABILIZING_DEPTH, STABILIZING_DISTANCE_TO_SOURCE"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    // The material code IS forced, exactly as for N.
    assertThat(stored).containsEntry("ILCR_ROAD_BALLAST_MATERL_CODE", "NA");
    // The four dimensions are NOT zeroed — this is the whole asymmetry.
    assertThat((BigDecimal) stored.get("STABILIZING_LENGTH")).isEqualByComparingTo("3.000");
    assertThat((BigDecimal) stored.get("STABILIZING_SURFACE_WIDTH")).isEqualByComparingTo("6.5");
    assertThat((BigDecimal) stored.get("STABILIZING_DEPTH")).isEqualByComparingTo("0.3");
    assertThat((BigDecimal) stored.get("STABILIZING_DISTANCE_TO_SOURCE"))
        .isEqualByComparingTo("12.4");

    // And neither stabilizing cost is zeroed.
    Map<String, Object> costs =
        jdbc.queryForMap(
            "SELECT SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 22 THEN COST END) AS ST_ACTUAL,"
                + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 9  THEN COST END) AS ST_OTHER"
                + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(((Number) costs.get("ST_ACTUAL")).intValue()).isEqualTo(40000);
    assertThat(((Number) costs.get("ST_OTHER")).intValue()).isEqualTo(-1500);
  }

  @Test
  @DisplayName("unknown detail codes are rejected as 400s, not left to the foreign keys")
  void unknownDetailCodesAreRejected() throws Exception {
    // Only the forest-region guard had a negative test. Every road-detail request in the repo sent
    // offered codes, so deleting the road-lifetime, RSMR or ballast-method guard left the suite
    // green
    // while the code reached Oracle as an opaque 500 (code review 2026-08-18).
    int pageId = newPage("Detail Bad Codes");
    String base = PAGES + "/" + pageId + "/road-details";

    // Road type (ILCR_ROAD_LIFETIME_CODE) — an enabled FK sits behind it.
    mockMvc
        .perform(
            post(base)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Bad Lifetime","roadLifetimeCode":"ZZ","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", containsString("A valid value must be selected from the list.")));

    // RSMR class.
    mockMvc
        .perform(
            post(base)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Bad RSMR","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"ZZ","stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isBadRequest());

    // Ballast method.
    mockMvc
        .perform(
            post(base)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Bad Ballast","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"ZZ"}}
                """))
        .andExpect(status().isBadRequest());

    // Ballast material, on the branch that requires one.
    mockMvc
        .perform(
            post(base)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Bad Material","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1",
                 "stabilizing":{"ballastMethodCode":"C","ballastMaterialCode":"ZZ"}}
                """))
        .andExpect(status().isBadRequest());

    // Nothing persisted by any of the four.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
                Integer.class,
                pageId))
        .isZero();
  }

  @Test
  @DisplayName("a stale revision on a road detail is a 409, and an unknown id is still a 404")
  void staleRevisionOnRoadDetailIsConflict() throws Exception {
    // The road-detail optimistic lock had NO test at all — the only stale-revision test in the
    // story
    // was at page level, so updateRoadDetail's `updated == 0` branch and its 404-vs-409
    // disambiguation never ran (code review 2026-08-18).
    int pageId = newPage("Detail Stale Rev");
    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8801, "1", "N", "Stale Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    String edit =
        """
        {"roadName":"Stale Edit","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"},"revisionCount":99}
        """;

    // Same row, wrong token — a conflict, NOT a not-found.
    mockMvc
        .perform(
            put(PAGES + "/" + pageId + "/road-details/" + detailId)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isConflict());

    // The row is untouched by the refused edit.
    assertThat(
            jdbc.queryForObject(
                "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
                String.class,
                detailId))
        .isEqualTo("Stale Road");

    // The other half of the disambiguation: an id that does not exist is a 404, not a 409.
    mockMvc
        .perform(
            put(PAGES + "/" + pageId + "/road-details/999998")
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("an edit re-stamps UPDATE_* on the detail AND its cost rows, leaving ENTRY_* alone")
  void editRestampsUpdateColumnsOnBothTables() throws Exception {
    // Code review 2026-08-18: UPDATE_TIMESTAMP re-stamping was asserted on NO table, and the cost
    // table's audit columns were asserted nowhere at all — and because they are nullable/defaulted
    // in
    // the test schema, removing all five from insertCostLine was silently green.
    //
    // Backdating first is what makes this non-flaky: SYSDATE has one-second granularity, so an
    // immediate edit can produce an identical timestamp and a "moved forward" assertion would flap.
    int pageId = newPage("Detail Audit Restamp");
    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8801, "1", "N", "Audit Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    // All five audit/revision columns are populated on INSERT, on BOTH tables.
    var inserted =
        jdbc.queryForMap(
            "SELECT REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, ENTRY_TIMESTAMP, UPDATE_TIMESTAMP"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(((Number) inserted.get("REVISION_COUNT")).intValue()).isZero();
    assertThat(inserted)
        .containsEntry("ENTRY_USERID", "dev-submitter")
        .containsEntry("UPDATE_USERID", "dev-submitter");

    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ? AND ENTRY_USERID = 'dev-submitter'"
                    + " AND UPDATE_USERID = 'dev-submitter' AND REVISION_COUNT = 0"
                    + " AND ENTRY_TIMESTAMP IS NOT NULL AND UPDATE_TIMESTAMP IS NOT NULL",
                Integer.class,
                detailId))
        .as("every cost row carries all five audit/revision columns on insert")
        .isEqualTo(12);

    // Backdate both tables, then edit.
    jdbc.update(
        "UPDATE THE.ROAD_CONSTRUCTION_REPRT_DTL SET UPDATE_TIMESTAMP = DATE '2000-01-01',"
            + " UPDATE_USERID = 'STALE' WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        detailId);
    jdbc.update(
        "UPDATE THE.ILCR_COST_REPORT_DETAIL SET UPDATE_TIMESTAMP = TIMESTAMP"
            + " '2000-01-01 00:00:00', UPDATE_USERID = 'STALE'"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        detailId);
    var before =
        jdbc.queryForMap(
            "SELECT ENTRY_USERID, ENTRY_TIMESTAMP FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);

    mockMvc
        .perform(
            put(PAGES + "/" + pageId + "/road-details/" + detailId)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Audit Road Edited","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","subGrade":{"actualCost":123},
                 "stabilizing":{"ballastMethodCode":"N"},"revisionCount":0}
                """))
        .andExpect(status().isOk());

    var after =
        jdbc.queryForMap(
            "SELECT ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(after).containsEntry("UPDATE_USERID", "dev-submitter");
    assertThat((Timestamp) after.get("UPDATE_TIMESTAMP"))
        .as("UPDATE_TIMESTAMP is re-stamped, not left at the backdated value")
        .isAfter(Timestamp.valueOf("2000-01-02 00:00:00"));
    assertThat(after)
        .containsEntry("ENTRY_USERID", before.get("ENTRY_USERID"))
        .containsEntry("ENTRY_TIMESTAMP", before.get("ENTRY_TIMESTAMP"));

    // The cost rows the edit touched are re-stamped too; none is left at the backdated value.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ? AND UPDATE_USERID = 'STALE'",
                Integer.class,
                detailId))
        .as("no cost row keeps the stale update stamp")
        .isZero();
  }

  @Test
  @DisplayName("an edit that does not change the classification PRESERVES the stored moisture pair")
  void unchangedClassificationPreservesMoistureCodes() throws Exception {
    // Legacy never rewrites these two columns on a save: filterMoistureCodeLists() rebuilds the two
    // dropdown LISTS and never assigns, so the codes move only when the user picks new ones. BEC
    // 8802
    // + RSMR '2' has SEVERAL xref candidates, so a re-derivation would apply the driest-candidate
    // tie-break and visibly change the stored pair (code review 2026-08-18, decision D4).
    int pageId = newPage("Detail Moisture");
    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(detailJson(8802, "2", "N", "Moisture Road")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    // Force a stored pair the tie-break would NOT choose, so preservation is observable.
    jdbc.update(
        "UPDATE THE.ROAD_CONSTRUCTION_REPRT_DTL"
            + " SET RELATIVE_SOIL_MOISTUR_RGM_CODE = 'F', ILCR_SOIL_MOISTURE_CODE = 'Moist'"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        detailId);

    // An edit that touches only the comment, with the classification resubmitted unchanged.
    mockMvc
        .perform(
            put(PAGES + "/" + pageId + "/road-details/" + detailId)
                .param("millId", MILL)
                .param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"Moisture Road","roadLifetimeCode":"P","becbiogeoCatalogueId":8802,
                 "relSoilMoistRgmClsCode":"2","stabilizing":{"ballastMethodCode":"N"},
                 "comments":"only the comment changed","revisionCount":0}
                """))
        .andExpect(status().isOk());

    var stored =
        jdbc.queryForMap(
            "SELECT RELATIVE_SOIL_MOISTUR_RGM_CODE, ILCR_SOIL_MOISTURE_CODE"
                + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
            detailId);
    assertThat(stored)
        .as("an unrelated edit must not rewrite the derived moisture pair")
        .containsEntry("RELATIVE_SOIL_MOISTUR_RGM_CODE", "F")
        .containsEntry("ILCR_SOIL_MOISTURE_CODE", "Moist");
  }

  @Test
  @DisplayName("an omitted detailed-engineering indicator defaults to N rather than failing")
  void omittedEngineeringIndicatorDefaultsToN() throws Exception {
    // DETAIL_ENGINEERING_COST_IND is NOT NULL, the field is optional, and @Pattern passes null — so
    // omitting it reached Oracle as ORA-01400 and surfaced as an opaque 500. Legacy's
    // pageDtlECIncludeCosts is a two-item dropdown with no empty option, so its effective default
    // is
    // N (code review 2026-08-18).
    int pageId = newPage("Detail Eng Default");
    String body =
        mockMvc
            .perform(
                post(PAGES + "/" + pageId + "/road-details")
                    .param("millId", MILL)
                    .param("year", YEAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(
                        """
                    {"roadName":"No Indicator","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                     "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"}}
                    """))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    assertThat(
            jdbc.queryForObject(
                "SELECT DETAIL_ENGINEERING_COST_IND FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
                String.class,
                detailId))
        .isEqualTo("N");
  }

  @Test
  @DisplayName("a road detail cannot be edited through another page's path")
  void detailIsScopedToItsParentPage() throws Exception {
    String edit =
        """
        {"roadName":"Wrong Parent","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"},"revisionCount":0}
        """;
    // Detail 8968 belongs to page 8955. Addressing it under page 8953 must not reach it.
    mockMvc
        .perform(
            put(PAGES + "/8953/road-details/8968")
                .param("millId", "723")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isNotFound());

    assertThat(
            jdbc.queryForObject(
                "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8968",
                String.class))
        .isEqualTo("Neighbour Road");
  }
}
