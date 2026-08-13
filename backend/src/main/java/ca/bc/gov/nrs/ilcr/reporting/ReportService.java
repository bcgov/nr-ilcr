package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.millcontext.ScheduleNotFoundException;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.jackson.util.JacksonUtil;
import net.sf.jasperreports.pdf.JRPdfExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Embedded JasperReports 7 engine (AD-16): renders Schedule 9 to PDF in-process, with no standalone
 * Jasper Server and no cross-network call. The {@code .jrxml} template is compiled to a {@link
 * JasperReport} lazily on the first request and cached — never at startup, so a template or engine
 * problem in the deployed (read-only-root, non-root) container surfaces as a 500 on this one
 * endpoint instead of crashing the application context at boot.
 *
 * <p>The report SQL is embedded in the template and runs on a {@link Connection} borrowed from the
 * single {@code @Primary} application {@link DataSource} (AD-2/DL-25 — no dedicated reporting user
 * or pool, decided 2026-08-13). The connection is opened in try-with-resources and closed the
 * instant the fill returns: {@code JasperPrint} is fully in memory by then, so PDF export needs no
 * database.
 *
 * <p>Data-sensitivity (AD-11/NFR3): this service logs only mill/year/record-count — never cost,
 * volume, or personal data.
 */
@Service
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final DataSource dataSource;
  private final Schedule9Repository schedule9Repository;
  private final Resource schedule9Template;

  /** Compiled lazily on first use (see {@link #schedule9Report()}); never at startup. Guarded by {@code this}. */
  private JasperReport schedule9Report;

  /**
   * @param dataSource the single {@code @Primary} application datasource the report fills from
   * @param schedule9Repository the Story 9.1 read, reused for the empty-schedule pre-check
   * @param schedule9Template the classpath {@code .jrxml}, compiled on first request (not at boot)
   */
  public ReportService(
      DataSource dataSource,
      Schedule9Repository schedule9Repository,
      @Value("classpath:reports/schedule9.jrxml") Resource schedule9Template) {
    this.dataSource = dataSource;
    this.schedule9Repository = schedule9Repository;
    this.schedule9Template = schedule9Template;
  }

  /**
   * Render the Schedule 9 PDF for a mill/year. A mill/year whose Schedule 9 has no records yields
   * no PDF — the legacy single-schedule outcome (ERR-005) — so this pre-checks the record count via
   * the Story 9.1 read and throws {@link ScheduleNotFoundException} (→ 404 {@code Schedule not
   * found.}) before filling, rather than emitting an empty/placeholder PDF.
   *
   * @param millId the mill id (already validated + resolved by the caller's context guard)
   * @param year the reporting year
   * @return the rendered PDF bytes
   */
  public byte[] renderSchedule9Pdf(long millId, int year) {
    int recordCount = schedule9Repository.findRecords(millId, year).size();
    if (recordCount == 0) {
      throw new ScheduleNotFoundException();
    }
    log.info(
        "Rendering Schedule 9 PDF for mill {} year {} ({} records)", millId, year, recordCount);

    Map<String, Object> params = new HashMap<>();
    params.put("millId", millId);
    params.put("year", year);
    // Single-schedule proof shows everything; the three-option selection UI is a later Epic 20
    // story.
    params.put("p_do_print_body", Boolean.TRUE);
    params.put("p_do_print_comment", Boolean.TRUE);

    JasperPrint print = fill(params);
    return exportPdf(print);
  }

  /** Fill the cached report on a primary-datasource connection, closed the moment fill returns. */
  private JasperPrint fill(Map<String, Object> params) {
    try (Connection connection = dataSource.getConnection()) {
      return JasperFillManager.fillReport(schedule9Report(), params, connection);
    } catch (SQLException | JRException e) {
      throw new ReportGenerationException("Failed to fill the Schedule 9 report", e);
    }
  }

  private static byte[] exportPdf(JasperPrint print) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      JRPdfExporter exporter = new JRPdfExporter();
      exporter.setExporterInput(new SimpleExporterInput(print));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
      exporter.exportReport();
      return out.toByteArray();
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to export the Schedule 9 report to PDF", e);
    }
  }

  /**
   * The compiled template, built on first use and cached (synchronized lazy init). Compilation is
   * kept OFF the startup path deliberately: if the engine cannot compile in the deployed container,
   * the failure surfaces as a 500 on this endpoint, not a boot-time context failure that would take
   * the whole backend pod down (the cause of the Epic 20 PR-env deploy failure). The lock cost is
   * negligible for a report endpoint, and it sidesteps the visibility pitfalls of a volatile field.
   */
  private synchronized JasperReport schedule9Report() {
    if (schedule9Report == null) {
      schedule9Report = compile(schedule9Template);
    }
    return schedule9Report;
  }

  /**
   * Compile the classpath {@code .jrxml} to a {@link JasperReport}. The v7 template is parsed with
   * the Jackson-based loader into a {@link JasperDesign}, then compiled with the bundled expression
   * evaluator (no runtime JDT dependency needed).
   */
  private static JasperReport compile(Resource template) {
    try (InputStream in = template.getInputStream()) {
      JasperDesign design =
          JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
              .loadXml(in, JasperDesign.class);
      return JasperCompileManager.compileReport(design);
    } catch (IOException | JRException e) {
      throw new ReportGenerationException("Failed to compile the Schedule 9 report template", e);
    }
  }
}
