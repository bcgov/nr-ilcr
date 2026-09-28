package ca.bc.gov.nrs.ilcr.schedule8;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;

/**
 * The legacy Tree-to-Truck page and sample titles, as the Schedule 8 screen shows them and as Check
 * Status prefixes its findings with them (#461).
 *
 * <p>Legacy composed the page title in {@code TreeToTruckReportDO.java:289-293} as {@code "Page # "
 * + rowNumber + " -TSA: " + tsaNumber + " -CP: " + cuttingPermit} (two spaces before {@code -TSA})
 * and printed it above each page's Check Status findings ({@code checkStatusSchedule8.xhtml:12});
 * sample findings were prefixed with the sample's row number ({@code
 * Schedule8CheckStatus.java:126}). The rewrite's Page Summary composes the same text client-side
 * ({@code schedule8/index.tsx} {@code pageLabel}/{@code sampleLabel}), with two null-guards legacy
 * lacked: a null TSA renders as empty rather than {@code "null"}, and a blank cutting permit as
 * {@code " - "}. This class mirrors THAT composition, so a Check Status line names a page exactly
 * as the summary row above it does. (The CSV extract's {@code Schedule8Section.pageTitle} keeps
 * legacy's unguarded TSA on purpose.)
 *
 * <p>Row numbers are 1-based positions in the document's page order (ascending report id) and in
 * the page's sample order — the same ordinals the screen numbers them with.
 */
final class Schedule8Labels {

  private Schedule8Labels() {}

  /** {@code "Page # 1 -TSA: TSA5 -CP: cp123"}; a blank cutting permit renders as {@code " - "}. */
  static String pageLabel(Page page, int pageNumber) {
    String tsa = page.tsaNumber() == null ? "" : page.tsaNumber();
    String cp =
        page.cuttingPermit() == null || page.cuttingPermit().isBlank()
            ? " - "
            : page.cuttingPermit();
    return "Page # " + pageNumber + "  -TSA: " + tsa + " -CP: " + cp;
  }

  /** {@code "Sample # 1 - CMET"}; a blank contract id leaves the trailing text empty. */
  static String sampleLabel(Sample sample, int sampleNumber) {
    String contract =
        sample.contractId() == null || sample.contractId().isBlank() ? "" : sample.contractId();
    return "Sample # " + sampleNumber + " - " + contract;
  }
}
