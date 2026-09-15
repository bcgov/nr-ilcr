package ca.bc.gov.nrs.ilcr.originalvalue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ca.bc.gov.nrs.ilcr.schedule10.Schedule10Repository;
import ca.bc.gov.nrs.ilcr.schedule11.Schedule11Repository;
import ca.bc.gov.nrs.ilcr.schedule5.Schedule5Repository;
import ca.bc.gov.nrs.ilcr.schedule6.Schedule6Repository;
import ca.bc.gov.nrs.ilcr.schedule7a.Schedule7aRepository;
import ca.bc.gov.nrs.ilcr.schedule7b.Schedule7bRepository;
import ca.bc.gov.nrs.ilcr.schedule8.Schedule8Repository;
import ca.bc.gov.nrs.ilcr.schedule9.Schedule9Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the eight {@code *_S_VW} snapshot {@link org.springframework.jdbc.core.RowMapper}s
 * added by Story 16.2 — no DB, no Docker (Story 16.2 §Dev Notes: the analysis pipeline runs the
 * surefire phase only, so a mapper covered exclusively by an Oracle {@code *IT} reads as zero).
 *
 * <h2>Why these are worth testing rather than just covering</h2>
 *
 * <p>Every one of these mappers reads at least one Oracle {@code NUMBER} that is nullable in the
 * view, and JDBC's only way to tell 0 from NULL is to call {@link ResultSet#wasNull()} <em>before
 * the next column is read</em>. That is a real trap and it bit this story once: the Schedule 5
 * mapper originally asked {@code wasNull()} after a later {@code getBigDecimal}, so a camp with no
 * size would have reported the DISTANCE column's nullity instead. A mapper that mis-orders that
 * call still compiles, still returns a row, and silently turns "the licensee submitted nothing"
 * into "the licensee submitted zero" — which is the difference between showing no indicator and
 * showing one against a fabricated original.
 *
 * <p>So the fake below is a real fake and not a stub sequence. It implements JDBC's contract —
 * {@code wasNull()} reports on the column last read — which means a mapper that asks in the wrong
 * order fails here. A {@code when(rs.wasNull()).thenReturn(true, false, …)} chain would instead
 * pass whatever order the mapper used, testing nothing.
 */
@DisplayName("*_S_VW snapshot row mappers")
class SnapshotRowMapperTest {

