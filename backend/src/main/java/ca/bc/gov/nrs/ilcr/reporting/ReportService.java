package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Service;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Service;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Service;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aService;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bService;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.jackson.util.JacksonUtil;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Embedded JasperReports 7 engine (AD-16): renders each schedule section to a {@link JasperPrint}
 * in-process, with no standalone Jasper Server and no cross-network call. Templates are compiled
 * lazily on first use and cached in a per-schedule map (never at startup — the 20.1 boot-crash
 * lesson), so a template or engine problem in the deployed (read-only-root, non-root) container
 * surfaces as a 500 on a report endpoint instead of crashing the application context at boot.
 *
 * <p>Two fill modes coexist (the recorded 20.2 data-feed decision):
 * <ul>
 *   <li><b>Schedule 9</b> keeps its embedded-SQL template, filled on a {@link Connection} borrowed
 *       from the single {@code @Primary} application {@link DataSource} (AD-2/DL-25).</li>
 *   <li><b>Schedules 5/6/7A/7B/11</b> are filled from a bean datasource mapped from each schedule's
 *       existing {@code *Service} DTO, so every derived total / $-per-unit / rmg / code-description
 *       is the tested service arithmetic rather than re-ported template SQL; these need no database
 *       connection at fill time (the data is already fetched).</li>
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
  private final Schedule9Repository schedule9Repository;
  private final Schedule11Service schedule11Service;

  /** Compiled templates, built on first use and cached (boot-safe); keyed by {@link ScheduleKey}. */
  private final Map<ScheduleKey, JasperReport> compiledTemplates = new ConcurrentHashMap<>();

  /**
   * @param dataSource the single {@code @Primary} application datasource Schedule 9 fills from
   * @param schedule5Service the Schedule 5 read (bean-datasource feed)
   * @param schedule6Service the Schedule 6 read (bean-datasource feed)
   * @param schedule7aService the Schedule 7A read (bean-datasource feed)
   * @param schedule7bService the Schedule 7B read (bean-datasource feed)
   * @param schedule9Repository the Story 9.1 read, reused for the empty-schedule pre-check
   * @param schedule11Service the Schedule 11 read (bean-datasource feed)
   */
  public ReportService(
      DataSource dataSource,
      Schedule5Service schedule5Service,
      Schedule6Service schedule6Service,
      Schedule7aService schedule7aService,
      Schedule7bService schedule7bService,
      Schedule9Repository schedule9Repository,
      Schedule11Service schedule11Service) {
    this.dataSource = dataSource;
    this.schedule5Service = schedule5Service;
    this.schedule6Service = schedule6Service;
    this.schedule7aService = schedule7aService;
    this.schedule7bService = schedule7bService;
    this.schedule9Repository = schedule9Repository;
    this.schedule11Service = schedule11Service;
  }

  /**
   * Render the standalone Schedule 9 PDF for a mill/year (Story 20.1 endpoint, unchanged). A
   * mill/year whose Schedule 9 has no records yields no PDF — the legacy single-schedule outcome
   * (ERR-005) — so this pre-checks the record count via the Story 9.1 read and throws {@link
   * ScheduleNotFoundException} (→ 404 {@code Schedule not found.}) before filling.
   *
   * @param millId the mill id (already validated + resolved by the caller's context guard)
   * @param year the reporting year
   * @return the rendered PDF bytes
   */
  public byte[] renderSchedule9Pdf(long millId, int year) {
    // Schedule 9 fills from its embedded-SQL template and carries its own title block, so the
    // resolved bean-section title block is irrelevant here (passed null, ignored by fillSchedule9).
    JasperPrint print =
        fillSection(ScheduleKey.SCHEDULE_9, millId, year, PrintOptions.showEverything(), null);
    if (print == null) {
      throw new ScheduleNotFoundException();
    }
    return exportPdf(List.of(print));
  }

  /**
   * Fill ONE schedule section for the mill/year, or return {@code null} when the schedule has no
   * data for that mill/year (the skip-empty signal, BR-09). Schedule 9 uses the embedded-SQL
   * connection fill; the others use their {@code *Service} DTO mapped to a bean datasource.
   *
   * @param key the schedule to render
   * @param millId the validated mill id
   * @param year the reporting year
   * @param options the print options (schedule information / comments) passed through to the template
   * @param millTitleBlock the {@code name-number} title block resolved ONCE for the request and
   *     shared by every bean-section header (Schedule 9 supplies its own, so it is ignored there)
   * @return the filled {@link JasperPrint}, or {@code null} when the schedule has no data
   */
  public JasperPrint fillSection(
      ScheduleKey key, long millId, int year, PrintOptions options, String millTitleBlock) {
    return switch (key) {
      case SCHEDULE_5 -> fillBean(key, millId, year, options, millTitleBlock,
          Schedule5SectionMapper.map(schedule5Service.getSchedule5(millId, year, false)));
      case SCHEDULE_6 -> fillBean(key, millId, year, options, millTitleBlock,
          Schedule6SectionMapper.map(schedule6Service.getSchedule6(millId, year, false)));
      case SCHEDULE_7A -> fillBean(key, millId, year, options, millTitleBlock,
          Schedule7aSectionMapper.map(schedule7aService.getSchedule7a(millId, year, false)));
      case SCHEDULE_7B -> fillBean(key, millId, year, options, millTitleBlock,
          Schedule7bSectionMapper.map(schedule7bService.getSchedule7b(millId, year, false)));
      case SCHEDULE_11 -> fillBean(key, millId, year, options, millTitleBlock,
          Schedule11SectionMapper.map(schedule11Service.getSchedule11(millId, year, false)));
      case SCHEDULE_9 -> fillSchedule9(millId, year, options);
    };
  }

  /** Bean-datasource fill: no rows → no section (null); else fill from the mapped section rows. */
  private JasperPrint fillBean(ScheduleKey key, long millId, int year, PrintOptions options,
      String millTitleBlock, SectionData section) {
    if (section == null || section.rows().isEmpty()) {
      return null;
    }
    Map<String, Object> params = baseParams(millTitleBlock, year, options);
    params.putAll(section.parameters());
    log.info("Rendering {} section for mill {} year {} ({} rows)",
        key, millId, year, section.rows().size());
    try {
      return JasperFillManager.fillReport(
          template(key), params, new JRMapCollectionDataSource(section.rows()));
    } catch (JRException e) {
      throw new ReportGenerationException("Failed to fill the " + key + " report", e);
    }
  }

  /** Schedule 9's embedded-SQL connection fill (20.1). Empty → null so the combiner can skip it. */
  private JasperPrint fillSchedule9(long millId, int year, PrintOptions options) {
    int recordCount = schedule9Repository.findRecords(millId, year).size();
    if (recordCount == 0) {
      return null;
    }
    log.info("Rendering SCHEDULE_9 section for mill {} year {} ({} records)", millId, year, recordCount);
    Map<String, Object> params = new HashMap<>();
    params.put("millId", millId);
    params.put("year", year);
    params.put("p_do_print_body", options.printBody());
    params.put("p_do_print_comment", options.printComment());
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
      String millTitleBlock, int year, PrintOptions options) {
    Map<String, Object> params = new HashMap<>();
    params.put("millTitleBlock", millTitleBlock);
    params.put("year", year);
    params.put("p_do_print_body", options.printBody());
    params.put("p_do_print_comment", options.printComment());
    return params;
  }

  /** Export a list of filled sections to ONE bookmarked PDF (batch-mode bookmarks, BR-08). */
  byte[] exportPdf(List<JasperPrint> prints) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      JRPdfExporter exporter = new JRPdfExporter();
      exporter.setExporterInput(SimpleExporterInput.getInstance(prints));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
      exporter.setConfiguration(batchBookmarkConfig(prints.size()));
      exporter.exportReport();
      return out.toByteArray();
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to export the combined report to PDF", e);
    }
  }

  private static net.sf.jasperreports.pdf.SimplePdfExporterConfiguration batchBookmarkConfig(
      int sectionCount) {
    var config = new net.sf.jasperreports.pdf.SimplePdfExporterConfiguration();
    // One top-level bookmark per section (BR-08): each JasperPrint's name becomes a bookmark. Only
    // enable batch-mode bookmarks for a MULTI-section export — a single-section PDF (the standalone
    // Schedule 9 path) would otherwise get a spurious top-level bookmark whose name is null, since
    // that template's JasperPrint carries no bookmark title.
    config.setCreatingBatchModeBookmarks(sectionCount > 1);
    return config;
  }

  /** The compiled template for a schedule, built on first use and cached (boot-safe). */
  private JasperReport template(ScheduleKey key) {
    return compiledTemplates.computeIfAbsent(key, k -> compile(new ClassPathResource(k.templatePath())));
  }

  /**
   * Compile a classpath {@code .jrxml} to a {@link JasperReport}. The v7 template is parsed with the
   * Jackson-based loader into a {@link JasperDesign}, then compiled with the bundled expression
   * evaluator (no runtime JDT dependency needed) — the same load path Story 20.1 established.
   */
  private static JasperReport compile(Resource template) {
    try (InputStream in = template.getInputStream()) {
      JasperDesign design =
          JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
              .loadXml(in, JasperDesign.class);
      return JasperCompileManager.compileReport(design);
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to compile the report template " + template, e);
    }
  }
}
