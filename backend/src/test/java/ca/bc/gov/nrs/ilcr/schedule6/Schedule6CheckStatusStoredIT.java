package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.dto.base.CheckStatusOutcome;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckRequest.CheckEntry;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Story 15.0 AC 4 — Schedule 6's stored-data Check Status, against a live schema.
 *
 * <p>New in 15.0 and new in kind: every other schedule's Check Status already read the database,
 * Schedule 6's read only the submitted payload. This proves the stored path exists, reads the saved
 * rows, resolves its text, and mutates nothing — and, most importantly, that it DISAGREES with the
 * payload path when the screen and the database disagree, because that divergence is the deliberate
 * design and not a bug to be smoothed over.
 *
 * <p>Fixture: mill 726 / 2020 (V20260822) holds ONE road record — 8399, TSA {@code Y9}, supply
 * block {@code Y9A}, with an item-69 detail carrying {@code COST = 15000}. Everything the check
 * judges is present, so the SAVED schedule is complete.
 *
 * <p>The stored path has no endpoint by design, so this exercises {@link
 * Schedule6CheckStatusResolver} directly — which is also the seam Story 15.1's sweep will call.
 */
@DisplayName("Schedule 6 — Check Status over STORED data (Story 15.0 AC4)")
@TestPropertySource(properties = "ilcr.security.enabled=false")
class Schedule6CheckStatusStoredIT extends AbstractOracleIT {

  private static final long MILL = 726L;
  private static final int YEAR = 2020;

  @Autowired private Schedule6CheckStatusResolver resolver;
  @Autowired private Schedule6Service service;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("the SAVED schedule is complete: 726/2020 reads MET with a fully-resolved banner")
  void storedScheduleIsMet() {
    Schedule6CheckStatusResponse response = resolver.checkStatusStored(MILL, YEAR);

    assertEquals(CheckStatusOutcome.MET, response.outcome());
    assertTrue(response.records().isEmpty(), "the MET branch emits no per-record results");
    assertEquals("scheduleRequirementsMetMsg", response.messages().get(0).key());
    // Fully resolved — this is the AC 1 property, proven here against the real bundle rather than
    // asserted as non-null.
    assertEquals(
        "All requirements for this schedule have been met", response.messages().get(0).text());
  }

  @Test
  @DisplayName(
      "the two paths ANSWER DIFFERENT QUESTIONS: the same mill/year is MET stored and ISSUES for "
          + "a screen whose cost has been cleared")
  void storedAndPayloadPathsMayDisagree() {
    // The screen: row 8399's values with the cost blanked, as a reporter mid-edit would send.
    Schedule6CheckRequest onScreen =
        new Schedule6CheckRequest(
            null, List.of(new CheckEntry("Y9", null, "Y9A", null, null, null)));

    Schedule6CheckStatusResponse payload =
        resolver.resolve(service.checkStatus(MILL, YEAR, onScreen));
    Schedule6CheckStatusResponse stored = resolver.checkStatusStored(MILL, YEAR);

    assertEquals(CheckStatusOutcome.ISSUES, payload.outcome(), "the SCREEN is incomplete");
    assertEquals(CheckStatusOutcome.MET, stored.outcome(), "what is SAVED is complete");
    // The divergence is the whole point of AC 4: a report-level sweep must describe the database,
    // while the endpoint must keep describing the screen (legacy's ajax="false" postback applied
    // on-screen inputs before validating, Schedule6MB:139-140). Making these agree — in either
    // direction — would break one of the two callers.
    assertEquals(
        "Road : 1 - TSA or TFL (Cost $) : Value Required",
        payload.records().get(0).issues().get(0).message().text());
  }

  // NOTE on what is deliberately NOT tested here. A stored-path FINDING (and with it the stored
  // recordId, the display ordinal, and the composed "Road : N - … : Value Required" bytes) would
  // need this mill's cost cleared, and mill 726/2020 is a shared read-only fixture that
  // Schedule6CheckStatusIT and the wire-contract goldens also read — a temporarily-mutating test in
  // a one-container-per-JVM suite is precisely the order-dependence this story avoided elsewhere.
  // Nothing is lost by leaving it out: the composed bytes go through the SAME resolve(raw) the
  // payload path uses, byte-proven over all four field segments by
  // Schedule6CheckStatusCompositionTest, and the stored recordId/ordinal mapping is pinned over
  // stubbed rows by Schedule6CheckStatusServiceTest.checkStatusStored_ordinalIsTheDisplayPosition.

  @Test
  @DisplayName("the stored path mutates nothing (AD-5)")
  void storedPathMutatesNothing() {
    String before = fingerprint();

    assertNotNull(resolver.checkStatusStored(MILL, YEAR));

    assertEquals(before, fingerprint(), "check status must not touch a single column");
  }

  /** Every mutable column of the mill's road record and its item-69 detail, both audit pairs. */
  private String fingerprint() {
    return jdbcTemplate.queryForObject(
        "SELECT r.TSA_NUMBER || '|' || r.TSB_NUMBER_CODE || '|' || r.COMMENTS"
            + " || '|' || r.REVISION_COUNT || '|' || r.UPDATE_USERID || '|' || r.UPDATE_TIMESTAMP"
            + " || '|' || NVL(TO_CHAR(d.COST), 'null') || '|' || d.VOLUME"
            + " || '|' || d.UPDATE_USERID || '|' || d.UPDATE_TIMESTAMP"
            + " FROM THE.ROAD_MAINTENANCE_REPORT r"
            + " JOIN THE.ILCR_COST_REPORT_DETAIL d"
            + " ON d.ROAD_MAINTENANCE_REPORT_ID = r.ROAD_MAINTENANCE_REPORT_ID"
            + " WHERE r.ROAD_MAINTENANCE_REPORT_ID = 8399",
        String.class);
  }
}
