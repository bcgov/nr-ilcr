package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule6.dto.RoadRecord;
import ca.bc.gov.nrs.ilcr.schedule6.dto.Schedule6Response;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 6 read document to the section datasource: one detail row per road record, plus
 * the footer totals and the single schedule-level general comment as section parameters. The service
 * has already excluded the general-comment placeholder rows (they supplied {@code generalComments}),
 * derived {@code rmg} via the road-group lookup, and computed the BR-07 running totals — this mapper
 * only formats. The {@code roadReportEntryTimestamp} legacy field has no backend source and is
 * omitted.
 */
final class Schedule6SectionMapper {

  private Schedule6SectionMapper() {
  }

  static SectionData map(Schedule6Response response) {
    List<RoadRecord> records = response.roadRecords();
    if (records == null || records.isEmpty()) {
      return null;
    }
    List<Map<String, ?>> rows = records.stream().map(Schedule6SectionMapper::toRow).toList();

    Map<String, Object> params = new HashMap<>();
    params.put("generalComments", SectionFormat.text(response.generalComments()));
    params.put("totalVolume", SectionFormat.decimal(response.totalVolume()));
    params.put("totalCost", SectionFormat.money(response.totalCost()));
    params.put("totalCostPerVolume", SectionFormat.decimal(response.totalCostPerVolume()));
    return new SectionData(rows, params);
  }

  private static Map<String, ?> toRow(RoadRecord record) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("areaLabel", areaLabel(record));
    row.put("supplyBlock", SectionFormat.text(record.supplyBlock()));
    row.put("rmg", SectionFormat.text(record.rmg()));
    row.put("volume", SectionFormat.decimal(record.volume()));
    row.put("cost", SectionFormat.money(record.cost()));
    row.put("costPerVolume", SectionFormat.decimal(record.costPerVolume()));
    row.put("comments", SectionFormat.text(record.comments()));
    return row;
  }

  /** The TSA/TFL label: the TFL number for a TFL record, else the TSA area type (BR-02). */
  private static String areaLabel(RoadRecord record) {
    if ("TFL".equalsIgnoreCase(record.areaType())) {
      return SectionFormat.text(record.tflNumber());
    }
    return SectionFormat.text(record.areaType());
  }
}
