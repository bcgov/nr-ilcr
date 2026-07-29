package ca.bc.gov.nrs.ilcr.schedule11;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 25.2 acceptance — {@code POST /api/v1/schedule11/check-status} (AC9/AC10, slices
 * S04/S05/S06; BR-07). Read-only validation: a location passes iff BOTH costs are non-null. SUC-004
 * ("Status has been checked") is returned on every call; SUC-003 ("All requirements … met") only
 * when met. Per-missing-cost flags are composed VERBATIM in legacy order with the double space after
 * {@code location}. Fixtures are distinct from the write IT's (616 all-met, 617 missing, 613 empty)
 * so the shared container's write mutations never perturb them.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule11/check-status — Schedule 11 Check Status (Story 25.2)")
class Schedule11CheckStatusIT extends AbstractOracleIT {

    private static final String CHECK_STATUS = "/api/v1/schedule11/check-status";
    private static final String ENDPOINT = "/api/v1/schedule11";

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("S04: all locations have both costs -> requirementsMet, SUC-003 + SUC-004")
    void allRequirementsMet() throws Exception {
        mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "616").param("year", "2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsMet", is(true)))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.requirementsMetMessage.text",
                        is("All requirements for this schedule have been met")))
                .andExpect(jsonPath("$.message.key", is("checkStatusMessage")))
                .andExpect(jsonPath("$.message.text", is("Status has been checked")));
    }

    @Test
    @DisplayName("S05/S06: missing actual + missing planned -> FLD-004 flags, legacy order, no SUC-003")
    void missingCostsFlagged() throws Exception {
        mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "617").param("year", "2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsMet", is(false)))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                // Ordered by BASIC_SILVICULTURE_REPORT_ID (9205 then 9206); double space after "location".
                .andExpect(jsonPath("$.errors[0].text",
                        is("location  : Missing Actual - Actual cost: Value Required")))
                .andExpect(jsonPath("$.errors[1].text",
                        is("location  : Missing Planned - Planned cost: Value Required")))
                // No SUC-003 when not met, but SUC-004 is still emitted.
                .andExpect(jsonPath("$.requirementsMetMessage").doesNotExist())
                .andExpect(jsonPath("$.message.text", is("Status has been checked")));
    }

    @Test
    @DisplayName("S04 boundary: zero locations -> vacuously met (SUC-003 + SUC-004)")
    void zeroLocations_vacuouslyMet() throws Exception {
        // Mill 613 (V20): Draft, ZERO locations — reused read-only (check-status mutates nothing).
        mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "613").param("year", "2021"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requirementsMet", is(true)))
                .andExpect(jsonPath("$.errors", hasSize(0)))
                .andExpect(jsonPath("$.requirementsMetMessage.text",
                        is("All requirements for this schedule have been met")));
    }

    @Test
    @DisplayName("AC9: check-status mutates nothing (row counts + every REVISION_COUNT identical, doc identical)")
    void mutatesNothing() throws Exception {
        // DB-level proof (per the story's Task 8 spec): GET-body equality alone could miss a write
        // to a column the served document does not expose (audit columns, another mill's rows).
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String bsrCount = "SELECT COUNT(*) FROM THE.BASIC_SILVICULTURE_REPORT";
        String costCount = "SELECT COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL";
        String bsrRevisions = "SELECT BASIC_SILVICULTURE_REPORT_ID, REVISION_COUNT"
                + " FROM THE.BASIC_SILVICULTURE_REPORT ORDER BY BASIC_SILVICULTURE_REPORT_ID";
        Integer bsrBefore = jdbc.queryForObject(bsrCount, Integer.class);
        Integer costBefore = jdbc.queryForObject(costCount, Integer.class);
        List<Map<String, Object>> revisionsBefore = jdbc.queryForList(bsrRevisions);
        String docBefore = getDocument();

        mockMvc.perform(post(CHECK_STATUS).with(csrf()).param("millId", "616").param("year", "2021"))
                .andExpect(status().isOk());

        assertEquals(bsrBefore, jdbc.queryForObject(bsrCount, Integer.class));
        assertEquals(costBefore, jdbc.queryForObject(costCount, Integer.class));
        assertEquals(revisionsBefore, jdbc.queryForList(bsrRevisions));
        assertEquals(docBefore, getDocument());
    }

    private String getDocument() throws Exception {
        return mockMvc.perform(get(ENDPOINT).param("millId", "616").param("year", "2021")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
