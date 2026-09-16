package ca.bc.gov.nrs.ilcr.checkstatus;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-level acceptance for every Schedule 1&ndash;10 audit touch performed during Submit.
 *
 * <p>The endpoint transition fixture deliberately leaves these schedules empty, so an endpoint
 * success can prove that zero-row touches are accepted but cannot prove the SQL predicates. Each
 * test here selects a seeded mill/year with both parent and child rows, asserts the repository's
 * affected-row count, and then counts the unique marker across the whole table. Equality proves the
 * touch marked every intended row and no row outside the selected mill/year. It also pins the
 * legacy revision behavior: the versioned Schedule 1&ndash;3 summaries and all three Schedule 8
 * families increment once; audit-only families do not increment. The transaction rolls every marker
 * back.
 */
@DisplayName("ReportTrackTransitionRepository — Schedule 1-10 touch scope and revision behavior")
@Transactional
class ReportTrackTransitionRepositoryIT extends AbstractOracleIT {

  @Autowired private ReportTrackTransitionRepository repository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("Schedules 1-3 touch their summaries and costs for one mill/year only")
  void touchesSchedules1To3Scope() {
    Context context =
        context(
            "SELECT c.ILCR_MILL_ID, c.REPORT_YEAR FROM ("
                + " SELECT s.ILCR_MILL_ID, s.REPORT_YEAR FROM THE.ILCR_REPORT_SUMMARY s"
                + " WHERE s.ILCR_CATEGORY_ID IN ('1', '2', '3')"
                + " GROUP BY s.ILCR_MILL_ID, s.REPORT_YEAR"
                + " HAVING COUNT(DISTINCT s.ILCR_CATEGORY_ID) = 3) c"
                + " WHERE EXISTS (SELECT 1 FROM THE.ILCR_COST_REPORT_DETAIL d"
                + " JOIN THE.ILCR_REPORT_SUMMARY s"
                + " ON s.ILCR_REPORT_SUMMARY_ID = d.ILCR_REPORT_SUMMARY_ID"
                + " WHERE s.ILCR_MILL_ID = c.ILCR_MILL_ID"
                + " AND s.REPORT_YEAR = c.REPORT_YEAR"
                + " AND s.ILCR_CATEGORY_ID IN ('1', '2', '3'))"
                + " FETCH FIRST 1 ROWS ONLY");

    assertScopedTouch(
        "ILCR_REPORT_SUMMARY",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID IN ('1', '2', '3')",
        context,
        "T153_S123_PARENT",
        RevisionEffect.INCREMENT_ONCE,
        repository::touchReportSummaries);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.ILCR_REPORT_SUMMARY_ID IN (SELECT s.ILCR_REPORT_SUMMARY_ID"
            + " FROM THE.ILCR_REPORT_SUMMARY s WHERE s.ILCR_MILL_ID = ?"
            + " AND s.REPORT_YEAR = ? AND s.ILCR_CATEGORY_ID IN ('1', '2', '3'))",
        context,
        "T153_S123_COST",
        repository::touchReportSummaryCostDetails);
  }

  @Test
  @DisplayName("Schedule 4 touches transportation parents and costs for one mill/year only")
  void touchesSchedule4Scope() {
    Context context =
        contextWithCostChild(
            "TRANSPORTATION_REPORT", "TRANSPORTATION_REPORT_ID", "TRANSPORTATION_REPORT_ID", "4");

    assertScopedTouch(
        "TRANSPORTATION_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '4'",
        context,
        "T153_S4_PARENT",
        repository::touchTransportationReports);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.TRANSPORTATION_REPORT_ID IN (SELECT p.TRANSPORTATION_REPORT_ID "
            + "FROM THE.TRANSPORTATION_REPORT p WHERE p.ILCR_MILL_ID = ? "
            + "AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '4')",
        context,
        "T153_S4_COST",
        repository::touchTransportationCostDetails);
  }

