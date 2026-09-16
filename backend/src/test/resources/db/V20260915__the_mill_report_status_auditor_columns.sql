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
-- NOT independently confirmed against the delivery database. The column names and the composite-FK
-- shape come from the legacy mapping, which is authoritative for names but has been an unreliable
-- guide to physical shape before (see the V20260910 header). Confirm against THE on fortmp1 before
-- this is relied on beyond test scope.

ALTER TABLE THE.ILCR_MILL_REPORT_STATUS ADD (
  AUDITOR_MILL_ID     NUMBER(10),
  AUDITOR_USER_GUID   VARCHAR2(32),
  LICENSEE_MILL_ID    NUMBER(10),
  LICENSEE_USER_GUID  VARCHAR2(32)
);
