-- THE.ILCR_MILL_REPORT_STATUS: the four auditor/licensee reference columns.
--
-- TEST-SCOPE ONLY (the app executes no runtime DDL, AD-2) and DDL ONLY -- no seed rows, per the
-- 2026-08-20 Flyway fixture decision enforced by
-- FlywayMigrationConventionTest.newVersionedMigrationsCarryNoSeedData. These four columns exist in
-- managed THE but were never needed by a shipped slice, so the V1 snapshot omitted them. V1 is not
-- edited to add them: shipped migrations are immutable (AD-10), and editing V1 would invalidate the
-- Flyway checksum of every database already migrated.
--
-- Why they are a PAIR each, not a single user id: the legacy Hibernate mapping joins each role
-- through a COMPOSITE foreign key into THE.ILCR_MILL_USER_XREF (ILCR_MILL_ID, USER_GUID) --
-- ILCRMillReportStatus.java:79-86 in the 2.0.4 snapshot. So a report can only record an auditor who
-- holds a mill-user association for that mill; legacy writes NULL into both columns when the acting
-- user has none, which is why all four are nullable here.
--
-- Types are taken from the FK target columns rather than inferred: ILCR_MILL_USER_XREF.ILCR_MILL_ID
-- is NUMBER(10) and USER_GUID is VARCHAR2(32) (V20260825__the_ilcr_user_and_mill_user_xref.sql:53-68).
-- The composite FK constraints themselves are deliberately NOT declared here -- the snapshot keeps
-- FK/index DDL in the R__90+ band, and delivery's constraints are out of this story's scope. One
-- consequence for tests: an auditor GUID with no matching xref row inserts cleanly in test scope
-- though delivery would reject it.
--
-- CONFIRMED against the delivery database 2026-09-16 (THE on fortmp1, Oracle 19c 19.10.0.0.0,
-- read-only ALL_TAB_COLUMNS probe). All four columns exist with these exact names and compatible
-- types -- AUDITOR_MILL_ID / LICENSEE_MILL_ID are NUMBER(10) nullable and AUDITOR_USER_GUID /
-- LICENSEE_USER_GUID are VARCHAR2(32) nullable -- so the Story 17.1 UPDATE cannot raise ORA-00904,
-- and VARCHAR2(32) is an exact fit for the 32-char directory GUID. The earlier caveat here (names
-- inferred from the legacy Hibernate mapping, unconfirmed) is discharged.
--
-- The SAME probe found three places where THIS SNAPSHOT diverges from delivery on the base table,
-- recorded here because they mislead anyone reading V1 as if it were delivery's shape:
--   * ILCR_MILL_REPORT_STATUS_CODE and MILL_SILVICULTUR_STATUS_CODE are NOT NULL in delivery;
--     V1 declares both nullable. Code tolerating a null track code therefore guards a state
--     delivery cannot hold.
--   * REVISION_COUNT is NUMBER(5) NOT NULL in delivery; V1 has NUMBER(10) DEFAULT 0, nullable.
--   * Every UPDATE_TIMESTAMP on the fifteen tables Story 17.1 writes is DATE NOT NULL in delivery,
--     while V1 and its successors declare several as TIMESTAMP. That one had teeth: the sweep chose
--     SYSTIMESTAMP for nine statements on the snapshot's authority, which Oracle narrows silently
--     into a DATE column, so no test could have caught it. All twenty now use SYSDATE.
-- V1 is NOT edited to fix these (shipped migrations are immutable, AD-10). They are cross-cutting
-- snapshot debt, not Story 17.1 scope.

ALTER TABLE THE.ILCR_MILL_REPORT_STATUS ADD (
  AUDITOR_MILL_ID     NUMBER(10),
  AUDITOR_USER_GUID   VARCHAR2(32),
  LICENSEE_MILL_ID    NUMBER(10),
  LICENSEE_USER_GUID  VARCHAR2(32)
);
