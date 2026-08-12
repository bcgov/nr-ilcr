package ca.bc.gov.nrs.ilcr.schedule9;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Story 9.2 acceptance — write validation (AC3/AC4; slices S13/S17–S26). FLD-001 required selects
 * (one line per field, verbatim {@code "{0}: Value is required."}), the BR-04 conditional
 * descriptions, FLD-002/003/004 ranges with boundary acceptance, and FLD-005 force selection. Every
 * rejection persists NOTHING; the boundary-accept adds are the only writes here.
 */
@TestPropertySource(properties = "ilcr.security.enabled=false")
@DisplayName("POST /api/v1/schedule9/records — write validation (Story 9.2)")
class Schedule9WriteValidationIT extends AbstractOracleIT {

  private static final String RECORDS = "/api/v1/schedule9/records";
  private static final String DOCUMENT = "/api/v1/schedule9";

  private final ObjectMapper mapper = new ObjectMapper();

  @Autowired
  private DataSource dataSource;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private long recordCount() {
    return jdbc().queryForObject(
        "SELECT COUNT(*) FROM THE.CONTRACTUAL_WORK_REPORT WHERE ILCR_MILL_ID = 705", Long.class);
  }

  private void expectRejectedWithNothingPersisted(String bodyJson, int expectedStatus)
      throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content(bodyJson))
        .andExpect(status().is(expectedStatus));
    assertEquals(before, recordCount(), "a rejected write must persist nothing");
  }

  // ---- AC4: FLD-001 required (S17–S21) ---------------------------------------------------------

  @Test
  @DisplayName("all five required selects omitted -> one FLD-001 line each, in screen order")
  void required_allFiveMissing() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[*].text", contains(
            "Company ID: Value is required.",
            "Contractual Item: Value is required.",
            "Unit Type: Value is required.",
            "Biogeoclimatic Zone: Value is required.",
            "Source: Value is required.")));
    assertEquals(before, recordCount());
  }

  @Test
  @DisplayName("a blank Company ID alone -> exactly one FLD-001 line (S17)")
  void required_companyIdOnly() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("   ", 108, "M3", "A", 100, "1.0", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.messages[0].key", is("javax.faces.component.UIInput.REQUIRED")))
        .andExpect(jsonPath("$.messages[0].text", is("Company ID: Value is required.")));
  }

  // ---- AC3: conditional descriptions are NOT required at Save (legacy parity, BR-04) ------------
  // Legacy leaves itemDescription un-required, unitDescription required="false", and sourceDescription
  // with a misspelled require= JSF ignores — so an "Other" description SAVES (and round-trips) rather
  // than being required.

  @Test
  @DisplayName("item 114 'Other' saves WITH its description, round-tripped")
  void otherItem_savesAndStoresDescription() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"contractorId":"OTHER-ITEM","contractualItemCode":114,"itemDescription":"Custom gate",
                 "unitCode":"M3","numberOfUnits":1.0,"biogeoclimaticZone":"BZ1","cost":100,
                 "sourceCode":"A"}
                """))
        .andExpect(status().isOk());
    JsonNode served = recordByContractor("OTHER-ITEM");
    assertEquals("Custom gate", served.path("itemDescription").asText());
    assertEquals("114", served.path("contractualItem").path("code").asText());
  }

  @Test
  @DisplayName("item 114 'Other' with NO description still SAVES (descriptions are not required)")
  void otherItem_savesWithoutDescription() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"contractorId":"OTHER-NODESC","contractualItemCode":114,"unitCode":"M3",
                 "numberOfUnits":1.0,"biogeoclimaticZone":"BZ1","cost":100,"sourceCode":"A"}
                """))
        .andExpect(status().isOk());
    assertEquals(before + 1, recordCount());
    assertEquals("114",
        recordByContractor("OTHER-NODESC").path("contractualItem").path("code").asText());
  }

  @Test
  @DisplayName("unit O and source S save WITH their descriptions, round-tripped")
  void otherUnitAndSource_saveAndStoreDescriptions() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"contractorId":"OTHER-US","contractualItemCode":108,"unitCode":"O",
                 "unitDescription":"linear metre","numberOfUnits":1.0,"biogeoclimaticZone":"BZ1",
                 "cost":100,"sourceCode":"S","sourceDescription":"Contractor quote"}
                """))
        .andExpect(status().isOk());
    JsonNode served = recordByContractor("OTHER-US");
    assertEquals("linear metre", served.path("unitDescription").asText());
    assertEquals("Contractor quote", served.path("sourceDescription").asText());
  }

  // ---- AC4: FLD-002/003/004 ranges + boundaries (S13/S22/S23/S24) ------------------------------

  @Test
  @DisplayName("cost above 9,999,999 -> verbatim FLD-002")
  void range_costTooHigh() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR", 108, "M3", "A", 10000000, "1.0", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered cost must be between 0 and 9,999,999.")));
    assertEquals(before, recordCount());
  }

  @Test
  @DisplayName("side slope above 100 -> verbatim FLD-003")
  void range_sideSlopeTooHigh() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR", 111, "M3", "A", 100, "1.0", 101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Side slope (%): percentage must be between 0 and 100.")));
    assertEquals(before, recordCount());
  }

  @Test
  @DisplayName("number of units above 99,999.9 -> verbatim FLD-004")
  void range_unitsTooHigh() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR", 108, "M3", "A", 100, "100000.0", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail",
            containsString("Entered number of units must be between 0.0 and 99,999.9.")));
    assertEquals(before, recordCount());
  }

  @Test
  @DisplayName("S13: boundary values (cost 9,999,999, side slope 100, units 99,999.9) SAVE")
  void boundary_valuesAccepted() throws Exception {
    // item 111 so the side slope is kept; 100 saves (<=100) even though Check Status flags it (>99).
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("BOUNDARY", 111, "M3", "A", 9999999, "99999.9", 100)))
        .andExpect(status().isOk());

    JsonNode served = recordByContractor("BOUNDARY");
    assertEquals(9999999, served.path("cost").asInt());
    assertEquals(100, served.path("sideSlopePct").asInt());
    assertEquals(99999.9, served.path("numberOfUnits").asDouble(), 0.0001);
  }

  // ---- AC4: FLD-005 force selection (S26) ------------------------------------------------------

  @Test
  @DisplayName("a Contractual Item outside 108–114 -> FLD-005")
  void code_itemOutOfRange() throws Exception {
    long before = recordCount();
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2021")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CTR", 999, "M3", "A", 100, "1.0", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", is("A valid value must be selected from the list.")));
    assertEquals(before, recordCount());
  }

  @Test
  @DisplayName("a Unit Type not in the code list -> FLD-005")
  void code_unitNotInList() throws Exception {
    expectRejectedWithNothingPersisted(body("CTR", 108, "ZZ", "A", 100, "1.0", null), 400);
  }

  // ---- AC2: conditional-null on save (item 108 clears the side slope it does not enable) --------

  @Test
  @DisplayName("item 108 with a side slope in the body -> stored side slope is NULL")
  void conditionalNull_sideSlopeClearedForNonRoadItem() throws Exception {
    mockMvc.perform(post(RECORDS).with(csrf()).param("millId", "705").param("year", "2022")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body("CLEARED", 108, "M3", "A", 100, "1.0", 55)))
        .andExpect(status().isOk());

    JsonNode served = recordByContractorYear(2022, "CLEARED");
    assertTrue(served.path("sideSlopePct").isMissingNode() || served.path("sideSlopePct").isNull(),
        "item 108 does not enable side slope, so it must not be stored");
  }

  /** A full body; unit/source/item vary. Side slope and revisionCount are optional (null omits). */
  private static String body(
      String contractor, int item, String unit, String source, Integer cost, String units,
      Integer sideSlope) {
    return """
        {
          "contractorId": "%s",
          "contractualItemCode": %d,
          "unitCode": "%s",
          "numberOfUnits": %s,
          "biogeoclimaticZone": "BZ1",
          "cost": %s,
          "sideSlopePct": %s,
          "sourceCode": "%s"
        }
        """.formatted(contractor, item, unit, units, cost == null ? "null" : cost.toString(),
        sideSlope == null ? "null" : sideSlope.toString(), source);
  }

  private JsonNode recordByContractor(String contractor) throws Exception {
    return recordByContractorYear(2021, contractor);
  }

  private JsonNode recordByContractorYear(int year, String contractor) throws Exception {
    String json = mockMvc.perform(
            get(DOCUMENT).param("millId", "705").param("year", String.valueOf(year)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    for (JsonNode record : mapper.readTree(json).path("records")) {
      if (contractor.equals(record.path("contractorId").asText())) {
        return record;
      }
    }
    throw new AssertionError("record for contractor '" + contractor + "' not served in " + year);
  }
}
