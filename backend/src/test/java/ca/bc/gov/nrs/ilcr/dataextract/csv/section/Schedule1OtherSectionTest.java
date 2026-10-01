package ca.bc.gov.nrs.ilcr.dataextract.csv.section;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostRow;
import ca.bc.gov.nrs.ilcr.schedule1.dto.OtherCostsDocument;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test — {@link Schedule1OtherSection}. Beyond the header and title locks, this pins what
 * legacy's {@code Schedule1OtherExtract} did: every itemized row shows its OWN stored volume and
 * its own cost/volume (never the document's shared Other-Costs volume, nor the owner's per-row
 * figure derived from it), the total's volume is the sum of those per-row volumes, and the total
 * row formats the summed VOLUME to two decimals while formatting the summed COST to none — the
 * inverse of the rows above it.
 */
@DisplayName("Schedule1OtherSection — the itemized Other Costs rows and their Total row")
class Schedule1OtherSectionTest {

  private static final String NO_DATA = "*** NO DATA FOUND ***";

  private static final RowContext CTX = new RowContext("670", "2021", "Draft", "514");

  private final Schedule1OtherSection section = new Schedule1OtherSection();

  @Nested
  @DisplayName("fixed shape")
  class FixedShape {

    @Test
    @DisplayName("the title is legacy's verbatim")
    void title_isVerbatim() {
      // Schedule1OtherExtract.java:33.
      assertThat(section.title()).isEqualTo("**** Schedule 1 - Other Costs ****");
    }

    @Test
    @DisplayName("the header is the eight legacy columns, in order")
    void header_isTheEightLegacyColumns() {
      // Schedule1OtherExtract.java:109-118. Written out literally so a rename or reorder fails
      // here rather than in a downstream spreadsheet.
      assertThat(section.header())
          .containsExactly(
              "MILL_NUMBER",
              "REPORTING_YEAR",
              "STATUS",
              "MILL_ID",
              "DESCRIPTION",
              "VOLUME",
              "COST",
              "CPU_$/M3");
      assertThat(section.header()).hasSize(8);
    }

    @Test
    @DisplayName("header() hands out a copy, so a caller cannot mutate the shared columns")
    void header_isDefensivelyCopied() {
      String[] first = section.header();
      first[4] = "TAMPERED";

      assertThat(section.header()[4]).isEqualTo("DESCRIPTION");
    }
  }

  @Nested
  @DisplayName("the itemized rows")
  class ItemizedRows {

