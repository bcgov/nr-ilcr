package ca.bc.gov.nrs.ilcr.millreportstatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.dto.ReportingYear;
import ca.bc.gov.nrs.ilcr.millreportstatus.dto.MillReportStatusRow;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearGuard;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearNotOpenException;
import ca.bc.gov.nrs.ilcr.reporting.ReportYearRequiredException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for the Mill Status Report endpoint's guard and response shape.
 *
 * <p>The REAL {@link ReportYearGuard} is wired over a mocked {@link MillContextService} — the guard
 * is the endpoint's only validation, so mocking it away would leave the two 400s unasserted.
 *
 * <p>These also carry the "opened year no mill reported in" case, which the integration test
 * cannot: both seeded opened reporting periods have report-view rows, and seeding a third empty one
 * changes mill-context resolution and breaks {@code MillContextResolveIT}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MillReportStatusController")
class MillReportStatusControllerTest {

  @Mock private MillReportStatusService service;
  @Mock private MillContextService millContextService;

  private MillReportStatusController controller;

  @BeforeEach
  void createController() {
    controller = new MillReportStatusController(service, new ReportYearGuard(millContextService));
  }

  private void yearsAre(int... years) {
    when(millContextService.listReportingYears())
        .thenReturn(Arrays.stream(years).mapToObj(ReportingYear::new).toList());
  }

  @Test
  @DisplayName("an open year answers 200 with the service's rows, in the order given")
  void openYearReturnsRows() {
    yearsAre(2021, 2020);
    when(service.findRows(2021)).thenReturn(List.of(row(514), row(730)));

    ResponseEntity<List<MillReportStatusRow>> response =
        controller.getMillReportStatus("2021", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .extracting(MillReportStatusRow::millId)
        .containsExactly(514L, 730L);
  }

  @Test
  @DisplayName("an opened year with no mills answers 200 with an EMPTY list, never a 404")
  void emptyYearIsAnEmptyOkList() {
    // Deliberately unlike the sibling Mill Information PDF, where no mills means there is no
    // legitimate document (404). An empty sortable table is a correct render, so a 404 here would
    // make the page show a failure banner for a perfectly valid year.
    yearsAre(2021, 2019);
    when(service.findRows(2019)).thenReturn(List.of());

    ResponseEntity<List<MillReportStatusRow>> response =
        controller.getMillReportStatus("2019", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  @DisplayName("a surrounding whitespace-padded year is still accepted")
  void paddedYearIsAccepted() {
    yearsAre(2021);
    when(service.findRows(2021)).thenReturn(List.of());

    assertThat(controller.getMillReportStatus(" 2021 ", null).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @DisplayName("absent, blank and non-numeric years all reject as a missing required field")
  void unusableYearsRejectAsRequired() {
    for (String year : new String[] {null, "", "   ", "not-a-year", "20x1"}) {
      assertThatThrownBy(() -> controller.getMillReportStatus(year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearRequiredException.class)
          .extracting(e -> ((ReportYearRequiredException) e).getStatus())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    // Nothing is read for an unusable year.
    verify(service, never()).findRows(anyInt());
  }

  @Test
  @DisplayName("a parseable year that is not an open period rejects, rather than reaching the read")
  void yearNotOpenRejects() {
    yearsAre(2021, 2020);

    // "99999999999" is all digits but overflows an int. A year WAS supplied, so it must land here
    // rather than in the required-field rejection above (P9).
    for (String year :
        new String[] {"1899", "1999", "0", "-1", "99999", "99999999999", "-99999999999"}) {
      assertThatThrownBy(() -> controller.getMillReportStatus(year, null))
          .as("year=%s", year)
          .isInstanceOf(ReportYearNotOpenException.class)
          .extracting(e -> ((ReportYearNotOpenException) e).getStatus())
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }
    verify(service, never()).findRows(anyInt());
  }

  @Test
  @DisplayName("no mill/year working-context guard is ever consulted")
  void noWorkingContextGuardRuns() {
    // The endpoint must list EVERY mill, closed ones included, and carries no mill parameter.
    // validateMillYearActive would apply submitter mill scope and reject a closed mill, so a future
    // "alignment" with the schedule endpoints has to fail here.
    yearsAre(2021);
    when(service.findRows(2021)).thenReturn(List.of(row(514)));

    controller.getMillReportStatus("2021", null);

    verify(millContextService, never()).validateMillYearActive(anyLong(), anyInt());
    verify(millContextService, never()).validateMillYearActive(anyString(), anyString());
  }

  private static MillReportStatusRow row(long millId) {
    return new MillReportStatusRow(
        millId, "7300", "MILL", null, true, null, null, null, null, null, null, null);
  }
}