  @Test
  @DisplayName(
      "Schedule 5 — camp: a null CAMP_SIZE_CAPACITY stays null, and does not borrow the distance's nullity")
  void campSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "CAMP_REPORT_ID", 7001,
            "CAMP_NAME", "North Camp",
            "DISTANCE_TO_OPERATING_AREA", "12.5",
            "ASSOCIATED_CAMP_VOLUME", "60000",
            "ISOLATED_CAMP_IND", "Y",
            "COMMENTS", "as the mill reported it");

    Schedule5Repository.CampSnapshotRow row =
        new Schedule5Repository.CampSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.campId()).isEqualTo(7001);
    assertThat(row.campName()).isEqualTo("North Camp");
    assertThat(row.distanceToOperatingArea()).isEqualByComparingTo("12.5");
    // The load-bearing assertion: CAMP_SIZE_CAPACITY is absent (SQL NULL) while the very next
    // column read is a non-null distance. Only a mapper that captured wasNull() immediately gets
    // this right.
    assertThat(row.sizeOfCamp()).isNull();
    assertThat(row.associatedCampVolume()).isEqualByComparingTo("60000");
    assertThat(row.isolatedCampInd()).isEqualTo("Y");
    assertThat(row.comments()).isEqualTo("as the mill reported it");
  }

  @Test
  @DisplayName("Schedule 6 — road record: the three classification codes and the general comment")
  void roadRecordSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "ROAD_MAINTENANCE_REPORT_ID", 6001,
            "TSA_NUMBER", "07",
            "TSB_NUMBER_CODE", "A",
            "TFL_NUMBER_CODE", "TFL",
            "COMMENTS", "general note");

    Schedule6Repository.RoadRecordSnapshotRow row =
        new Schedule6Repository.RoadRecordSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.recordId()).isEqualTo(6001);
    assertThat(row.tsaNumber()).isEqualTo("07");
    assertThat(row.tsbNumberCode()).isEqualTo("A");
    assertThat(row.tflNumberCode()).isEqualTo("TFL");
    assertThat(row.generalComment()).isEqualTo("general note");
  }

  @Test
  @DisplayName(
      "Schedule 7A — bridge: BUILT_DATE becomes a LocalDate, and both nullable NUMBERs stay null")
  void bridgeSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "BRIDGE_REPORT_ID", 7101,
            "LOCATION_NAME", "Km 14 crossing",
            "BUILT_DATE", "2016-01-26",
            "ILCR_BRIDGE_CNSTRCTN_TYPE_CODE", "RN",
            "ILCR_BRIDGE_SUPERSTRUCTR_CODE", "S",
            "ILCR_DECK_CODE", "D",
            "ILCR_BRIDGE_ABUTMENT_TYPE_CODE", "A",
            "ILCR_BRIDGE_LOAD_RATING_CODE", "L1",
            "HEIGHT", "3.5",
            "COMMENTS", "replaced");

    Schedule7aRepository.BridgeSnapshotRow row =
        new Schedule7aRepository.BridgeSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.bridgeReportId()).isEqualTo(7101);
    assertThat(row.builtDate()).isEqualTo(LocalDate.of(2016, 1, 26));
    assertThat(row.loadRatingCode()).isEqualTo("L1");
    assertThat(row.abutmentHeight()).isEqualByComparingTo("3.5");
    // EXPECTED_BRIDGE_LIFE_SPAN and DISTANCE_FROM_STORAGE are absent, and each is captured before
    // the next read.
    assertThat(row.lifeSpan()).isNull();
    assertThat(row.distance()).isNull();
    assertThat(row.length()).isNull();
    assertThat(row.comments()).isEqualTo("replaced");
  }

  @Test
  @DisplayName("Schedule 7A — bridge: a null BUILT_DATE does not become epoch")
  void bridgeSnapshotNullDate() throws Exception {
    ResultSet rs = snapshot("BRIDGE_REPORT_ID", 7102);

    Schedule7aRepository.BridgeSnapshotRow row =
        new Schedule7aRepository.BridgeSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.builtDate()).isNull();
    assertThat(row.locationName()).isNull();
  }

  @Test
  @DisplayName("Schedule 7B — culvert: all three nullable counts stay null independently")
  void culvertSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "CULVERT_REPORT_ID", 7801,
            "ILCR_CULVERT_TYPE_CODE", "C",
            "SPAN_SIZE", 900,
            "LENGTH", "12.5",
            "COMMENTS", "steel");

    Schedule7bRepository.CulvertSnapshotRow row =
        new Schedule7bRepository.CulvertSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.culvertReportId()).isEqualTo(7801);
    assertThat(row.spanSize()).isEqualTo(900);
    // RISE_SIZE and CULVERT_PIECE_COUNT are absent while SPAN_SIZE is present — three
    // getInt/wasNull
    // pairs that must not read each other's nullity.
    assertThat(row.riseSize()).isNull();
    assertThat(row.culvertPieceCount()).isNull();
    assertThat(row.length()).isEqualByComparingTo("12.5");
    assertThat(row.comments()).isEqualTo("steel");
  }

  @Test
  @DisplayName("Schedule 8 — page: the twelve submitted page attributes")
  void pageSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "TREE_TO_TRUCK_REPORT_ID", 8001,
            "DIVISION_LOCATION", "Div 1",
            "HARVEST_LICENSE_NUMBER", "A1234",
            "CONTACT_NAME", "the mill's contact",
            "CONTACT_PHONE_NUMBER", "222-222-2222",
            "CUTTING_PERMIT_NUMBER", "CP1",
            "ILCR_SUPPORT_CENTRE_CODE", "SC",
            "ILCR_FOREST_REGION_CODE", "R",
            "BEC_ZONE_CODE", "BEC",
            "TSA_NUMBER", "07",
            "COMMENTS", "page note");

    Schedule8Repository.PageSnapshotRow row =
        new Schedule8Repository.PageSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.id()).isEqualTo(8001);
    assertThat(row.division()).isEqualTo("Div 1");
    assertThat(row.license()).isEqualTo("A1234");
    assertThat(row.contact()).isEqualTo("the mill's contact");
    assertThat(row.phone()).isEqualTo("222-222-2222");
    assertThat(row.cuttingPermit()).isEqualTo("CP1");
    assertThat(row.supportCentre()).isEqualTo("SC");
    assertThat(row.region()).isEqualTo("R");
    assertThat(row.becZone()).isEqualTo("BEC");
    assertThat(row.tsaNumber()).isEqualTo("07");
    assertThat(row.tflNumber()).isNull();
    assertThat(row.supplyBlock()).isNull();
    assertThat(row.comments()).isEqualTo("page note");
  }

  @Test
  @DisplayName("Schedule 8 — sample: uphill and water-dump come from their OWN columns (D3)")
  void sampleSnapshot() throws Exception {
    // The regression this pins: legacy read the uphill original from WATER_DUMP_DESTINATION_IND
    // (Schedule8DAO.java:616-617). Giving the two columns OPPOSITE values is what makes a mapper
    // that reads the wrong one fail here rather than pass by coincidence.
    ResultSet rs =
        snapshot(
            "TREE_TO_TRUCK_DETAIL_REPORT_ID", 8101,
            "CONTRACTOR_ID", "C1",
            "CUT_BLOCK", "B1",
            "GROUND_BASE_PCT", 60,
            "UPHILL_DIRECTION_IND", "Y",
            "WATER_DUMP_DESTINATION_IND", "N",
            "ILCR_SKID_TYPE_CODE", "S",
            "CYCLE_TIME", "1.5",
            "ORIGINAL_TREE_TO_TRUCK_RATE", "12.34");

    Schedule8Repository.SampleSnapshotRow row =
        new Schedule8Repository.SampleSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.id()).isEqualTo(8101);
    assertThat(row.uphillDirectionInd()).isEqualTo("Y");
    assertThat(row.waterDumpDestinationInd()).isEqualTo("N");
    assertThat(row.groundBasePct()).isEqualTo(60);
    // Every other nullable percentage is absent and must stay null rather than read as 0 — which
    // would flag an indicator against a submitted "0%" the licensee never entered.
    assertThat(row.grapplePct()).isNull();
    assertThat(row.skylinePct()).isNull();
    assertThat(row.highleadPct()).isNull();
    assertThat(row.helicopterPct()).isNull();
    assertThat(row.otherSkiddingPct()).isNull();
    assertThat(row.skylineSlopeDistance()).isNull();
    assertThat(row.skylineSupportNumber()).isNull();
    assertThat(row.coniferousVolume()).isNull();
    assertThat(row.deciduousVolume()).isNull();
    assertThat(row.cycleTime()).isEqualByComparingTo("1.5");
    assertThat(row.originalRate()).isEqualByComparingTo("12.34");
  }

  @Test
  @DisplayName("Schedule 8 — rate row: a null cost item stays null")
  void rateSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "TREE_TO_TRUCK_RATE_DETAIL_ID", 8201,
            "ITEM_DESCRIPTION", "Addition",
            "COSTING_RATE", "3.50",
            "ILCR_RATE_COST_TYPE_CODE", "A");

    Schedule8Repository.RateSnapshotRow row =
        new Schedule8Repository.RateSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.id()).isEqualTo(8201);
    assertThat(row.costItemCode()).isNull();
    assertThat(row.itemDescription()).isEqualTo("Addition");
    assertThat(row.costingRate()).isEqualByComparingTo("3.50");
    assertThat(row.costTypeCode()).isEqualTo("A");
  }

  @Test
  @DisplayName("Schedule 9 — contractual work: SIDE_SLOPE_PCT is read from its own column (D4)")
  void contractualSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "CONTRACTUAL_WORK_REPORT_ID", 9001,
            "CONTRACTOR_ID", "C9",
            "PERFORMED_UNIT", "40.5",
            "SIDE_SLOPE_PCT", 35,
            "ILCR_UNIT_CODE", "U",
            "UNIT_DESCRIPTION", "unit desc",
            "ILCR_CONTRACTUAL_SOURCE_CODE", "S",
            "SOURCE_DESCRIPTION", "source desc",
            "BEC_ZONE_CODE", "BEC",
            "COMMENTS", "note");

    Schedule9Repository.ContractualSnapshotRow row =
        new Schedule9Repository.ContractualSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.reportId()).isEqualTo(9001);
    assertThat(row.contractorId()).isEqualTo("C9");
    assertThat(row.performedUnit()).isEqualByComparingTo("40.5");
    // Legacy wrote this into one field and read another that was never assigned, so its indicator
    // fired on every non-empty side slope. Reading the column is the fix.
    assertThat(row.sideSlopePct()).isEqualTo(35);
    assertThat(row.unitCode()).isEqualTo("U");
    assertThat(row.unitDescription()).isEqualTo("unit desc");
    assertThat(row.sourceCode()).isEqualTo("S");
    assertThat(row.sourceDescription()).isEqualTo("source desc");
    assertThat(row.becZoneCode()).isEqualTo("BEC");
    assertThat(row.comments()).isEqualTo("note");
  }

  @Test
  @DisplayName("Schedule 9 — a null SIDE_SLOPE_PCT stays null rather than reading as 0")
  void contractualSnapshotNullSlope() throws Exception {
    ResultSet rs = snapshot("CONTRACTUAL_WORK_REPORT_ID", 9002, "CONTRACTOR_ID", "C9");

    Schedule9Repository.ContractualSnapshotRow row =
        new Schedule9Repository.ContractualSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.sideSlopePct()).isNull();
    assertThat(row.performedUnit()).isNull();
  }

  @Test
  @DisplayName("Schedule 10 — construction page: the six submitted page attributes")
  void constructionPageSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "ROAD_CONSTRUCTION_REPRT_ID", 10001,
            "CONSTRUCTION_DIVISION_NAME", "Div 10",
            "CONSTRUCTION_PERIOD", "2021-06",
            "ILCR_FOREST_REGION_CODE", "R",
            "TSA_NUMBER", "07",
            "TSB_NUMBER_CODE", "A",
            "TFL_NUMBER_CODE", "TFL");

    Schedule10Repository.PageSnapshotRow row =
        new Schedule10Repository.PageSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.pageId()).isEqualTo(10001);
    assertThat(row.divisionName()).isEqualTo("Div 10");
    assertThat(row.constructionPeriod()).isEqualTo("2021-06");
    assertThat(row.forestRegionCode()).isEqualTo("R");
    assertThat(row.tsaNumber()).isEqualTo("07");
    assertThat(row.tsbNumberCode()).isEqualTo("A");
    assertThat(row.tflNumberCode()).isEqualTo("TFL");
  }

  @Test
  @DisplayName("Schedule 10 — road detail: six nullable percentages, each with its own nullity")
  void roadDetailSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "ROAD_CONSTRUCTION_REPRT_DTL_ID", 10101,
            "ROAD_NAME", "Mainline",
            "ILCR_ROAD_LIFETIME_CODE", "P",
            "BECBIOGEO_CATALOGUE_ID", 55,
            "REL_SOIL_MOIST_RGM_CLS_CODE", "M",
            "SIDE_SLOPE_PCT", 20,
            "SOLID_ROCK_PCT", 10,
            "SUB_GRADE_LENGTH", "1.250",
            "DETAIL_ENGINEERING_COST_IND", "Y",
            "COMMENTS", "detail note");

    Schedule10Repository.DetailSnapshotRow row =
        new Schedule10Repository.DetailSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.detailId()).isEqualTo(10101);
    assertThat(row.roadName()).isEqualTo("Mainline");
    assertThat(row.roadLifetimeCode()).isEqualTo("P");
    assertThat(row.becCatalogueId()).isEqualTo(55);
    assertThat(row.sideSlopePct()).isEqualTo(20);
    assertThat(row.solidRockPct()).isEqualTo(10);
    // The four material percentages that follow SOLID_ROCK_PCT are absent: six getInt/wasNull pairs
    // in a row, and the mapper must not let a populated one mask an empty one.
    assertThat(row.rippableRockPct()).isNull();
    assertThat(row.coarseMaterialPct()).isNull();
    assertThat(row.fineMaterialPct()).isNull();
    assertThat(row.organicMaterialPct()).isNull();
    assertThat(row.subGradeLength()).isEqualByComparingTo("1.250");
    assertThat(row.detailEngineeringCostInd()).isEqualTo("Y");
    assertThat(row.comments()).isEqualTo("detail note");
  }

  @Test
  @DisplayName("Schedule 11 — silviculture location: the four columns the view actually carries")
  void silvicultureSnapshot() throws Exception {
    ResultSet rs =
        snapshot(
            "BASIC_SILVICULTURE_REPORT_ID",
            11001,
            "LOCATION",
            "Block 4",
            "BECBIOGEOCLIMATIC_CATALOGUE_ID",
            77,
            "REFORESTED_NET_AREA",
            "12.5");

    Schedule11Repository.LocationSnapshotRow row =
        new Schedule11Repository.LocationSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.locationId()).isEqualTo(11001);
    assertThat(row.location()).isEqualTo("Block 4");
    assertThat(row.biogeoclimaticCatalogueId()).isEqualTo(77L);
    assertThat(row.netArea()).isEqualByComparingTo("12.5");
  }

  @Test
  @DisplayName("Schedule 11 — a null catalogue id stays null rather than reading as 0")
  void silvicultureSnapshotNullCatalogue() throws Exception {
    ResultSet rs = snapshot("BASIC_SILVICULTURE_REPORT_ID", 11002, "LOCATION", "Block 5");

    Schedule11Repository.LocationSnapshotRow row =
        new Schedule11Repository.LocationSnapshotRowMapper().mapRow(rs, 1);

    assertThat(row.biogeoclimaticCatalogueId()).isNull();
    assertThat(row.netArea()).isNull();
  }

  /**
   * A {@link ResultSet} over a column map that honours JDBC's {@code wasNull()} contract: it
   * reports on the column last read, so a mapper that asks out of order gets the wrong answer here
   * exactly as it would against Oracle. An absent key is SQL NULL.
   *
   * <p>Given as alternating column/value pairs rather than a {@code Map} because several of these
   * views carry more than the ten entries {@code Map.of} accepts. Values may be the natural Java
   * type or a string; the numeric getters convert, so a test can write {@code "12.5"} for a NUMBER
   * without deciding its JDBC type.
   */
  private static ResultSet snapshot(Object... columnsAndValues) {
    Map<String, Object> data = new HashMap<>();
    for (int i = 0; i < columnsAndValues.length; i += 2) {
      data.put((String) columnsAndValues[i], columnsAndValues[i + 1]);
    }
    AtomicBoolean lastWasNull = new AtomicBoolean(false);
    return mock(
        ResultSet.class,
        invocation -> {
          String method = invocation.getMethod().getName();
          if ("wasNull".equals(method)) {
            return lastWasNull.get();
          }
          if (invocation.getArguments().length != 1
              || !(invocation.getArgument(0) instanceof String column)) {
            return null;
          }
          Object value = data.get(column);
          lastWasNull.set(value == null);
          return switch (method) {
            case "getString" -> value == null ? null : value.toString();
            case "getInt" -> value == null ? 0 : Integer.parseInt(value.toString());
            case "getLong" -> value == null ? 0L : Long.parseLong(value.toString());
            case "getBigDecimal" -> value == null ? null : new BigDecimal(value.toString());
            case "getDate" -> value == null ? null : java.sql.Date.valueOf(value.toString());
            default -> null;
          };
        });
  }
}
