-- Story 9.1 (Schedule 9 read) seed EXTENSION (never edit an earlier migration, AR8). Adds the
-- THE.CONTRACTUAL_WORK_REPORT table (one contractual-work record per row, keyed ILCR_MILL_ID +
-- REPORT_YEAR + ILCR_CATEGORY_ID='9'), the CONTRACTUAL_WORK_REPORT_ID FK column on the shared
-- ILCR_COST_REPORT_DETAIL (absent from V1 -> added here, the same guarded move V34 made for
-- CAMP_REPORT_ID), the THE.ILCR_CONTRACTUAL_SOURCE_CODE reference table (not in the V1 snapshot),
-- the seven category-'9' cost items (108-114, BR-09), and the read fixtures.
--
-- Timestamped version (db/README.md escape hatch): the V## line has collided repeatedly, so this
-- takes the next free timestamp after V20260812 (the Story 24.3 code-table seed). Re-scan before
-- pushing; on collision bump THIS migration, never a merged one.
--
-- Storage model (legacy ContractualWorkReport + ILCRCostReportDetail; delivery Task-1 gate): a
-- record is ONE CONTRACTUAL_WORK_REPORT row carrying the contractor + four code-list selects
-- (unit / source / BEC, plus their "Other" free-text descriptions) + Number of Units + Side Slope
-- + comments + its own REVISION_COUNT. Its Contractual Item (108-114) and Cost are ONE keyed row
-- in the shared ILCR_COST_REPORT_DETAIL, joined by CONTRACTUAL_WORK_REPORT_ID and discriminated by
-- ILCR_REPORT_COST_ITEM_ID. There is NO category-'9' ILCR_REPORT_SUMMARY row, so Schedule 9 is
-- summary-less like Schedules 4/5/6: trackStatus comes from ILCR_MILL_REPORT_STATUS (seeded in V2 --
-- mill 514/2021 'D', 517/2021 'S', 515/2021 'D') and the optimistic-lock token is per record.
--
-- Test-scope id ranges: CONTRACTUAL_WORK_REPORT is a brand-new table (its own PK space) -> ids
-- 9101-9199. ILCR_COST_REPORT_DETAIL is SHARED, so cost-line ids take the free 8481-8499 gap
-- (above Schedule 5's 8411-8480, below Schedule 8's >=8500, under the 9000 sequence start).

-- The CONTRACTUAL_WORK_REPORT table (present in the real THE schema; shape from legacy
-- ContractualWorkReport). PERFORMED_UNIT is the delivery NUMBER(6,1) "Number of Units" and
-- SIDE_SLOPE_PCT the Integer percent. REVISION_COUNT and all four audit columns are NOT NULL with
-- NO defaults, so an insert that skips any of them fails HERE like it would in delivery (the
-- Schedule 1 lax-snapshot lesson, which let the audit-column bug ship three times).
CREATE TABLE THE.CONTRACTUAL_WORK_REPORT (
  CONTRACTUAL_WORK_REPORT_ID    NUMBER(10) PRIMARY KEY,
  REPORT_YEAR                   NUMBER(4) NOT NULL,
  ILCR_MILL_ID                  NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID              VARCHAR2(5) NOT NULL,
  CONTRACTOR_ID                 VARCHAR2(30),
  SIDE_SLOPE_PCT                NUMBER(3),
  PERFORMED_UNIT                NUMBER(6,1),
  ILCR_UNIT_CODE                VARCHAR2(10),
  UNIT_DESCRIPTION              VARCHAR2(120),
  ILCR_CONTRACTUAL_SOURCE_CODE  VARCHAR2(10),
  SOURCE_DESCRIPTION            VARCHAR2(120),
  BEC_ZONE_CODE                 VARCHAR2(10),
  COMMENTS                      VARCHAR2(2000),
  REVISION_COUNT                NUMBER(5) NOT NULL,
  ENTRY_USERID                  VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP               DATE NOT NULL,
  UPDATE_USERID                 VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP              DATE NOT NULL
);

-- The contractual-source reference table (Source select). Two-column code/description shape, the
-- same as BEC_ZONE_CODE; not present in V1, so created here.
CREATE TABLE THE.ILCR_CONTRACTUAL_SOURCE_CODE (
  ILCR_CONTRACTUAL_SOURCE_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION                  VARCHAR2(120)
);
INSERT INTO THE.ILCR_CONTRACTUAL_SOURCE_CODE VALUES ('A', 'Actual Cost');
INSERT INTO THE.ILCR_CONTRACTUAL_SOURCE_CODE VALUES ('O', 'Other');

