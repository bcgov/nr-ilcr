package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule11.dto.Schedule11Response;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureLocation;
import ca.bc.gov.nrs.ilcr.schedule11.dto.SilvicultureTotals;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 11 read document to the section datasource: one detail row per location plus the
 * BR-08 footer totals as section parameters. The service has already computed the {@code becLabel}
 * concat, the per-row total/$-per-NAR and the footer totals with the exact legacy null rules — this
 * mapper only formats.
 */
final class Schedule11SectionMapper {

  private Schedule11SectionMapper() {
  }

  static SectionData map(Schedule11Response response) {
    List<SilvicultureLocation> locations = response.locations();
    if (locations == null || locations.isEmpty()) {
      return null;
    }
    List<Map<String, ?>> rows = locations.stream().map(Schedule11SectionMapper::toRow).toList();

    SilvicultureTotals totals = response.totals();
    Map<String, Object> params = new HashMap<>();
    params.put("totalNetArea", SectionFormat.decimal(totals == null ? null : totals.netArea()));
    params.put("totalActualCost", SectionFormat.money(totals == null ? null : totals.actualCost()));
    params.put("totalPlannedCost", SectionFormat.money(totals == null ? null : totals.plannedCost()));
    params.put("totalCost", SectionFormat.money(totals == null ? null : totals.totalCost()));
    params.put("totalCostPerNetArea",
        SectionFormat.decimal(totals == null ? null : totals.costPerNetArea()));
    return new SectionData(rows, params);
  }

  private static Map<String, ?> toRow(SilvicultureLocation location) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("location", SectionFormat.text(location.location()));
    row.put("enhanced", location.enhancedIndicator() ? "Y" : "N");
    row.put("becLabel", SectionFormat.text(location.becLabel()));
    row.put("netArea", SectionFormat.decimal(location.netArea()));
    row.put("actualCost", SectionFormat.money(location.actualCost()));
    row.put("plannedCost", SectionFormat.money(location.plannedCost()));
    row.put("totalCost", SectionFormat.money(location.totalCost()));
    row.put("costPerNetArea", SectionFormat.decimal(location.costPerNetArea()));
    row.put("comments", SectionFormat.text(location.comments()));
    return row;
  }
}
