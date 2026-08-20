package ca.bc.gov.nrs.ilcr.reporting;

import ca.bc.gov.nrs.ilcr.schedule5.dto.Camp;
import ca.bc.gov.nrs.ilcr.schedule5.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule5.dto.Schedule5Response;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the Schedule 5 read document to the section datasource: ONE row per camp (the legacy S17
 * fan-out — one section per camp), each carrying the camp descriptors, the twelve category costs and
 * the four derived totals PRE-FORMATTED (the service already computed every total, $/m&sup3; and
 * sub-page aggregate with the exact legacy null rules). Costs render as whole dollars; a null total
 * stays {@code "-"} rather than collapsing to {@code 0}.
 */
final class Schedule5SectionMapper {

  private Schedule5SectionMapper() {
  }

  static SectionData map(Schedule5Response response) {
    List<Camp> camps = response.camps();
    if (camps == null || camps.isEmpty()) {
      return null;
    }
    List<Map<String, ?>> rows = camps.stream().map(Schedule5SectionMapper::toRow).toList();
    return new SectionData(rows, Map.of());
  }

  private static Map<String, ?> toRow(Camp camp) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("campName", SectionFormat.text(camp.campName()));
    row.put("roadDistance", SectionFormat.decimal(camp.roadDistanceToOperatingArea()));
    row.put("sizeOfCamp", SectionFormat.integer(camp.sizeOfCamp()));
    row.put("campVolume", SectionFormat.decimal(camp.associatedCampVolume()));
    row.put("isolatedCamp", isolated(camp.isolatedCamp()));
    row.put("cateringAndFood", cost(camp.cateringAndFood()));
    row.put("wagesAndBenefits", cost(camp.wagesAndBenefits()));
    row.put("depreciationLease", cost(camp.depreciationLease()));
    row.put("generalCampExpenses", cost(camp.generalCampExpenses()));
    row.put("otherCampExpenses", cost(camp.otherCampExpenses()));
    row.put("campSubTotal", cost(camp.campSubTotal()));
    row.put("recoveries", cost(camp.recoveries()));
    row.put("campTotal", cost(camp.campTotal()));
    row.put("crewTransportation", cost(camp.crewTransportation()));
    row.put("equipLand", cost(camp.equipAndSuppliesLand()));
    row.put("equipRail", cost(camp.equipAndSuppliesRail()));
    row.put("equipAir", cost(camp.equipAndSuppliesAir()));
    row.put("equipWater", cost(camp.equipAndSuppliesWater()));
    row.put("otherAccessExpenses", cost(camp.otherAccessExpenses()));
    row.put("accessExpenseTotal", cost(camp.accessExpenseTotal()));
    row.put("campAndAccessTotal", cost(camp.campAndAccessTotal()));
    row.put("comments", SectionFormat.text(camp.comments()));
    return row;
  }

  private static String cost(CategoryAmount amount) {
    return amount == null ? SectionFormat.money((Long) null) : SectionFormat.money(amount.cost());
  }

  private static String isolated(Boolean value) {
    if (value == null) {
      return "-";
    }
    return Boolean.TRUE.equals(value) ? "Yes" : "No";
  }
}
