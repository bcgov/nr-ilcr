-- TEST-SCOPE ONLY (Story 5.7). A single canonical submitter actively associated to EVERY seeded test
-- mill, so that security-ON submitter schedule ITs can reach their mill under the new per-endpoint
-- mill-scope enforcement (MillContextService.validateMillAccess). Security-OFF ITs use the mock
-- principal and are exempt (AD-7 / AC6), so they need nothing here.
--
-- REPEATABLE (R__) seed per the 2026-08-20 Flyway fixture convention (FlywayMigrationConventionTest):
-- seed data lives in an R__ migration in the 10-80 data band, never in a versioned V__ file. Runs
-- after ALL versioned migrations, so the tables (ILCR_USER + ILCR_MILL_USER_XREF, V20260825) and their
-- FK targets (ILCR_ROLE V20260820; the mill status-xref rows) already exist.
--
-- The association is a SET-BASED insert over THE.ILCR_MILL_STATUS_XREF — the FK target of
-- ILCR_MILL_USER_XREF.ILCR_MILL_ID — so it covers whatever mills the shared snapshot seeds without
-- enumerating them (and can never violate the FK). Active row: ACTIVE_DATE set, INACTIVE_DATE null
-- (the app's active convention). One migrate() per test JVM (AbstractOracleIT static block), so this
-- runs exactly once against a fresh container — no duplicate-key concern.

INSERT INTO THE.ILCR_USER
    (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
     ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
VALUES ('CANONSUBMITTERBBBBCCCCDDDD000001', 'LICENSEE', 'Y', 0,
        'canon', SYSDATE, 'canon', SYSDATE);

INSERT INTO THE.ILCR_MILL_USER_XREF
    (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
     ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
SELECT x.ILCR_MILL_STATUS_XREF_ID, 'CANONSUBMITTERBBBBCCCCDDDD000001', SYSDATE, NULL, 0,
       'canon', SYSDATE, 'canon', SYSDATE
  FROM THE.ILCR_MILL_STATUS_XREF x;
