package ca.bc.gov.nrs.ilcr.schedule1;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

/**
 * RED-PHASE ATDD SCAFFOLD — Story 2.1 (AD-4/5/6/8/9/10, AR11). PUT + DELETE /api/v1/schedule1.
 *
 * <p>Security is OFF (no {@code @TestPropertySource}) so the mock {@code ILCR_SUBMITTER} principal
 * holds EDIT_SCHEDULE — this isolates the persistence / write-gate / concurrency behaviour from authz
 * (the 403 case lives in {@link Schedule1WriteAuthorizationIT}, which runs with security ON). Request
 * bodies are raw JSON text blocks so this file COMPILES before the {@code Schedule1Request} DTO
 * exists — the pinned wire contract is asserted via jsonPath + direct DB reads, never by referencing
 * an unbuilt production type.
 *
 * <p>RED before implementation: the controller has no PUT/DELETE handler yet, so every case fails
 * (405/404 rather than the asserted 2xx/4xx). Class-level {@code @Disabled} is the red-phase gate.
 *
 * <p>Mutating tests use DEDICATED mills seeded by {@code V4} so they never clobber the read-only
 * 514/2021 fixture the other *IT classes assert against (the container + data are shared across the
 * whole run): mill 518 / summary 1018 (save + ignore-derived; itemized item-19 Row A/B ids 5020/5021),
 * mill 519 / summary 1019 (DELETE, S13), mill 520 / summary 1020 (optimistic lock, AC7), mill 517 /
 * summary 1017 (Submitted — non-Draft write gate, S22-write). Only ONE test persists to 518, and it
 * reads the revision at runtime, so cases stay order-independent.
 *
 * ACTIVATION (dev-story): implement Tasks 1–6, then remove {@code @Disabled} and green each case.
 */
@DisplayName("PUT/DELETE /api/v1/schedule1 — write path (Story 2.1)")
class Schedule1WriteIT extends AbstractOracleIT {

    private static final String ENDPOINT = "/api/v1/schedule1";
    private static final String SUMMARY = "THE.ILCR_REPORT_SUMMARY";
    private static final String DETAIL = "THE.ILCR_COST_REPORT_DETAIL";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int revisionOf(long summaryId) {
        return jdbcTemplate.queryForObject(
                "SELECT REVISION_COUNT FROM " + SUMMARY + " WHERE ILCR_REPORT_SUMMARY_ID = ?",
                Integer.class, summaryId);
    }

    /** Minimal valid pinned request (writable fields only) for the given optimistic-lock token. */
    private static String validBody(int revisionCount) {
        return """
            {
              "revisionCount": %d,
              "comments": "PUT by IT",
              "lineItems": [ { "costItemCode": 12, "volume": 2000, "cost": 60000 } ],
              "silviculture": {
                "actualSpent":       { "volume": 500, "cost": 20000 },
                "accruedLessActual": { "volume": null, "cost": null }
              },
              "otherCostsVolume": 8000
            }
            """.formatted(revisionCount);
    }

    // ---- S01 + AC2: save persists, echoes recomputed doc + bumped revision; derived/read-only
    //      inputs ignored; the two itemized Other-Costs rows (Row A/B) are never touched. --------------

