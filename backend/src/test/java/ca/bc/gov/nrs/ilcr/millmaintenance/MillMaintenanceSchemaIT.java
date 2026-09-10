package ca.bc.gov.nrs.ilcr.millmaintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schema tripwire for the tables mill administration writes.
 *
 * <p>Two jobs. First, the columns the write path depends on must exist at the shapes it assumes, so
 * a snapshot that drifts fails here with a column name rather than as a null field far downstream.
 * Second, and more useful: this snapshot deliberately leaves {@code ILCR_MILL_STATUS_XREF} looser
 * than delivery — the status code, the revision and the audit quartet are NOT NULL there and
 * nullable here, because dozens of grandfathered fixtures insert only three columns. That means the
 * snapshot itself cannot catch an omitted audit column, so the assertions below pin what the
 * application must supply regardless, and the negative cases prove the constraints that ARE
 * mirrored actually bite.
 */
@DisplayName("Mill administration schema")
class MillMaintenanceSchemaIT extends AbstractOracleIT {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("The cross-reference carries every column the write path touches")
  void crossReferenceHasTheWritePathColumns() {
    Map<String, String> types = columnTypes("ILCR_MILL_STATUS_XREF");

    assertThat(types)
        .containsEntry("ILCR_MILL_STATUS_XREF_ID", "NUMBER(10,0)")
        .containsEntry("ILCR_MILL_STATUS_CODE", "VARCHAR2(3)")
        .containsEntry("HEAD_OFFICE_CONTACT_IND", "VARCHAR2(1)")
        .containsEntry("HEAD_OFFICE_CONTACT_ID", "NUMBER(12,0)")
        .containsEntry("DIVISION_CONTACT_ID", "NUMBER(12,0)")
        // Added for this surface: legacy stamps a provenance note on every imported row, and the
        // delivery audit trigger copies it into the shadow table.
        .containsEntry("COMMENTS", "VARCHAR2(4000)")
        // 30 characters is what makes the raw custom:idp_username claim the only usable identity:
        // the 32-char directory GUID and the 36-char subject both overflow it.
        .containsEntry("ENTRY_USERID", "VARCHAR2(30)")
        .containsEntry("UPDATE_USERID", "VARCHAR2(30)");
  }

