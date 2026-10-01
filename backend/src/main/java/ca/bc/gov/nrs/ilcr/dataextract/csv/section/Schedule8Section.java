package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;

/**
 * Legacy {@code Schedule8Extract}: one 17-column row per Tree-to-Truck page.
 *
 * <p>{@code PAGE_NO} is legacy's page title, {@code "Page # n -TSA: <tsa> -CP: <cp>"}, built from
 * the page's 1-based position within its mill/year (legacy's {@code rowNumber}); a null TSA
 * concatenates as the literal {@code null}, as Java did for legacy. {@code S_BLOCK} is dashed when
 * the TSA is the {@code TFL} sentinel.
 */
public final class Schedule8Section implements SectionBuilder {

  /** Legacy {@code Constant.TFL}: the TSA-or-TFL selector's TFL marker. */
  static final String TFL = "TFL";

  static final String[] PAGE_COLUMNS = {
    "PAGE_NO",
    "DIVISION",
    "CONTACT",
    "PHONE",
    "REGION",
    "TSA",
    "TFL",
    "S_BLOCK",
    "LICENSE",
    "CUT_PERMIT",
    "SUPPORT_CTR",
    "BIOGEO_ZONE"
  };

  private static final String[] HEADER = {
    "MILL_NUMBER",
    "REPORTING_YEAR",
    "STATUS",
    "MILL_ID",
    "PAGE_NO",
    "DIVISION",
    "CONTACT",
    "PHONE",
    "REGION",
    "TSA",
    "TFL",
    "S_BLOCK",
    "LICENSE",
    "CUT_PERMIT",
    "SUPPORT_CTR",
    "BIOGEO_ZONE",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 8 ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /**
   * The row for one page.
   *
   * @param pageNumber the page's 1-based position within its mill/year
   */
  public String[] row(RowContext ctx, Page page, int pageNumber) {
    String[] cells = pageCells(page, pageNumber);
    String[] row = new String[cells.length + 1];
    System.arraycopy(cells, 0, row, 0, cells.length);
    row[cells.length] = text(page.comments());
    return ctx.with(row);
  }

  /** Legacy's page title: {@code "Page # n -TSA: <tsa> -CP: <cp>"}. */
  static String pageTitle(Page page, int pageNumber) {
    String cuttingPermit =
        page.cuttingPermit() == null || page.cuttingPermit().isEmpty()
            ? " - "
            : page.cuttingPermit();
    return "Page # " + pageNumber + "  -TSA: " + page.tsaNumber() + " -CP: " + cuttingPermit;
  }

  /** The twelve page-level cells shared by the four Schedule 8 sections, in legacy order. */
  static String[] pageCells(Page page, int pageNumber) {
    return new String[] {
      text(pageTitle(page, pageNumber)),
      text(page.division()),
      text(page.contact()),
      raw(page.phone()),
      raw(page.region()),
      raw(page.tsaNumber()),
      raw(page.tflNumber()),
      supplyBlock(page),
      raw(page.license()),
      raw(page.cuttingPermit()),
      raw(page.supportCentre()),
      raw(page.becZone())
    };
  }

  /** The page-level cells of the three sample sections, which do NOT dash the TFL supply block. */
  static String[] samplePageCells(Page page, int pageNumber, String sampleTitle) {
    return new String[] {
      text(sampleTitle),
      text(page.division()),
      text(page.contact()),
      raw(page.phone()),
      raw(page.region()),
      raw(page.tsaNumber()),
      raw(page.tflNumber()),
      raw(page.supplyBlock()),
      raw(page.license()),
      raw(page.cuttingPermit()),
      raw(page.supportCentre()),
      raw(page.becZone())
    };
  }

  /**
   * The main section's {@code S_BLOCK}: the stored TSB code with its first two characters stripped,
   * and dashed outright when the TSA is the {@code TFL} sentinel.
   *
   * <p>The truncation is legacy's, and is specific to THIS section. Legacy's extract emitted {@code
   * getSupplyBlock()} ({@code Schedule8Extract.java:67}), which its DAO had already set to {@code
   * tsb_number_code.substring(2)} ({@code Schedule8DAO.java:283}) — the code's leading two
   * characters repeat the TSA that sits in the column beside it. The three SAMPLE sections emit the
   * same field untruncated and undashed ({@code Schedule8TTTExtract.java:86}), which is why {@link
   * #samplePageCells} deliberately does not call this.
   *
   * <p>A code shorter than the two characters it is supposed to shed cannot be truncated, and a
   * silent {@code substring(2)} on one would throw mid-extract; such a value is emitted whole
   * rather than losing the file to it.
   */
  private static String supplyBlock(Page page) {
    String code = page.supplyBlock();
    if (code == null || code.isEmpty()) {
      return NULL_VALUE;
    }
    if (TFL.equals(page.tsaNumber())) {
      return NULL_VALUE;
    }
    return code.length() > 2 ? code.substring(2) : code;
  }
}
