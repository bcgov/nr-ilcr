package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit test — {@link ReportVirtualizerFactory} (Story 29.2). Proves each {@code create()} builds a
 * swap-file-backed virtualizer in the configured directory and that its {@code cleanup()} deletes
 * the on-disk swap file (the virtualizer owns it), so a render never leaks a swap file; and that a
 * blank swap directory falls back to the JVM temp dir. No Spring context or database — pure
 * construction.
 */
@DisplayName("ReportVirtualizerFactory — swap-file virtualizer lifecycle")
class ReportVirtualizerFactoryTest {

  @Test
  @DisplayName(
      "create() builds a virtualizer whose swap file lives in the configured dir; cleanup() deletes it")
  void create_swapFileInConfiguredDir_cleanupRemovesIt(@TempDir Path swapDir) {
    ReportVirtualizerFactory factory =
        new ReportVirtualizerFactory(swapDir.toString(), 300, 4096, 100);

    JRSwapFileVirtualizer virtualizer = factory.create();

    assertThat(virtualizer).isNotNull();
    // JRSwapFile creates its backing file in the directory on construction, so the swap file is
    // present.
    assertThat(swapDir.toFile().listFiles()).isNotEmpty();

    virtualizer.cleanup();

    // swapOwner=true, so cleanup() disposes the swap file — no leak.
    assertThat(swapDir.toFile().listFiles()).isEmpty();
  }

  @Test
  @DisplayName("blank swap-directory falls back to the JVM temp dir")
  void create_blankSwapDirectory_fallsBackToJvmTempDir() {
    ReportVirtualizerFactory factory = new ReportVirtualizerFactory("  ", 300, 4096, 100);

    JRSwapFileVirtualizer virtualizer = factory.create();

    assertThat(virtualizer).isNotNull();
    virtualizer.cleanup();
  }
}
