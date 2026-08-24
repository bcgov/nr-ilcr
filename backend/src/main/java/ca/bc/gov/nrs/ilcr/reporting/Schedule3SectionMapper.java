package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule3.dto.CostLine;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.OtherAcceptableRow;
import ca.bc.gov.nrs.ilcr.schedule3.dto.Schedule3Response;
import ca.bc.gov.nrs.ilcr.schedule3.dto.ThreeColumnTotal;
import ca.bc.gov.nrs.ilcr.schedule3.dto.TimberBlock;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableDocument;
import ca.bc.gov.nrs.ilcr.schedule3.dto.UnacceptableRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the Schedule 3 (Forest Management Administration Costs) read model to the print section
 * datasource. Schedule 3 is a three-column ledger (Harvest Total $ | PO&P $ | Crown $) rendered as
 * ONE section row carrying THREE nested collections the jrxml list components iterate — the eleven
 * fixed admin lines, the Other Acceptable itemization, and the Other Unacceptable itemization —
 * plus the fixed total rows (Subtotal Other Costs, Included Unacceptable Costs, Total Cost) and the
 * timber block (PO&P Timber, Crown Timber, Total Overhead). Every value is reused from the
 * service's derived figures, format-only.
 *
 * <p>Deliberate deviations, grounded in the legacy report (Story 20.7 CONFIRMED PRINT SPEC):
 *
 * <ul>
 *   <li>Only THREE total lines print — the DTO's {@code subtotalActualCosts} block has no legacy
 *       print counterpart and is intentionally NOT rendered.
 *   <li>Included Unacceptable Costs shows Harvest + Crown only (no PO&P cell; the two are the same
 *       figure by design).
 *   <li>The Other Unacceptable table prepends the read-only Annual Rents (Forest Act, S111) row
 *       (Total & Crown = the item-29 harvest; PO&P blank); its item-38 rows carry PO&P blank and
 *       Crown = a copy of the Total.
 *   <li>The two harvest-only admin lines (Annual Rents 29, Silviculture Admin 37) render blank PO&P
 *       / Crown cells (the columns do not apply to those rows).
 *   <li>Null money renders {@code "-"} (the shared {@code SectionFormat} convention across the
 *       rebuilt print sections) rather than the legacy blank — a deliberate consistency choice for
 *       the combined PDF.
 * </ul>
 */
final class Schedule3SectionMapper {

  private static final String BLANK = "";

  /** The two harvest-only lines: legacy shows no PO&P / Crown cell for these. */
  private static final Set<Integer> HARVEST_ONLY = Set.of(29, 37);

  private record AdminLine(int code, String label) {}

  /** The eleven fixed admin lines in LEGACY DISPLAY order (35 Residue before 34 Cruising). */
  private static final List<AdminLine> ADMIN =
      List.of(
          new AdminLine(27, "Licenses, Fees, Insurance: "),
          new AdminLine(28, "Taxes, Leases, Rentals: "),
          new AdminLine(29, "Annual Rents: "),
          new AdminLine(30, "Wages/Salaries incl Benefits: "),
          new AdminLine(31, "Vehicle Expense: "),
          new AdminLine(32, "Office Expense: "),
          new AdminLine(33, "Scaling Expense: "),
          new AdminLine(35, "Residue & Waste Expense: "),
          new AdminLine(34, "Cruising & Layout Expense: "),
          new AdminLine(36, "Depreciation Expense: "),
          new AdminLine(37, "Silviculture Admin Costs: "));

  private Schedule3SectionMapper() {}

  static SectionData map(
      Schedule3Response response,
      OtherAcceptableDocument acceptable,
      UnacceptableDocument unacceptable) {
    if (response == null) {
      return null;
    }
    Map<String, Object> row = new LinkedHashMap<>();

    row.put("adminLines", adminLines(response.lineItems()));

    // Subtotal Other Costs (three-column).
    putTotal(row, "subtotalOtherCosts", response.subtotalOtherCosts());

    row.put("acceptableRows", acceptableRows(acceptable));

    // Included Unacceptable Costs — Harvest + Crown only (no PO&P cell, by design).
    ThreeColumnTotal unaccTotal = response.includedUnacceptableCosts();
    row.put(
        "includedUnacceptableHarvest",
        SectionFormat.money(unaccTotal == null ? null : unaccTotal.harvest()));
    row.put(
        "includedUnacceptableCrown",
        SectionFormat.money(unaccTotal == null ? null : unaccTotal.crown()));

    row.put("unacceptableRows", unacceptableRows(unacceptable));

    // Total Cost (three-column).
    putTotal(row, "totalCost", response.totalCosts());

    // Timber block: volume | cost | $-per-m³ (perUnit → the *CostVol cell).
    putTimber(row, "popTimber", response.popTimber());
    putTimber(row, "crownTimber", response.crownTimber());
    putTimber(row, "totalOverhead", response.totalOverhead());

    row.put("comments", SectionFormat.text(response.comments()));

    return new SectionData(List.of(row), Map.of());
  }