    @Test
    @DisplayName("S01/AC2 — 518 Draft PUT persists+recomputes+bumps revision; derived ignored, itemized untouched")
    void put_persistsRecomputesBumpsRevision_ignoresDerived_leavesItemizedRows() throws Exception {
        int before = revisionOf(1018);
        // Body smuggles server-owned fields the PUT must ignore: perUnit, trackStatus, editable,
        // crownVolume, the derived otherCosts subtotal, and the pulled 143 + derived 144 codes.
        String body = """
            {
              "revisionCount": %d,
              "trackStatus": "IGNORED",
              "editable": true,
              "crownVolume": 99999,
              "comments": "updated by test",
              "lineItems": [
                { "costItemCode": 12,  "volume": 2000, "cost": 60000, "perUnit": 999.0 },
                { "costItemCode": 144, "volume": 5,    "cost": 999 },
                { "costItemCode": 143, "volume": 5,    "cost": 999 }
              ],
              "silviculture": {
                "actualSpent":       { "volume": 500, "cost": 20000 },
                "accruedLessActual": { "volume": null, "cost": null }
              },
              "otherCostsVolume": 9000,
              "otherCosts": { "costSubtotal": 777, "perUnit": 777.0, "count": 9 }
            }
            """.formatted(before);
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "518").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.millId", is(518)))
                .andExpect(jsonPath("$.revisionCount", is(before + 1)))
                .andExpect(jsonPath("$.message.text", is("Data saved successfully")))
                // code 12: server recomputes perUnit 60000/2000 = 30.0 (NOT the smuggled 999.0)
                .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].cost", contains(60000)))
                .andExpect(jsonPath("$.lineItems[?(@.costItemCode == 12)].perUnit", contains(30.0)))
                // AC2: the itemized Other-Costs subtotal (Row A/B, 12000 each) is server-recomputed
                // from the untouched rows, NOT the smuggled 777.
                .andExpect(jsonPath("$.otherCosts.volume", is(9000)))
                .andExpect(jsonPath("$.otherCosts.count", is(2)))
                .andExpect(jsonPath("$.otherCosts.costSubtotal", is(24000)));

        assertEquals(before + 1, revisionOf(1018), "REVISION_COUNT must advance by exactly one");
        // The itemized item-19 rows are the sole province of Story 2.4 — unchanged in count and value.
        assertEquals(2, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
                "ILCR_REPORT_SUMMARY_ID = 1018 AND ITEM_DESCRIPTION IS NOT NULL AND COST = 12000"),
                "PUT must not add/remove/modify itemized Other-Costs rows");
    }

    // ---- S03–S07: field validation -> 400 ProblemDetail (verbatim) and nothing persisted. ----------
    // "Nothing persisted" is proven per-test by reading REVISION_COUNT(1018) before and after; a
    // Bean-Validation reject never opens the write transaction, so the revision must not move.
    // NOTE (Task 1 open item): which fixed fields are 7-digit vs 8-digit volume validators is resolved
    // from legacy schedule1.xhtml during dev-story. FLD-002 targets a line-item volume; FLD-003 targets
    // otherCostsVolume as the 8-digit candidate — realign the target field to the resolved map.

    @Test
    @DisplayName("S03 — cost > 99,999,999 -> 400 FLD-001 (costValidatorErrorMsg), nothing persisted")
    void put_costOverRange_returns400_fld001() throws Exception {
        expect400NothingPersisted("""
                { "revisionCount": %d, "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": 100000000 } ],
                  "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
                  "otherCostsVolume": 0 }
                """.formatted(revisionOf(1018)),
                "Entered cost must be between -99,999,999 and 99,999,999.");
    }

    @Test
    @DisplayName("S06 — non-numeric cost -> 400 FLD-004 (costConverterErrorMsg), nothing persisted")
    void put_nonNumericCost_returns400_fld004() throws Exception {
        expect400NothingPersisted("""
                { "revisionCount": %d, "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": "abc" } ],
                  "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
                  "otherCostsVolume": 0 }
                """.formatted(revisionOf(1018)),
                "Entered cost is invalid.");
    }

    @Test
    @DisplayName("S04 — 7-digit volume > 9,999,999 -> 400 FLD-002 (volume7DigitValidatorErrorMsg)")
    void put_volume7DigitOverRange_returns400_fld002() throws Exception {
        expect400NothingPersisted("""
                { "revisionCount": %d, "lineItems": [ { "costItemCode": 12, "volume": 10000000, "cost": 5000 } ],
                  "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
                  "otherCostsVolume": 0 }
                """.formatted(revisionOf(1018)),
                "Entered volume must be between -9,999,999 and 9,999,999.");
    }

    @Test
    @DisplayName("S05 — 8-digit volume > 99,999,999 -> 400 FLD-003 (volume8DigitValidatorErrorMsg)")
    void put_volume8DigitOverRange_returns400_fld003() throws Exception {
        expect400NothingPersisted("""
                { "revisionCount": %d, "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": 50000 } ],
                  "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
                  "otherCostsVolume": 100000000 }
                """.formatted(revisionOf(1018)),
                "Entered volume must be between -99,999,999 and 99,999,999.");
    }

    @Test
    @DisplayName("S07 — non-numeric volume -> 400 FLD-005 (volumeConverterErrorMsg), nothing persisted")
    void put_nonNumericVolume_returns400_fld005() throws Exception {
        expect400NothingPersisted("""
                { "revisionCount": %d, "lineItems": [ { "costItemCode": 12, "volume": "xyz", "cost": 50000 } ],
                  "silviculture": { "actualSpent": { "volume": 1, "cost": 1 }, "accruedLessActual": { "volume": null, "cost": null } },
                  "otherCostsVolume": 0 }
                """.formatted(revisionOf(1018)),
                "Entered volume entry is invalid.");
    }

    @Test
    @DisplayName("missing revisionCount -> 400 (required token), nothing persisted")
    void put_missingRevisionCount_returns400() throws Exception {
        expect400NothingPersisted("""
                { "lineItems": [ { "costItemCode": 12, "volume": 1000, "cost": 50000 } ],
                  "otherCostsVolume": 0 }
                """,
                "revisionCount is required");
    }

    private void expect400NothingPersisted(String body, String verbatimDetail) throws Exception {
        int before = revisionOf(1018);
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "518").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", is(verbatimDetail)));
        assertEquals(before, revisionOf(1018), "a rejected PUT must not persist anything");
    }

    // ---- S22-write: non-Draft track is read-only; PUT/DELETE -> 409 (verbatim), no data change. -----

    @Test
    @DisplayName("S22-write — PUT against non-Draft (mill 517, track S) -> 409, no change")
    void put_nonDraft_returns409() throws Exception {
        int before = revisionOf(1017);
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "517").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(before)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
        assertEquals(before, revisionOf(1017), "a gated PUT must not change data");
    }

    @Test
    @DisplayName("S22-write — DELETE against non-Draft (mill 517, track S) -> 409, row survives")
    void delete_nonDraft_returns409() throws Exception {
        mockMvc.perform(delete(ENDPOINT).param("millId", "517").param("year", "2021"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", is("This schedule cannot be edited in its current status.")));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
                "ILCR_REPORT_SUMMARY_ID = 1017"), "gated DELETE must not remove the row");
    }

    // ---- S13: DELETE removes the whole schedule (BR-08); re-GET is the empty-schedule state. --------

    @Test
    @DisplayName("S13 — 519 Draft DELETE 200 (SUC-002) removes summary + all details; re-GET 404")
    void delete_removesWholeSchedule() throws Exception {
        mockMvc.perform(delete(ENDPOINT).param("millId", "519").param("year", "2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message.text", is("Data deleted successfully")));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, SUMMARY,
                "ILCR_REPORT_SUMMARY_ID = 1019"), "summary row must be gone (BR-08)");
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, DETAIL,
                "ILCR_REPORT_SUMMARY_ID = 1019"), "all detail rows must be gone (BR-08)");
        // Re-GET of a mill with no summary is the established empty-schedule state (Story 1.2: 404).
        // If the team elects a 200 empty-document instead, realign this expectation in dev-story.
        mockMvc.perform(get(ENDPOINT).param("millId", "519").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ---- AC7 (AR11): optimistic concurrency — stale revision -> 409; reload + retry -> 200. ---------

    @Test
    @DisplayName("AC7 — 520 second PUT with a stale revision -> 409; reload + retry with N+1 -> 200")
    void put_staleRevision_returns409_thenRetrySucceeds() throws Exception {
        int n = revisionOf(1020);
        // First writer wins (N -> N+1).
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "520").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(n)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionCount", is(n + 1)));
        // Second writer still holds the stale token N -> rejected, no overwrite.
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "520").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(n)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail", is("This schedule was changed by another user. Please reload and try again.")));
        assertEquals(n + 1, revisionOf(1020), "the stale PUT must not overwrite");
        // Reload the fresh token and retry -> succeeds.
        mockMvc.perform(put(ENDPOINT)
                        .param("millId", "520").param("year", "2021")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody(n + 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionCount", is(greaterThan(n + 1))));
    }

    // ---- S23/S24: persistence failure -> rollback + 500 ERR-004; identical retry succeeds. ----------
    // Implemented in Schedule1WriteFailureIT (its own @MockitoSpyBean context, so fault injection does
    // not dirty this class's context or affect the persistence assertions above).
}
