package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Service;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Service;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
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
  private final Schedule5Service schedule5Service;
  private final Schedule6Service schedule6Service;
  private final Schedule7aService schedule7aService;
  private final Schedule7bService schedule7bService;
  private final Schedule9Service schedule9Service;
  private final Schedule10Service schedule10Service;
  private final Schedule11Service schedule11Service;
  private final ReportVirtualizerFactory virtualizerFactory;

  /**
   * Compiled templates, built on first use and cached (boot-safe); keyed by {@link ScheduleKey}.
   */
  private final Map<ScheduleKey, JasperReport> compiledTemplates = new ConcurrentHashMap<>();

  /**
   * Constructs a new ReportService.
   *
   * @param dataSource the dedicated reporting datasource (Story 29.1) the Schedule 9 fill borrows
   *     from — its own small pool, isolated from the {@code @Primary} transactional pool so a burst
   *     of report renders cannot starve ordinary schedule requests (its connections are read-only
   *     as a hint, not an enforced privilege)
   * @param schedule5Service the Schedule 5 read (bean-datasource feed)
   * @param schedule6Service the Schedule 6 read (bean-datasource feed)
   * @param schedule7aService the Schedule 7A read (bean-datasource feed)
   * @param schedule7bService the Schedule 7B read (bean-datasource feed)
   * @param schedule9Service the Schedule 9 read seam, used for the empty-schedule pre-check (29.10
   *     — through the service, not the repository)
   * @param schedule10Service the Schedule 10 read (bean-datasource feed, Story 20.4)
   * @param schedule11Service the Schedule 11 read (bean-datasource feed)
   * @param virtualizerFactory builds the per-render Jasper swap-file virtualizer (Story 29.2) so a
   *     large or combined fill spills page objects to disk instead of pinning them on the heap
   */
  public ReportService(
      @Qualifier("reportingDataSource") DataSource dataSource,
      Schedule5Service schedule5Service,
      Schedule6Service schedule6Service,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule9Service schedule9Service,
      Schedule10Service schedule10Service,
      Schedule11Service schedule11Service,
      ReportVirtualizerFactory virtualizerFactory) {
    this.dataSource = dataSource;
    this.schedule5Service = schedule5Service;
    this.schedule6Service = schedule6Service;
    this.schedule7aService = schedule7aService;
    this.schedule7bService = schedule7bService;
    this.schedule9Service = schedule9Service;
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
    JRSwapFileVirtualizer virtualizer = virtualizerFactory.create();
    boolean ownershipTransferred = false;
    try {
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
              virtualizer);
      if (print == null) {
        throw new ScheduleNotFoundException();
      }
      RenderedReport report = new RenderedReport(List.of(print), virtualizer);
      ownershipTransferred = true;
      return report;
    } finally {
      // The empty-schedule 404 or any fill failure produces no PDF, so clean the swap file here;
      // otherwise ownership passes to the RenderedReport, which the streaming caller closes.
      if (!ownershipTransferred) {
        virtualizer.cleanup();
      }
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
    params.put("p_do_print_body", options.printBody());
    params.put("p_do_print_comment", options.printComment());
    params.put("bookmarkTitle", bookmarkTitle);
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
    params.put("p_do_print_body", options.printBody());
    params.put("p_do_print_comment", options.printComment());
    params.put("bookmarkTitle", bookmarkTitle);
    return params;
  }

  /** The compiled template for a schedule, loaded on first use and cached (boot-safe). */
  private JasperReport template(ScheduleKey key) {
    return compiledTemplates.computeIfAbsent(key, ReportService::load);
  }

  /**
   * Load the pre-compiled {@code .jasper} for a schedule from the classpath. Templates are compiled
   * from {@code .jrxml} to {@code .jasper} at BUILD time ({@code ReportPrecompiler}, run by
   * exec-maven-plugin with the build JDK), so the runtime — a JRE container without {@code javac} —
   * never compiles a report. The {@code .jrxml} stays the source of truth; only the extension
   * swaps.
   */
  private static JasperReport load(ScheduleKey key) {
    String path = key.templatePath().replaceAll("\\.jrxml$", ".jasper");
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return (JasperReport) JRLoader.loadObject(in);
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to load the compiled report template " + path, e);
    }
  }
}
