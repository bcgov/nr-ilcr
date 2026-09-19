import java.sql.*;

/** Read-only: UPDATE_TIMESTAMP column type for every table the 17.1 audit sweep writes. */
public class Probe2 {
  private static final String[] TABLES = {
    "ILCR_MILL_REPORT_STATUS", "ILCR_REPORT_CATEGORY", "ILCR_REPORT_SUMMARY",
    "ILCR_COST_REPORT_DETAIL", "TRANSPORTATION_REPORT", "CAMP_REPORT",
    "ROAD_MAINTENANCE_REPORT", "BRIDGE_REPORT", "CULVERT_REPORT",
    "TREE_TO_TRUCK_REPORT", "TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_RATE_DETAIL",
    "CONTRACTUAL_WORK_REPORT", "ROAD_CONSTRUCTION_REPRT", "ROAD_CONSTRUCTION_REPRT_DTL"
  };

  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection(a[0], a[1], a[2])) {
      String sql =
          "SELECT DATA_TYPE, NULLABLE FROM ALL_TAB_COLUMNS WHERE OWNER='THE' "
              + "AND TABLE_NAME=? AND COLUMN_NAME='UPDATE_TIMESTAMP'";
      try (PreparedStatement p = c.prepareStatement(sql)) {
        for (String t : TABLES) {
          p.setString(1, t);
          try (ResultSet r = p.executeQuery()) {
            if (r.next()) {
              System.out.printf("%-30s %-14s nullable=%s%n", t, r.getString(1), r.getString(2));
            } else {
              System.out.printf("%-30s (no UPDATE_TIMESTAMP column found)%n", t);
            }
          }
        }
      }
    }
  }
}
