package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.util.List;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.base.JRBasePrintPage;
import net.sf.jasperreports.engine.fill.JRSwapFileVirtualizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link RenderedReport} (Story 29.2): the streaming export writes a real PDF to the
 * given stream (never buffering a {@code byte[]} itself), and {@link RenderedReport#close()}
 * disposes the virtualizer so the caller's try-with-resources cleans the swap file on every path. A
 * null virtualizer (were one ever passed) must be tolerated. No Spring context or database.
 */
@DisplayName("RenderedReport — streaming export + swap cleanup")
class RenderedReportTest {

  @Test
  @DisplayName("writeTo streams a %PDF to the provided output stream")
  void writeTo_streamsPdfToOutputStream() {
    JasperPrint print = new JasperPrint();
    print.setName("unit");
    print.setPageWidth(595);
    print.setPageHeight(842);
    print.addPage(new JRBasePrintPage());
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    new RenderedReport(List.of(print), null).writeTo(out);

    byte[] pdf = out.toByteArray();
    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
  }

  @Test
  @DisplayName("close() disposes the virtualizer (swap file cleaned on success and error paths)")
  void close_cleansUpTheVirtualizer() {
    JRSwapFileVirtualizer virtualizer = mock(JRSwapFileVirtualizer.class);

    new RenderedReport(List.of(), virtualizer).close();

    verify(virtualizer).cleanup();
  }

  @Test
  @DisplayName("close() tolerates a null virtualizer")
  void close_toleratesNullVirtualizer() {
    RenderedReport report = new RenderedReport(List.of(), null);

    assertThatCode(report::close).doesNotThrowAnyException();
  }
}
