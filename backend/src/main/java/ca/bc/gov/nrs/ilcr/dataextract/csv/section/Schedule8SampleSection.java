package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NO_DATA_FOUND;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.NULL_VALUE;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.raw;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.text;
import static ca.bc.gov.nrs.ilcr.dataextract.csv.ExtractFormat.whole;

import ca.bc.gov.nrs.ilcr.schedule8.dto.Page;
import ca.bc.gov.nrs.ilcr.schedule8.dto.RateRow;
import ca.bc.gov.nrs.ilcr.schedule8.dto.Sample;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Legacy {@code Schedule8TTTExtract}: one 41-column row per sample under a page.
 *
 * <p>{@code HELI_DIR} and {@code HELI_DUMP} are the legacy enum LABELS ({@code Uphill} / {@code
 * Downhill}, {@code Water Dump} / {@code Land Dump}) — the enums overrode {@code toString()}.
 * {@code DEDUCTIONS} is gated on the ADDITIONS total being present, a legacy copy-paste kept
 * verbatim. A total is "present" only when at least one of its rate rows carries a costing rate —
 * see {@link #legacyTotal}. {@code PAGE_NO} carries legacy's sample title, {@code "Sample # n -
 * <contractor>"}.
 */
public final class Schedule8SampleSection implements SectionBuilder {

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
    "CONTRACT_ID",
    "CUT_BLOCK",
    "SY_GROUNDBASE_%",
    "SY_GRAPPLE_%",
    "SY_HIGHLEAD_%",
    "SKY_%",
    "SKY_SLOPE_M",
    "SKY_SUPP_NUM",
    "SKY_AVG_DIST",
    "HELI_%",
    "DISTANCE",
    "HELI_CYCLE",
    "HELI_DIR",
    "HELI_DUMP",
    "SY_OTHER",
    "SY_OTHER_%",
    "TOTAL_%",
    "CONIF_M3",
    "DECID_M3",
    "ACTUAL_M3",
    "ORIG_TTT_RATE",
    "ADDITIONS",
    "DEDUCTIONS",
    "FINAL_TTT_RATE",
    "COMMENTS"
  };

  @Override
  public String title() {
    return "**** Schedule 8 - TTT ****";
  }

  @Override
  public String[] header() {
    return HEADER.clone();
  }

  /** The rows for one page: one per sample, or the per-page marker when it has none. */
  public List<String[]> rows(RowContext ctx, Page page, int pageNumber) {
    List<Sample> samples = page.samples() == null ? List.of() : page.samples();
    if (samples.isEmpty()) {
      return List.<String[]>of(noSamplesRow(ctx, page));
    }
    List<String[]> rows = new ArrayList<>();
    int sampleNumber = 0;
    for (Sample sample : samples) {
      sampleNumber++;
      rows.add(row(ctx, page, pageNumber, sample, sampleNumber));
    }
    return rows;
  }

  /** Legacy's per-page marker: the leading cells, a dash, division, contact, the marker. */
  static String[] noSamplesRow(RowContext ctx, Page page) {
    return ctx.with(NULL_VALUE, text(page.division()), text(page.contact()), NO_DATA_FOUND);
  }

  /** Legacy's sample title: {@code "Sample # n - <contractor>"}, contractor blank when absent. */
  static String sampleTitle(Sample sample, int sampleNumber) {
    String contractor = sample.contractId() == null ? "" : sample.contractId();
    return "Sample # " + sampleNumber + " - " + contractor;
  }

  /**
   * The owner's served total, or null where legacy's total was null. Legacy summed through {@code
   * CoreUtil.sumBigDecimalValues}, which returns null unless at least one rate is non-null ({@code
   * Schedule8TTTExtract.java:67-68}), so a sample with no rate rows printed {@code -} and only a
   * real sum printed {@code 0}. The owner seeds its totals at zero for the screen, so the absence
   * is read off the rate rows it serves alongside them (#474).
   */
  static BigDecimal legacyTotal(List<RateRow> rates, BigDecimal servedTotal) {
    boolean anyRate = rates != null && rates.stream().anyMatch(r -> r.costingRate() != null);
    return anyRate ? servedTotal : null;
  }

  private static String[] row(
      RowContext ctx, Page page, int pageNumber, Sample sample, int sampleNumber) {
    BigDecimal additionsTotal = legacyTotal(sample.additions(), sample.additionsTotal());
    BigDecimal deductionsTotal = legacyTotal(sample.deductions(), sample.deductionsTotal());
    String[] pageCells =
        Schedule8Section.samplePageCells(page, pageNumber, sampleTitle(sample, sampleNumber));
    String[] sampleCells = {
      raw(sample.contractId()),
      raw(sample.cutBlock()),
      whole(sample.groundBasePct()),
      whole(sample.grapplePct()),
      whole(sample.highleadPct()),
      whole(sample.skylinePct()),
      whole(sample.skylineSlopeDistance()),
      whole(sample.skylineSupportNumber()),
      whole(sample.supportAvgDistance()),
      whole(sample.helicopterPct()),
      whole(sample.distance()),
      whole(sample.cycleTime()),
      sample.uphillDirection() ? "Uphill" : "Downhill",
      sample.waterDumpDestination() ? "Water Dump" : "Land Dump",
      raw(sample.skidTypeCode()),
      whole(sample.otherSkiddingPct()),
      whole(sample.percentTotal()),
      whole(sample.coniferousVolume()),
      whole(sample.deciduousVolume()),
      whole(sample.actualHarvested()),
      whole(sample.originalRate()),
      whole(additionsTotal),
      additionsTotal != null ? whole(deductionsTotal) : NULL_VALUE,
      whole(sample.finalRate()),
      // Legacy read the sample's own COMMENTS column here. The owning Schedule 8 read has never
      // served a sample-level comments field (its document notes the gap), and the extract takes
      // figures only from the owner (AD-14), so the column keeps its header and shows the null
      // marker until the owner serves it.
      NULL_VALUE
    };
    String[] rest = new String[pageCells.length + sampleCells.length];
    System.arraycopy(pageCells, 0, rest, 0, pageCells.length);
    System.arraycopy(sampleCells, 0, rest, pageCells.length, sampleCells.length);
    return ctx.with(rest);
  }
}