    @Test
    @DisplayName("each itemized cost becomes one row, then the Total row")
    void twoItems_becomeTwoRowsAndATotal() {
      OtherCostsDocument document =
          document(null, row("Aerial\tsurvey", 1_200, "1500"), row("Consulting", 1_800, null));

      List<String[]> rows = section.rows(CTX, document);

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0))
          .containsExactly(
              "670",
              "2021",
              "Draft",
              "514",
              "Aerial  survey", // the tab legacy replaced with two spaces
              "1,500", // VOLUME — grouped, no decimals (###,###,##0)
              "1,200", // COST — likewise
              "0.80"); // CPU_$/M3 — always exactly two decimals
      // A row with no stored volume shows the null marker in VOLUME, and so has no CPU either.
      assertThat(rows.get(1))
          .containsExactly("670", "2021", "Draft", "514", "Consulting", "-", "1,800", "-");
    }

    @Test
    @DisplayName("each row shows its OWN stored volume, even with no shared volume row at all")
    void eachRow_showsItsOwnVolume_whenTheSharedVolumeIsMissing() {
      // The mill 7777 / 2015 shape: itemized item-19 rows each carrying a volume, and no
      // null-description row, so the document's shared volume is null. Legacy printed each row's
      // ocl.getVolume() and ocl.getCostVolume() (Schedule1OtherExtract.java:77, :79) and summed the
      // per-row volumes into Total: (:81, :88), so none of these cells may collapse to "-".
      OtherCostsDocument document =
          document(
              null, row("OrtherCost1-1", 25_000, "175000"), row("Other Costs 2", 26_250, "125000"));

      List<String[]> rows = section.rows(CTX, document);

      assertThat(rows).hasSize(3);
      // 25000 / 175000 = 0.142857… -> 0.14; 26250 / 125000 = 0.21 — two DIFFERENT volumes, so a
      // section that repeated one figure across the rows would fail one of them.
      assertThat(rows.get(0))
          .containsExactly(
              "670", "2021", "Draft", "514", "OrtherCost1-1", "175,000", "25,000", "0.14");
      assertThat(rows.get(1))
          .containsExactly(
              "670", "2021", "Draft", "514", "Other Costs 2", "125,000", "26,250", "0.21");
      // Σvol 300000 (two decimals), Σcost 51250 (none), 51250 / 300000 = 0.170833… -> 0.17.
      assertThat(rows.get(2))
          .containsExactly("", "", "", "", "Total:", "300,000.00", "51,250", "0.17");
    }

    @Test
    @DisplayName("a shared volume that differs from a row's own is ignored")
    void sharedVolume_isNotReadByTheExtract() {
      // The shared figure is the sub-page's (BR-06), not the extract's: legacy's extract never read
      // it. 1000 here would give "1,000" / 0.30 / "1,000.00"; the row's own 4000 gives 0.08.
      OtherCostsDocument document = document("1000", row("Fuel", 300, "4000"));

      List<String[]> rows = section.rows(CTX, document);

      assertThat(rows.get(0)[5]).isEqualTo("4,000");
      assertThat(rows.get(0)[7]).isEqualTo("0.08");
      assertThat(rows.get(1)).containsExactly("", "", "", "", "Total:", "4,000.00", "300", "0.08");
    }

    @Test
    @DisplayName("the row CPU is the row's own cost/volume, not the owner's per-unit figure")
    void rowCpu_isCostOverOwnVolume_notTheOwnersPerUnit() {
      // Legacy's getCostVolume() is bigDecimalDivision(cost, volume) on the row itself
      // (CostVolumeType.java:85-86). The owner's perUnit divides by the SHARED volume, so it is
      // null whenever that row is missing — the very case this section must still print.
      OtherCostRow withDecoy =
          new OtherCostRow(
              8_201, "Fuel", 300, new BigDecimal("4000"), new BigDecimal("9.99"), null);

      List<String[]> rows = section.rows(CTX, document(null, withDecoy));

      assertThat(rows.get(0)[7]).isEqualTo("0.08");
    }

    @Test
    @DisplayName("a row with no description is dropped, as legacy's filtered list dropped it")
    void blankDescription_isFilteredOut() {
      // Legacy iterated getOtherCostListFiltered() (Schedule1DO.java:180-188), which kept only
      // rows whose description was non-empty, and decided the marker on THAT list. So a pair whose
      // only rows are blank-described gets the per-record marker, and a blank row is never summed
      // into Total:.
      List<String[]> onlyBlank =
          section.rows(CTX, document("1000", row(null, 500, null), row("", 300, null)));

      assertThat(onlyBlank).hasSize(1);
      assertThat(onlyBlank.get(0)[4]).isEqualTo("*** NO DATA FOUND ***");

      List<String[]> mixed =
          section.rows(CTX, document("1000", row(null, 500, null), row("Fuel", 300, null)));

      // One itemized row (Fuel) and the total — the blank row contributes to neither.
      assertThat(mixed).hasSize(2);
      assertThat(mixed.get(0)[4]).isEqualTo("Fuel");
      assertThat(mixed.get(1)[4]).isEqualTo("Total:");
      assertThat(mixed.get(1)[6]).isEqualTo("300");
    }
  }

  @Nested
  @DisplayName("the Total row")
  class TotalRow {

    @Test
    @DisplayName("the summed volume gets two decimals and the summed cost gets none")
    void totalRow_invertsTheUsualDecimalPairing() {
      OtherCostsDocument document =
          document(null, row("Aerial survey", 1_200, "1500"), row("Consulting", 1_800, "1500"));

      List<String[]> rows = section.rows(CTX, document);

      // Schedule1OtherExtract.java:85-92. Both sums are 3000 here, which is the point: legacy
      // printed the VOLUME total with two decimals and the COST total with none, the opposite of
      // the VOLUME/COST columns above. Not to be tidied into a consistent pairing.
      assertThat(rows.get(2))
          .containsExactly("", "", "", "", "Total:", "3,000.00", "3,000", "1.00");
    }

    @Test
    @DisplayName("the Total row drops the four leading context cells")
    void totalRow_hasNoContextCells() {
      OtherCostsDocument document = document(null, row("Aerial survey", 1_200, "1500"));

      List<String[]> rows = section.rows(CTX, document);

      // Legacy built the total row from scratch rather than from the row context, so the mill,
      // year, status and id cells are empty rather than repeated.
      assertThat(Arrays.copyOf(rows.get(rows.size() - 1), 4)).containsExactly("", "", "", "");
    }

    @Test
    @DisplayName("a row with no stored volume adds nothing to the volume total")
    void totalRow_skipsARowWithNoVolume() {
      OtherCostsDocument document =
          document(null, row("Fuel", 300, "1000"), row("Freight", 700, null));

      List<String[]> rows = section.rows(CTX, document);

      // sumBigDecimalCosts skips a null term, so Σvol is Fuel's 1000 alone while Σcost is both
      // costs: 1000 / 1000 -> 1.00.
      assertThat(rows.get(2))
          .containsExactly("", "", "", "", "Total:", "1,000.00", "1,000", "1.00");
    }

    @Test
    @DisplayName("the volume total's two decimals are decoration: each term is rounded whole first")
    void totalRow_volumeIsRoundedWholeThenPrintedWithTwoPlaces() {
      OtherCostsDocument document = document(null, row("Field crew", 1_000, "1234.40"));

      List<String[]> rows = section.rows(CTX, document);

      // sumBigDecimalCosts rounds every term to a whole number before adding, so the ".00" the
      // total row prints can never carry the fractional part the row's volume actually has.
      assertThat(rows.get(0)[5]).isEqualTo("1,234"); // the row's own volume cell
      assertThat(rows.get(0)[7]).isEqualTo("0.81"); // 1000 / 1234.40, the row's own CPU
      assertThat(rows.get(1))
          .containsExactly("", "", "", "", "Total:", "1,234.00", "1,000", "0.81");
    }

    @Test
    @DisplayName("the Total row's CPU divides the two-decimal sums, keeping the fraction")
    void totalRow_cpuDividesTheTwoDecimalSums() {
      OtherCostsDocument document = document(null, row("Field crew", 1_000, "1234.40"));

      List<String[]> rows = section.rows(CTX, document);

      // The CPU cell divides sumBig2DecimalCosts by sumBig2DecimalCosts — the unrounded 1234.40,
      // not the whole 1234 the volume cell shows — so 1000.00 / 1234.40 = 0.81.
      assertThat(rows.get(1)[7]).isEqualTo("0.81");
    }

    @Test
    @DisplayName("the CPU's divisor is the 2-dp volume sum, not the whole-rounded one shown")
    void totalRow_cpuDivisorIsTheTwoDecimalSumNotTheDisplayedWholeSum() {
      // Costs are Integer (OtherCostRow.cost), so sumCosts and sumCostsTwoDecimals agree on every
      // cost sum; the only term that can carry a fraction is a row's VOLUME. So the discrimination
      // is on volumes. Three described rows (the section drops a blank description) each at
      // 100.40 and costs 400 + 300 + 300 = 1000:
      //   sumCosts(volumes)            = 100 + 100 + 100        = 300     (each term whole-rounded)
      //   sumCostsTwoDecimals(volumes) = 100.40 + 100.40 + 100.40 = 301.20 (each term 2-dp)
      //   CPU = 1000.00 / 301.20 = 3.32005312…  -> twoDecimals -> "3.32"
      // Dividing by the whole-rounded 300 instead gives 3.3333… -> "3.33", so a swap of the two
      // helpers on either operand of the CPU fails here. The volume cell above the CPU shows the
      // whole-rounded 300 dressed as "300.00", so the file's own total and its CPU disagree, as
      // legacy's did (Schedule1OtherExtract.java:85-92).
      OtherCostsDocument document =
          document(
              null,
              row("Camp water", 400, "100.40"),
              row("Fuel", 300, "100.40"),
              row("Freight", 300, "100.40"));

      List<String[]> rows = section.rows(CTX, document);

      assertThat(rows).hasSize(4);
      assertThat(rows.get(0)[5]).isEqualTo("100"); // each row's own volume, whole
      assertThat(rows.get(3)).containsExactly("", "", "", "", "Total:", "300.00", "1,000", "3.32");
    }

    @Test
    @DisplayName("an all-null-cost set still emits a Total row, with the null marker in its sums")
    void allNullCosts_stillEmitATotalRow() {
      List<String[]> rows = section.rows(CTX, document(null, row("Unpriced", null, null)));

      // sumBigDecimalCosts returns null when nothing was added, and a null sum formats as the null
      // marker — the row count is still one per item plus the total.
      assertThat(rows).hasSize(2);
      assertThat(rows.get(1)).containsExactly("", "", "", "", "Total:", "-", "-", "-");
    }
  }

  @Nested
  @DisplayName("marker rows")
  class MarkerRows {

    @Test
    @DisplayName("a null document yields the per-record marker")
    void nullDocument_yieldsThePerRecordMarker() {
      // Schedule1OtherExtract.java:55-62.
      assertThat(section.rows(CTX, null))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("a document with no rows yields the per-record marker")
    void emptyRows_yieldThePerRecordMarker() {
      OtherCostsDocument empty =
          new OtherCostsDocument(new BigDecimal("1500"), 0L, null, 0, List.of(), false, null);

      // A shared volume with no itemized rows is still nothing to itemize.
      assertThat(section.rows(CTX, empty))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("a document whose row list is null yields the per-record marker")
    void nullRowList_yieldsThePerRecordMarker() {
      OtherCostsDocument noList =
          new OtherCostsDocument(new BigDecimal("1500"), 0L, null, 0, null, false, null);

      assertThat(section.rows(CTX, noList))
          .singleElement()
          .satisfies(
              row -> assertThat(row).containsExactly("670", "2021", "Draft", "514", NO_DATA));
    }

    @Test
    @DisplayName("the whole-schedule marker is the mills, the year range, two dashes, the marker")
    void wholeScheduleMarker_isTheFiveLegacyCells() {
      // The SectionBuilder default; this section does not override it.
      assertThat(section.noDataRow("670, 671", "2020 - 2021"))
          .containsExactly("670, 671", "2020 - 2021", "-", "-", NO_DATA);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Fixtures
  // ---------------------------------------------------------------------------------------------

  /** An itemized row carrying its own stored volume, and no owner per-unit figure. */
  private static OtherCostRow row(String description, Integer cost, String volume) {
    return new OtherCostRow(
        8_201, description, cost, volume == null ? null : new BigDecimal(volume), null, null);
  }

  /**
   * Only the rows are read by this section. The shared volume is set by some tests purely to prove
   * it is NOT read; the rest is filler.
   */
  private static OtherCostsDocument document(String sharedVolume, OtherCostRow... rows) {
    return new OtherCostsDocument(
        sharedVolume == null ? null : new BigDecimal(sharedVolume),
        null,
        null,
        rows.length,
        List.of(rows),
        false,
        null);
  }
}
