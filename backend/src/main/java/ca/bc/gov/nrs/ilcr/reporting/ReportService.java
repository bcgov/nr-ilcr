package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.millinformation.MillInformationService;
import ca.bc.gov.nrs.ilcr.millinformation.dto.MillInformationSection;
import ca.bc.gov.nrs.ilcr.schedule1.Schedule1Service;
import ca.bc.gov.nrs.ilcr.schedule1.dto.Schedule1Response;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Service;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule2.Schedule2Service;
import ca.bc.gov.nrs.ilcr.schedule3.Schedule3Service;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule4.Schedule4Service;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Service;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Embedded JasperReports 7 engine (AD-16): renders each schedule section to a {@link JasperPrint}
 * in-process, with no standalone Jasper Server and no cross-network call. Templates are compiled
 * lazily on first use and cached in a per-schedule map (never at startup — the 20.1 boot-crash
 * lesson), so a template or engine problem in the deployed (read-only-root, non-root) container
 * surfaces as a 500 on a report endpoint instead of crashing the application context at boot.
 *
 * <p>Two fill modes coexist (the recorded 20.2 data-feed decision):
 *
 * <ul>
 *   <li><b>Schedule 9</b> keeps its embedded-SQL template, filled on a {@link Connection} borrowed
 *       from the single {@code @Primary} application {@link DataSource} (AD-2/DL-25).
 *   <li><b>Schedules 5/6/7A/7B/11</b> are filled from a bean datasource mapped from each schedule's
 *       existing {@code *Service} DTO, so every derived total / $-per-unit / rmg / code-description
 *       is the tested service arithmetic rather than re-ported template SQL; these need no database
 *       connection at fill time (the data is already fetched).
 * </ul>
 *
 * <p>Data-sensitivity (AD-11/NFR3): this service logs only mill/year/section keys/record counts —
 * never cost, volume, or personal data.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final DataSource dataSource;
  private final Schedule1Service schedule1Service;
  private final Schedule2Service schedule2Service;
  private final Schedule3Service schedule3Service;
  private final Schedule4Service schedule4Service;
  private final Schedule5Service schedule5Service;
  private final Schedule6Service schedule6Service;
  private final Schedule7aService schedule7aService;
  private final Schedule7bService schedule7bService;
  private final Schedule8Service schedule8Service;
  private final Schedule9Service schedule9Service;
  private final Schedule10Service schedule10Service;
  private final Schedule11Service schedule11Service;
  private final MillInformationService millInformationService;
  private final ReportVirtualizerFactory virtualizerFactory;

  /**
   * Compiled templates, built on first use and cached (boot-safe); keyed by classpath template
   * path.
   *
   * <p>Keyed by PATH rather than by {@link ScheduleKey} because not every report is a schedule —
   * the Mill Information report has no place in that enum, whose iteration order IS the combined
   * print's fixed section order (BR-08).
   */
  private final Map<String, JasperReport> compiledTemplates = new ConcurrentHashMap<>();

  /**
   * Constructs a new ReportService.
   *
   * @param dataSource the dedicated reporting datasource (Story 29.1) the Schedule 9 fill borrows
   *     from — its own small pool, isolated from the {@code @Primary} transactional pool so a burst
   *     of report renders cannot starve ordinary schedule requests (its connections are read-only
   *     as a hint, not an enforced privilege)
   * @param schedule1Service the Schedule 1 read (bean-datasource feed, Story 20.5 — the statement +
   *     the itemized Other-Cost-List sub-document)
   * @param schedule2Service the Schedule 2 read (bean-datasource feed, Story 20.6)
   * @param schedule3Service the Schedule 3 read (bean-datasource feed, Story 20.7 — the
   *     three-column ledger + the two itemization sub-documents)
   * @param schedule4Service the Schedule 4 read (bean-datasource feed, Story 20.9)
   * @param schedule5Service the Schedule 5 read (bean-datasource feed)
   * @param schedule6Service the Schedule 6 read (bean-datasource feed)
   * @param schedule7aService the Schedule 7A read (bean-datasource feed)
   * @param schedule7bService the Schedule 7B read (bean-datasource feed)
   * @param schedule8Service the Schedule 8 read (bean-datasource feed, Story 20.8 — the three-level
   *     page → sample → rate-detail hierarchy)
   * @param schedule9Service the Schedule 9 read seam, used for the empty-schedule pre-check (29.10
   *     — through the service, not the repository)
   * @param schedule10Service the Schedule 10 read (bean-datasource feed, Story 20.4)
   * @param schedule11Service the Schedule 11 read (bean-datasource feed)
   * @param millInformationService the Mill Information read (all mills for one reporting year)
   * @param virtualizerFactory builds the per-render Jasper swap-file virtualizer (Story 29.2) so a
   *     large or combined fill spills page objects to disk instead of pinning them on the heap
   */
  public ReportService(
      @Qualifier("reportingDataSource") DataSource dataSource,
      Schedule1Service schedule1Service,
      Schedule2Service schedule2Service,
      Schedule3Service schedule3Service,
      Schedule4Service schedule4Service,
      Schedule5Service schedule5Service,
      Schedule6Service schedule6Service,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule8Service schedule8Service,
      Schedule9Service schedule9Service,
      Schedule10Service schedule10Service,
      Schedule11Service schedule11Service,
      MillInformationService millInformationService,
      ReportVirtualizerFactory virtualizerFactory) {
    this.dataSource = dataSource;
    this.schedule1Service = schedule1Service;
    this.schedule2Service = schedule2Service;
    this.schedule3Service = schedule3Service;
    this.schedule4Service = schedule4Service;
    this.schedule5Service = schedule5Service;
    this.schedule6Service = schedule6Service;
    this.schedule7aService = schedule7aService;
    this.schedule7bService = schedule7bService;
    this.schedule8Service = schedule8Service;
    this.schedule9Service = schedule9Service;
    this.millInformationService = millInformationService;
    this.schedule10Service = schedule10Service;
    this.schedule11Service = schedule11Service;
    this.virtualizerFactory = virtualizerFactory;
  }

  /**
   * Render the standalone Schedule 9 PDF for a mill/year (Story 20.1 endpoint, unchanged). A
   * mill/year whose Schedule 9 has no records yields no PDF — the legacy single-schedule outcome
   * (ERR-005) — so this pre-checks the record count via the Story 9.1 read and throws {@link
   * ScheduleNotFoundException} (→ 404 {@code Schedule not found.}) before filling.
   *
   * @param millId the mill id (already validated + resolved by the caller's context guard)
   * @param year the reporting year
   * @return the filled report, ready to stream to the response (the caller closes it after export)
   */
  public RenderedReport renderSchedule9(long millId, int year) {
    try (VirtualizerHandle handle = new VirtualizerHandle(virtualizerFactory.create())) {
      // Schedule 9 fills from its embedded-SQL template and carries its own title block, so the
      // resolved bean-section title block is irrelevant here (passed null, ignored by
      // fillSchedule9).
      // Standalone Schedule 9 (20.1): no bookmark. A null bookmark title suppresses the section's
      // outline anchor, so this single-schedule PDF has no top-level bookmark at all.
      JasperPrint print =
          fillSection(
              ScheduleKey.SCHEDULE_9,
              millId,
              year,
              PrintOptions.showEverything(),
              null,
              null,
              handle.virtualizer());
      if (print == null) {
        throw new ScheduleNotFoundException();
      }
      return handle.transferTo(List.of(print));
    }
  }

  /** Fill parameter gating the report body; the templates read it in printWhenExpressions. */
  private static final String PARAM_PRINT_BODY = "p_do_print_body";

  /** Fill parameter carrying a section's PDF outline title, or null to suppress its anchor. */
  private static final String PARAM_BOOKMARK_TITLE = "bookmarkTitle";

  /**
   * The Mill Information report's classpath template. Not a {@link ScheduleKey} — it is not a
   * schedule.
   */
  private static final String MILL_INFORMATION_TEMPLATE = "reports/mill-information.jrxml";

  /**
   * Render the Mill Information report for one reporting year: every mill with a report-status row
   * for that year, one section each, combined into a single PDF (BR-01/BR-05).
   *
   * <p>One fill per mill rather than one fill over an all-mills datasource, which is the legacy
   * shape ({@code ILCRPrintService.getMillReportPrintStream} adds one JasperPrint per mill and
   * exports the list). It is what gives each mill its own title block and its own first page, and
   * it lets the outline anchor stay a fill parameter as in every other template here.
   *
   * <p>A year with no mills yields no PDF and a 404 of its own ({@link
   * MillInformationNoMillsException}). By the time this runs the caller has already rejected any
   * year that is not an OPEN reporting period, so reaching here means an opened year genuinely has
   * no mill report statuses — a data condition, not a fault, which is why it does not share the
   * catch-all {@code undefinedError} that a real render failure raises.
   *
   * @param year the reporting year
   * @return the filled report, ready to stream (the caller closes it after export)
   */
  public RenderedReport renderMillInformation(int year) {
    List<MillInformationSection> sections = millInformationService.findSections(year);
    if (sections.isEmpty()) {
      // WARN, not ERROR: the year is open and simply has no mills initialised against it. Nobody
      // needs to fix code for this, so it must not raise the 5xx rate or page anyone.
      log.warn("No mill carries a report status for year {} — nothing to render", year);
      throw new MillInformationNoMillsException();
    }
    try (VirtualizerHandle handle = new VirtualizerHandle(virtualizerFactory.create())) {
      List<JasperPrint> prints = new ArrayList<>();
      for (MillInformationSection section : sections) {
        prints.add(fillMillInformation(section, year, handle.virtualizer()));
      }
      log.info("Rendered {} mill information sections for year {}", prints.size(), year);
      return handle.transferTo(prints);
    }
  }

  /**
   * Fill one mill's section. Always exactly one detail row, so there is no skip-empty case here.
   */
  private JasperPrint fillMillInformation(
      MillInformationSection section, int year, JRSwapFileVirtualizer virtualizer) {
    Map<String, Object> params = new HashMap<>();
    params.put("year", year);
    params.put(PARAM_PRINT_BODY, Boolean.TRUE);
    params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
    SectionData data = MillInformationSectionMapper.map(section);
    try {
      return JasperFillManager.fillReport(
          template(MILL_INFORMATION_TEMPLATE), params, new JRMapCollectionDataSource(data.rows()));
    } catch (JRException e) {
      log.error("Mill Information fill failed for mill {} year {}", section.millId(), year, e);
      throw new MillInformationReportException();
    }
  }

  /**
   * Fill ONE schedule section for the mill/year, or return {@code null} when the schedule has no
   * data for that mill/year (the skip-empty signal, BR-09). Schedule 9 uses the embedded-SQL
   * connection fill; the others use their {@code *Service} DTO mapped to a bean datasource.
   *
   * @param key the schedule to render
   * @param millId the validated mill id
   * @param year the reporting year
   * @param options the print options (schedule information / comments) passed through to the
   *     template
   * @param millTitleBlock the {@code name-number} title block resolved ONCE for the request and
   *     shared by every bean-section header (Schedule 9 supplies its own, so it is ignored there)
   * @param bookmarkTitle the top-level PDF outline title for this section, or {@code null} for none
   *     (the standalone Schedule 9 path passes null so its single-schedule PDF has no bookmark)
   * @param virtualizer the per-render swap-file virtualizer (Story 29.2), passed as the Jasper fill
   *     virtualizer so this section's page objects can spill to disk under a large fill
   * @return the filled {@link JasperPrint}, or {@code null} when the schedule has no data
   */
  public JasperPrint fillSection(
      ScheduleKey key,
      long millId,
      int year,
      PrintOptions options,
      String millTitleBlock,
      String bookmarkTitle,
      JRSwapFileVirtualizer virtualizer) {
    return switch (key) {
      case SCHEDULE_1 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              schedule1Section(millId, year),
              virtualizer);
      case SCHEDULE_2 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule2SectionMapper.map(schedule2Service.getSchedule2(millId, year, false)),
              virtualizer);
      case SCHEDULE_3 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              schedule3Section(millId, year),
              virtualizer);
      case SCHEDULE_4 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              schedule4Section(millId, year),
              virtualizer);
      case SCHEDULE_5 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule5SectionMapper.map(schedule5Service.getSchedule5(millId, year, false)),
              virtualizer);
      case SCHEDULE_6 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule6SectionMapper.map(schedule6Service.getSchedule6(millId, year, false)),
              virtualizer);
      case SCHEDULE_7A ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule7aSectionMapper.map(schedule7aService.getSchedule7a(millId, year, false)),
              virtualizer);
      case SCHEDULE_7B ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule7bSectionMapper.map(schedule7bService.getSchedule7b(millId, year, false)),
              virtualizer);
      case SCHEDULE_8 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule8SectionMapper.map(schedule8Service.getSchedule8(millId, year, false)),
              virtualizer);
      case SCHEDULE_10 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule10SectionMapper.map(schedule10Service.getSchedule10(millId, year, false)),
              virtualizer);
      case SCHEDULE_11 ->
          fillBean(
              key,
              millId,
              year,
              options,
              millTitleBlock,
              bookmarkTitle,
              Schedule11SectionMapper.map(schedule11Service.getSchedule11(millId, year, false)),
              virtualizer);
      case SCHEDULE_9 -> fillSchedule9(millId, year, options, bookmarkTitle, virtualizer);
    };
  }

  /**
   * Build the Schedule 3 section (the three-column ledger plus the two itemization sub-documents),
   * or {@code null} when the mill/year has no Schedule 3 summary (the BR-09 skip-empty rule).
   *
   * <p>The absence check is {@code findSchedule3}, NOT the never-404 {@code getSchedule3}, so the
   * skip runs off an explicit signal rather than a thrown exception. Being precise about what that
   * buys, because the first draft of this comment overstated it (#296 code review): HERE it is
   * defence in depth, not a live fix — the two sub-document reads below still throw on an absent
   * summary and are still caught, so {@code getSchedule3} would yield the same null section today.
   * The Schedule 1 sibling is where it genuinely matters: {@code schedule1Section} TOLERATES a
   * missing Other-Costs document, so a never-404 read there really would emit a blank section.
   * Read-only: every read passes {@code callerMayEdit = false} (no BR-09 crown push).
   */
  private SectionData schedule3Section(long millId, int year) {
    Schedule3Response summary = schedule3Service.findSchedule3(millId, year, false).orElse(null);
    if (summary == null) {
      log.debug(
          "Schedule 3 summary not found for mill {} year {} -> skipping section (BR-09)",
          millId,
          year);
      return null;
    }
    try {
      return Schedule3SectionMapper.map(
          summary,
          schedule3Service.getOtherAcceptableDocument(millId, year, false),
          schedule3Service.getUnacceptableDocument(millId, year, false));
    } catch (ScheduleNotFoundException e) {
      return null;
    }
  }

  /** Build the Schedule 4 section, translating an absent document into the combined-print skip. */
  private SectionData schedule4Section(long millId, int year) {
    try {
      return Schedule4SectionMapper.map(schedule4Service.getSchedule4(millId, year, false));
    } catch (ScheduleNotFoundException e) {
      log.debug(
          "Schedule 4 summary not found for mill {} year {} -> skipping section (BR-09)",
          millId,
          year);
      return null;
    }
  }

  /**
   * Build the Schedule 1 section (the statement plus the itemized Other-Cost-List sub-document), or
   * {@code null} when the mill/year has no Schedule 1 summary (the BR-09 skip-empty rule).
   *
   * <p>The absence check is {@code findSchedule1}, NOT the never-404 {@code getSchedule1} — since
   * defect #296 the latter serves an EMPTY document for an unsaved Schedule 1, which would put a
   * blank Schedule 1 section into every combined report for a mill/year that has none. {@code
   * getOtherCostsDocument} still throws on an absent summary and is still caught below. Read-only:
   * both reads pass {@code callerMayEdit = false}.
   */
  private SectionData schedule1Section(long millId, int year) {
    Schedule1Response summary = schedule1Service.findSchedule1(millId, year, false).orElse(null);
    if (summary == null) {
      log.debug(
          "Schedule 1 summary not found for mill {} year {} -> skipping section (BR-09)",
          millId,
          year);
      return null;
    }

    ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument otherCosts = null;
    try {
      otherCosts = schedule1Service.getOtherCostsDocument(millId, year, false);
    } catch (ScheduleNotFoundException e) {
      log.debug(
          "Schedule 1 other costs document not found for mill {} year {} -> mapping with empty list",
          millId,
          year);
    }

    return Schedule1SectionMapper.map(summary, otherCosts);
  }

  /** Bean-datasource fill: no rows → no section (null); else fill from the mapped section rows. */
  private JasperPrint fillBean(
      ScheduleKey key,
      long millId,
      int year,
      PrintOptions options,
      String millTitleBlock,
      String bookmarkTitle,
      SectionData section,
      JRSwapFileVirtualizer virtualizer) {
    if (section == null || section.rows().isEmpty()) {
      return null;
    }
    Map<String, Object> params = baseParams(millTitleBlock, year, options, bookmarkTitle);
    params.putAll(section.parameters());
    params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
    log.info(
        "Rendering {} section for mill {} year {} ({} rows)",
        key,
        millId,
        year,
        section.rows().size());
    try {
      return JasperFillManager.fillReport(
          template(key), params, new JRMapCollectionDataSource(section.rows()));
    } catch (JRException e) {
      throw new ReportGenerationException("Failed to fill the " + key + " report", e);
    }
  }

  /** Schedule 9's embedded-SQL connection fill (20.1). Empty → null so the combiner can skip it. */
  private JasperPrint fillSchedule9(
      long millId,
      int year,
      PrintOptions options,
      String bookmarkTitle,
      JRSwapFileVirtualizer virtualizer) {
    // Count-only pre-check: the template's embedded SQL re-runs the full record query at fill time,
    // so
    // a findRecords().size() here would materialize (and throw away) that whole list just to test
    // empty.
    int recordCount = schedule9Service.countRecords(millId, year);
    if (recordCount == 0) {
      return null;
    }
    log.info(
        "Rendering SCHEDULE_9 section for mill {} year {} ({} records)", millId, year, recordCount);
    Map<String, Object> params = new HashMap<>();
    params.put("millId", millId);
    params.put("year", year);
    params.put(PARAM_PRINT_BODY, options.printBody());
    params.put("p_do_print_comment", options.printComment());
    params.put(PARAM_BOOKMARK_TITLE, bookmarkTitle);
    params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
    try (Connection connection = dataSource.getConnection()) {
      return JasperFillManager.fillReport(template(ScheduleKey.SCHEDULE_9), params, connection);
    } catch (SQLException | JRException e) {
      throw new ReportGenerationException("Failed to fill the Schedule 9 report", e);
    }
  }

  /**
   * The parameters every bean section shares: the request-scoped mill title block (resolved ONCE by
   * the caller, not re-queried per section), the year, and the two print flags.
   */
  private static Map<String, Object> baseParams(
      String millTitleBlock, int year, PrintOptions options, String bookmarkTitle) {
    Map<String, Object> params = new HashMap<>();
    params.put("millTitleBlock", millTitleBlock);
    params.put("year", year);
    params.put(PARAM_PRINT_BODY, options.printBody());
    params.put("p_do_print_comment", options.printComment());
    params.put(PARAM_BOOKMARK_TITLE, bookmarkTitle);
    return params;
  }

  /** The compiled template for a schedule, loaded on first use and cached (boot-safe). */
  private JasperReport template(ScheduleKey key) {
    return template(key.templatePath());
  }

  /** The compiled template at a classpath {@code .jrxml} path, loaded on first use and cached. */
  private JasperReport template(String templatePath) {
    return compiledTemplates.computeIfAbsent(templatePath, ReportService::load);
  }

  /**
   * Load the pre-compiled {@code .jasper} for a schedule from the classpath. Templates are compiled
   * from {@code .jrxml} to {@code .jasper} at BUILD time ({@code ReportPrecompiler}, run by
   * exec-maven-plugin with the build JDK), so the runtime — a JRE container without {@code javac} —
   * never compiles a report. The {@code .jrxml} stays the source of truth; only the extension
   * swaps.
   */
  private static JasperReport load(String templatePath) {
    String path = templatePath.replaceAll("\\.jrxml$", ".jasper");
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return (JasperReport) JRLoader.loadObject(in);
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to load the compiled report template " + path, e);
    }
  }

  /**
   * Owns a fill's virtualizer until a {@link RenderedReport} takes it over.
   *
   * <p>The swap file has exactly one owner at a time. Until the report exists that owner is this
   * handle, so a fill that throws — or an empty result that never becomes a PDF — still releases
   * it. Once {@link #transferTo} hands the virtualizer to the report, the streaming caller closes
   * it and this handle has nothing left to release.
   *
   * <p>This replaces a {@code boolean ownershipTransferred} plus {@code finally} in both render
   * methods. Same behaviour, but the transfer is now a method call rather than a flag a future edit
   * could forget to set.
   */
  private static final class VirtualizerHandle implements AutoCloseable {

    private JRSwapFileVirtualizer virtualizer;

    VirtualizerHandle(JRSwapFileVirtualizer virtualizer) {
      this.virtualizer = virtualizer;
    }

    JRSwapFileVirtualizer virtualizer() {
      return virtualizer;
    }

    /** Hand the virtualizer to a report, which becomes responsible for closing it. */
    RenderedReport transferTo(List<JasperPrint> sections) {
      RenderedReport report = new RenderedReport(sections, virtualizer);
      virtualizer = null;
      return report;
    }

    @Override
    public void close() {
      if (virtualizer != null) {
        virtualizer.cleanup();
      }
    }
  }
}
