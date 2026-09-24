package ca.bc.gov.nrs.ilcr.schedule11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.security.CognitoGroupsJwtAuthenticationConverter;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — Story 26.2 AC 2/4/5/6/8/9: the ministry corrects a Submitted Schedule 11 on the
 * Licensee's own page, through the page-level save, and no track moves. Security ON: the admin's
 * authorities come through the production {@link CognitoGroupsJwtAuthenticationConverter}. The test
 * post-processor names the token from its {@code sub} rather than the converter's {@code
 * custom:idp_username}, so both carry the one audit name the write must stamp.
 *
 * <p>Fixtures ({@code R__58}): 802 is written, by one test that runs the whole correction story in
 * order (so JUnit's method order cannot split it); 803, 804 and 805 are never written — every arm
 * on them refuses, and "nothing written" is proven by reading the rows before and after; 806
 * (silviculture {@code D}) is written only by the Licensee-at-Draft arm. On every mill but 806 the
 * silviculture track is {@code S} while Schedules 1–10 are {@code D}: a write that reached the
 * wrong track's status or categories shows here instead of coinciding with the right answer.
 *
 * <p><strong>The echoed {@code trackStatus} is not evidence the status did not move</strong> — it
 * is the gate's own pre-write read. The status row and the category rows are READ, before and
 * after.
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 11 — ministry correction at Submitted (Story 26.2)")
class Schedule11CorrectionIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/schedule11";
  private static final String LOCATIONS = ENDPOINT + "/locations";
  private static final String CHECK_STATUS = ENDPOINT + "/check-status";
  private static final String ADMIN_NAME = "IDIR\\CORRECTOR";
  private static final CognitoGroupsJwtAuthenticationConverter CONVERTER =
      new CognitoGroupsJwtAuthenticationConverter();

  private static final String SAVED = "Data saved successfully";
  private static final String STALE =
      "This schedule was changed by another user. Please reload and try again.";
  private static final String NOT_EDITABLE =
      "This schedule cannot be edited in its current status.";
  private static final String NOT_FOUND = "Location not found.";

  @MockitoBean private JwtDecoder jwtDecoder;
  @Autowired private JdbcTemplate jdbc;
  private final ObjectMapper mapper = new ObjectMapper();

  private static RequestPostProcessor admin() {
    return jwt()
        .jwt(
            j ->
                j.subject(ADMIN_NAME)
                    .claim("cognito:groups", List.of("ILCR_ADMIN"))
                    .claim("custom:idp_username", ADMIN_NAME)
                    .claim("custom:idp_user_id", "CORRECTORADMIN00000000000000001"))
        .authorities(j -> CONVERTER.convert(j).getAuthorities());
  }

  private static MockHttpServletRequestBuilder save(long mill, String body) {
    return put(LOCATIONS)
        .with(csrf())
        .param("millId", String.valueOf(mill))
        .param("year", "2021")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private static String saveBody(String items, String deletedIds) {
    return "{\"locations\":[" + items + "],\"deletedIds\":[" + deletedIds + "]}";
  }

  private static String item(long id, String location) {
    return "{\"basicSilvicultureReportId\":" + id + ",\"location\":" + location + "}";
  }

  private static String fields(String location, long bec, String nar, String actual, int rev) {
    return """
        {"location":"%s","enhancedIndicator":false,"biogeoclimaticCatalogueId":%d,"netArea":%s,
         "actualCost":%s,"plannedCost":2000,"revisionCount":%d}
        """
        .formatted(location, bec, nar, actual, rev);
  }

  private Map<String, Object> statusRow(long mill) {
    return jdbc.queryForMap(
        "SELECT ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, REVISION_COUNT,"
            + " UPDATE_USERID, UPDATE_TIMESTAMP, LICENSEE_USER_GUID, AUDITOR_USER_GUID"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        mill);
  }

  private List<Map<String, Object>> categories(long mill) {
    return jdbc.queryForList(
        "SELECT ILCR_CATEGORY_ID, CATEGORY_STATE_CODE, REVISION_COUNT, UPDATE_USERID,"
            + " UPDATE_TIMESTAMP FROM THE.ILCR_REPORT_CATEGORY"
            + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021 ORDER BY TO_NUMBER(ILCR_CATEGORY_ID)",
        mill);
  }

  /** Every Schedule 11 location and cost row of the mill, and every audit row, as one string. */
  private String footprint(long mill) {
    return jdbc.queryForList(
                "SELECT BASIC_SILVICULTURE_REPORT_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID,"
                    + " REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, UPDATE_USERID,"
                    + " UPDATE_TIMESTAMP FROM THE.BASIC_SILVICULTURE_REPORT"
                    + " WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021"
                    + " ORDER BY BASIC_SILVICULTURE_REPORT_ID",
                mill)
            .toString()
        + jdbc.queryForList(
                "SELECT d.ILCR_COST_REPORT_DETAIL_ID, d.ILCR_REPORT_COST_ITEM_ID, d.COST,"
                    + " d.UPDATE_USERID, d.UPDATE_TIMESTAMP FROM THE.ILCR_COST_REPORT_DETAIL d"
                    + " JOIN THE.BASIC_SILVICULTURE_REPORT b"
                    + " ON b.BASIC_SILVICULTURE_REPORT_ID = d.BASIC_SILVICULTURE_REPORT_ID"
                    + " WHERE b.ILCR_MILL_ID = ? AND b.REPORT_YEAR = 2021"
                    + " ORDER BY d.ILCR_COST_REPORT_DETAIL_ID",
                mill)
            .toString()
        + statusRow(mill)
        + categories(mill);
  }

  /** Count and latest touch of both audit tables: the application must never write one. */
  private String auditTables() {
    return jdbc.queryForObject(
            "SELECT COUNT(*) || '/' || NVL(TO_CHAR(MAX(UPDATE_TIMESTAMP), 'YYYYMMDDHH24MISS'),'-')"
                + " FROM THE.BASIC_SILVICULTURE_RPRT_AUD",
            String.class)
        + ";"
        + jdbc.queryForObject(
            "SELECT COUNT(*) || '/' || NVL(TO_CHAR(MAX(UPDATE_TIMESTAMP), 'YYYYMMDDHH24MISS'),'-')"
                + " FROM THE.ILCR_COST_REPORT_DETAIL_AUD",
            String.class);
  }

  private int revision(long locationId) {
    return jdbc.queryForObject(
        "SELECT REVISION_COUNT FROM THE.BASIC_SILVICULTURE_REPORT"
            + " WHERE BASIC_SILVICULTURE_REPORT_ID = ?",
        Integer.class,
        locationId);
  }

  private JsonNode document(long mill, RequestPostProcessor who) throws Exception {
    return mapper.readTree(
        mockMvc
            .perform(
                get(ENDPOINT)
                    .param("millId", String.valueOf(mill))
                    .param("year", "2021")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(who))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private static JsonNode location(JsonNode document, long id) {
    for (JsonNode loc : document.get("locations")) {
      if (loc.get("locationId").asLong() == id) {
        return loc;
      }
    }
    throw new IllegalStateException("location " + id + " not served");
  }

  @Test
  @DisplayName(
      "AC 4 + CHK-011 S07: admin at S adds, then bulk-saves an edit and a delete, twice — rows"
          + " written, no track moved, no audit row written, and the original stays the Licensee's")
  void correctionAtSubmitted_movesNoTrack_andKeepsTheSubmittedBaseline() throws Exception {
    Map<String, Object> statusBefore = statusRow(802);
    List<Map<String, Object>> categoriesBefore = categories(802);
    String auditBefore = auditTables();
    assertThat(statusBefore.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("S");
    assertThat(statusBefore.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("D");

    JsonNode served = document(802, admin());
    assertThat(served.get("editable").asBoolean()).as("admin may write at S (16.1)").isTrue();
    assertThat(served.get("trackStatus").asText()).isEqualTo("S");

    // Add persists at once, as legacy's addLocation() -> save(true) did.
    mockMvc
        .perform(
            post(LOCATIONS)
                .with(csrf())
                .param("millId", "802")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"location":"Ministry Added","enhancedIndicator":false,
                     "biogeoclimaticCatalogueId":8803,"netArea":3,"actualCost":100}
                    """)
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is(SAVED)));

    int rev = revision(9423);
    mockMvc
        .perform(
            save(802, saveBody(item(9423, fields("First Fix", 8801, "21", "1100", rev)), "9424"))
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is(SAVED)))
        .andExpect(jsonPath("$.trackStatus", is("S")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.locations[?(@.locationId==9424)]", hasSize(0)));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT LOCATION, REVISION_COUNT, UPDATE_USERID FROM THE.BASIC_SILVICULTURE_REPORT"
                + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9423");
    assertThat(row.get("LOCATION")).isEqualTo("First Fix");
    assertThat(((Number) row.get("REVISION_COUNT")).intValue()).isEqualTo(rev + 1);
    assertThat(row.get("UPDATE_USERID")).isEqualTo(ADMIN_NAME);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.BASIC_SILVICULTURE_REPORT"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9424",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9424",
                Integer.class))
        .as("the deleted location's whole cost family goes with it")
        .isZero();

    // A second correction of the same fields (CHK-011 S07).
    mockMvc
        .perform(
            save(802, saveBody(item(9423, fields("Second Fix", 8801, "22", "1200", rev + 1)), ""))
                .with(admin()))
        .andExpect(status().isOk());

    // No track moved, value for value, and no audit row was written by the application.
    assertThat(statusRow(802)).isEqualTo(statusBefore);
    assertThat(categories(802)).isEqualTo(categoriesBefore);
    assertThat(auditTables()).as("the application never writes *_AUD rows").isEqualTo(auditBefore);

    // So the baseline is still the Licensee's submission, however many corrections landed.
    JsonNode corrected = location(document(802, admin()), 9423);
    assertThat(corrected.get("location").asText()).isEqualTo("Second Fix");
    assertThat(corrected.at("/originalValues/location/value").asText()).isEqualTo("Submitted Name");
    assertThat(corrected.at("/originalValues/netArea/value").asText()).isEqualTo("18");
    assertThat(corrected.at("/originalValues/actualCost/value").asText())
        .as("the snapshot of detail 5833 itself, not the older 5899 row with the higher audit id")
        .isEqualTo("900");

    // A location the ministry ADDED at S has no snapshot: every tracked field is on file as empty.
    JsonNode added = null;
    for (JsonNode loc : document(802, admin()).get("locations")) {
      if ("Ministry Added".equals(loc.get("location").asText())) {
        added = loc;
      }
    }
    assertThat(added).isNotNull();
    for (String key :
        List.of("location", "biogeoclimaticCatalogueId", "netArea", "actualCost", "plannedCost")) {
      assertThat(added.at("/originalValues/" + key + "/value").asText()).as(key).isEmpty();
      assertThat(added.at("/originalValues/" + key + "/tooltip").asText())
          .isEqualTo("Original Submission Value: ");
    }
  }

  @Test
  @DisplayName("AC 2: a stale revisionCount on any row -> 409, and nothing is written")
  void staleRevision_409_writesNothing() throws Exception {
    String before = footprint(805);

    mockMvc
        .perform(
            save(805, saveBody(item(9428, fields("Stale", 8801, "10", "300", 0)), ""))
                .with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(STALE)));

    assertThat(footprint(805)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC 2: an unknown id, to update or to delete, -> 404, and nothing is written")
  void unknownId_404_writesNothing() throws Exception {
    String before = footprint(805);

    mockMvc
        .perform(
            save(805, saveBody(item(999999, fields("X", 8801, "1", "1", 0)), "")).with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(NOT_FOUND)));
    // A delete of a real row in the SAME request is rolled back with the refusal.
    mockMvc
        .perform(save(805, saveBody("", "9428,999999")).with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(NOT_FOUND)));

    assertThat(footprint(805)).isEqualTo(before);
  }

  static List<Object[]> refusals() {
    String ok = fields("Refusal Block", 8801, "9", "100", 0);
    return List.<Object[]>of(
        new Object[] {
          "blank location",
          item(9427, ok.replace("\"Refusal Block\"", "\"\"")),
          "Location: Value is required."
        },
        new Object[] {
          "location over 30",
          item(9427, ok.replace("Refusal Block", "x".repeat(31))),
          "Location must be 30 characters or fewer."
        },
        new Object[] {
          "missing enhanced",
          item(9427, ok.replace("\"enhancedIndicator\":false,", "")),
          "Enhanced: Value is required."
        },
        new Object[] {
          "missing BEC",
          item(9427, ok.replace("\"biogeoclimaticCatalogueId\":8801,", "")),
          "Biogeo/Subzone/Variant: Value is required."
        },
        new Object[] {
          "unresolvable BEC",
          item(9427, fields("Refusal Block", 999999, "9", "100", 0)),
          "Biogeo/Subzone/Variant code is invalid. The code must be corrected before the schedule"
              + " can be saved."
        },
        new Object[] {
          "missing NAR", item(9427, ok.replace("\"netArea\":9,", "")), "NAR(ha): Value is required."
        },
        new Object[] {
          "NAR over range",
          item(9427, fields("Refusal Block", 8801, "1000000", "100", 0)),
          "Entered NAR (ha) must be between 0 and 999,999.9."
        },
        new Object[] {
          "NAR below range",
          item(9427, fields("Refusal Block", 8801, "-1", "100", 0)),
          "Entered NAR (ha) must be between 0 and 999,999.9."
        },
        new Object[] {
          "cost over range",
          item(9427, fields("Refusal Block", 8801, "9", "100000000", 0)),
          "Entered cost must be between -99,999,999 and 99,999,999."
        },
        new Object[] {
          "comments over 3500",
          item(
              9427,
              ok.replace(
                  "\"revisionCount\":0",
                  "\"comments\":\"" + "c".repeat(3501) + "\",\"revisionCount\":0")),
          "Comments must be 3500 characters or fewer."
        },
        new Object[] {
          "missing revisionCount",
          item(9427, ok.replace(",\"revisionCount\":0", "")),
          "Revision count is required for an update."
        },
        new Object[] {
          "the same location twice",
          item(9427, ok) + "," + item(9427, ok),
          "The same location was submitted more than once."
        },
        new Object[] {"nothing to save", "", "There are no changes to save."});
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusals")
  @DisplayName(
      "AC 6: every refusal the Licensee gets holds for the admin at S — verbatim, nothing written")
  void refusalTable_asAdminAtSubmitted(String label, String items, String detail) throws Exception {
    String before = footprint(804);

    mockMvc
        .perform(save(804, saveBody(items, "")).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString(detail)));

    assertThat(footprint(804)).as(label).isEqualTo(before);
  }

  @Test
  @DisplayName("AC 6 (CHK-011 S17): several invalid fields in one save are all reported at once")
  void multiErrorBatch_reportsEveryFailure() throws Exception {
    String bad = item(9427, fields("", 8801, "1000000", "100000000", 0));

    mockMvc
        .perform(save(804, saveBody(bad, "")).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Location: Value is required.")))
        .andExpect(jsonPath("$.detail", containsString("Entered NAR (ha) must be between")))
        .andExpect(jsonPath("$.detail", containsString("Entered cost must be between")));
  }

  @Test
  @DisplayName("AC 2: a save that would duplicate the biogeo/location key -> 409, nothing written")
  void uniqueKeyClash_409_writesNothing() throws Exception {
    String before = footprint(803);

    // 9426 takes 9425's (BEC 8801, 'Missing Planned') key.
    mockMvc
        .perform(
            save(
                    803,
                    saveBody(
                        item(9426, fields("Missing Planned", 8801, "8", "800", revision(9426))),
                        ""))
                .with(admin()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath(
                "$.detail",
                is(
                    "Schedule could not be saved. The Biogeo/Subzone/Variant has to be unique for a"
                        + " location.")));

    assertThat(footprint(803)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 5: Check Status at Submitted flags the missing cost verbatim and mutates nothing")
  void checkStatusAtSubmitted_mutatesNothing() throws Exception {
    String before = footprint(803);

    mockMvc
        .perform(
            post(CHECK_STATUS)
                .with(csrf())
                .param("millId", "803")
                .param("year", "2021")
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(
            jsonPath(
                "$.errors[*].text",
                hasItem("location  : Missing Planned - Planned cost: Value Required")));

    assertThat(footprint(803)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 8: the admin at silviculture D is refused on Add and on the save; read-only page")
  void adminAtDraft_refused() throws Exception {
    String before = footprint(806);

    assertThat(document(806, admin()).get("editable").asBoolean()).isFalse();
    mockMvc
        .perform(
            save(806, saveBody(item(9429, fields("No", 8801, "11", "500", 0)), "")).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));
    mockMvc
        .perform(
            post(LOCATIONS)
                .with(csrf())
                .param("millId", "806")
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"location\":\"No\",\"enhancedIndicator\":false,"
                        + "\"biogeoclimaticCatalogueId\":8801,\"netArea\":1}")
                .with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));

    assertThat(footprint(806)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "AC 9: the Licensee at silviculture D saves an edit on the rebuilt page; at S, refused")
  void licensee_savesAtDraft_andIsRefusedAtSubmitted() throws Exception {
    int rev = revision(9429);
    mockMvc
        .perform(
            save(806, saveBody(item(9429, fields("Licensee Fix", 8801, "12", "550", rev)), ""))
                .with(canonicalSubmitter()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is(SAVED)))
        .andExpect(
            jsonPath("$.locations[?(@.locationId==9429)].location", hasItem("Licensee Fix")));

    String before = footprint(804);
    mockMvc
        .perform(
            save(804, saveBody(item(9427, fields("No", 8801, "9", "100", 0)), ""))
                .with(canonicalSubmitter()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));
    assertThat(footprint(804)).isEqualTo(before);
  }

  @Test
  @DisplayName("AC 8: the save's context guards — 400 no mill, 409 closed mill, 404 no status row")
  void contextGuards_onTheSave() throws Exception {
    String body = saveBody("", "9427");
    mockMvc
        .perform(
            put(LOCATIONS)
                .with(csrf())
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")));
    mockMvc
        .perform(save(516, body).with(admin()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath(
                "$.detail",
                is(
                    "This Mill is not active for the current Reporting Year. Please select another"
                        + " mill from the Home Page.")));
    mockMvc
        .perform(
            put(LOCATIONS)
                .with(csrf())
                .param("millId", "804")
                .param("year", "1999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(admin()))
        .andExpect(status().isNotFound());
  }
}