  @Test
  @DisplayName("Schedule 5 touches camp parents and costs for one mill/year only")
  void touchesSchedule5Scope() {
    Context context = contextWithCostChild("CAMP_REPORT", "CAMP_REPORT_ID", "CAMP_REPORT_ID", "5");

    assertScopedTouch(
        "CAMP_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '5'",
        context,
        "T153_S5_PARENT",
        repository::touchCamps);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.CAMP_REPORT_ID IN (SELECT p.CAMP_REPORT_ID FROM THE.CAMP_REPORT p "
            + "WHERE p.ILCR_MILL_ID = ? AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '5')",
        context,
        "T153_S5_COST",
        repository::touchCampCostDetails);
  }

  @Test
  @DisplayName("Schedule 6 touches maintenance parents and costs for one mill/year only")
  void touchesSchedule6Scope() {
    Context context =
        contextWithCostChild(
            "ROAD_MAINTENANCE_REPORT",
            "ROAD_MAINTENANCE_REPORT_ID",
            "ROAD_MAINTENANCE_REPORT_ID",
            "6");

    assertScopedTouch(
        "ROAD_MAINTENANCE_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '6'",
        context,
        "T153_S6_PARENT",
        repository::touchRoadMaintenanceReports);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.ROAD_MAINTENANCE_REPORT_ID IN (SELECT p.ROAD_MAINTENANCE_REPORT_ID "
            + "FROM THE.ROAD_MAINTENANCE_REPORT p WHERE p.ILCR_MILL_ID = ? "
            + "AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '6')",
        context,
        "T153_S6_COST",
        repository::touchRoadMaintenanceCostDetails);
  }

  @Test
  @DisplayName("Schedule 7 touches bridge and culvert trees for their own mill/year only")
  void touchesSchedule7Scope() {
    Context bridge =
        contextWithCostChild("BRIDGE_REPORT", "BRIDGE_REPORT_ID", "BRIDGE_REPORT_ID", "7");
    Context culvert =
        contextWithCostChild("CULVERT_REPORT", "CULVERT_REPORT_ID", "CULVERT_REPORT_ID", "7");

    assertScopedTouch(
        "BRIDGE_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '7'",
        bridge,
        "T153_S7A_PARENT",
        repository::touchBridges);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.BRIDGE_REPORT_ID IN (SELECT p.BRIDGE_REPORT_ID FROM THE.BRIDGE_REPORT p "
            + "WHERE p.ILCR_MILL_ID = ? AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '7')",
        bridge,
        "T153_S7A_COST",
        repository::touchBridgeCostDetails);
    assertScopedTouch(
        "CULVERT_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '7'",
        culvert,
        "T153_S7B_PARENT",
        repository::touchCulverts);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.CULVERT_REPORT_ID IN (SELECT p.CULVERT_REPORT_ID FROM THE.CULVERT_REPORT p "
            + "WHERE p.ILCR_MILL_ID = ? AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '7')",
        culvert,
        "T153_S7B_COST",
        repository::touchCulvertCostDetails);
  }

