package ca.bc.gov.nrs.ilcr.millreportstatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Acceptance test — the Mill Status Report table (UC-MRPT-004). {@code GET
 * /api/v1/reports/mill-status?year=} answers one row per mill with a report status for the year, in
 * mill-id order, with no mill parameter and no working-context guard.
 *
 * <p>Seed is R__40: mills 730 (fully dated on both tracks), 731 (prefix-only milestones), 732 (all
 * milestone columns NULL) and 733 (ACT for 2021 in the view but CLS on its status xref), alongside
 * the pre-existing mill 514 from V2/V9.
 *
 * <p>Security is pinned OFF so these isolate the read from authorization, which {@link
 * MillReportStatusAuthorizationIT} covers.
 */
@DisplayName("GET /api/v1/reports/mill-status — Mill Status Report table")
// Security OFF isolates the read from authorization. The mock principal defaults to ILCR_SUBMITTER,
// which this ADMIN-only endpoint would 403, so the mock role is raised to ILCR_ADMIN.
@TestPropertySource(
    properties = {"ilcr.security.enabled=false", "ilcr.security.mock-role=ILCR_ADMIN"})
class MillReportStatusIT extends AbstractOracleIT {

  private static final String ENDPOINT = "/api/v1/reports/mill-status";
  private static final String PROBLEM_JSON = "application/problem+json";
  private static final String YEAR_REQUIRED = "Report Year: Value is required.";
  private static final String YEAR_NOT_OPEN = "Report Year is not an open reporting period.";

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("2021 -> 200 JSON, five rows, in mill-id order")
  void openYear_returnsRowsInMillIdOrder() throws Exception {
    // Order matters and is legacy's (Order.asc("ilcr_mill_id")), so this asserts the SEQUENCE
    // rather
    // than mere presence — dropping the ORDER BY, or narrowing the join so a mill disappears,
    // fails.
    // Note 514 sorts FIRST by mill id even though its mill NUMBER (514) is the smallest too; 730's
    // number is 7300, which is what makes id and number distinguishable at all.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(
            jsonPath("$[*].millId").value(org.hamcrest.Matchers.contains(514, 730, 731, 732, 733)))
        .andExpect(jsonPath("$[0].millNumber").value("514"))
        .andExpect(jsonPath("$[0].millName").value("AAA Milling"))
        .andExpect(jsonPath("$[1].millNumber").value("7300"))
        .andExpect(jsonPath("$[1].millName").value("MILL INFO FULL"))
        .andExpect(jsonPath("$[2].millName").value("MILL INFO SPARSE"))
        .andExpect(jsonPath("$[3].millName").value("MILL INFO NO CLIENT"))
        .andExpect(jsonPath("$[4].millName").value("MILL INFO CLOSED SINCE"));
  }

  @Test
  @DisplayName("the year is a filter, not decoration: 2020 answers only mill 514")
  void yearFiltersTheRows() throws Exception {
    // V9 seeds exactly one 2020 view row (514). If the year predicate were dropped, this would
    // return the 2021 rows too.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2020").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].millId").value(514));
  }

  @Test
  @DisplayName("the milestone strings arrive RAW, prefix intact, on the correct track")
  void milestonesArriveRawAndOnTheirOwnTrack() throws Exception {
    // Mill 730 (index 1) is dated on both tracks with SIX distinct dates: Mar/May/Jul on Schedules
    // 1–10 and Apr/Jun/Aug on Schedule 11. The prefix is part of the value on this surface — the
    // page's O/D/S/V legend is what decodes it — so a strip anywhere in the chain fails here.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].openDate").value("O: 2021-01-05"))
        .andExpect(jsonPath("$[1].draftDate").value("D: 2021-03-10"))
        .andExpect(jsonPath("$[1].submitDate").value("S: 2021-05-20"))
        .andExpect(jsonPath("$[1].verifyDate").value("V: 2021-07-01"))
        .andExpect(jsonPath("$[1].silviDraftDate").value("D: 2021-04-12"))
        .andExpect(jsonPath("$[1].silviSubmitDate").value("S: 2021-06-15"))
        .andExpect(jsonPath("$[1].silviVerifyDate").value("V: 2021-08-20"));
  }

  @Test
  @DisplayName("a prefix-only milestone is served as its prefix; a NULL one is served as null")
  void nullAndPrefixOnlyMilestones() throws Exception {
    // Mill 731 (index 2) carries "D: " / "S: " / "V: " on BOTH tracks — the shape 80 of the 118
    // delivery rows have. Mill 732 (index 3) carries NULL columns outright. Neither may become the
    // string "null", and neither may be stripped away.
    //
    // A null is asserted as doesNotExist(), not as a JSON null: the app sets Jackson
    // default-property-inclusion=non_null (application.yml:5), so a null field is OMITTED from the
    // body entirely. That is the wire shape the frontend interface has to model (optional fields),
    // and asserting a JSON null here would be asserting a shape the app never sends.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[2].openDate").value("O: 2021-01-05"))
        .andExpect(jsonPath("$[2].draftDate").value("D: "))
        .andExpect(jsonPath("$[2].submitDate").value("S: "))
        .andExpect(jsonPath("$[2].verifyDate").value("V: "))
        .andExpect(jsonPath("$[2].silviDraftDate").value("D: "))
        .andExpect(jsonPath("$[2].silviSubmitDate").value("S: "))
        .andExpect(jsonPath("$[2].silviVerifyDate").value("V: "))
        .andExpect(jsonPath("$[3].openDate").doesNotExist())
        .andExpect(jsonPath("$[3].draftDate").doesNotExist())
        .andExpect(jsonPath("$[3].submitDate").doesNotExist())
        .andExpect(jsonPath("$[3].verifyDate").doesNotExist())
        .andExpect(jsonPath("$[3].silviDraftDate").doesNotExist())
        .andExpect(jsonPath("$[3].silviSubmitDate").doesNotExist())
        .andExpect(jsonPath("$[3].silviVerifyDate").doesNotExist());
  }

  @Test
  @DisplayName("Active reflects the REPORTING YEAR's status, not the mill's status today")
  void activeFlagIsPerReportingYear() throws Exception {
    // Mill 733 (index 4) is ACT for 2021 in the report view but CLS on its status xref (closed
    // since). Reading the xref would answer false and silently rewrite history on every reload.
    // Mill 732 (index 3) is CLS for the year itself, so it must answer false — proving the flag is
    // read at all rather than hardcoded.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[4].active").value(true))
        .andExpect(jsonPath("$[3].active").value(false))
        .andExpect(jsonPath("$[1].active").value(true));
  }

  @Test
  @DisplayName("Region resolves from the separately-read zone table, and is null when absent")
  void regionResolvesOrDegrades() throws Exception {
    // Mill 730 carries zone Z1, which R__40 describes. Mill 731 carries no zone code at all, and
    // 514 carries none either — for those the field is absent from the body (Jackson non_null) and
    // the page renders "-".
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].region").value("Kootenay Selling Price Zone"))
        .andExpect(jsonPath("$[2].region").doesNotExist())
        .andExpect(jsonPath("$[0].region").doesNotExist());
  }

  @Test
  @DisplayName("the response carries NO personal data — no addresses, contacts, phones or emails")
  void responseCarriesNoPersonalData() throws Exception {
    // Legacy's shared MillReportStatusType held all of it for the drill-down PDF. Mill 730's
    // client location and both contacts ARE seeded, so if the projection ever widened to legacy's
    // shape those values would appear here.
    String body =
        mockMvc
            .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("100 MAIN STREET")
        .doesNotContain("CRANBROOK")
        .doesNotContain("V1C1A1")
        .doesNotContain("HEAD OFFICE CONTACT")
        .doesNotContain("DIVISION CONTACT")
        .doesNotContain("2505551212")
        .doesNotContain("head.office@example.test")
        .doesNotContain("FULL OWNERSHIP HOLDINGS LTD");
  }

  @Test
  @DisplayName("an unreadable zone table costs Region only — the endpoint still answers 200")
  void unreadableZoneTableDegradesRatherThanFailing() throws Exception {
    // The end-to-end proof of the degrade, not just the unit-level one. APPRAISAL_SELL_PRICE_ZONE_
    // CODE is a shared ministry code table reached through a PUBLIC synonym, and a synonym whose
    // target is missing makes Oracle answer ORA-00942 for the whole statement; renaming the table
    // away reproduces that exactly. (Until 2026-09-02 the app read THE.ISP_SELL_PRICE_ZONE_CODE --
    // the MILL COLUMN's name, not legacy's lookup table -- which is absent on FTA, so this degrade
    // was firing on every real request. The name is fixed; this test keeps the safety net.)
    //
    // Two separate mechanisms can turn this into a 500, and both are asserted against here:
    //   1. Map.of().get(null) throws NPE on Java 21, and most mills carry no zone code.
    //   2. The zone read is a Spring Data JDBC repository call, which is itself transactional. If
    // it
    //      participated in findRows' transaction, its DataAccessException would mark that
    //      transaction rollback-only and the outer commit would throw UnexpectedRollbackException —
    //      a 500 raised AFTER the catch had already handled the failure.
    // Whichever is broken, this test fails; it is the only place either is observable.
    jdbc.execute(
        "ALTER TABLE THE.APPRAISAL_SELL_PRICE_ZONE_CODE RENAME TO APPRAISAL_SELL_PRICE_ZONE_GONE");
    try {
      mockMvc
          .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(5))
          // Every Region degrades (the field is omitted, Jackson non_null) — including mill 730's,
          // which normally resolves. Nothing else about the rows changes.
          .andExpect(jsonPath("$[0].region").doesNotExist())
          .andExpect(jsonPath("$[1].region").doesNotExist())
          .andExpect(jsonPath("$[2].region").doesNotExist())
          .andExpect(jsonPath("$[3].region").doesNotExist())
          .andExpect(jsonPath("$[4].region").doesNotExist())
          .andExpect(jsonPath("$[1].millName").value("MILL INFO FULL"))
          .andExpect(jsonPath("$[1].openDate").value("O: 2021-01-05"))
          .andExpect(jsonPath("$[4].active").value(true));
    } finally {
      // Restored in a finally: the Oracle container is shared by every IT in the run, so leaving
      // the
      // table renamed would redden Story 19.1's report tests for a reason that is not theirs.
      jdbc.execute(
          "ALTER TABLE THE.APPRAISAL_SELL_PRICE_ZONE_GONE RENAME TO APPRAISAL_SELL_PRICE_ZONE_CODE");
    }
  }

  @Test
  @DisplayName("after the zone table is restored, Region resolves again — the rename left no trace")
  void zoneTableRestoresCleanly() throws Exception {
    // Guards the finally above. If the restore ever silently failed, this would be the test that
    // says so, rather than an unrelated Story 19.1 IT failing later in the run.
    mockMvc
        .perform(get(ENDPOINT).param("year", "2021").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].region").value("Kootenay Selling Price Zone"));
  }

  // The "opened year that no mill reported in -> 200 []" case is NOT asserted here, deliberately.
  // Both seeded opened periods carry report-view rows (2021 has five, 2020 has one), and seeding a
  // third empty one is not safe: an opened ILCR_REPORTING_PERIOD changes mill-context resolution,
  // and
  // adding 2019 made MillContextResolveIT.unknownMillOrYear_returns404 answer 200 instead of 404 —
  // it probes 514/2019 precisely because 2019 is not an opened period. The case is covered instead
  // by
  // MillReportStatusControllerTest.emptyYearIsAnEmptyOkList (the 200 + [] contract) and
  // MillReportStatusServiceTest.emptyYearYieldsNoRows (the read), with the UI half in
  // MillReportStatus.test.tsx.

  @Test
  @DisplayName("no year -> 400 with the verbatim required-field message")
  void missingYear_rejects() throws Exception {
    mockMvc
        .perform(get(ENDPOINT).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
  }

  @Test
  @DisplayName("blank and non-numeric years -> 400, the same rejection as an absent one")
  void blankAndNonNumericYear_reject() throws Exception {
    for (String year : new String[] {"  ", "not-a-year", "20x1"}) {
      mockMvc
          .perform(get(ENDPOINT).param("year", year).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(YEAR_REQUIRED));
    }
  }

  @Test
  @DisplayName("a year that is not an open reporting period -> 400, not a 500 system fault")
  void yearNotOpen_rejects() throws Exception {
    // 99999999999 is all digits but overflows an int: a year WAS supplied, so it must reject as
    // "not an open period", not as a missing required field (P9).
    for (String year :
        new String[] {"1899", "1999", "0", "-1", "99999", "99999999999", "-99999999999"}) {
      mockMvc
          .perform(get(ENDPOINT).param("year", year).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value(YEAR_NOT_OPEN));
    }
  }
}
