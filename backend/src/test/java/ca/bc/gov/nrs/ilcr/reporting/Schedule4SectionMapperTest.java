package ca.bc.gov.nrs.ilcr.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.schedule4.dto.CategoryAmount;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Location;
import ca.bc.gov.nrs.ilcr.schedule4.dto.Schedule4Response;
import ca.bc.gov.nrs.ilcr.schedule4.dto.SubPageRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Schedule4SectionMapper — transportation print section")
class Schedule4SectionMapperTest {

  @Test
  @DisplayName("null and empty locations are skipped")
  void emptyResponse_isNull() {
    assertThat(Schedule4SectionMapper.map(null)).isNull();
    assertThat(Schedule4SectionMapper.map(response(List.of()))).isNull();
  }

  @Test
  @DisplayName("maps fixed/distance categories and all three sub-page lists")
  void populatedLocation_mapsPrintRows() {
    Location location =
        new Location(
            8001,
            0,
            "Cedar Flats",
            "Haul notes",
            List.of(
                new CategoryAmount(
                    40, "FIXED", new BigDecimal("123.4"), 500, null, new BigDecimal("4.05")),
                new CategoryAmount(
                    47,
                    "DISTANCE",
                    new BigDecimal("200"),
                    1000,
                    new BigDecimal("12"),
                    new BigDecimal("5.00"))),
            List.of(
                new SubPageRow(
                    8101,
                    43,
                    "Tow One",
                    new BigDecimal("2"),
                    new BigDecimal("10"),
                    100,
                    null,
                    new BigDecimal("10.00")),
                new SubPageRow(
                    8102,
                    46,
                    "Rehaul One",
                    new BigDecimal("3"),
                    new BigDecimal("20"),
                    200,
                    1,
                    new BigDecimal("10.00")),
                new SubPageRow(
                    8103,
                    55,
                    "Other One",
                    new BigDecimal("4"),
                    new BigDecimal("30"),
                    300,
                    null,
                    new BigDecimal("10.00"))));

    SectionData section = Schedule4SectionMapper.map(response(List.of(location)));

    assertThat(section).isNotNull();
    assertThat(section.rows()).hasSize(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> row = (Map<String, Object>) section.rows().get(0);
    assertThat(row)
        .containsEntry("locationName", "Cedar Flats")
        .containsEntry("lakeSideDryDumpVol", "123")
        .containsEntry("lakeSideDryDumpCost", "500")
        .containsEntry("lakeSideDryDumpCostVol", "4.05")
        .containsEntry("truckBargeFerryDist", "12")
        .containsEntry("truckBargeFerryVol", "200")
        .containsEntry("truckBargeFerryCost", "1,000")
        .containsEntry("truckBargeFerryCostVol", "5.00")
        .containsEntry("waterDumpVol", "-")
        .containsEntry("comments", "Haul notes")
        .containsEntry("towingTotalTotalDist", "2")
        .containsEntry("towingTotalTotalVol", "10")
        .containsEntry("towingTotalTotalCost", "100")
        .containsEntry("towingTotalTotalCostVol", "10.00");

    assertThat((List<?>) row.get("towingRows"))
        .singleElement()
        .satisfies(
            value -> {
              assertThat((Map<String, Object>) value)
                  .containsEntry("towingTotalDescription", "Tow One")
                  .containsEntry("towingTotalDist", "2")
                  .containsEntry("towingTotalVolume", "10")
                  .containsEntry("towingTotalCost", "100")
                  .containsEntry("towingTotalCostVolume", "10.00");
            });
    assertThat((List<?>) row.get("truckRehaulRows")).hasSize(1);
    assertThat((List<?>) row.get("otherTransportationRows")).hasSize(1);
  }

  private static Schedule4Response response(List<Location> locations) {
    return new Schedule4Response(514, 2021, "D", true, locations, null);
  }
}
