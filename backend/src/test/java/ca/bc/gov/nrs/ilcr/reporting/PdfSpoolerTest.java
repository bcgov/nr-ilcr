package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the spooler's failure paths.
 *
 * <p>The success path is covered through {@code ReportControllerTest}, which is where the ORDERING
 * matters. What is worth isolating here is cleanup: the spooler holds two disposable things — the
 * report's virtualizer swap file and its own spool file — and every failure path has to release
 * both, on a component whose entire job is to make failures survivable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfSpooler")
class PdfSpoolerTest {

  @Mock private RenderedReport report;

  @TempDir private Path tempDir;

  private static final byte[] EXPORTED = "%PDF-1.4 ... %%EOF".getBytes(StandardCharsets.UTF_8);

  private List<Path> filesIn(Path directory) throws Exception {
    try (var files = Files.list(directory)) {
      return files.toList();
    }
  }

  @Test
  @DisplayName("a complete export yields a spool file of the exported length")
  void spoolsTheExport() throws Exception {
    doAnswer(
            invocation -> {
              ((OutputStream) invocation.getArgument(0)).write(EXPORTED);
              return null;
            })
        .when(report)
        .writeTo(any());

    ExportedPdf pdf = new PdfSpooler(tempDir.toString()).spool(report);

    assertThat(pdf.size()).isEqualTo(EXPORTED.length);
    // Consumed: the virtualizer is released as soon as the export is done, not held for the whole
    // transfer.
    verify(report).close();
    assertThat(filesIn(tempDir)).hasSize(1);

    pdf.close();
    assertThat(filesIn(tempDir)).isEmpty();
  }

  @Test
  @DisplayName("a failed export deletes its partial spool and still closes the report")
  void failedExportReleasesBoth() throws Exception {
    doThrow(new ReportGenerationException("export blew up", null)).when(report).writeTo(any());

    PdfSpooler spooler = new PdfSpooler(tempDir.toString());

    assertThatThrownBy(() -> spooler.spool(report)).isInstanceOf(ReportGenerationException.class);

    verify(report).close();
    assertThat(filesIn(tempDir)).isEmpty();
  }

  @Test
  @DisplayName(
      "an unusable spool directory still closes the report, rather than leaking its swap file")
  void unusableDirectoryStillClosesTheReport() throws Exception {
    // A directory path whose parent is a regular FILE: it can neither exist nor be created, so
    // createSpoolFile throws before any spool exists.
    Path notADirectory = Files.createFile(tempDir.resolve("occupied"));
    Path unusable = notADirectory.resolve("spool");

    // The regression this pins: the spool file used to be acquired BEFORE the try-with-resources
    // that owns the report, so this path returned without ever closing it — leaking the fill's
    // virtualizer swap file on the very volume that had just proved unusable, once per request.
    PdfSpooler spooler = new PdfSpooler(unusable.toString());

    assertThatThrownBy(() -> spooler.spool(report))
        .isInstanceOf(ReportGenerationException.class)
        .hasMessageContaining("Failed to create a spool file");

    verify(report).close();
    // Never exported to a stream that could not exist.
    verify(report, never()).writeTo(any());
  }

  @Test
  @DisplayName(
      "a spool directory that does not exist yet is created rather than failing the report")
  void missingDirectoryIsCreated() throws Exception {
    doAnswer(
            invocation -> {
              ((OutputStream) invocation.getArgument(0)).write(EXPORTED);
              return null;
            })
        .when(report)
        .writeTo(any());
    // An unmounted volume or a fresh container: configured, but not there yet.
    Path missing = tempDir.resolve("not").resolve("created").resolve("yet");

    ExportedPdf pdf = new PdfSpooler(missing.toString()).spool(report);

    assertThat(pdf.size()).isEqualTo(EXPORTED.length);
    assertThat(filesIn(missing)).hasSize(1);
    pdf.close();
  }

  @Test
  @DisplayName("an empty configured directory falls back to the JVM temp dir")
  void blankDirectoryFallsBackToTheJvmTempDir() throws Exception {
    // Mirrors ReportVirtualizerFactory's own fallback, so the two scratch locations stay the same
    // place by default and ops sizes one volume. Asserted on where the spool actually LANDS: a
    // constructor that merely returns non-null would pass while resolving the directory to "".
    doAnswer(
            invocation -> {
              ((OutputStream) invocation.getArgument(0)).write(EXPORTED);
              return null;
            })
        .when(report)
        .writeTo(any());

    Path jvmTemp = Path.of(System.getProperty("java.io.tmpdir"));
    List<Path> before = spoolsIn(jvmTemp);

    ExportedPdf pdf = new PdfSpooler("   ").spool(report);

    try (pdf) {
      assertThat(pdf.size()).isEqualTo(EXPORTED.length);
      // The spool landed in the JVM temp dir, not in "" — which Paths.get resolves to the working
      // directory, where createTempFile would happily succeed and the fallback would look fine.
      assertThat(spoolsIn(jvmTemp)).hasSize(before.size() + 1);
    }
    // Order-insensitive: DirectoryStream does not specify one.
    assertThat(spoolsIn(jvmTemp)).containsExactlyInAnyOrderElementsOf(before);
  }

  /** The spool files this class's naming convention would produce in {@code directory}. */
  private List<Path> spoolsIn(Path directory) throws Exception {
    try (var files = Files.newDirectoryStream(directory, "ilcr-report-*.pdf")) {
      List<Path> found = new java.util.ArrayList<>();
      files.forEach(found::add);
      return found;
    }
  }
}
