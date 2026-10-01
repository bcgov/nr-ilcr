-- THE.ILCR_MILL_REPORT_STATUS: the four LICENSEE_* / AUDITOR_* columns and the COMMENTS column that the
-- V1 snapshot omitted. TEST-SCOPE ONLY (the app executes no runtime DDL, AD-2), and DDL ONLY -- no seed
-- rows here, per the 2026-08-20 Flyway fixture decision enforced by
-- FlywayMigrationConventionTest.newVersionedMigrationsCarryNoSeedData.
--
-- None of this is new. All five columns and both composite foreign keys already exist in managed THE.
-- Every type, precision, nullability and constraint below was read from the live dev database
-- (fortmp1.nrs.bcgov, schema THE) on 2026-09-15 via ALL_TAB_COLUMNS, ALL_CONSTRAINTS and
-- ALL_CONS_COLUMNS rather than inferred from the legacy Hibernate mapping (ILCRMillReportStatus.java
-- maps the two pairs as @JoinColumns to ILCR_MILL_USER_XREF, which agrees).
--
-- Submitting Schedules 1-10 records the submitting user as the report's licensee (UC-CHK-002 BR-05):
-- legacy SubmitReportDAO.updateILCRMillReportStatus writes the (ILCR_MILL_ID, USER_GUID) of the
-- caller's ILCR_MILL_USER_XREF row into LICENSEE_MILL_ID / LICENSEE_USER_GUID on Draft -> Submitted, and
-- into the AUDITOR_* pair on every other transition (Verify, Set to Draft). Both pairs are nullable:
-- legacy writes NULLs when the caller has no xref row for the mill.
--
-- The composite FKs target IUMX_PK (ILCR_MILL_ID, USER_GUID) on THE.ILCR_MILL_USER_XREF exactly as
-- delivery does (ILCR_IMUX_LICENSEE_FK / ILCR_IMUX_AUDITOR_FK), so a fixture or a write that names a
-- pair with no xref row fails here rather than only in production.
--
-- COMMENTS is VARCHAR2(4000) NULL in delivery and is carried so the status row's shape is complete;
-- nothing in the application writes it.
ALTER TABLE THE.ILCR_MILL_REPORT_STATUS ADD (
  AUDITOR_MILL_ID    NUMBER(10),
  AUDITOR_USER_GUID  VARCHAR2(32),
  LICENSEE_MILL_ID   NUMBER(10),
  LICENSEE_USER_GUID VARCHAR2(32),
  COMMENTS           VARCHAR2(4000)
);

ALTER TABLE THE.ILCR_MILL_REPORT_STATUS ADD CONSTRAINT ILCR_IMUX_LICENSEE_FK
  FOREIGN KEY (LICENSEE_MILL_ID, LICENSEE_USER_GUID)
  REFERENCES THE.ILCR_MILL_USER_XREF (ILCR_MILL_ID, USER_GUID);

ALTER TABLE THE.ILCR_MILL_REPORT_STATUS ADD CONSTRAINT ILCR_IMUX_AUDITOR_FK
  FOREIGN KEY (AUDITOR_MILL_ID, AUDITOR_USER_GUID)
  REFERENCES THE.ILCR_MILL_USER_XREF (ILCR_MILL_ID, USER_GUID);
