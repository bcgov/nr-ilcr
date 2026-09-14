package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.sumCosts;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy {@code Schedule8TTTAddExtract} / {@code Schedule8TTTDedExtract}: one 23-column row per
 * rate adjustment under a sample, then a {@code Total:} row whose label sits nineteen empty cells
 * in — under {@code OTH_DESC}, with the sum under {@code $/M3} — exactly where legacy put it.
 *
 * <p>The {@code ADDITIONS}/{@code DEDUCTIONS} cell is the cost ITEM's catalogue name, which the
 * rate row does not carry; it is resolved through the Schedule 8 owner's option lists (the same
 * lookup its editor dropdowns use). A page with no samples, or a sample with no rate rows, gets
 * legacy's eight-cell marker.
 */
public final class Schedule8RateSection implements SectionBuilder {

  /** Which of the two legacy builders this instance is. */
  public enum Kind {
    ADDITIONS("**** Schedule 8 - TTT Additions ****", "ADDITIONS"),
    DEDUCTIONS("**** Schedule 8 - TTT Deductions ****", "DEDUCTIONS");

    private final String title;
    private final String column;

    Kind(String title, String column) {
      this.title = title;
      this.column = column;
    }
  }

  private static final String[] LEADING = {
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
    "CONTRACT_ID",
    "CUT_BLOCK"
  };

  private static final String[] TRAILING = {"OTH_DESC", "$/M3", "COST_TYPE", "OTH_TYPE_DESC"};

  private final Kind kind;

  public Schedule8RateSection(Kind kind) {
    this.kind = kind;
  }

  @Override
  public String title() {
    return kind.title;
  }

  @Override
  public String[] header() {
    String[] header = new String[LEADING.length + 1 + TRAILING.length];
    System.arraycopy(LEADING, 0, header, 0, LEADING.length);
    header[LEADING.length] = kind.column;
    System.arraycopy(TRAILING, 0, header, LEADING.length + 1, TRAILING.length);
    return header;
  }

  /**
   * The rows for one page: per sample, one per rate row plus a total, or a marker; a page without
   * samples gets one marker.
   *
   * @param costItemNames the Schedule 8 owner's cost item code → catalogue name
   */
  public List<String[]> rows(
      RowContext ctx, Page page, int pageNumber, Map<Integer, String> costItemNames) {
    List<Sample> samples = page.samples() == null ? List.of() : page.samples();
    if (samples.isEmpty()) {
      return List.<String[]>of(Schedule8SampleSection.noSamplesRow(ctx, page));
    }
    List<String[]> rows = new ArrayList<>();
    int sampleNumber = 0;
    for (Sample sample : samples) {
      sampleNumber++;
      List<RateRow> rates = kind == Kind.ADDITIONS ? sample.additions() : sample.deductions();
      if (rates == null || rates.isEmpty()) {
        rows.add(Schedule8SampleSection.noSamplesRow(ctx, page));
        continue;
      }
      String[] pageCells =
          Schedule8Section.samplePageCells(
              page, pageNumber, Schedule8SampleSection.sampleTitle(sample, sampleNumber));
      List<Number> rateTotals = new ArrayList<>();
      for (RateRow rate : rates) {
        String itemName =
            rate.costItemCode() == null ? null : costItemNames.get(rate.costItemCode());
        String[] rateCells = {
          raw(sample.contractId()),
          raw(sample.cutBlock()),
          raw(itemName),
          raw(rate.itemDescription()),
          whole(rate.costingRate()),
          raw(rate.costTypeCode()),
          rate.costTypeDescription() == null || rate.costTypeDescription().isEmpty()
              ? NULL_VALUE
              : rate.costTypeDescription()
        };
        String[] rest = new String[pageCells.length + rateCells.length];
        System.arraycopy(pageCells, 0, rest, 0, pageCells.length);
        System.arraycopy(rateCells, 0, rest, pageCells.length, rateCells.length);
        rows.add(ctx.with(rest));
        rateTotals.add(rate.costingRate());
      }
      String[] total = new String[21];
      java.util.Arrays.fill(total, "");
      total[19] = "Total:";
      total[20] = whole(sumCosts(rateTotals));
      rows.add(total);
    }
    return rows;
  }
}
