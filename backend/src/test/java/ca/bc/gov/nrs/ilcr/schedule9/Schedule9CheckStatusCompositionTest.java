package ca.bc.gov.nrs.ilcr.schedule9;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.dto.base.MessageInfo;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.schedule9.dto.Schedule9CheckStatusResponse;
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
 */
@DisplayName("Schedule9Controller — byte-exact check-status composition (all eight segments)")
class Schedule9CheckStatusCompositionTest {

  private static final long MILL = 703L;
  private static final int YEAR = 2021;

  private final Schedule9Service service = mock(Schedule9Service.class);
  private final Schedule9Controller controller =
      new Schedule9Controller(
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

  @Test
  @DisplayName("valueRequired segments compose verbatim, with NO space before the final colon")
  void valueRequiredSegments_composeVerbatim() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule9CheckStatusResponse(
                false,
                List.of(
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Company ID: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Contractual Item: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Side Slope %: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Number of Units: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Unit Type: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Biogeoclimatic Zone: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Cost$: Value Required"),
                    new MessageInfo(
                        "missingRequiredFieldMsg",
                        "Contractual Work Report Id : 1 Source: Value Required")),
                null));

    Schedule9CheckStatusResponse body =
        controller.checkStatus("703", "2021", mock(Authentication.class)).getBody();

    assertEquals(8, body.errors().size());
    assertEquals(
        "Contractual Work Report Id : 1 Company ID: Value Required", body.errors().get(0).text());
  }

  @Test
  @DisplayName(
      "rangeError segments compose verbatim, using the real messages.properties invalidRangeErrorMsg")
  void rangeErrorSegments_composeVerbatim() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule9CheckStatusResponse(
                false,
                List.of(
                    new MessageInfo(
                        "invalidRangeErrorMsg",
                        "Contractual Work Report Id : 1 Side Slope %: Entered value must be between 0 and 99"),
                    new MessageInfo(
                        "invalidRangeErrorMsg",
                        "Contractual Work Report Id : 1 Number of Units: Entered value must be between 0.0 and 99,999.9"),
                    new MessageInfo(
                        "invalidRangeErrorMsg",
                        "Contractual Work Report Id : 1 Cost$: Entered value must be between 0 and 9,999,999")),
                null));

    Schedule9CheckStatusResponse body =
        controller.checkStatus("703", "2021", mock(Authentication.class)).getBody();

    assertEquals(3, body.errors().size());
    assertEquals(
        "Contractual Work Report Id : 1 Side Slope %: Entered value must be between 0 and 99",
        body.errors().get(0).text());
    assertEquals(
        "Contractual Work Report Id : 1 Number of Units: Entered value must be between 0.0 and 99,999.9",
        body.errors().get(1).text());
    assertEquals(
        "Contractual Work Report Id : 1 Cost$: Entered value must be between 0 and 9,999,999",
        body.errors().get(2).text());
  }

  @Test
  @DisplayName("the schedule-level banner resolves verbatim — and has NO trailing period")
  void scheduleBanner_resolvesVerbatim() {
    when(service.checkStatus(anyLong(), anyInt()))
        .thenReturn(
            new Schedule9CheckStatusResponse(
                true,
                List.of(),
                new MessageInfo(
                    "scheduleRequirementsMetMsg",
                    "All requirements for this schedule have been met")));

    Schedule9CheckStatusResponse body =
        controller.checkStatus("703", "2021", mock(Authentication.class)).getBody();

    assertEquals(
        "All requirements for this schedule have been met", body.requirementsMetMessage().text());
  }
}
