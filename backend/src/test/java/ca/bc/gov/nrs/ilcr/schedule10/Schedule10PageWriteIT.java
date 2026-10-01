package ca.bc.gov.nrs.ilcr.schedule10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Acceptance test — Schedule 10 construction-page writes.
 *
 * <p>Security OFF so the mock {@code ILCR_SUBMITTER} principal applies; authorization is covered
 * separately.
 *
 * <p><strong>Isolation.</strong> A context is (mill, YEAR), so every destructive method below
 * claims its own year on mill 717 and no two tests can interfere regardless of execution order.
 * Story 11.1's mills 710–716 are never written to.
 */
@DisplayName("Schedule 10 — construction page writes")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule10PageWriteIT extends AbstractOracleIT {

  private static final String PAGES = "/api/v1/schedule10/pages";
  private static final String MILL = "717";
  private static final String NON_DRAFT_MILL = "718";
  private static final String NEIGHBOUR_MILL = "723";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private JdbcTemplate jdbc;

  private static String pageJson(String tsaOrTfl, String supplyBlock, String tfl, String division) {
    return """
        {"forestRegionCode":"RNI","tsaOrTfl":"%s","supplyBlock":%s,"tflNumberCode":%s,
         "divisionName":"%s","constructionPeriod":"2021-06"}
        """
        .formatted(tsaOrTfl, quoted(supplyBlock), quoted(tfl), division);
  }

  private static String quoted(String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  /** The page the response reports for a given division name, so tests never guess at ids. */
  private static JsonNode pageNamed(String body, String division) throws Exception {
    for (JsonNode page : MAPPER.readTree(body).get("pages")) {
      if (page.hasNonNull("divisionName") && division.equals(page.get("divisionName").asText())) {
        return page;
      }
    }
    throw new AssertionError("no page named " + division);
  }

  @Test
  @DisplayName("creates a TSA-located page, derives its Road Group, and stamps every audit column")
  void createsTsaLocatedPage() throws Exception {
    String body =
        mockMvc
            .perform(
                post(PAGES)
                    .param("millId", MILL)
                    .param("year", "2020")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(pageJson("01", "01A", null, "Created TSA")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
            .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode page = pageNamed(body, "Created TSA");
    int pageId = page.get("pageId").asInt();

    assertThat(page.get("roadGroup").asText()).isEqualTo("11");
    assertThat(page.get("revisionCount").asInt()).isZero();
    assertThat(page.get("roadDetailCount").asInt()).isZero();
    assertThat(page.path("tflNumberCode").isMissingNode()).isTrue();

    // The id must come from the sequence, not from anything hand-picked.
    assertThat(pageId).isGreaterThanOrEqualTo(9600);

    // All five NOT NULL audit/revision columns, which no trigger populates.
    //
    // The two timestamps are asserted by VALUE, not with isNotNull(): both columns are DATE NOT
    // NULL,
    // so isNotNull() is satisfied by any row that exists and constrained nothing — the real guard
    // was
    // status().isOk() failing on ORA-01400 (code review 2026-08-18). REVISION_COUNT is likewise
    // read
    // from the row rather than only from the echoed document.
    var stored =
        jdbc.queryForMap(
            "SELECT ILCR_CATEGORY_ID, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,"
                + " UPDATE_TIMESTAMP, CONSTRUCTION_DATE FROM THE.ROAD_CONSTRUCTION_REPRT"
                + " WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
            pageId);
    assertThat(stored)
        .containsEntry("ILCR_CATEGORY_ID", "10")
        .containsEntry("ENTRY_USERID", "dev-submitter")
        .containsEntry("UPDATE_USERID", "dev-submitter");
    assertThat(((Number) stored.get("REVISION_COUNT")).intValue()).isZero();
    // A fresh insert stamps both from the same SYSDATE, so they are equal — and both sit in the
    // present, which a defaulted or absent value would not.
    assertThat(stored).containsEntry("ENTRY_TIMESTAMP", stored.get("UPDATE_TIMESTAMP"));
    assertThat((Timestamp) stored.get("ENTRY_TIMESTAMP"))
        .isAfter(Timestamp.valueOf("2020-01-01 00:00:00"));
    // Legacy never writes this column and every real delivery page holds NULL.
    assertThat(stored.get("CONSTRUCTION_DATE")).isNull();
  }

  @Test
  @DisplayName("a TFL page clears the supply block server-side, even when the client sends one")
  void tflPageClearsSupplyBlockCounterpart() throws Exception {
    // The client deliberately sends BOTH a supply block and a TFL. Legacy enforced the exclusion in
    // the browser only and its DAO would store the inconsistent pair.
    String body =
        mockMvc
            .perform(
                post(PAGES)
                    .param("millId", MILL)
                    .param("year", "2021")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(pageJson("TFL", "01A", "08", "Created TFL")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode page = pageNamed(body, "Created TFL");
    assertThat(page.get("tflNumberCode").asText()).isEqualTo("08");
    assertThat(page.path("tsaNumber").isMissingNode()).isTrue();
    assertThat(page.path("tsbNumberCode").isMissingNode()).isTrue();
    // The TFL table, not the TSA one — these differ between schedules and this is Schedule 10's.
    assertThat(page.get("roadGroup").asText()).isEqualTo("10");

    var stored =
        jdbc.queryForMap(
            "SELECT TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE FROM THE.ROAD_CONSTRUCTION_REPRT"
                + " WHERE ROAD_CONSTRUCTION_REPRT_ID = ?",
            page.get("pageId").asInt());
    assertThat(stored.get("TSA_NUMBER")).isNull();
    assertThat(stored.get("TSB_NUMBER_CODE")).isNull();
    assertThat(stored).containsEntry("TFL_NUMBER_CODE", "08");
  }

  @Test
  @DisplayName("a TFL entered without its leading zero is canonicalised before storage")
  void tflIsCanonicalisedOnWrite() throws Exception {
    String body =
        mockMvc
            .perform(
                post(PAGES)
                    .param("millId", MILL)
                    .param("year", "2022")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(pageJson("TFL", null, "8", "Alias TFL")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Legacy validated the alias but stored the raw entry, leaving a value the table cannot
    // resolve.
    JsonNode page = pageNamed(body, "Alias TFL");
    assertThat(page.get("tflNumberCode").asText()).isEqualTo("08");
    assertThat(page.get("roadGroup").asText()).isEqualTo("10");
  }

  @Test
  @DisplayName("an unmapped TSA/supply-block combination still saves, with no Road Group")
  void unmappedCombinationStillSaves() throws Exception {
    String body =
        mockMvc
            .perform(
                post(PAGES)
                    .param("millId", MILL)
                    .param("year", "2023")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(pageJson("99", "99A", null, "Unmapped Save")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode page = pageNamed(body, "Unmapped Save");
    // Blank, and no error — the derivation simply yields nothing.
    assertThat(page.path("roadGroup").isMissingNode()).isTrue();
    assertThat(page.get("tsaNumber").asText()).isEqualTo("99");
  }

  @Test
  @DisplayName("an edit bumps the revision, re-derives the Road Group, and restamps UPDATE_* only")
  void editBumpsRevisionAndRestampsUpdateColumns() throws Exception {
    Object entryBefore =
        jdbc.queryForObject(
            "SELECT ENTRY_USERID FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = 8956",
            String.class);

    // Seeded at revision 2 deliberately, so a hardcoded 0 would fail here.
    String edit =
        """
        {"forestRegionCode":"RNI","tsaOrTfl":"TFL","supplyBlock":"01A","tflNumberCode":"08",
         "divisionName":"Edited Division","constructionPeriod":"2019-06","revisionCount":2}
        """;
    String body =
        mockMvc
            .perform(
                put(PAGES + "/8956")
                    .param("millId", MILL)
                    .param("year", "2019")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(edit))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode page = pageNamed(body, "Edited Division");
    assertThat(page.get("pageId").asInt()).isEqualTo(8956);
    assertThat(page.get("revisionCount").asInt()).isEqualTo(3);
    // Switching TSA -> TFL re-derives from the other table and clears the counterpart.
    assertThat(page.get("roadGroup").asText()).isEqualTo("10");
    assertThat(page.path("tsaNumber").isMissingNode()).isTrue();

    var stored =
        jdbc.queryForMap(
            "SELECT ENTRY_USERID, UPDATE_USERID FROM THE.ROAD_CONSTRUCTION_REPRT"
                + " WHERE ROAD_CONSTRUCTION_REPRT_ID = 8956");
    // ENTRY_* must survive an update untouched; only UPDATE_* is restamped.
    assertThat(stored)
        .containsEntry("ENTRY_USERID", entryBefore)
        .containsEntry("UPDATE_USERID", "dev-submitter");
  }

  @Test
  @DisplayName("a stale revision is a 409 and changes nothing")
  void staleRevisionIsConflict() throws Exception {
    Integer before =
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = 8955",
            Integer.class);

    String stale =
        """
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A","tflNumberCode":null,
         "divisionName":"Should Not Persist","constructionPeriod":"2021-06","revisionCount":99}
        """;
    mockMvc
        .perform(
            put(PAGES + "/8955")
                .param("millId", NEIGHBOUR_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(stale))
        .andExpect(status().isConflict());

    Integer after =
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = 8955",
            Integer.class);
    assertThat(after).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT CONSTRUCTION_DIVISION_NAME FROM THE.ROAD_CONSTRUCTION_REPRT"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_ID = 8955",
                String.class))
        .isEqualTo("Do Not Touch");
  }

  @Test
  @DisplayName("a missing revision token is a clean 400, never a coerced conflict")
  void missingRevisionIsBadRequest() throws Exception {
    String noToken =
        """
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A","tflNumberCode":null,
         "divisionName":"No Token","constructionPeriod":"2021-06"}
        """;
    mockMvc
        .perform(
            put(PAGES + "/8955")
                .param("millId", NEIGHBOUR_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(noToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an unknown page id is a 404, and so is another mill's page (IDOR)")
  void unknownAndForeignPagesAreNotFound() throws Exception {
    String edit =
        """
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A","tflNumberCode":null,
         "divisionName":"Nope","constructionPeriod":"2021-06","revisionCount":0}
        """;
    mockMvc
        .perform(
            put(PAGES + "/999999")
                .param("millId", MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isNotFound());

    // Page 8955 belongs to mill 723. Addressing it as mill 717 must not reach it.
    mockMvc
        .perform(
            put(PAGES + "/8955")
                .param("millId", MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isNotFound());

    assertThat(
            jdbc.queryForObject(
                "SELECT CONSTRUCTION_DIVISION_NAME FROM THE.ROAD_CONSTRUCTION_REPRT"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_ID = 8955",
                String.class))
        .isEqualTo("Do Not Touch");
  }

  @Test
  @DisplayName("every write is refused outside Draft")
  void writesAreRefusedOutsideDraft() throws Exception {
    mockMvc
        .perform(
            post(PAGES)
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(pageJson("01", "01A", null, "Blocked")))
        .andExpect(status().isConflict());

    String edit =
        """
        {"forestRegionCode":"RNI","tsaOrTfl":"01","supplyBlock":"01A","tflNumberCode":null,
         "divisionName":"Blocked","constructionPeriod":"2021-06","revisionCount":0}
        """;
    mockMvc
        .perform(
            put(PAGES + "/8957")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(edit))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            delete(PAGES + "/8957")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .with(csrf()))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            post(PAGES + "/8957/copy")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .with(csrf()))
        .andExpect(status().isConflict());

    // The three ROAD-DETAIL endpoints, which AC8 claims are covered and previously were not: the
    // gate was proven on four of the seven writes only (code review 2026-08-18). requireDraft is
    // called in all seven service methods, so this was an unverified claim rather than a hole — but
    // an unverified claim is exactly what a refactor removes without anyone noticing. Detail 8970
    // lives under the submitted page 8957.
    String detail =
        """
        {"roadName":"Blocked Road","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
         "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"},"revisionCount":0}
        """;
    mockMvc
        .perform(
            post(PAGES + "/8957/road-details")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(detail))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            put(PAGES + "/8957/road-details/8970")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(detail))
        .andExpect(status().isConflict());

    mockMvc
        .perform(
            delete(PAGES + "/8957/road-details/8970")
                .param("millId", NON_DRAFT_MILL)
                .param("year", "2021")
                .with(csrf()))
        .andExpect(status().isConflict());

    // Nothing was created, and the submitted page AND its road detail survive untouched.
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ILCR_MILL_ID = 718",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT ROAD_NAME FROM THE.ROAD_CONSTRUCTION_REPRT_DTL"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_DTL_ID = 8970",
                String.class))
        .isEqualTo("Submitted Road");
  }

  @Test
  @DisplayName("a road-detail write does NOT bump its parent page's revision (deviation (f))")
  void roadDetailWriteDoesNotBumpThePage() throws Exception {
    // Deviation (f) had no assertion anywhere: no test read the parent page's revision after a
    // detail
    // write, so a stray page UPDATE would have gone unnoticed (code review 2026-08-18). Page 8956
    // owns detail 8969, seeded at revision 5.
    int pageRevisionBefore =
        jdbc.queryForObject(
            "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT"
                + " WHERE ROAD_CONSTRUCTION_REPRT_ID = 8956",
            Integer.class);

    mockMvc
        .perform(
            post(PAGES + "/8956/road-details")
                .param("millId", "717")
                .param("year", "2019")
                .contentType(MediaType.APPLICATION_JSON)
                .with(csrf())
                .content(
                    """
                {"roadName":"No Page Bump","roadLifetimeCode":"P","becbiogeoCatalogueId":8801,
                 "relSoilMoistRgmClsCode":"1","stabilizing":{"ballastMethodCode":"N"}}
                """))
        .andExpect(status().isOk());

    assertThat(
            jdbc.queryForObject(
                "SELECT REVISION_COUNT FROM THE.ROAD_CONSTRUCTION_REPRT"
                    + " WHERE ROAD_CONSTRUCTION_REPRT_ID = 8956",
                Integer.class))
        .as("a child write must not invalidate the parent's optimistic-lock token")
        .isEqualTo(pageRevisionBefore);
  }
}
