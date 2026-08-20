package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.MillContextService;
import ca.bc.gov.nrs.ilcr.millcontext.MillContextService.MillYearContext;
import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.reporting.api.PrintRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import net.sf.jasperreports.engine.JasperPrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the combined Print Schedules PDF (Epic 20.2). Given a validated mill/year context and
 * a {@link PrintRequest}, it fills each SELECTED in-scope schedule section in the FIXED legacy order
 * (5 → 6 → 7A → 7B → 9 → 11, the {@link ScheduleKey} declaration order), SKIPS any section with no
 * data (BR-09 skip-empty), and exports the accumulated sections to ONE bookmarked PDF (BR-08) — one
 * top-level bookmark per rendered schedule. When no selected content yields any data the result is
 * all-empty and no PDF is produced: {@link ScheduleNotFoundException} (→ 404 ERR-005).
 *
 * <p>Selected-but-unimplemented schedules (1/2/3/8/10) and the mill-information-report option are
 * accepted for forward-compatibility but produce no section yet — they are skipped-with-a-log
 * (documented interim gap) until their story lands. Selection VALIDATION (ERR-002/003/004) is the
 * controller's responsibility and runs before this.
 *
 * <p>Read-only (BR-01). Data-sensitivity (AD-11): logs only mill/year/section keys, never data.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class PrintService {

  private static final Logger log = LoggerFactory.getLogger(PrintService.class);

  private final ReportService reportService;
  private final MillContextService millContextService;

  public PrintService(ReportService reportService, MillContextService millContextService) {
    this.reportService = reportService;
    this.millContextService = millContextService;
  }

  /**
   * Render the combined Print Schedules PDF for the validated context and selection.
   *
   * @param context the validated (millId, year) pair
   * @param request the print selection (schedule flags + "all" + print options)
   * @return the combined, bookmarked PDF bytes
   * @throws ScheduleNotFoundException 404 — no selected in-scope schedule has any data (ERR-005)
   */
  // Deliberately NOT @Transactional: this is six independent read-only reads, each *Service.getScheduleN
  // managing its own transaction and the Schedule 9 fill borrowing (and releasing) its own connection. A
  // method-wide readOnly transaction would pin ONE pooled connection for the whole render while the
  // Schedule 9 fill grabs a SECOND — two connections per /print on a maximum-pool-size of 5, so a handful
  // of concurrent prints would exhaust the pool and block until the 30s timeout.
  public byte[] render(MillYearContext context, PrintRequest request) {
    PrintOptions options =
        new PrintOptions(request.printScheduleInformation(), request.printComments());
    Predicate<ScheduleKey> selected = selectionOf(request);

    if (isMillInformationReportOnly(request, selected)) {
      // The ONLY requested content is the deferred Mill Information report — no in-scope schedule
      // and no content option. That yields no PDF for a reason unrelated to missing schedule data,
      // so surface the honest "not yet available" rather than the misleading "Schedule not found.".
      log.info("Mill-information-report-only selection for mill {} year {} — report not yet available",
          context.millId(), context.year());
      throw new MillInformationReportUnavailableException();
    }

    // Resolve the mill title block ONCE per request and share it across every section header,
    // instead of re-querying it for each of the five bean sections.
    String millTitleBlock = millContextService.resolveMillTitleBlock(context.millId());

    List<JasperPrint> sections = new ArrayList<>();
    for (ScheduleKey key : ScheduleKey.values()) {
      if (!selected.test(key)) {
        continue;
      }
      // BR-08: pass the section's bookmark title so its template renders the top-level PDF outline
      // anchor. Every rendered schedule gets exactly one bookmark, including a single-schedule print.
      JasperPrint print = reportService.fillSection(
          key, context.millId(), context.year(), options, millTitleBlock, key.bookmarkTitle());
      if (print == null) {
        // BR-09 skip-empty: a selected schedule with no data contributes nothing and never aborts.
        log.info("Skipping {} for mill {} year {} — no data",
            key, context.millId(), context.year());
        continue;
      }
      sections.add(print);
    }

    logUnimplementedSelections(request);

    if (sections.isEmpty()) {
      // All-empty (S11): no selected content produced any section → no PDF, 404 ERR-005.
      throw new ScheduleNotFoundException();
    }
    log.info("Combining {} section(s) into one PDF for mill {} year {}",
        sections.size(), context.millId(), context.year());
    return reportService.exportPdf(sections);
  }

  /**
   * Whether the ONLY requested content is the deferred Mill Information report: it is checked, no
   * content option is on, and no in-scope schedule is selected. {@code allSchedules} counts as
   * selecting in-scope schedules (BR-07), so an "all" request is never mill-info-only even with both
   * content options off.
   */
  private static boolean isMillInformationReportOnly(
      PrintRequest request, Predicate<ScheduleKey> selected) {
    return request.printMillInformationReport()
        && !request.printScheduleInformation()
        && !request.printComments()
        && Arrays.stream(ScheduleKey.values()).noneMatch(selected);
  }

  /**
   * Whether a given in-scope schedule is selected, honouring the {@code allSchedules} shortcut
   * (BR-07: "all" selects every schedule; only in-scope ones render).
   */
  private static Predicate<ScheduleKey> selectionOf(PrintRequest request) {
    if (request.allSchedules()) {
      return key -> true;
    }
    return key -> switch (key) {
      case SCHEDULE_5 -> request.schedule5();
      case SCHEDULE_6 -> request.schedule6();
      case SCHEDULE_7A -> request.schedule7a();
      case SCHEDULE_7B -> request.schedule7b();
      case SCHEDULE_9 -> request.schedule9();
      case SCHEDULE_11 -> request.schedule11();
    };
  }

  /**
   * Note any selected-but-unimplemented content so the interim gap is visible in the logs without
   * failing the request (the frontend can build the full screen before those stories land).
   */
  private void logUnimplementedSelections(PrintRequest request) {
    boolean anyUnimplemented = request.allSchedules()
        || request.schedule1() || request.schedule2() || request.schedule3()
        || request.schedule4() || request.schedule8() || request.schedule10()
        || request.printMillInformationReport();
    if (anyUnimplemented) {
      // DEBUG, not INFO: the SPA (Story 20.3) disables the deferred schedules/options, so this is an
      // expected, benign skip for API/allSchedules callers — not worth a per-request INFO line. The
      // interim gap is documented; raise to DEBUG logging when diagnosing a caller that still sends them.
      log.debug("Print selection includes schedules/options not yet implemented in Epic 20.2 "
          + "(1/2/3/4/8/10 and/or the Mill Information report); those are skipped for now");
    }
  }
}
