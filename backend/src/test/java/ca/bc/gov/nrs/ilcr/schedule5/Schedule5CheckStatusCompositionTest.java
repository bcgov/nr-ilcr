package ca.bc.gov.nrs.ilcr.schedule5;

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
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CampCheckResult.CampCheckMessage;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5CheckStatusResponse;
import ca.bc.gov.nrs.ilcr.security.SchedulePermissions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.core.Authentication;

/**
 * The byte-exact check-status line composition, over ALL EIGHT legacy field segments and against
 * the REAL {@code messages.properties} bundle.
 *
 * <p><strong>Why a dedicated class.</strong> {@link Schedule5CheckStatusIT}'s fixtures can only
 * reach six of the eight segments — the camp-name line needs a whitespace-only stored name and the
 * four sub-list lines need particular row shapes — and {@link Schedule5CheckStatusServiceTest}
 * deliberately asserts {@code null} text, because the service emits keys and the controller
 * resolves them. Schedule 6 shipped with two of its four segments asserted NOWHERE until its own
 * review caught it; this class is that lesson applied up front.
 *
 * <p><strong>The one-byte trap this class exists to catch.</strong> Schedule 5's segments carry a
 * leading space and NO trailing space, so every line reads {@code "… - Camp name: Value Required"}
 * — no space before the final colon. Schedule 6's carry BOTH, so its lines read {@code "… - Supply
 * Block : Value Required"}. The difference is in the legacy source, not a typo: {@code
 * FacesUtil.addCheckStatusErrorMessage} (:134) appends {@code ": "} to whatever label it receives,
 * and Schedule 5's callers ({@code Schedule5MB.checkValidatedCurrentCamp():348-359, 425-436}) pass
 * segments that stop at the word. Copying Schedule 6's {@code FIELD_SEGMENTS} map wholesale gets
 * all eight of these wrong.
 */
@DisplayName("Schedule5Controller — byte-exact check-status composition (all eight segments)")
class Schedule5CheckStatusCompositionTest {

  private static final long MILL = 673L;
  private static final int YEAR = 2021;
  private static final String CAMP = "Cedar Flats Camp";

  private final Schedule5Service service = mock(Schedule5Service.class);
  private final Schedule5Controller controller =
      new Schedule5Controller(
          millContext(), service, mock(SchedulePermissions.class), realBundle());

  private static MillContextService millContext() {
    MillContextService millContextService = mock(MillContextService.class);
    when(millContextService.validateMillYearActive(any(), any()))
        .thenReturn(new MillYearContext(MILL, YEAR));
    return millContextService;
  }

  /** The real bundle — so a renamed or retexted key fails HERE, not just a hand-written stub. */
  private static MessageSource realBundle() {
    ResourceBundleMessageSource bundle = new ResourceBundleMessageSource();
    bundle.setBasename("messages");
    bundle.setDefaultEncoding("UTF-8");
    return bundle;
  }

  private static CampCheckMessage finding(String field) {
    return new CampCheckMessage("missingRequiredFieldMsg", field, null);
  }

