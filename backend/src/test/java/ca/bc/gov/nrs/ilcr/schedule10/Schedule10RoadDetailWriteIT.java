package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  @Autowired
  private JdbcTemplate jdbc;

  /** Creates a page in the shared context and returns its id. */
  private int newPage(String division) throws Exception {
    String body = mockMvc.perform(post(PAGES).param("millId", MILL).param("year", YEAR)
            .contentType(MediaType.APPLICATION_JSON).with(csrf())
            .content("""
                {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A",
                 "divisionName":"%s","constructionPeriod":"2021-06"}
                """.formatted(division)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
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
        """.formatted(roadName, becId, rsmr, ballast);
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
  @DisplayName("creates a road detail, derives both moisture codes, and writes all twelve cost lines")
  void createsRoadDetailWithDerivedMoistureAndCosts() throws Exception {
    int pageId = newPage("Detail Create");

    String body = mockMvc.perform(
            post(PAGES + "/" + pageId + "/road-details").param("millId", MILL).param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content(detailJson(8801, "1", "C", "Derived Road")))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    JsonNode detail = detailOf(body, pageId);
    int detailId = detail.get("roadDetailId").asInt();

    // The two removed fields must never appear on the wire, in either direction.
    assertThat(detail.path("asmCode").isMissingNode()).isTrue();
    assertThat(detail.path("soilMoistureCode").isMissingNode()).isTrue();
    assertThat(detail.path("materialComposition").path("boulderAreaPct").isMissingNode()).isTrue();

    // BEC 8801 + RSMR '1' resolves to exactly one pair through the cross-reference.
    var stored = jdbc.queryForMap(
        "SELECT ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, ENTRY_USERID,"
            + " UPDATE_USERID, REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?", detailId);
    assertThat(stored.get("ILCR_SOIL_MOISTURE_CODE")).isEqualTo("Dry");
    assertThat(stored.get("RELATIVE_SOIL_MOISTUR_RGM_CODE")).isEqualTo("MD");
    assertThat(stored.get("ENTRY_USERID")).isEqualTo("dev-submitter");
    assertThat(stored.get("UPDATE_USERID")).isEqualTo("dev-submitter");

    // All twelve ordinals, routed across all four subcategories.
    Map<String, Object> costs = jdbc.queryForMap(
        "SELECT COUNT(*) AS TOTAL,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 20 THEN COST END) AS SG_ACTUAL,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 3  THEN COST END) AS SG_TT,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 5  THEN COST END) AS SG_OTHER,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 4  THEN COST END) AS LESS_OTHER_ENG,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 9  THEN COST END) AS ST_OTHER,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 10 THEN COST END) AS ST_TT,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 22 THEN COST END) AS ST_ACTUAL"
            + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?", detailId);
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

    // Cost rows hang off the road detail, never off a summary row — Schedule 10 has no category row.
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?"
            + " AND ILCR_REPORT_SUMMARY_ID IS NOT NULL", Integer.class, detailId)).isZero();
  }

  @Test
  @DisplayName("a multi-candidate classification resolves by the documented tie-break")
  void multipleCandidatesResolveByGradient() throws Exception {
    int pageId = newPage("Detail TieBreak");

    String body = mockMvc.perform(
            post(PAGES + "/" + pageId + "/road-details").param("millId", MILL).param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content(detailJson(8802, "2", "C", "TieBreak Road")))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    // BEC 8802 + RSMR '2' offers SD/Moist and F/Moist. The gradient orders SD before F, so the
    // driest candidate wins. Legacy left this choice to a dropdown that no longer exists.
    var stored = jdbc.queryForMap(
        "SELECT ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE"
            + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        detailId);
    assertThat(stored.get("RELATIVE_SOIL_MOISTUR_RGM_CODE")).isEqualTo("SD");
    assertThat(stored.get("ILCR_SOIL_MOISTURE_CODE")).isEqualTo("Moist");
  }

  @Test
  @DisplayName("a classification the cross-reference cannot resolve is rejected, and nothing persists")
  void unresolvableClassificationIsRejected() throws Exception {
    int pageId = newPage("Detail Unresolvable");

    // BEC 8801 + RSMR '2' exists only through an INACTIVE link, so it resolves to nothing.
    mockMvc.perform(
            post(PAGES + "/" + pageId + "/road-details").param("millId", MILL).param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content(detailJson(8801, "2", "C", "Inactive Link Road")))
        .andExpect(status().isBadRequest());

    // BEC 8803 is in the catalogue but absent from the gate, so it is not offerable at all.
    mockMvc.perform(
            post(PAGES + "/" + pageId + "/road-details").param("millId", MILL).param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content(detailJson(8803, "1", "C", "Not Offerable Road")))
        .andExpect(status().isBadRequest());

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
        Integer.class, pageId)).isZero();
  }

  @Test
  @DisplayName("ballast method N zeroes the stabilizing figures and forces the material code")
  void ballastNotRequiredZeroesStabilizing() throws Exception {
    int pageId = newPage("Detail Ballast N");

    String body = mockMvc.perform(
            post(PAGES + "/" + pageId + "/road-details").param("millId", MILL).param("year", YEAR)
                .contentType(MediaType.APPLICATION_JSON).with(csrf())
                .content(detailJson(8801, "1", "N", "Ballast N Road")))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    int detailId = detailOf(body, pageId).get("roadDetailId").asInt();

    var stored = jdbc.queryForMap(
        "SELECT ILCR_ROAD_BALLAST_MATERL_CODE, STABILIZING_LENGTH, STABILIZING_SURFACE_WIDTH,"
            + " STABILIZING_DEPTH, STABILIZING_DISTANCE_TO_SOURCE"
            + " FROM THE.ROAD_CONSTRUCTION_REPRT_DTL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?",
        detailId);
    // The client sent 'GR' and real dimensions; legacy's save-time rule overrides all of them.
    assertThat(stored.get("ILCR_ROAD_BALLAST_MATERL_CODE")).isEqualTo("NA");
    assertThat(((Number) stored.get("STABILIZING_LENGTH")).intValue()).isZero();
    assertThat(((Number) stored.get("STABILIZING_DEPTH")).intValue()).isZero();

    Map<String, Object> costs = jdbc.queryForMap(
        "SELECT SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 22 THEN COST END) AS ST_ACTUAL,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 9  THEN COST END) AS ST_OTHER,"
            + " SUM(CASE WHEN ILCR_REPORT_COST_ITEM_ID = 10 THEN COST END) AS ST_TT"
            + " FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = ?", detailId);
    assertThat(((Number) costs.get("ST_ACTUAL")).intValue()).isZero();
    assertThat(((Number) costs.get("ST_OTHER")).intValue()).isZero();
    // The tree-to-truck transfer is deliberately NOT zeroed — legacy re-converts only the other two.
    assertThat(((Number) costs.get("ST_TT")).intValue()).isEqualTo(2500);
  }

  @Test
  @DisplayName("an edit updates cost lines IN PLACE rather than inserting a second row")
  void editUpdatesCostLinesInPlace() throws Exception {
    // Detail 8969 is seeded at revision 5 with ONE existing cost line, so the upsert's UPDATE branch
    // runs. Asserting COUNT(*) = 1 for that item is what pins update-in-place over a second insert.
    String edit = """
        {"roadName":"Edited Road","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","sideSlopePct":30,"detailedEngineeringCostInd":"Y",
         "subGrade":{"actualCost":777000},
         "stabilizing":{"ballastMethodCode":"C","ballastMaterialCode":"GR"},
         "materialComposition":{"solidRockPct":10,"rippableRockPct":20,"coarsePct":40,
                                "finePct":20,"organicPct":10},
         "comments":"Edited","revisionCount":5}
        """;
    mockMvc.perform(put(PAGES + "/8956/road-details/8969").param("millId", "717").param("year", "2019")
            .contentType(MediaType.APPLICATION_JSON).with(csrf()).content(edit))
        .andExpect(status().isOk());

    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
            + " AND ILCR_REPORT_COST_ITEM_ID = 20", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
            + " AND ILCR_REPORT_COST_ITEM_ID = 20", Integer.class)).isEqualTo(777000);
    // A cost the client omitted is cleared IN PLACE, never deleted — legacy keeps the row.
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969"
            + " AND ILCR_REPORT_COST_ITEM_ID = 3 AND COST IS NULL", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject(
        "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8969", Integer.class)).isEqualTo(6);
  }

  @Test
  @DisplayName("a road detail cannot be edited through another page's path")
  void detailIsScopedToItsParentPage() throws Exception {
    String edit = """
        {"roadName":"Wrong Parent","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"},"revisionCount":0}
        """;
    // Detail 8968 belongs to page 8955. Addressing it under page 8953 must not reach it.
    mockMvc.perform(put(PAGES + "/8953/road-details/8968").param("millId", "723").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).with(csrf()).content(edit))
        .andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject(
        "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
            + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8968", String.class))
        .isEqualTo("Neighbour Road");
  }
}