  @Test
  @DisplayName("Schedule 8 touches all three tree-to-truck levels for one mill/year only")
  void touchesSchedule8Scope() {
    Context context =
        context(
            "SELECT p.ILCR_MILL_ID, p.REPORT_YEAR FROM THE.TREE_TO_TRUCK_REPORT p "
                + "WHERE p.ILCR_CATEGORY_ID = '8' AND EXISTS (SELECT 1 "
                + "FROM THE.TREE_TO_TRUCK_DETAIL_REPORT d "
                + "JOIN THE.TREE_TO_TRUCK_RATE_DETAIL r ON r.TREE_TO_TRUCK_DETAIL_REPORT_ID = "
                + "d.TREE_TO_TRUCK_DETAIL_REPORT_ID WHERE d.TREE_TO_TRUCK_REPORT_ID = "
                + "p.TREE_TO_TRUCK_REPORT_ID) FETCH FIRST 1 ROWS ONLY");

    assertScopedTouch(
        "TREE_TO_TRUCK_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '8'",
        context,
        "T153_S8_PARENT",
        RevisionEffect.INCREMENT_ONCE,
        repository::touchTreeToTruckReports);
    assertScopedTouch(
        "TREE_TO_TRUCK_DETAIL_REPORT",
        "t.TREE_TO_TRUCK_REPORT_ID IN (SELECT p.TREE_TO_TRUCK_REPORT_ID "
            + "FROM THE.TREE_TO_TRUCK_REPORT p WHERE p.ILCR_MILL_ID = ? "
            + "AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '8')",
        context,
        "T153_S8_DETAIL",
        RevisionEffect.INCREMENT_ONCE,
        repository::touchTreeToTruckDetailReports);
    assertScopedTouch(
        "TREE_TO_TRUCK_RATE_DETAIL",
        "t.TREE_TO_TRUCK_DETAIL_REPORT_ID IN (SELECT d.TREE_TO_TRUCK_DETAIL_REPORT_ID "
            + "FROM THE.TREE_TO_TRUCK_DETAIL_REPORT d JOIN THE.TREE_TO_TRUCK_REPORT p "
            + "ON p.TREE_TO_TRUCK_REPORT_ID = d.TREE_TO_TRUCK_REPORT_ID "
            + "WHERE p.ILCR_MILL_ID = ? AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '8')",
        context,
        "T153_S8_RATE",
        RevisionEffect.INCREMENT_ONCE,
        repository::touchTreeToTruckRateDetails);
  }

  @Test
  @DisplayName("Schedule 9 touches contractual-work parents and costs for one mill/year only")
  void touchesSchedule9Scope() {
    Context context =
        contextWithCostChild(
            "CONTRACTUAL_WORK_REPORT",
            "CONTRACTUAL_WORK_REPORT_ID",
            "CONTRACTUAL_WORK_REPORT_ID",
            "9");

    assertScopedTouch(
        "CONTRACTUAL_WORK_REPORT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '9'",
        context,
        "T153_S9_PARENT",
        repository::touchContractualWorkReports);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.CONTRACTUAL_WORK_REPORT_ID IN (SELECT p.CONTRACTUAL_WORK_REPORT_ID "
            + "FROM THE.CONTRACTUAL_WORK_REPORT p WHERE p.ILCR_MILL_ID = ? "
            + "AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '9')",
        context,
        "T153_S9_COST",
        repository::touchContractualWorkCostDetails);
  }

  @Test
  @DisplayName("Schedule 10 touches page, road, and cost levels for one mill/year only")
  void touchesSchedule10Scope() {
    Context context =
        context(
            "SELECT p.ILCR_MILL_ID, p.REPORT_YEAR FROM THE.ROAD_CONSTRUCTION_REPRT p "
                + "WHERE p.ILCR_CATEGORY_ID = '10' AND EXISTS (SELECT 1 "
                + "FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d "
                + "JOIN THE.ILCR_COST_REPORT_DETAIL c ON c.ROAD_CONSTRUCTION_REPRT_DTL_ID = "
                + "d.ROAD_CONSTRUCTION_REPRT_DTL_ID WHERE d.ROAD_CONSTRUCTION_REPRT_ID = "
                + "p.ROAD_CONSTRUCTION_REPRT_ID) FETCH FIRST 1 ROWS ONLY");

    assertScopedTouch(
        "ROAD_CONSTRUCTION_REPRT",
        "t.ILCR_MILL_ID = ? AND t.REPORT_YEAR = ? AND t.ILCR_CATEGORY_ID = '10'",
        context,
        "T153_S10_PARENT",
        repository::touchRoadConstructionReports);
    assertScopedTouch(
        "ROAD_CONSTRUCTION_REPRT_DTL",
        "t.ROAD_CONSTRUCTION_REPRT_ID IN (SELECT p.ROAD_CONSTRUCTION_REPRT_ID "
            + "FROM THE.ROAD_CONSTRUCTION_REPRT p WHERE p.ILCR_MILL_ID = ? "
            + "AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '10')",
        context,
        "T153_S10_DETAIL",
        repository::touchRoadConstructionDetails);
    assertScopedTouch(
        "ILCR_COST_REPORT_DETAIL",
        "t.ROAD_CONSTRUCTION_REPRT_DTL_ID IN (SELECT d.ROAD_CONSTRUCTION_REPRT_DTL_ID "
            + "FROM THE.ROAD_CONSTRUCTION_REPRT_DTL d JOIN THE.ROAD_CONSTRUCTION_REPRT p "
            + "ON p.ROAD_CONSTRUCTION_REPRT_ID = d.ROAD_CONSTRUCTION_REPRT_ID "
            + "WHERE p.ILCR_MILL_ID = ? AND p.REPORT_YEAR = ? AND p.ILCR_CATEGORY_ID = '10')",
        context,
        "T153_S10_COST",
        repository::touchRoadConstructionCostDetails);
  }

