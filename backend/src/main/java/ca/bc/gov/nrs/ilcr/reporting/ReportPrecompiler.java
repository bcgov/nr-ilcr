package ca.bc.gov.nrs.ilcr.reporting;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.jackson.util.JacksonUtil;

/**
 * BUILD-TIME ONLY — not a runtime component and never invoked by the app. Run by exec-maven-plugin at
 * the {@code process-classes} phase (where the build JVM is a JDK) to compile every {@code
 * reports/*.jrxml} to a sibling {@code reports/*.jasper}. Baking the compiled expression classes into
 * {@code .jasper} at build time means {@link ReportService} only ever LOADS them at runtime — so the
 * JRE container (no {@code javac}) never compiles a report (the cause of the Print Schedules 500).
 *
 * <p>Uses the same JR7 Jackson load path as the runtime read, so it parses the exact template format
 * the app ships. Arg 0 is the compiled-resources directory (e.g. {@code target/classes/reports}).
 */
public final class ReportPrecompiler {

  private ReportPrecompiler() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: ReportPrecompiler <reports-directory>");
    }
    Path reportsDir = Path.of(args[0]);
    int compiled = 0;
    try (var jrxmlFiles = Files.newDirectoryStream(reportsDir, "*.jrxml")) {
      for (Path jrxml : jrxmlFiles) {
        try (InputStream in = Files.newInputStream(jrxml)) {
          JasperDesign design = JacksonUtil.getInstance(DefaultJasperReportsContext.getInstance())
              .loadXml(in, JasperDesign.class);
          JasperReport report = JasperCompileManager.compileReport(design);
          String jasperName = toJasperName(jrxml.getFileName().toString());
          Path out = jrxml.resolveSibling(jasperName);
          JRSaver.saveObject(report, out.toFile());
          System.out.println("Precompiled report: " + jrxml.getFileName() + " -> " + jasperName);
          compiled++;
        }
      }
    }
    // Fail the build if nothing compiled. The runtime has no compile fallback, so a green build that
    // produced zero .jasper files would surface only as a 500 in the JRE container — the exact bug
    // this precompile fixes. A -DskipTests build must not sail past it (the reporting ITs would, but
    // they are a later phase). If templates are added/removed, update the expected count.
    if (compiled == 0) {
      throw new IllegalStateException(
          "No report templates (*.jrxml) found or compiled in " + reportsDir.toAbsolutePath()
              + " — the packaged app would have no .jasper files to load at runtime.");
    }
    System.out.println("Precompiled " + compiled + " report template(s).");
  }

  /** Swap the trailing {@code .jrxml} extension for {@code .jasper} (extension swap, not substring). */
  private static String toJasperName(String jrxmlFileName) {
    return jrxmlFileName.replaceAll("\\.jrxml$", ".jasper");
  }
}
