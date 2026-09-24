import java.sql.*;

/** Read-only metadata probe: does delivery THE carry the four auditor/licensee columns? */
public class Probe {
  public static void main(String[] a) throws Exception {
    try (Connection c = DriverManager.getConnection(a[0], a[1], a[2])) {
      System.out.println("connected: " + c.getMetaData().getDatabaseProductVersion());
      System.out.println("--- THE.ILCR_MILL_REPORT_STATUS columns ---");
      String sql =
          "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, NULLABLE "
              + "FROM ALL_TAB_COLUMNS WHERE OWNER = 'THE' "
              + "AND TABLE_NAME = 'ILCR_MILL_REPORT_STATUS' ORDER BY COLUMN_ID";
      try (PreparedStatement p = c.prepareStatement(sql);
          ResultSet r = p.executeQuery()) {
        boolean any = false;
        while (r.next()) {
          any = true;
          System.out.printf(
              "%-32s %-12s len=%-6s prec=%-5s nullable=%s%n",
              r.getString(1),
              r.getString(2),
              r.getString(3),
              r.getString(4) == null ? "-" : r.getString(4),
              r.getString(5));
        }
        if (!any) {
          System.out.println("(no rows -- table not visible to this account)");
        }
      }
    }
  }
}