  private Context contextWithCostChild(
      String parentTable, String parentId, String costForeignKey, String category) {
    return context(
        "SELECT p.ILCR_MILL_ID, p.REPORT_YEAR FROM THE."
            + parentTable
            + " p WHERE p.ILCR_CATEGORY_ID = '"
            + category
            + "' AND EXISTS (SELECT 1 FROM THE.ILCR_COST_REPORT_DETAIL c WHERE c."
            + costForeignKey
            + " = p."
            + parentId
            + ") FETCH FIRST 1 ROWS ONLY");
  }

  private Context context(String sql) {
    return jdbc.queryForObject(
        sql, (result, row) -> new Context(result.getLong(1), result.getInt(2)));
  }

  private void assertScopedTouch(
      String table, String predicate, Context context, String marker, Touch touch) {
    assertScopedTouch(table, predicate, context, marker, RevisionEffect.UNCHANGED, touch);
  }

  private void assertScopedTouch(
      String table,
      String predicate,
      Context context,
      String marker,
      RevisionEffect revisionEffect,
      Touch touch) {
    Integer expected =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM THE." + table + " t WHERE " + predicate,
            Integer.class,
            context.millId(),
            context.year());
    long revisionsBefore = revisionTotal(table, predicate, context);

    assertThat(expected).as("fixture rows for %s", marker).isPositive();
    assertThat(touch.apply(context.millId(), context.year(), marker)).isEqualTo(expected);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE."
                    + table
                    + " t WHERE ("
                    + predicate
                    + ") AND t.UPDATE_USERID = ?",
                Integer.class,
                context.millId(),
                context.year(),
                marker))
        .as("intended rows marked for %s", marker)
        .isEqualTo(expected);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM THE." + table + " WHERE UPDATE_USERID = ?",
                Integer.class,
                marker))
        .as("rows marked across the whole %s table", table)
        .isEqualTo(expected);

    long expectedRevisions =
        revisionEffect == RevisionEffect.INCREMENT_ONCE
            ? revisionsBefore + expected
            : revisionsBefore;
    assertThat(revisionTotal(table, predicate, context))
        .as("revision total for %s", marker)
        .isEqualTo(expectedRevisions);
  }

  private long revisionTotal(String table, String predicate, Context context) {
    Number total =
        jdbc.queryForObject(
            "SELECT NVL(SUM(t.REVISION_COUNT), 0) FROM THE." + table + " t WHERE " + predicate,
            Number.class,
            context.millId(),
            context.year());
    return total.longValue();
  }

  private record Context(long millId, int year) {}

  private enum RevisionEffect {
    UNCHANGED,
    INCREMENT_ONCE
  }

  @FunctionalInterface
  private interface Touch {
    int apply(long millId, int year, String user);
  }
}
