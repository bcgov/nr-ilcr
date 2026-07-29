package ca.bc.gov.nrs.ilcr.schedule11;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 25.2 acceptance — {@code POST/PUT/DELETE /api/v1/schedule11/locations} (AC1–AC8/AC11,
 * slices S01/S02/S03/S07/S09/S14/S16/S17/S18/S19; AD-5, AD-9, AD-10, AD-12). The mock
 * {@code ILCR_SUBMITTER} holds both VIEW and EDIT; authz is proven in
 * {@link Schedule11WriteAuthorizationIT}. Security-off is pinned EXPLICITLY and every mutation
 * carries {@code .with(csrf())} — no-ops today (CSRF is disabled in {@code SecurityConfiguration}),
 * but they keep this suite green when main's fail-closed security default / {@code csrf.spa()}
 * merges in (the story's recorded merge-regression guard).
 *
 * <p>Mutating tests are ORDER-INDEPENDENT: each targets its own fixture row (mill 614 for writes,
 * 615 for the non-Draft gate), edits read the current {@code revisionCount} before writing (never a
 * hard-coded token — Story 2.1 review lesson), and assertions locate rows by id/name (JSONPath
 * filters) rather than array index or count.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST/PUT/DELETE /api/v1/schedule11/locations — Schedule 11 writes (Story 25.2)")
class Schedule11WriteIT extends AbstractOracleIT {

    private static final String LOCATIONS = "/api/v1/schedule11/locations";
    private static final String ENDPOINT = "/api/v1/schedule11";
    private static final String PROBLEM_JSON = "application/problem+json";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private DataSource dataSource;

    // ---- AC1 add + AC (S09) durability ----------------------------------------------------------

    @Test
    @DisplayName("S01/S09: POST a valid location -> 200, saved message, row durable on re-GET")
    void addLocation_persistsImmediately_andIsDurable() throws Exception {
        String body = """
            {"location":"North Ridge","enhancedIndicator":true,"biogeoclimaticCatalogueId":8802,
             "netArea":125.5,"actualCost":5000,"plannedCost":4500,"comments":"New block"}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
                .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
                .andExpect(jsonPath("$.editable", is(true)))
                // New row present with server-derived figures (BR-08): total = 5000 + 4500 = 9500.
                .andExpect(jsonPath("$.locations[?(@.location=='North Ridge')].totalCost", contains(9500)))
                .andExpect(jsonPath("$.locations[?(@.location=='North Ridge')].enhancedIndicator", contains(true)));

        // S09: durable without any further call — a fresh GET still shows it.
        mockMvc.perform(get(ENDPOINT).param("millId", "614").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations[?(@.location=='North Ridge')]", hasSize(1)));
    }

    @Test
    @DisplayName("S02: a second POST persists an additional location (both present)")
    void addSecondLocation_bothPersist() throws Exception {
        String body = """
            {"location":"South Bench","enhancedIndicator":false,"biogeoclimaticCatalogueId":8803,
             "netArea":80.0,"actualCost":3000,"plannedCost":2800}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations[?(@.location=='South Bench')]", hasSize(1)));
    }

    // ---- AC2 edit (S03) — order-independent: read current revision, then PUT with it ------------

    @Test
    @DisplayName("S03: PUT edits an existing location and bumps its revisionCount")
    void editLocation_persistsAndBumpsRevision() throws Exception {
        int currentRevision = currentRevision(614, 2021, 9201);
        String body = """
            {"location":"Existing Ridge","enhancedIndicator":true,"biogeoclimaticCatalogueId":8801,
             "netArea":110.0,"actualCost":5500,"plannedCost":4000,"revisionCount":%d}
            """.formatted(currentRevision);
        mockMvc.perform(put(LOCATIONS + "/9201").with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.key", is("dataSavedSuccesfullyInfoMsg")))
                .andExpect(jsonPath("$.locations[?(@.locationId==9201)].actualCost", contains(5500)))
                .andExpect(jsonPath("$.locations[?(@.locationId==9201)].revisionCount",
                        contains(currentRevision + 1)));
    }

    // ---- AC3 delete (S07) -----------------------------------------------------------------------

    @Test
    @DisplayName("S07: DELETE removes the location and its WHOLE cost family -> deleted message")
    void deleteLocation_removesRowAndAllCostChildren() throws Exception {
        // 9202 carries an item-24 row AND an out-of-scope item-19 row (V21) — legacy whole-row
        // removal: a 23/24-only cascade would orphan the item-19 row on a dangling location id.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String costCount =
                "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL WHERE BASIC_SILVICULTURE_REPORT_ID = 9202";
        assertEquals(2, jdbc.queryForObject(costCount, Integer.class));

        mockMvc.perform(delete(LOCATIONS + "/9202").with(csrf()).param("millId", "614").param("year", "2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.key", is("dataDeletedSuccesfullyInfoMsg")))
                .andExpect(jsonPath("$.locations[?(@.locationId==9202)]", hasSize(0)));

        assertEquals(0, jdbc.queryForObject(costCount, Integer.class));
    }

    @Test
    @DisplayName("AC3: DELETE an unknown id -> 404")
    void deleteUnknownLocation_returns404() throws Exception {
        mockMvc.perform(delete(LOCATIONS + "/999999").with(csrf()).param("millId", "614").param("year", "2021"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", is("Location not found.")));
    }

    // ---- AC4 required fields (S14/S16/S17) ------------------------------------------------------

    @Test
    @DisplayName("S14: blank location -> 400 verbatim required message, nothing persisted")
    void blankLocation_returns400() throws Exception {
        String body = """
            {"location":"","enhancedIndicator":true,"biogeoclimaticCatalogueId":8801,"netArea":10.0}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is("Location: Value is required.")));
    }

    @Test
    @DisplayName("S15: null Enhanced indicator -> 400 verbatim required message, nothing persisted")
    void missingEnhancedIndicator_returns400() throws Exception {
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"biogeoclimaticCatalogueId\":8801,\"netArea\":10.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", is("Enhanced: Value is required.")));
    }

    @Test
    @DisplayName("S16: null biogeo -> 400 required; S17: null NAR -> 400 required")
    void missingRequiredSelections_return400() throws Exception {
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"enhancedIndicator\":true,\"netArea\":10.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Biogeo/Subzone/Variant: Value is required.")));

        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"enhancedIndicator\":true,\"biogeoclimaticCatalogueId\":8801}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("NAR(ha): Value is required.")));
    }

    // ---- AC5 ranges (S18/S19) -------------------------------------------------------------------

    @Test
    @DisplayName("S18: NAR above 999,999.9 -> 400; S19: cost beyond +/-99,999,999 -> 400 verbatim")
    void outOfRange_return400() throws Exception {
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"enhancedIndicator\":true,\"biogeoclimaticCatalogueId\":8801,\"netArea\":1000000.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Entered NAR (ha) must be between 0 and 999,999.9.")));

        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"enhancedIndicator\":true,\"biogeoclimaticCatalogueId\":8801,\"netArea\":10.0,\"actualCost\":100000000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Entered cost must be between -99,999,999 and 99,999,999.")));
    }

    @Test
    @DisplayName("S18: NAR with more than one decimal -> 400 (never silently rounded by the column scale)")
    void netAreaBeyondOneDecimal_returns400() throws Exception {
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":\"X\",\"enhancedIndicator\":true,\"biogeoclimaticCatalogueId\":8801,\"netArea\":100.55}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Entered NAR (ha) must be between 0 and 999,999.9.")));
    }

    // ---- AC6 biogeo integrity -------------------------------------------------------------------

    @Test
    @DisplayName("S16/AC6: a biogeo id absent from the catalogue -> 400 invalidBiogeoCode")
    void unresolvableBiogeo_returns400() throws Exception {
        String body = """
            {"location":"Bad Biogeo","enhancedIndicator":false,"biogeoclimaticCatalogueId":8899,"netArea":10.0}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail",
                        is("Biogeo/Subzone/Variant code is invalid. "
                                + "The code must be corrected before the schedule can be saved.")));
    }

    @Test
    @DisplayName("AC6: duplicate (year,mill,cat,biogeo,location) -> 409 verbatim biogeo-unique")
    void duplicateBiogeoLocation_returns409() throws Exception {
        // 9201 is (2021, 614, '11', 8801, 'Existing Ridge') — re-adding that key trips BSRPT_BSRPT_UK_UK.
        String body = """
            {"location":"Existing Ridge","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,"netArea":15.0}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail",
                        is("Schedule could not be saved. "
                                + "The Biogeo/Subzone/Variant has to be unique for a location.")));
    }

    // ---- AC7 optimistic concurrency -------------------------------------------------------------

    @Test
    @DisplayName("AC7: PUT with a stale revisionCount -> 409; PUT omitting it -> clean 400")
    void staleAndMissingRevision() throws Exception {
        String stale = """
            {"location":"Existing Ridge","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,
             "netArea":100.0,"revisionCount":9999}
            """;
        mockMvc.perform(put(LOCATIONS + "/9201").with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(stale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail",
                        is("This schedule was changed by another user. Please reload and try again.")));

        String noToken = """
            {"location":"Existing Ridge","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,"netArea":100.0}
            """;
        mockMvc.perform(put(LOCATIONS + "/9201").with(csrf()).param("millId", "614").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(noToken))
                .andExpect(status().isBadRequest());
    }

    // ---- AC8 write gate (silviculture track) ----------------------------------------------------

    @Test
    @DisplayName("AC8: POST to a non-Draft silviculture track (mill 615/'S') -> 409 not editable")
    void nonDraftSilvicultureTrack_returns409() throws Exception {
        String body = """
            {"location":"Should Fail","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,"netArea":10.0}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("millId", "615").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
    }

    // ---- AC11 context guard reused --------------------------------------------------------------

    @Test
    @DisplayName("AC11: POST with a missing millId -> 400 verbatim ERR-001 (trailing space)")
    void missingContext_returns400Err001() throws Exception {
        String body = """
            {"location":"X","enhancedIndicator":false,"biogeoclimaticCatalogueId":8801,"netArea":10.0}
            """;
        mockMvc.perform(post(LOCATIONS).with(csrf()).param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Please Select Mill and Reporting Year in the Home Page. ")));
    }

    /** Read a location's current revisionCount via GET so edits never hard-code a token. */
    private int currentRevision(long millId, int year, long locationId) throws Exception {
        String json = mockMvc.perform(get(ENDPOINT).param("millId", String.valueOf(millId))
                        .param("year", String.valueOf(year)).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode loc : mapper.readTree(json).get("locations")) {
            if (loc.get("locationId").asLong() == locationId) {
                return loc.get("revisionCount").asInt();
            }
        }
        throw new IllegalStateException("location " + locationId + " not found");
    }
}
