package ca.bc.gov.nrs.ilcr.schedule6;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult;
import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecordCheckResult.FieldIssue;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.Authentication;

/**
 * The byte-exact check-status line composition, over ALL FOUR legacy field segments and against the
 * REAL {@code messages.properties} bundle.
 *
 * <p>Why this class exists (code review 2026-08-04): {@code Schedule6CheckStatusIT} pins only the
 * Supply Block and Cost lines, because those are the only two reachable from its fixtures, and
 * {@code Schedule6CheckStatusServiceTest} deliberately asserts {@code assertNull(...text())} since
 * the service emits keys with no text. That left {@code " - TSA or TFL TYPE "} and
 * {@code " - TFL Number "} — the latter claimed byte-for-byte by AC7 — asserted NOWHERE, so losing a
 * leading or trailing space in either literal would have shipped silently. The TFL-Number line in
 * particular is unreachable end-to-end (the service derives {@code areaType == "TFL"} only when
 * {@code TFL_NUMBER_CODE} is non-null, so it can never also be blank), which is exactly why it needs
 * pinning here rather than in an IT.
 *
 * <p>Every expectation below is the legacy string from {@code Schedule6MB.checkStatus()} :155–172
 * composed by {@code FacesUtil.addCheckStatusErrorMessage} :127–139 — note the space on BOTH sides
 * of the final colon, which comes from the field segment's leading and trailing space.
 */
@DisplayName("Schedule6Controller — byte-exact check-status composition (all four segments)")
class Schedule6CheckStatusCompositionTest {

  private static final long MILL = 664L;
  private static final int YEAR = 2021;

  private final Schedule6Service service = mock(Schedule6Service.class);
  private final Schedule6Controller controller = new Schedule6Controller(
      millContext(), service, mock(SchedulePermissions.class), realBundle());

  private static MillContextService millContext() {
    MillContextService millContextService = mock(MillContextService.class);
    when(millContextService.validateMillYearActive(any(), any()))
        .thenReturn(new MillYearContext(MILL, YEAR));
    return millContextService;
  }

  /** The real bundle — so a renamed or retexted key fails here, not just a hand-written stub. */
  private static MessageSource realBundle() {
    ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
    bundle.setBasename("messages");
    bundle.setDefaultEncoding("UTF-8");
    return bundle;
  }

  private static FieldIssue issue(String field) {
    return new FieldIssue(field, new MessageInfo("missingRequiredFieldMsg", null));
  }

  private List<String> composedTextsFor(int rowCounter, String... fields) {
    when(service.checkStatus(anyLong(), anyInt())).thenReturn(new Schedule6CheckStatusResponse(
        "ISSUES", List.of(),
        List.of(new RoadRecordCheckResult(9501, rowCounter, false, null,
            List.of(fields).stream().map(Schedule6CheckStatusCompositionTest::issue).toList()))));
    Schedule6CheckStatusResponse body = controller
        .checkStatus("664", "2021", mock(Authentication.class)).getBody();
    return body.records().get(0).issues().stream().map(i -> i.message().text()).toList();
  }

  @Test
  @DisplayName("All four segments compose verbatim — including the two no other test asserts and the "
      + "deliberate legacy cost mislabel (\"TSA or TFL (Cost $)\" checks COST, Schedule6MB.java:172)")
  void allFourSegments_composeVerbatim() {
    assertEquals(
        List.of(
            "Road : 1 - TSA or TFL TYPE : Value Required",
            "Road : 1 - TFL Number : Value Required",
            "Road : 1 - Supply Block : Value Required",
            "Road : 1 - TSA or TFL (Cost $) : Value Required"),
        composedTextsFor(1,
            Schedule6Service.FIELD_AREA_TYPE,
            Schedule6Service.FIELD_TFL_NUMBER,
            Schedule6Service.FIELD_SUPPLY_BLOCK,
            Schedule6Service.FIELD_COST));
  }

  @Test
  @DisplayName("The ordinal is interpolated as a plain number: a 4-digit rowCounter must NOT pick up "
      + "locale grouping (legacy passed a String — Schedule6MB.java:147)")
  void largeOrdinal_hasNoThousandsSeparator() {
    assertEquals(
        List.of("Road : 1000 - Supply Block : Value Required"),
        composedTextsFor(1000, Schedule6Service.FIELD_SUPPLY_BLOCK));

    // Same for the per-record met banner, whose {0} arg IS MessageFormat-interpolated.
    when(service.checkStatus(anyLong(), anyInt())).thenReturn(new Schedule6CheckStatusResponse(
        "ISSUES", List.of(),
        List.of(new RoadRecordCheckResult(9502, 1000, true,
            new MessageInfo("roadRequirementsMetMsg", null), List.of()))));
    assertEquals("All requirements for 1000 have been met.",
        controller.checkStatus("664", "2021", mock(Authentication.class))
            .getBody().records().get(0).metMessage().text());
  }

  @Test
  @DisplayName("An unmapped field name fails loudly instead of rendering \"Road : 1null: ...\"")
  void unmappedField_throws() {
    when(service.checkStatus(anyLong(), anyInt())).thenReturn(new Schedule6CheckStatusResponse(
        "ISSUES", List.of(),
        List.of(new RoadRecordCheckResult(9501, 1, false, null, List.of(issue("volume"))))));
    assertThrows(IllegalStateException.class,
        () -> controller.checkStatus("664", "2021", mock(Authentication.class)));
  }
}
