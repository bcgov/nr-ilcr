package ca.bc.gov.nrs.ilcr.reporting;

import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds a fresh {@link JRSwapFileVirtualizer} per report render (Story 29.2). A virtualizer lets the
 * Jasper fill spill page objects to a swap file on disk once more than {@code maxSize} pages are held
 * in memory, so a big "all schedules" print (or several concurrent prints) does not pin the whole
 * section object graph on the JVM heap.
 *
 * <p>The swap directory and the in-memory page ceiling are configurable ({@code
 * ilcr.reporting.virtualizer.*}); an empty swap directory defaults to the JVM temp dir. The
 * virtualizer OWNS its swap file (swapOwner=true), so {@code cleanup()} deletes the on-disk file when
 * the render finishes (see {@link RenderedReport#close()}) — no swap-file leak on success or error.
 */
@Component
@ConditionalOnProperty(name = "ilcr.datasource.enabled", havingValue = "true")
class ReportVirtualizerFactory {

  private final String swapDirectory;
  private final int maxSize;
  private final int blockSize;
  private final int minGrowCount;

  ReportVirtualizerFactory(
      @Value("${ilcr.reporting.virtualizer.swap-directory:}") String swapDirectory,
      @Value("${ilcr.reporting.virtualizer.max-size:300}") int maxSize,
      @Value("${ilcr.reporting.virtualizer.block-size:4096}") int blockSize,
      @Value("${ilcr.reporting.virtualizer.min-grow-count:100}") int minGrowCount) {
    this.swapDirectory =
        StringUtils.hasText(swapDirectory) ? swapDirectory : System.getProperty("java.io.tmpdir");
    this.maxSize = maxSize;
    this.blockSize = blockSize;
    this.minGrowCount = minGrowCount;
  }

  /**
   * A new virtualizer backed by its own swap file in the configured directory. {@code maxSize} is the
   * number of pages kept in memory before the least-recently-used ones are paged out to disk; the
   * swap file grows in {@code blockSize}-byte blocks, {@code minGrowCount} at a time.
   */
  JRSwapFileVirtualizer create() {
    JRSwapFile swapFile = new JRSwapFile(swapDirectory, blockSize, minGrowCount);
    return new JRSwapFileVirtualizer(maxSize, swapFile, true);
  }
}
