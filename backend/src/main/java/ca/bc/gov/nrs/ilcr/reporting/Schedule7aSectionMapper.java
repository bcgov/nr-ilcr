package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.dto.base.CodeDescriptionDto;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Bridge;
import ca.bc.gov.nrs.ilcr.schedule7a.dto.Schedule7aResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the Schedule 7A read document to the section datasource: one detail card per bridge, with
 * the New/Used construction type resolved code&rarr;description from the document's own {@code
 * codeLists} (legacy printed the description, not the raw code) and the BR-06 totals the service
 * computed. The material/deliver/install lines use the pre-summed {@code total*} fields.
 */
final class Schedule7aSectionMapper {

  private Schedule7aSectionMapper() {}

  static SectionData map(Schedule7aResponse response) {
    List<Bridge> bridges = response.bridges();
    if (bridges == null || bridges.isEmpty()) {
      return null;
    }
    Map<String, String> constructionTypes =
        descriptions(
            response.codeLists() == null ? null : response.codeLists().constructionTypes());
    List<Map<String, ?>> rows = new ArrayList<>(bridges.size());
    for (Bridge bridge : bridges) {
      rows.add(toRow(bridge, constructionTypes));
    }
    return new SectionData(rows, Map.of());
  }

  private static Map<String, Object> toRow(Bridge bridge, Map<String, String> constructionTypes) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("locationName", SectionFormat.text(bridge.locationName()));
    row.put("builtDate", SectionFormat.text(bridge.builtDate()));
    row.put("newUsed", describe(constructionTypes, bridge.constructionTypeCode()));
    row.put("sitePlanCost", SectionFormat.money(bridge.sitePlanCost()));
    row.put("totalMaterial", SectionFormat.money(bridge.totalMaterial()));
    row.put("totalDeliver", SectionFormat.money(bridge.totalDeliver()));
    row.put("totalInstall", SectionFormat.money(bridge.totalInstall()));
    row.put("grandTotal", SectionFormat.money(bridge.grandTotal()));
    row.put("comments", SectionFormat.text(bridge.comments()));
    return row;
  }

  /**
   * Resolve a code to its description; an unmapped/blank code falls back to the code (then dash).
   */
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
        .collect(
            Collectors.toMap(
                CodeDescriptionDto::code, Schedule7aSectionMapper::descriptionOf, (a, b) -> a));
  }

  private static String descriptionOf(CodeDescriptionDto option) {
    return option.description() == null ? option.code() : option.description();
  }
}
