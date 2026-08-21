package ca.bc.gov.nrs.ilcr.reporting;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test for the BUILD-TIME report precompiler. Runs on the test JVM (a JDK, so {@code javac} is
 * present, unlike the JRE runtime container), which is exactly where the precompile is meant to
 * run. Pins the fail-fast contract: a build that compiles zero templates must fail rather than ship
 * an app with no {@code .jasper} to load (PR #320 review, SScholefield/Rylan #3).
 */
class ReportPrecompilerTest {

  @Test
  void compilesJrxmlToSiblingJasper(@TempDir Path dir) throws Exception {
    // A real shipped template — the same ones the process-classes precompile handles.
    Path jrxml = dir.resolve("schedule5.jrxml");
    try (InputStream in = getClass().getResourceAsStream("/reports/schedule5.jrxml")) {
      Files.copy(in, jrxml, StandardCopyOption.REPLACE_EXISTING);
    }

    ReportPrecompiler.main(new String[] {dir.toString()});

    Path jasper = dir.resolve("schedule5.jasper");
    assertTrue(Files.exists(jasper), "expected a sibling .jasper to be produced");
    assertTrue(Files.size(jasper) > 0, "expected the compiled .jasper to have content");
    // The .jrxml source is left in place (only the extension is swapped, not replaced).
    assertTrue(Files.exists(jrxml), "the .jrxml source should remain");
  }

  @Test
  void failsWhenNoArguments() {
    assertThrows(IllegalArgumentException.class, () -> ReportPrecompiler.main(new String[] {}));
  }

  @Test
  void failsWhenDirectoryHasNoTemplates(@TempDir Path emptyDir) {
    // A green build that compiled nothing would surface only as a runtime 500 in the JRE container
    // (the exact bug the precompile fixes), so an empty compile set must fail the build.
    String[] args = {emptyDir.toString()};
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> ReportPrecompiler.main(args));
    assertTrue(ex.getMessage().contains("*.jrxml"), "message should name the missing templates");
  }

  @Test
  void requiresAnExistingDirectory(@TempDir Path dir) {
    // A missing reports directory is a hard build error (fail-fast), not a silent no-op.
    Path missing = dir.resolve("does-not-exist");
    assertThrows(Exception.class, () -> ReportPrecompiler.main(new String[] {missing.toString()}));
  }
}
