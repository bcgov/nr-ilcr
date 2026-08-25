package ca.bc.gov.nrs.ilcr.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.bc.gov.nrs.ilcr.support.AbstractOracleIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the shape of {@code THE.ILCR_USER} and {@code THE.ILCR_MILL_USER_XREF} in the test
 * snapshot against the real delivery schema.
 *
 * <p>These two tables already exist in managed THE, so the snapshot is a mirror rather than a
 * design. A mirror that drifts is worse than no mirror at all: a write that delivery rejects would
 * pass here and fail in production instead. Every expectation below was read from {@code
 * ALL_TAB_COLUMNS} and {@code ALL_CONSTRAINTS} on the seeded real-data image, so a snapshot edit
 * that diverges from delivery fails here and not at a customer.
 *
 * <p>The negative cases matter as much as the positive ones. The audit quartet and revision count
 * carry no DEFAULT, which is precisely what makes an INSERT that forgets one fail loudly rather
 * than silently persisting a bad row — a failure mode this codebase has shipped before.
 */
class AssignmentSchemaIT extends AbstractOracleIT {

  /** An existing mill-status cross-reference id, which is what the mill-side key resolves to. */
  private static final long SEEDED_MILL_ID = 514L;

  private static final String ROLE = "LICENSEE";
  private static final String USER_GUID = "AAAAAAAABBBBCCCCDDDDEEEEFFFF0001";
  private static final String ORPHAN_GUID = "AAAAAAAABBBBCCCCDDDDEEEEFFFF0002";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_MILL_USER_XREF WHERE USER_GUID IN (?, ?)", USER_GUID, ORPHAN_GUID);
    jdbcTemplate.update(
        "DELETE FROM THE.ILCR_USER WHERE USER_GUID IN (?, ?)", USER_GUID, ORPHAN_GUID);
  }

  @Test
  @DisplayName("ILCR_USER mirrors the delivery column shape")
  void ilcrUserMirrorsDeliveryColumnShape() {
    Map<String, String> actual = columnTypes("ILCR_USER");

    // ACTIVE_IND is VARCHAR2(1) in delivery, not CHAR(1); a CHAR would blank-pad and break
    // equality.
    assertEquals(
        Map.of(
            "USER_GUID", "VARCHAR2(32) NOT NULL",
            "ILCR_ROLE_NAME", "VARCHAR2(10) NOT NULL",
            "ACTIVE_IND", "VARCHAR2(1) NOT NULL",
            "REVISION_COUNT", "NUMBER(5,0) NOT NULL",
            "ENTRY_USERID", "VARCHAR2(30) NOT NULL",
            "ENTRY_TIMESTAMP", "DATE NOT NULL",
            "UPDATE_USERID", "VARCHAR2(30) NOT NULL",
            "UPDATE_TIMESTAMP", "DATE NOT NULL"),
        actual);
  }

  @Test
  @DisplayName("ILCR_MILL_USER_XREF mirrors the delivery column shape, dates nullable")
  void millUserXrefMirrorsDeliveryColumnShape() {
    Map<String, String> actual = columnTypes("ILCR_MILL_USER_XREF");

    // Both dates are nullable because they encode state: a null INACTIVE_DATE means ACTIVE.
    assertEquals(
        Map.of(
            "ILCR_MILL_ID", "NUMBER(10,0) NOT NULL",
            "USER_GUID", "VARCHAR2(32) NOT NULL",
            "ACTIVE_DATE", "DATE NULL",
            "INACTIVE_DATE", "DATE NULL",
            "REVISION_COUNT", "NUMBER(5,0) NOT NULL",
            "ENTRY_USERID", "VARCHAR2(30) NOT NULL",
            "ENTRY_TIMESTAMP", "DATE NOT NULL",
            "UPDATE_USERID", "VARCHAR2(30) NOT NULL",
            "UPDATE_TIMESTAMP", "DATE NOT NULL"),
        actual);
  }

  @Test
  @DisplayName("the retired profile-xref table is absent from the snapshot")
  void retiredProfileXrefTableIsAbsent() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = 'THE' AND TABLE_NAME = ?",
            Integer.class,
            "ILCR_MILL_USER_PROFILE_XREF");

    assertEquals(
        0,
        count,
        "the net-new profile xref was retired in favour of the legacy tables; recreating it would "
            + "split assignment state across two sources");
  }

  @Test
  @DisplayName("the xref primary key is the composite (ILCR_MILL_ID, USER_GUID), in that order")
  void primaryKeyIsCompositeMillIdThenUserGuid() {
    assertEquals(
        List.of("ILCR_MILL_ID", "USER_GUID"), keyColumns("ILCR_MILL_USER_XREF", "IUMX_PK"));
    assertEquals(List.of("USER_GUID"), keyColumns("ILCR_USER", "ILCR_USR_PK"));
  }

  @Test
  @DisplayName("both xref foreign keys are present and enabled")
  void foreignKeysArePresentAndEnabled() {
    assertEquals("ENABLED", constraintStatus("ILCR_MILL_USER_XREF", "ILCR_IUMX_USER_FK"));
    // The mill-side key is enforced against the status cross-reference, not MILL, so a mill with no
    // status row cannot be assigned at all.
    assertEquals("ENABLED", constraintStatus("ILCR_MILL_USER_XREF", "ILCR_IUMX_MSXRF_FK"));
    assertEquals("ENABLED", constraintStatus("ILCR_USER", "ILCR_USR_ILCR_ROLE_FK"));
  }

  @Test
  @DisplayName("the audit quartet and revision count carry no DEFAULT on either table")
  void auditColumnsCarryNoDefault() {
    List<String> defaulted =
        jdbcTemplate.queryForList(
            """
            SELECT TABLE_NAME || '.' || COLUMN_NAME
              FROM ALL_TAB_COLUMNS
             WHERE OWNER = 'THE'
               AND TABLE_NAME IN ('ILCR_USER', 'ILCR_MILL_USER_XREF')
               AND COLUMN_NAME IN ('REVISION_COUNT', 'ENTRY_USERID', 'ENTRY_TIMESTAMP',
                                   'UPDATE_USERID', 'UPDATE_TIMESTAMP')
               AND DEFAULT_LENGTH IS NOT NULL
            """,
            String.class);

    assertTrue(
        defaulted.isEmpty(),
        "a DEFAULT here would let an INSERT that omits an audit column succeed in tests and fail in "
            + "delivery; found defaults on "
            + defaulted);
  }

  @Test
  @DisplayName("an assignment for a user with no account row is refused by the user foreign key")
  void xrefInsertWithoutParentUserIsRefused() {
    // Proves the write path must provision the account row first rather than assuming one exists.
    DataIntegrityViolationException refused =
        assertThrows(DataIntegrityViolationException.class, () -> insertAssignment(ORPHAN_GUID));

    // Pin WHICH constraint fired: if the seeded mill row ever vanished, the mill-side FK would
    // throw the same exception type and this test would prove nothing about the user FK.
    assertTrue(
        refused.getMostSpecificCause().getMessage().contains("ILCR_IUMX_USER_FK"),
        "expected ILCR_IUMX_USER_FK to refuse the orphan insert, got: "
            + refused.getMostSpecificCause().getMessage());
  }

  @Test
  @DisplayName("an account insert that omits an audit column is refused by NOT NULL")
  void accountInsertOmittingAuditColumnIsRefused() {
    // The provisioning insert Story 2.2 writes lands on THIS table, so the audit tripwire must
    // exist here as well as on the xref.
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO THE.ILCR_USER
                  (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP)
                  VALUES (?, ?, 'N', 0, 'TRIPWIRE', SYSDATE)
                """,
                USER_GUID,
                ROLE));
  }

  @Test
  @DisplayName("ACTIVE_IND admits only Y or N, enforced by delivery's named check constraint")
  void activeIndAdmitsOnlyYorN() {
    // Delivery's ONE check constraint beyond NOT NULLs, read from ALL_CONSTRAINTS on the seeded
    // image 2026-08-25 (generated-looking name and all); ILCR_MILL_USER_XREF has none, so no
    // date-exclusivity CHECK exists anywhere to mirror.
    assertEquals("ENABLED", constraintStatus("ILCR_USER", "AVCON_1440773538_ACTIV_000"));

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO THE.ILCR_USER
                  (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
                   ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
                  VALUES (?, ?, 'X', 0, 'TRIPWIRE', SYSDATE, 'TRIPWIRE', SYSDATE)
                """,
                USER_GUID,
                ROLE));
  }

  @Test
  @DisplayName("an assignment that omits an audit column is refused by NOT NULL")
  void xrefInsertOmittingAuditColumnIsRefused() {
    insertAccount(USER_GUID);

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO THE.ILCR_MILL_USER_XREF
                  (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP)
                  VALUES (?, ?, SYSDATE, 0, 'TRIPWIRE', SYSDATE)
                """,
                SEEDED_MILL_ID,
                USER_GUID));
  }

  @Test
  @DisplayName("one user-mill pair admits only a single row, forcing toggle in place")
  void duplicatePairIsRefusedByTheCompositeKey() {
    insertAccount(USER_GUID);
    insertAssignment(USER_GUID);

    // No second row is possible, which is why reactivation updates the existing row and why no
    // reactivation history is retained.
    assertThrows(DataIntegrityViolationException.class, () -> insertAssignment(USER_GUID));
  }

  private void insertAccount(String userGuid) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_USER
          (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
          VALUES (?, ?, 'N', 0, 'TRIPWIRE', SYSDATE, 'TRIPWIRE', SYSDATE)
        """,
        userGuid,
        ROLE);
  }

  private void insertAssignment(String userGuid) {
    jdbcTemplate.update(
        """
        INSERT INTO THE.ILCR_MILL_USER_XREF
          (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
          VALUES (?, ?, SYSDATE, NULL, 0, 'TRIPWIRE', SYSDATE, 'TRIPWIRE', SYSDATE)
        """,
        SEEDED_MILL_ID,
        userGuid);
  }

  /** Column name to a rendered "TYPE(SIZE) NULL|NOT NULL" description, for whole-map comparison. */
  private Map<String, String> columnTypes(String tableName) {
    return jdbcTemplate
        .queryForList(
            """
            SELECT COLUMN_NAME,
                   CASE WHEN DATA_TYPE LIKE '%CHAR%' THEN DATA_TYPE || '(' || CHAR_LENGTH || ')'
                        WHEN DATA_TYPE = 'NUMBER'
                             -- scale included: NUMBER(10) and NUMBER(10,2) must not compare equal
                             THEN DATA_TYPE || '(' || DATA_PRECISION || ',' || DATA_SCALE || ')'
                        ELSE DATA_TYPE END
                   || CASE WHEN NULLABLE = 'N' THEN ' NOT NULL' ELSE ' NULL' END AS DESCRIPTION
              FROM ALL_TAB_COLUMNS
             WHERE OWNER = 'THE' AND TABLE_NAME = ?
            """,
            tableName)
        .stream()
        .collect(
            java.util.stream.Collectors.toMap(
                row -> (String) row.get("COLUMN_NAME"), row -> (String) row.get("DESCRIPTION")));
  }

  private List<String> keyColumns(String tableName, String constraintName) {
    return jdbcTemplate.queryForList(
        """
        SELECT cc.COLUMN_NAME
          FROM ALL_CONS_COLUMNS cc
         WHERE cc.OWNER = 'THE' AND cc.TABLE_NAME = ? AND cc.CONSTRAINT_NAME = ?
         ORDER BY cc.POSITION
        """,
        String.class,
        tableName,
        constraintName);
  }

  private String constraintStatus(String tableName, String constraintName) {
    return jdbcTemplate.queryForObject(
        """
        SELECT STATUS FROM ALL_CONSTRAINTS
         WHERE OWNER = 'THE' AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?
        """,
        String.class,
        tableName,
        constraintName);
  }
}