-- Number-of-Units "Other" unit for record 9103 (M3 is already seeded by V20260812).
INSERT INTO THE.ILCR_UNIT_CODE (ILCR_UNIT_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('OT', 'Other', DATE '2000-01-01', NULL, SYSTIMESTAMP);

-- ILCR_COST_REPORT_DETAIL carries one FK per report family; V1's snapshot only has
-- ILCR_REPORT_SUMMARY_ID, so add the Schedule 9 FK column here.
--
-- GUARDED because the target is a SHARED table (V34's reasoning): a concurrent branch adding
-- CONTRACTUAL_WORK_REPORT_ID, or a rebase that reorders it, would otherwise raise ORA-01430 inside
-- AbstractOracleIT's static block and red EVERY integration test in the repo. Swallowing only -1430
-- keeps the migration idempotent without hiding any other failure.
DECLARE
  e_column_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD (CONTRACTUAL_WORK_REPORT_ID NUMBER(10))';
EXCEPTION
  WHEN e_column_exists THEN NULL;
END;
/

-- The seven category-'9' cost items (BR-09; ids 108-114, legacy Constant.REPORT_COST_ITEMS
-- Schedule9_*). Cost items are shared master data -- define once, reference, never re-INSERT.
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (108, 'Cattleguard', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (109, 'Pipeline Crossing', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (110, 'Remedial Fence', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (111, 'Semi-permanent Road Deactivation', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (112, 'Permanent Road Deactivation', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (113, 'Wing Fencing', '9', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (114, 'Other', '9', '2', 'SEED');

-- ================================================================================================
-- Mill 514 / 2021 -- ACT, Draft (context in V2). Three records, INSERTED OUT OF ID ORDER (9103,
-- 9101, 9102) so a missing ORDER BY cannot pass: the document must serve 9101, 9102, 9103.
--
-- 9101 fully populated  -- units 12.5, cost 5000 -> $/Unit 400.00; every code-list select resolved.
--                          The fractional PERFORMED_UNIT pins the NUMBER(6,1) one-decimal precision:
--                          an integer value (e.g. 100.0) is canonicalized by Oracle to 100 and would
--                          serialize as an int, hiding whether the decimal survives the read.
-- 9102 zero units       -- PERFORMED_UNIT 0 -> $/Unit NULL (S14), even though a cost is stored.
-- 9103 "Other" free-text -- Unit 'OT'/UNIT_DESCRIPTION, Source 'O'/SOURCE_DESCRIPTION, and
--                           Contractual Item 114 with ITEM_DESCRIPTION on the cost line -- the three
--                           conditional descriptions, served verbatim.
-- ================================================================================================

-- 9103 (inserted first, served third).
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9103, 2021, 514, '9', 'CTR-003', 10, 50.0, 'OT', 'linear metre', 'O', 'Contractor quote', 'BZ1', 'Custom crossing.', 2, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8483, 9103, 114, 2500, 'Custom gate', 0, 'SEED');

-- 9101 (inserted second, served first).
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9101, 2021, 514, '9', 'CTR-001', 25, 12.5, 'M3', NULL, 'A', NULL, 'BZ1', 'Cattleguard install.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8481, 9101, 108, 5000, 0, 'SEED');

-- 9102 (inserted third, served second). Zero units -> null $/Unit.
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9102, 2021, 514, '9', 'CTR-002', NULL, 0, 'M3', NULL, 'A', NULL, 'BZ1', NULL, 1, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8482, 9102, 109, 3000, 0, 'SEED');

-- ================================================================================================
-- Mill 517 / 2021 -- ACT, track 'S' (Submitted, non-Draft). AC4: the record is served in full with
-- editable:false. One record, units 40.0, cost 8000 -> $/Unit 200.00.
-- ================================================================================================
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9110, 2021, 517, '9', 'CTR-517', 15, 40.0, 'M3', NULL, 'A', NULL, 'BZ1', 'Submitted for review.', 3, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8484, 9110, 111, 8000, 0, 'SEED');

-- ================================================================================================
-- DECOYS that make each SQL predicate load-bearing. Both must be INVISIBLE to
-- GET /api/v1/schedule9?millId=514&year=2021.
--   9190 -- same mill, year 2020         -> pins AND REPORT_YEAR = :year
--   9191 -- same mill/year, category '4'  -> pins AND ILCR_CATEGORY_ID = '9'
-- Mill 515 / 2021 deliberately gets NO records: it has an ILCR_MILL_REPORT_STATUS row but no
-- CONTRACTUAL_WORK_REPORT rows, which for a summary-less schedule is the 200-with-empty-records
-- case (AC5), NOT a 404.
-- ================================================================================================
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9190, 2020, 514, '9', 'DECOY-YEAR', 5, 99.0, 'M3', NULL, 'A', NULL, 'BZ1', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8490, 9190, 108, 99999, 0, 'SEED');
INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (9191, 2021, 514, '4', 'DECOY-CAT', 6, 88.0, 'M3', NULL, 'A', NULL, 'BZ1', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CONTRACTUAL_WORK_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8491, 9191, 108, 88888, 0, 'SEED');
