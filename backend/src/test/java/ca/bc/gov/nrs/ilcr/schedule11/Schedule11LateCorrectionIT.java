package ca.bc.gov.nrs.ilcr.schedule11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Acceptance test — UC-CHK-015: the administrator makes a late correction to a Verified Schedule
 * 11, on the same page and through the same field checks the Licensee had, and the track stays
 * Verified. Legacy {@code UserSessionMB.disableUserInputSchedule11()} opened the page at {@code V}
 * to the administrator alone; everything after that gate — Save, Add, Delete, Check Status — is
 * status-blind in legacy and here, so this class mirrors {@link Schedule11CorrectionIT} at {@code
 * V} rather than testing anything new. What it adds is that {@code V} is SERVED: narrowing the
 * administrator's editable statuses to {@code S} fails the write arms below.
 *
 * <p>Fixtures ({@code R__61}), all silviculture {@code V} with category {@code '11'} at {@code
 * 'V'}: 817 is written, by the one test that runs the whole correction in order; 818–822 are never
 * written, and "nothing written" is proven by reading the rows before and after. On 817 the 1–10
 * track is {@code S}, so a write that reached the wrong track's status shows here.
 *
 * <p><strong>What this can and cannot prove.</strong> The test schema has no audit trigger, so the
 * {@code 'S'} snapshot is seeded. This class proves the READ of it at {@code V} and that the
 * application writes no {@code *_AUD} row and no category row; that a delivery save at {@code V} is
 * stamped {@code 'V'} and so never displaces the snapshot rests on the trigger mapping ({@code
 * V20260910}'s header).
 */
@TestPropertySource(properties = "ilcr.security.enabled=true")
@DisplayName("Schedule 11 — late correction at Verified (UC-CHK-015)")
class Schedule11LateCorrectionIT extends Schedule11CorrectionSupport {

  private static final String REQUIREMENTS_MET = "All requirements for this schedule have been met";
  private static final String STATUS_CHECKED = "Status has been checked";

  /** Both halves of both recorded user pairs — {@code statusRow} carries only the GUIDs. */
  private Map<String, Object> pairs(long mill) {
    return jdbc.queryForMap(
        "SELECT LICENSEE_MILL_ID, LICENSEE_USER_GUID, AUDITOR_MILL_ID, AUDITOR_USER_GUID"
            + " FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID = ? AND REPORT_YEAR = 2021",
        mill);
  }

  private Map<String, Object> row(long locationId) {
    return jdbc.queryForMap(
        "SELECT LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, REVISION_COUNT,"
            + " UPDATE_USERID, UPDATE_TIMESTAMP FROM THE.BASIC_SILVICULTURE_REPORT"
            + " WHERE BASIC_SILVICULTURE_REPORT_ID = ?",
        locationId);
  }

  private static void assertOriginal(JsonNode loc, String key, String value, String shown) {
    assertThat(loc.at("/originalValues/" + key + "/value").asText()).as(key).isEqualTo(value);
    assertThat(loc.at("/originalValues/" + key + "/tooltip").asText())
        .as(key)
        .isEqualTo("Original Submission Value: " + shown);
  }

  private static MockHttpServletRequestBuilder checkStatus(long mill) {
    return post(CHECK_STATUS)
        .with(csrf())
        .param("millId", String.valueOf(mill))
        .param("year", "2021");
  }

  @Test
  @DisplayName(
      "S01/S05/S08: admin at V adds, then bulk-saves an edit and a delete, then corrects again —"
          + " rows written, no track moved, no audit row, and the baseline stays the Licensee's")
  void lateCorrectionAtVerified_movesNoTrack_andKeepsTheSubmittedBaseline() throws Exception {
    Map<String, Object> statusBefore = statusRow(817);
    Map<String, Object> pairsBefore = pairs(817);
    List<Map<String, Object>> categoriesBefore = categories(817);
    String auditBefore = auditTables();
    Map<String, Object> untouchedBefore = row(9445);
    assertThat(statusBefore.get("MILL_SILVICULTUR_STATUS_CODE")).isEqualTo("V");
    assertThat(statusBefore.get("ILCR_MILL_REPORT_STATUS_CODE")).isEqualTo("S");
    assertThat(pairsBefore.get("LICENSEE_USER_GUID")).isNotNull();
    assertThat(pairsBefore.get("AUDITOR_USER_GUID")).isNotNull();

    JsonNode served = document(817, admin());
    assertThat(served.get("editable").asBoolean()).as("admin may write at V (16.1)").isTrue();
    assertThat(served.get("trackStatus").asText()).isEqualTo("V");

    // S04: Add persists at once, as legacy's addLocation() -> save(true) did.
    mockMvc
        .perform(
            add(
                    817,
                    """
                    {"location":"Late Ministry Added","enhancedIndicator":false,
                     "biogeoclimaticCatalogueId":8803,"netArea":3,"actualCost":100}
                    """)
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is(SAVED)))
        .andExpect(jsonPath("$.trackStatus", is("V")))
        .andExpect(jsonPath("$.editable", is(true)));

    // S05: 9443 takes over 9444's biogeo+location ('Late Delete Me', 8802) in the SAME save that
    // deletes 9444 — valid only because the deletes run first, against the live BSRPT_BSRPT_UK_UK.
    int rev = revision(9443);
    mockMvc
        .perform(
            save(
                    817,
                    saveBody(item(9443, fields("Late Delete Me", 8802, "21", "1100", rev)), "9444"))
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
        .andExpect(jsonPath("$.message.text", is(SAVED)))
        .andExpect(jsonPath("$.trackStatus", is("V")))
        .andExpect(jsonPath("$.editable", is(true)))
        .andExpect(jsonPath("$.locations[?(@.locationId==9444)]", hasSize(0)));

    Map<String, Object> edited = row(9443);
    assertThat(edited.get("LOCATION")).isEqualTo("Late Delete Me");
    assertThat(((Number) edited.get("BECBIOGEOCLIMATIC_CATALOGUE_ID")).longValue())
        .isEqualTo(8802L);
    assertThat(((Number) edited.get("REFORESTED_NET_AREA")).intValue()).isEqualTo(21);
    assertThat(((Number) edited.get("REVISION_COUNT")).intValue()).isEqualTo(rev + 1);
    assertThat(edited.get("UPDATE_USERID")).isEqualTo(ADMIN_NAME);
    assertThat(
            jdbc.queryForObject(
                "SELECT COST FROM THE.ILCR_COST_REPORT_DETAIL WHERE ILCR_COST_REPORT_DETAIL_ID ="
                    + " 5874",
                Integer.class))
        .isEqualTo(1100);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.BASIC_SILVICULTURE_REPORT"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9444",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL"
                    + " WHERE BASIC_SILVICULTURE_REPORT_ID = 9444",
                Integer.class))
        .as("the deleted location's whole cost family goes with it")
        .isZero();

    // S08: a second correction of the same fields.
    mockMvc
        .perform(
            save(
                    817,
                    saveBody(
                        item(9443, fields("Late Second Fix", 8801, "22", "1200", rev + 1)), ""))
                .with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message.text", is(SAVED)))
        .andExpect(jsonPath("$.trackStatus", is("V")))
        .andExpect(jsonPath("$.editable", is(true)));
    assertThat(((Number) row(9443).get("REVISION_COUNT")).intValue()).isEqualTo(rev + 2);

    // The row nobody sent is untouched (the Save carries edited rows only, 26.2 deviation (E)).
    assertThat(row(9445)).isEqualTo(untouchedBefore);

    // No track moved, value for value, and no audit row was written by the application.
    assertThat(statusRow(817)).isEqualTo(statusBefore);
    assertThat(pairs(817)).as("the LICENSEE and AUDITOR pairs").isEqualTo(pairsBefore);
    assertThat(categories(817)).isEqualTo(categoriesBefore);
    assertThat(auditTables()).as("the application never writes *_AUD rows").isEqualTo(auditBefore);

    // So the baseline is still the Licensee's submission, for either role (read here, not in a
    // sibling test: JUnit promises no method order, and only this test writes 817).
    for (RequestPostProcessor who : List.of(admin(), canonicalSubmitter())) {
      JsonNode doc = document(817, who);
      JsonNode corrected = location(doc, 9443);
      assertThat(corrected.get("location").asText()).isEqualTo("Late Second Fix");
      assertOriginal(corrected, "location", "Late Submitted Name", "Late Submitted Name");
      assertOriginal(corrected, "biogeoclimaticCatalogueId", "8802", "CWHvm");
      assertOriginal(corrected, "netArea", "18", "18.0");
      // The snapshot of detail 5874 itself, not the older 5898 row with the higher audit id.
      assertOriginal(corrected, "actualCost", "900", "900");
      assertOriginal(corrected, "plannedCost", "2000", "2,000");

      // A location the administrator ADDED at V has no snapshot: every tracked field on file empty.
      JsonNode added = null;
      for (JsonNode loc : doc.get("locations")) {
        if ("Late Ministry Added".equals(loc.get("location").asText())) {
          added = loc;
        }
      }
      assertThat(added).isNotNull();
      for (String key : TRACKED) {
        assertOriginal(added, key, "", "");
      }
    }
  }

  static List<Object[]> refusals() {
    return refusalsFor(9449);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusals")
  @DisplayName(
      "S13–S18: every bulk-save refusal holds for the admin at V — verbatim, nothing written")
  void refusalTable_asAdminAtVerified(String label, String items, String detail) throws Exception {
    String before = footprint(820);

    mockMvc
        .perform(save(820, saveBody(items, "")).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString(detail)));

    assertThat(footprint(820)).as(label).isEqualTo(before);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("addRefusals")
  @DisplayName("S13–S18: the same field refusals hold on Add for the admin at V — nothing written")
  void addRefusalTable_asAdminAtVerified(String label, String location, String detail)
      throws Exception {
    String before = footprint(820);

    mockMvc
        .perform(add(820, location).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString(detail)));

    assertThat(footprint(820)).as(label).isEqualTo(before);
  }

  static List<Object[]> malformedBodies() {
    return malformedBodiesFor(9449);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedBodies")
  @DisplayName("a malformed save body at V is a 400 naming the missing value, never a 500")
  void malformedBody_400_writesNothing(String label, String body) throws Exception {
    String before = footprint(820);

    mockMvc
        .perform(save(820, body).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Value Required")));

    assertThat(footprint(820)).as(label).isEqualTo(before);
  }

  @Test
  @DisplayName("S19: several invalid fields in one save at V are all reported at once")
  void multiErrorBatch_reportsEveryFailure() throws Exception {
    String before = footprint(820);
    String bad = item(9449, fields("", 8801, "1000000", "100000000", 0));

    mockMvc
        .perform(save(820, saveBody(bad, "")).with(admin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("Location: Value is required.")))
        .andExpect(jsonPath("$.detail", containsString("Entered NAR (ha) must be between")))
        .andExpect(jsonPath("$.detail", containsString("Entered cost must be between")));

    assertThat(footprint(820)).isEqualTo(before);
  }

  @Test
  @DisplayName("a save at V that would duplicate the biogeo/location key -> 409, nothing written")
  void uniqueKeyClash_409_writesNothing() throws Exception {
    String before = footprint(820);

    // 9450 takes 9449's (BEC 8801, 'Refusal Block') key.
    mockMvc
        .perform(
            save(
                    820,
                    saveBody(
                        item(9450, fields("Refusal Block", 8801, "8", "800", revision(9450))), ""))
                .with(admin()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath(
                "$.detail",
                is(
                    "Schedule could not be saved. The Biogeo/Subzone/Variant has to be unique for a"
                        + " location.")));

    assertThat(footprint(820)).isEqualTo(before);
  }

  @Test
  @DisplayName("a stale revisionCount at V -> 409 with the revision text, and nothing is written")
  void staleRevision_409_writesNothing() throws Exception {
    String before = footprint(821);

    mockMvc
        .perform(
            save(821, saveBody(item(9451, fields("Stale", 8801, "10", "300", 0)), ""))
                .with(admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(STALE)));

    assertThat(footprint(821)).isEqualTo(before);
  }

  @Test
  @DisplayName("an unknown id at V, to update or to delete, -> 404, and nothing is written")
  void unknownId_404_writesNothing() throws Exception {
    String before = footprint(821);

    mockMvc
        .perform(
            save(821, saveBody(item(999999, fields("X", 8801, "1", "1", 0)), "")).with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(NOT_FOUND)));
    // A delete of a real row in the SAME request is rolled back with the refusal.
    mockMvc
        .perform(save(821, saveBody("", "9451,999999")).with(admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail", is(NOT_FOUND)));

    assertThat(footprint(821)).isEqualTo(before);
  }

  @Test
  @DisplayName("S02/S03: Check Status at V flags each missing cost verbatim and mutates nothing")
  void checkStatusAtVerified_flagsMissingCosts() throws Exception {
    String before = footprint(818);

    mockMvc
        .perform(checkStatus(818).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(false)))
        .andExpect(jsonPath("$.errors", hasSize(2)))
        // Legacy's double space after "location", and Actual before Planned per row.
        .andExpect(
            jsonPath(
                "$.errors[0].text",
                is("location  : Late Missing Costs - Actual cost: Value Required")))
        .andExpect(
            jsonPath(
                "$.errors[1].text",
                is("location  : Late Missing Costs - Planned cost: Value Required")))
        .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist())
        .andExpect(jsonPath("$.message.key", is("checkStatusMessage")))
        .andExpect(jsonPath("$.message.text", is(STATUS_CHECKED)));

    assertThat(footprint(818)).isEqualTo(before);
  }

  @Test
  @DisplayName("S02: Check Status at V with every cost present -> all met, and mutates nothing")
  void checkStatusAtVerified_allMet() throws Exception {
    String before = footprint(819);

    mockMvc
        .perform(checkStatus(819).with(admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.errors", hasSize(0)))
        .andExpect(jsonPath("$.requirementsMetMessage.key", is("scheduleRequirementsMetMsg")))
        .andExpect(jsonPath("$.requirementsMetMessage.text", is(REQUIREMENTS_MET)))
        .andExpect(jsonPath("$.message.text", is(STATUS_CHECKED)));

    assertThat(footprint(819)).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "S12: the Licensee at V is read-only — Add and bulk save refused 409 by the one gate, nothing"
          + " written; Check Status still answers (the page, not the API, greys it)")
  void licenseeAtVerified_isReadOnly() throws Exception {
    String before = footprint(822);
    String auditBefore = auditTables();

    assertThat(document(822, canonicalSubmitter()).get("editable").asBoolean()).isFalse();
    // The TEXT, not only the status: a stale-revision 409 would otherwise pass for the gate's.
    mockMvc
        .perform(
            save(822, saveBody(item(9452, fields("No", 8801, "11", "500", 0)), ""))
                .with(canonicalSubmitter()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));
    mockMvc
        .perform(save(822, saveBody("", "9452")).with(canonicalSubmitter()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));
    mockMvc
        .perform(
            add(
                    822,
                    "{\"location\":\"No\",\"enhancedIndicator\":false,"
                        + "\"biogeoclimaticCatalogueId\":8801,\"netArea\":1}")
                .with(canonicalSubmitter()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail", is(NOT_EDITABLE)));
    mockMvc
        .perform(checkStatus(822).with(canonicalSubmitter()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requirementsMet", is(true)))
        .andExpect(jsonPath("$.message.text", is(STATUS_CHECKED)));

    assertThat(footprint(822)).isEqualTo(before);
    assertThat(auditTables()).isEqualTo(auditBefore);
  }

  /** The three requests, each as a fresh builder: MockMvc builders accumulate parameters. */
  static Stream<Arguments> guardedRequests() {
    String body = saveBody("", "9449");
    String location =
        "{\"location\":\"Guard\",\"enhancedIndicator\":false,"
            + "\"biogeoclimaticCatalogueId\":8801,\"netArea\":1}";
    return Stream.of(
        Arguments.of(
            "GET",
            (Supplier<MockHttpServletRequestBuilder>)
                () -> get(ENDPOINT).accept(MediaType.APPLICATION_JSON)),
        Arguments.of(
            "Add",
            (Supplier<MockHttpServletRequestBuilder>)
                () ->
                    post(LOCATIONS)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(location)),
        Arguments.of(
            "bulk save",
            (Supplier<MockHttpServletRequestBuilder>)
                () ->
                    put(LOCATIONS)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("guardedRequests")
  @DisplayName(
      "S09–S11: the context guards hold for the admin — 400 no/blank/bad mill or year, 409 closed"
          + " mill, 404 no status row")
  void contextGuards(String label, Supplier<MockHttpServletRequestBuilder> request)
      throws Exception {
    String before = footprint(820);
    String missing = "Please Select Mill and Reporting Year in the Home Page. ";

    for (String[] params :
        List.of(
            new String[] {null, "2021"},
            new String[] {"820", null},
            new String[] {" ", "2021"},
            new String[] {"820", " "},
            new String[] {"abc", "2021"},
            new String[] {"820", "abc"})) {
      MockHttpServletRequestBuilder r = request.get();
      if (params[0] != null) {
        r.param("millId", params[0]);
      }
      if (params[1] != null) {
        r.param("year", params[1]);
      }
      mockMvc
          .perform(r.with(admin()))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail", is(missing)));
    }
    mockMvc
        .perform(request.get().param("millId", "516").param("year", "2021").with(admin()))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath(
                "$.detail",
                is(
                    "This Mill is not active for the current Reporting Year. Please select another"
                        + " mill from the Home Page.")));
    mockMvc
        .perform(request.get().param("millId", "820").param("year", "1999").with(admin()))
        .andExpect(status().isNotFound());

    assertThat(footprint(820)).as(label).isEqualTo(before);
  }
}
