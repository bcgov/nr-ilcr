-- Story 8.1 (Schedule 6 read) seed EXTENSION (never edit V1-V27). Adds the THE.ROAD_MAINTENANCE_REPORT
-- table (one road-maintenance record per row: the TSA/TSB or TFL classification stored as codes, the
-- schedule-level general comment in COMMENTS, own REVISION_COUNT, keyed by ILCR_MILL_ID + REPORT_YEAR
-- + ILCR_CATEGORY_ID='6'), the ROAD_MAINTENANCE_REPORT_ID FK column on ILCR_COST_REPORT_DETAIL (absent
-- from the V1 snapshot -> added here), the Schedule 6 cost item (69), and read fixtures. V31 is the
-- next free migration number: V27 was the highest when this branch opened, but main has since taken
-- V28/V29 (schedule-11 biogeo) and V30 (ilcr_mill_user_profile_xref), so Schedule 6 yields again and
-- takes V31 here + V32 for the write fixtures (the "bump your own migration" rule). Renumbered from
-- V30/V31 at PR review 2026-08-05: main's V30 collided, which reds every IT at migrate() time and is
-- exactly what FlywayMigrationVersionUniquenessTest exists to catch on the merged tree.
--
-- Storage model (delivery-DB confirmed 2026-08-04, Story 8.1 Task 1): a road record is one
-- ROAD_MAINTENANCE_REPORT row; its cost/volume/per-record comment is the single ILCR_COST_REPORT_DETAIL
-- row for cost item 69 (Schedule6_1_Cost), joined by ROAD_MAINTENANCE_REPORT_ID. There is NO
-- category-'6' ILCR_REPORT_SUMMARY row (summary-less, like Schedule 4), so trackStatus comes from
-- ILCR_MILL_REPORT_STATUS and the general comment lives replicated on every ROAD_MAINTENANCE_REPORT
-- row (legacy data-model quirk; the read takes the last row's COMMENTS). A row whose TSA/TSB/TFL are
-- all blank is a general-comment placeholder (S18): excluded from roadRecords, but supplies the
-- generalComments. RMG (BR-04) and $/m3 (BR-04/BR-07) are derived server-side, never stored.
--
-- Test-scope id ranges (free gap between Schedule 4 write fixtures <=8182 and Schedule 8 >=8500, both
-- below ILCR_COST_REPORT_DETAIL_SEQ start 9000 and ILCR_REPORT_COMMON_SEQ start 9500):
-- ROAD_MAINTENANCE_REPORT_ID 8301-8399, ILCR_COST_REPORT_DETAIL_ID 8305-8399.

-- The ROAD_MAINTENANCE_REPORT table (present in the real THE schema). Shape is delivery-faithful
-- (ALL_TAB_COLUMNS verified 2026-08-04, code-review tightening): REPORT_YEAR NUMBER(4),
-- REVISION_COUNT NUMBER(5), audit columns DATE and NOT NULL with NO column defaults (the real
-- RMR_AUD_B_I_U trigger only feeds the _AUD shadow) — so an insert that skips REVISION_COUNT or the
-- UPDATE_* audit pair fails HERE like it would in delivery (the Schedule 1 lax-snapshot lesson).
CREATE TABLE THE.ROAD_MAINTENANCE_REPORT (
  ROAD_MAINTENANCE_REPORT_ID NUMBER(10) PRIMARY KEY,
  REPORT_YEAR                NUMBER(4) NOT NULL,
  ILCR_MILL_ID               NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID           VARCHAR2(5) NOT NULL,
  TSA_NUMBER                 VARCHAR2(2),
  TSB_NUMBER_CODE            VARCHAR2(3),
  TFL_NUMBER_CODE            VARCHAR2(2),
  COMMENTS                   VARCHAR2(4000),
  REVISION_COUNT             NUMBER(5) NOT NULL,
  ENTRY_USERID               VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP            DATE NOT NULL,
  UPDATE_USERID              VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP           DATE NOT NULL
);

-- ILCR_COST_REPORT_DETAIL carries a per-report FK per report family (summary/camp/transportation/road
-- maintenance/...). V1's test snapshot only carries ILCR_REPORT_SUMMARY_ID, so add the road FK here.
ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD (ROAD_MAINTENANCE_REPORT_ID NUMBER(10));

-- Schedule 6 cost item (legacy Constant.REPORT_COST_ITEMS.Schedule6_1_Cost = 69; real row verified:
-- ITEM_NAME 'Cost', category '6', subcategory '1').
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (69, 'Cost', '6', '1', 'SEED');

-- ================================================================================================
-- Mill 514 / 2021 — ACT, Draft (Schedules 1-10 track "D"; seeded in V2). Two road records sharing the
-- same replicated general comment. Ordered by ROAD_MAINTENANCE_REPORT_ID.
--   RMR 8301 — TSA "01" + Supply Block "01B" -> RMG "15"; VOLUME 1000 / COST 50000 -> $/m3 50.00.
--   RMR 8302 — TFL "18" -> RMG "4";                       VOLUME 400  / COST 30000 -> $/m3 75.00.
-- Totals: volume 1400, cost 80000, cost/volume 80000/1400 = 57.142857 -> 57.14 (scale 2 HALF_UP).
-- ================================================================================================
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8301, 2021, 514, '6', '01', '01B', NULL, 'General road maintenance comment for 2021.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8311, 8301, 69, 1000, 50000, 'Arrow FSR resurfacing', 'SEED');

INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8302, 2021, 514, '6', NULL, NULL, '18', 'General road maintenance comment for 2021.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8312, 8302, 69, 400, 30000, 'TFL 18 spur road', 'SEED');

-- ------------------------------------------------------------------------------------------------
-- DECOY rows (code-review 2026-08-04): each exists ONLY so that deleting one of the read query's
-- filter predicates changes the 514/2021 document and fails the pinned assertions.
--   RMR 8305 — same mill, year 2020            -> pins AND REPORT_YEAR = :year.
--   RMR 8306 — same mill/year, category '4'    -> pins AND ILCR_CATEGORY_ID = '6'.
--   Detail 8305 — item 68 on RMR 8301, id BELOW 8311 so first-by-id would surface its bogus
--   volume/cost if the filter dropped            -> pins AND d.ILCR_REPORT_COST_ITEM_ID = 69.
-- ------------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (68, 'Other', '5', '3', 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8305, 8301, 68, 999999, 999999, 'DECOY non-69 item', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8305, 2020, 514, '6', '05', '05A', NULL, 'DECOY other-year comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8306, 8305, 69, 111111, 111111, 'DECOY other-year detail', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8306, 2021, 514, '4', '07', '07A', NULL, 'DECOY other-category comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8307, 8306, 69, 222222, 222222, 'DECOY other-category detail', 'SEED');

-- ================================================================================================
-- Mill 517 / 2021 — ACT, non-Draft (Schedules 1-10 track "S"; seeded in V2). One record; the read must
-- still list it with editable:false.
--   RMR 8303 — TSA "03" + Supply Block "03B" -> RMG "1"; VOLUME 2000 / COST 40000 -> $/m3 20.00.
-- ================================================================================================
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8303, 2021, 517, '6', '03', '03B', NULL, 'Submitted road comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8313, 8303, 69, 2000, 40000, 'Bulkley haul road', 'SEED');

-- ================================================================================================
-- Mill 660 / 2021 — dedicated ACT, Draft context for the lone-comment (S18) state: a single
-- ROAD_MAINTENANCE_REPORT placeholder row (no TSA/TSB/TFL) carrying only the general comment, with NO
-- cost detail. Read => roadRecords: [], zero totals, generalComments populated.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (660, 'Sch6 Comment-Only Milling', 660, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (660, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 660, 'D', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8304, 2021, 660, '6', NULL, NULL, NULL, 'Only a general comment, no road records yet.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- NOTE: mill 515/2021 (ACT + Draft, seeded in V2, no schedule data) is reused for the no-records
-- 200-empty path — it has no category-"6" ROAD_MAINTENANCE_REPORT rows. Mill 516/2021 (CLS) is reused
-- for the 409 guard; an unknown mill (no ILCR_MILL_REPORT_STATUS row) is the 404 guard.

COMMIT;
