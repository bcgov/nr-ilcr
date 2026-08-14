package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Culvert;
import ca.bc.gov.nrs.ilcr.schedule7b.dto.Schedule7bResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the Schedule 7B read document to the section datasource: one detail row per culvert, with the
 * Type resolved code&rarr;description from the document's own {@code codeLists} (legacy printed the
 * description) and the BR-05 total the service computed. The {@code culvertEntryTimestamp} legacy
 * field has no backend source and is omitted.
 */
final class Schedule7bSectionMapper {

  private Schedule7bSectionMapper() {
  }

  static SectionData map(Schedule7bResponse response) {
    List<Culvert> culverts = response.culverts();
    if (culverts == null || culverts.isEmpty()) {
      return null;
    }
    Map<String, String> types = descriptions(
        response.codeLists() == null ? null : response.codeLists().culvertTypes());
    List<Map<String, ?>> rows = new ArrayList<>(culverts.size());
    for (Culvert culvert : culverts) {
      rows.add(toRow(culvert, types));
    }
    return new SectionData(rows, Map.of());
  }

  private static Map<String, Object> toRow(Culvert culvert, Map<String, String> types) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("type", describe(types, culvert.culvertTypeCode()));
    row.put("spanSize", SectionFormat.integer(culvert.spanSize()));
    row.put("riseSize", SectionFormat.integer(culvert.riseSize()));
    row.put("length", SectionFormat.decimal(culvert.length()));
    row.put("pieceCount", SectionFormat.integer(culvert.culvertPieceCount()));
    row.put("materialCost", SectionFormat.money(culvert.materialCost()));
    row.put("installCost", SectionFormat.money(culvert.installCost()));
    row.put("totalCost", SectionFormat.money(culvert.totalCost()));
    row.put("comments", SectionFormat.text(culvert.comments()));
    return row;
  }

  private static String describe(Map<String, String> lookup, String code) {
    if (code == null) {
      return "-";
    }
    return SectionFormat.text(lookup.getOrDefault(code, code));
  }

  private static Map<String, String> descriptions(List<CodeDescriptionDto> options) {
    if (options == null) {
      return Map.of();
    }
    return options.stream()
        .filter(option -> option.code() != null)
        .collect(Collectors.toMap(
            CodeDescriptionDto::code, Schedule7bSectionMapper::descriptionOf, (a, b) -> a));
  }

  private static String descriptionOf(CodeDescriptionDto option) {
    return option.description() == null ? option.code() : option.description();
  }
}