  @Test
  @DisplayName("The head-office indicator admits only Y and N")
  void headOfficeIndicatorIsConstrained() {
    // A real delivery CHECK, mirrored under its delivery name. Without this the Y/N domain would be
    // convention here and constraint in production — the shape that lets a test pass green against
    // a value the real database refuses.
    //
    // Restored in finally: if the constraint is ever MISSING (the drift this test exists to catch),
    // the UPDATE succeeds — and without the restore, the corrupted seed row would cascade the one
    // real failure into every other test in the JVM-shared container that asserts 751's state.
    try {
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE THE.ILCR_MILL_STATUS_XREF SET HEAD_OFFICE_CONTACT_IND = 'X' "
                          + "WHERE ILCR_MILL_STATUS_XREF_ID = 751"))
          .hasMessageContaining("AVCON_1440773538_HEAD__000");
    } finally {
      jdbcTemplate.update(
          "UPDATE THE.ILCR_MILL_STATUS_XREF SET HEAD_OFFICE_CONTACT_IND = NULL "
              + "WHERE ILCR_MILL_STATUS_XREF_ID = 751");
    }
  }

  @Test
  @DisplayName("A cross-reference cannot carry a status code the code table does not define")
  void statusCodeIsForeignKeyed() {
    // Same restore rationale as the indicator test above: a missing FK must fail ONE test, not
    // poison the shared fixture row for the rest of the suite.
    try {
      assertThatThrownBy(
              () ->
                  jdbcTemplate.update(
                      "UPDATE THE.ILCR_MILL_STATUS_XREF SET ILCR_MILL_STATUS_CODE = 'ZZZ' "
                          + "WHERE ILCR_MILL_STATUS_XREF_ID = 751"))
          .hasMessageContaining("ILCR_MSXRF_ILCRMSC_FK");
    } finally {
      jdbcTemplate.update(
          "UPDATE THE.ILCR_MILL_STATUS_XREF SET ILCR_MILL_STATUS_CODE = 'ACT' "
              + "WHERE ILCR_MILL_STATUS_XREF_ID = 751");
    }
  }

  @Test
  @DisplayName("The status code table holds exactly the two codes the application uses")
  void statusCodeTableHoldsActAndCls() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT ILCR_MILL_STATUS_CODE, DESCRIPTION FROM THE.ILCR_MILL_STATUS_CODE "
                + "ORDER BY ILCR_MILL_STATUS_CODE");

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).containsEntry("ILCR_MILL_STATUS_CODE", "ACT");
    assertThat(rows.get(0)).containsEntry("DESCRIPTION", "Active");
    // "Close", not "Closed" — this is the label legacy rendered, recovered from delivery.
    assertThat(rows.get(1)).containsEntry("ILCR_MILL_STATUS_CODE", "CLS");
    assertThat(rows.get(1)).containsEntry("DESCRIPTION", "Close");
  }

  @Test
  @DisplayName("The client contact key is numeric, as delivery defines it")
  void clientContactIdIsNumeric() {
    // The legacy Hibernate mapping models this as a String. Delivery disagrees, and the contact
    // columns on the cross-reference are NUMBER(12) to match, so the wire contract carries a
    // number.
    assertThat(columnTypes("CLIENT_CONTACT")).containsEntry("CLIENT_CONTACT_ID", "NUMBER(12,0)");
  }

  @Test
  @DisplayName("The fixture block this area owns is present and shaped as its tests assume")
  void fixtureBlockIsIntact() {
    // 750 and 756 must have NO cross-reference or the import path has nothing to act on; 752 must
    // have exactly one active assignment or the deactivation guard proves nothing.
    assertThat(xrefCount(750L)).isZero();
    assertThat(xrefCount(756L)).isZero();
    assertThat(activeAssignmentCount(751L)).isZero();
    assertThat(activeAssignmentCount(752L)).isOne();
    // No fixture may carry a current-year report-status row: another IT class asserts the count of
    // those directly.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM THE.ILCR_MILL_REPORT_STATUS "
                    + "WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID BETWEEN 750 AND 756",
                Integer.class))
        .isZero();
  }

  private int xrefCount(long millId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = ?",
        Integer.class,
        millId);
  }

  private int activeAssignmentCount(long millId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM THE.ILCR_MILL_USER_XREF WHERE ILCR_MILL_ID = ? "
            + "AND INACTIVE_DATE IS NULL",
        Integer.class,
        millId);
  }

  /**
   * Column name to {@code TYPE(precision,scale)} or {@code TYPE(length)}, from the data dictionary.
   */
  private Map<String, String> columnTypes(String tableName) {
    return jdbcTemplate
        .queryForList(
            "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE "
                + "FROM ALL_TAB_COLUMNS WHERE OWNER = 'THE' AND TABLE_NAME = ?",
            tableName)
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                row -> (String) row.get("COLUMN_NAME"), MillMaintenanceSchemaIT::renderType));
  }

  private static String renderType(Map<String, Object> row) {
    String type = (String) row.get("DATA_TYPE");
    Number precision = (Number) row.get("DATA_PRECISION");
    Number scale = (Number) row.get("DATA_SCALE");
    if (precision != null) {
      // Scale is included deliberately: without it NUMBER(10) and NUMBER(10,2) compare equal and a
      // drift in scale passes silently.
      return type + "(" + precision.intValue() + "," + (scale == null ? 0 : scale.intValue()) + ")";
    }
    return type + "(" + ((Number) row.get("DATA_LENGTH")).intValue() + ")";
  }
}