  private List<String> composedTextsFor(String campName, String... fields) {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule5CheckStatusResponse(
                "ISSUES",
                List.of(),
                List.of(
                    new CampCheckResult(
                        9501,
                        campName,
                        false,
                        List.of(fields).stream()
                            .map(Schedule5CheckStatusCompositionTest::finding)
                            .toList()))));
    Schedule5CheckStatusResponse body =
        controller.checkStatus("673", "2021", mock(Authentication.class)).getBody();
    return body.camps().get(0).messages().stream().map(CampCheckMessage::text).toList();
  }

  @Test
  @DisplayName("all eight segments compose verbatim, with NO space before the final colon")
  void allEightSegments_composeVerbatim() {
    assertEquals(
        List.of(
            "Camp Report Name : Cedar Flats Camp - Camp name: Value Required",
            "Camp Report Name : Cedar Flats Camp - Road Distance to Operating Area: Value Required",
            "Camp Report Name : Cedar Flats Camp - Size of Camp: Value Required",
            "Camp Report Name : Cedar Flats Camp - Associated Camp Volume: Value Required",
            "Camp Report Name : Cedar Flats Camp - Other Camp Expense List (Description): "
                + "Value Required",
            "Camp Report Name : Cedar Flats Camp - Other Camp Expense List (Cost $): Value Required",
            "Camp Report Name : Cedar Flats Camp - Other Access Expense List (Description): "
                + "Value Required",
            "Camp Report Name : Cedar Flats Camp - Other Access Expense List (Cost $): "
                + "Value Required"),
        composedTextsFor(
            CAMP,
            Schedule5Service.FIELD_CAMP_NAME,
            Schedule5Service.FIELD_ROAD_DISTANCE,
            Schedule5Service.FIELD_SIZE_OF_CAMP,
            Schedule5Service.FIELD_ASSOCIATED_CAMP_VOLUME,
            Schedule5Service.FIELD_OTHER_CAMP_DESCRIPTION,
            Schedule5Service.FIELD_OTHER_CAMP_COST,
            Schedule5Service.FIELD_OTHER_ACCESS_DESCRIPTION,
            Schedule5Service.FIELD_OTHER_ACCESS_COST));
  }

  @Test
  @DisplayName("the label embeds the camp NAME, not an id — and a whitespace-only name verbatim")
  void campNameIsEmbeddedVerbatim() {
    // Legacy's addMessageCheckStatus takes the camp NAME as its `reportID` argument
    // (Schedule5MB.java:338, 347) — unlike Schedule 6, which composes from a 1-based ordinal. A
    // port
    // that reached for campId would render "Camp Report Name : 9501 - …".
    //
    // The whitespace name is the ONLY way the camp-name condition is reachable from stored data:
    // CAMP_NAME is NOT NULL in delivery, so legacy's null branch is dead, while the test itself is
    // TRIMMED (CoreUtil.isNullOrEmptyString(name, true)). The raw name goes into the line
    // untouched.
    // FIVE spaces after the colon, and that arithmetic is the assertion: one from the "Camp Report
    // Name : " prefix, three from the stored name, one leading the " - Camp name" segment. Getting
    // this wrong by a single space is exactly the failure mode this class guards.
    assertEquals(
        List.of("Camp Report Name :     - Camp name: Value Required"),
        composedTextsFor("   ", Schedule5Service.FIELD_CAMP_NAME));
  }

  @Test
  @DisplayName("the per-camp met message interpolates the camp name as its {0} argument")
  void perCampMetMessage_interpolatesCampName() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule5CheckStatusResponse(
                "ISSUES",
                List.of(),
                List.of(
                    new CampCheckResult(
                        9502,
                        CAMP,
                        true,
                        List.of(new CampCheckMessage("campRequirementsMetMsg", null, null))))));

    Schedule5CheckStatusResponse body =
        controller.checkStatus("673", "2021", mock(Authentication.class)).getBody();

    // Verbatim legacy :40 — note the trailing period, which the SCHEDULE-level banner does not
    // have.
    assertEquals(
        "All requirements for Cedar Flats Camp have been met.",
        body.camps().get(0).messages().get(0).text());
    // A met message names no field, so `field` is absent from the JSON entirely (NON_NULL).
    assertEquals(null, body.camps().get(0).messages().get(0).field());
  }

  @Test
  @DisplayName("the schedule-level banner resolves verbatim — and has NO trailing period")
  void scheduleBanner_resolvesVerbatim() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule5CheckStatusResponse(
                "MET", List.of(new MessageInfo("scheduleRequirementsMetMsg", null)), List.of()));

    Schedule5CheckStatusResponse body =
        controller.checkStatus("673", "2021", mock(Authentication.class)).getBody();

    // legacy :38 — `scheduleRequirementsMetMsg = All requirements for this schedule have been met`,
    // declared with spaces around the `=` (which .properties strips) and ending WITHOUT a period,
    // unlike campRequirementsMetMsg above. Both are pinned so neither drifts toward the other.
    assertEquals("All requirements for this schedule have been met", body.messages().get(0).text());
  }

  @Test
  @DisplayName("an unmapped field name fails loudly instead of rendering \"…Campnull: …\"")
  void unmappedField_throws() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule5CheckStatusResponse(
                "ISSUES",
                List.of(),
                List.of(new CampCheckResult(9501, CAMP, false, List.of(finding("isolatedCamp"))))));

    // Schedule5Service and Schedule5Controller.FIELD_SEGMENTS are the only two places these names
    // live, so a mismatch is a programming error — never client input. isolatedCamp is a real
    // CampRequest field that check status deliberately does NOT test (deviation (E)), which makes
    // it
    // the most likely name someone would wire in by mistake.
    assertThrows(
        IllegalStateException.class,
        () -> controller.checkStatus("673", "2021", mock(Authentication.class)));
  }
}
