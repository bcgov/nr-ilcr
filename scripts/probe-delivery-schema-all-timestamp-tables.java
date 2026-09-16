import java.sql.*;

/** Read-only: UPDATE_/ENTRY_TIMESTAMP types for every table the backend writes SYSTIMESTAMP into. */
public class Probe3 {
  private static final String[] TABLES = {
    "BASIC_SILVICULTURE_REPORT", "ILCR_REPORT_COST_ITEM", "ILCR_ROLE",
    "BRIDGE_REPORT", "CAMP_REPORT", "CONTRACTUAL_WORK_REPORT", "CULVERT_REPORT",
    "ILCR_COST_REPORT_DETAIL", "ILCR_REPORT_SUMMARY", "ROAD_CONSTRUCTION_REPRT",
    "ROAD_CONSTRUCTION_REPRT_DTL", "ROAD_MAINTENANCE_REPORT", "TRANSPORTATION_REPORT",
    "TREE_TO_TRUCK_DETAIL_REPORT", "TREE_TO_TRUCK_RATE_DETAIL", "TREE_TO_TRUCK_REPORT",
    "ILCR_MILL_REPORT_STATUS", "ILCR_REPORT_CATEGORY", "ILCR_REPORTING_PERIOD"
  };

  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection(a[0], a[1], a[2])) {
      System.out.println("connected: " + c.getMetaData().getDatabaseProductVersion());
      String sql =
          "SELECT COLUMN_NAME, DATA_TYPE, NULLABLE FROM ALL_TAB_COLUMNS WHERE OWNER='THE' "
              + "AND TABLE_NAME=? AND COLUMN_NAME IN ('UPDATE_TIMESTAMP','ENTRY_TIMESTAMP') "
              + "ORDER BY COLUMN_NAME";
      try (PreparedStatement p = c.prepareStatement(sql)) {
        for (String t : TABLES) {
          p.setString(1, t);
          StringBuilder row = new StringBuilder();
          try (ResultSet r = p.executeQuery()) {
            while (r.next()) {
              row.append(String.format("%s=%s(null:%s) ", r.getString(1), r.getString(2), r.getString(3)));
            }
          }
          System.out.printf("%-32s %s%n", t, row.length() == 0 ? "(table/columns not found)" : row);
        }
      }
    }
  }
}