  /** The eleven fixed admin lines in legacy display order; harvest-only lines blank PO&P/Crown. */
  private static List<Map<String, ?>> adminLines(List<CostLine> lineItems) {
    Map<Integer, CostLine> byCode = new HashMap<>();
    if (lineItems != null) {
      for (CostLine li : lineItems) {
        if (li != null && li.costItemCode() != null) {
          byCode.put(li.costItemCode(), li);
        }
      }
    }
    List<Map<String, ?>> rows = new ArrayList<>();
    for (AdminLine spec : ADMIN) {
      CostLine li = byCode.get(spec.code());
      boolean harvestOnly = HARVEST_ONLY.contains(spec.code());
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("adminLabel", spec.label());
      r.put("adminHarvest", SectionFormat.money(li == null ? null : li.harvest()));
      r.put("adminPop", harvestOnly ? BLANK : SectionFormat.money(li == null ? null : li.pop()));
      r.put(
          "adminCrown", harvestOnly ? BLANK : SectionFormat.money(li == null ? null : li.crown()));
      rows.add(r);
    }
    return rows;
  }

  /** Other Acceptable itemization: description + Harvest total + PO&P + Crown (all real). */
  private static List<Map<String, ?>> acceptableRows(OtherAcceptableDocument doc) {
    List<Map<String, ?>> rows = new ArrayList<>();
    if (doc == null || doc.rows() == null) {
      return rows;
    }
    for (OtherAcceptableRow ar : doc.rows()) {
      Map<String, Object> r = new LinkedHashMap<>();
      r.put("acceptableDesc", SectionFormat.text(ar.description()));
      r.put("acceptableTotal", SectionFormat.money(ar.total()));
      r.put("acceptablePop", SectionFormat.money(ar.pop()));
      r.put("acceptableCrown", SectionFormat.money(ar.crown()));
      rows.add(r);
    }
    return rows;
  }

  /**
   * Other Unacceptable itemization: the read-only Annual Rents (S111) row prepended (Total & Crown
   * = the item-29 harvest; PO&P blank), then the item-38 rows (PO&P blank; Crown = a copy of the
   * Total).
   */
  private static List<Map<String, ?>> unacceptableRows(UnacceptableDocument doc) {
    List<Map<String, ?>> rows = new ArrayList<>();
    if (doc == null) {
      return rows;
    }
    Map<String, Object> annualRents = new LinkedHashMap<>();
    String annualRentsTotal = SectionFormat.money(doc.annualRentsTotal());
    annualRents.put("unacceptableDesc", "Annual Rents (Forest Act, S111)");
    annualRents.put("unacceptableTotal", annualRentsTotal);
    annualRents.put("unacceptablePop", BLANK);
    annualRents.put("unacceptableCrown", annualRentsTotal);
    rows.add(annualRents);
    if (doc.rows() != null) {
      for (UnacceptableRow ur : doc.rows()) {
        String total = SectionFormat.money(ur.total());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unacceptableDesc", SectionFormat.text(ur.description()));
        r.put("unacceptableTotal", total);
        r.put("unacceptablePop", BLANK);
        r.put("unacceptableCrown", total);
        rows.add(r);
      }
    }
    return rows;
  }

  /** A three-column total row: {@code <prefix>Harvest/Pop/Crown}. */
  private static void putTotal(Map<String, Object> row, String prefix, ThreeColumnTotal total) {
    row.put(prefix + "Harvest", SectionFormat.money(total == null ? null : total.harvest()));
    row.put(prefix + "Pop", SectionFormat.money(total == null ? null : total.pop()));
    row.put(prefix + "Crown", SectionFormat.money(total == null ? null : total.crown()));
  }

  /** A timber row: volume | cost | $-per-m³ ({@code <prefix>Vol/Cost/CostVol}). */
  private static void putTimber(Map<String, Object> row, String prefix, TimberBlock block) {
    row.put(prefix + "Vol", SectionFormat.decimal(block == null ? null : block.volume()));
    row.put(prefix + "Cost", SectionFormat.money(block == null ? null : block.cost()));
    row.put(prefix + "CostVol", SectionFormat.decimal(block == null ? null : block.perUnit()));
  }
}
