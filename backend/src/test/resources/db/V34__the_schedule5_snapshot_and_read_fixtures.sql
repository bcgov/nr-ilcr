-- Story 7.1 (Schedule 5 read) seed EXTENSION (never edit V1-V33). Adds the THE.CAMP_REPORT table
-- (one logging camp per row, keyed by ILCR_MILL_ID + REPORT_YEAR + ILCR_CATEGORY_ID='5'), the
-- CAMP_REPORT_ID FK column on ILCR_COST_REPORT_DETAIL (absent from the V1 snapshot -> added here,
-- the same move V31 made for ROAD_MAINTENANCE_REPORT_ID), the fourteen still-unregistered
-- category-'5' cost items, and the read fixtures.
--
-- V34 is the next free number: V32 is the highest on this branch, and V33 is ALREADY TAKEN by the
-- unmerged branch chore/sched-2-4-cleanup (V33__seed_schedule2_sch3_actual_costs.sql, f088eef).
-- Re-scan before pushing; on collision bump THIS migration, never a merged one. This convention has
-- now collided four times (schedules 2, 11, 6, and nearly here) -- db/README.md:64-66 recommends the
-- V20260806__... timestamp escape hatch and it is worth taking next time.
--
-- Storage model (delivery-DB confirmed 2026-08-06, Story 7.1 Task 1, image
-- ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded:latest): a camp is one CAMP_REPORT row;
-- its TWELVE category amounts are keyed rows in the shared ILCR_COST_REPORT_DETAIL joined by
-- CAMP_REPORT_ID and discriminated by ILCR_REPORT_COST_ITEM_ID -- items 56/58/59/60/61/63-67/141/142
-- are the single-row fixed grid, 62/68 are the sub-page rows (counted by 7.1, itemized by 7.4).
-- There is NO category-'5' ILCR_REPORT_SUMMARY row (gate (ii): zero rows in delivery; summaries
-- exist only for categories 1/2/3), so Schedule 5 is summary-less like Schedules 4 and 6:
-- trackStatus comes from ILCR_MILL_REPORT_STATUS and the optimistic-lock token is per camp.
--
-- Test-scope id ranges (free gap below ILCR_REPORT_COMMON_SEQ start 9500 and
-- ILCR_COST_REPORT_DETAIL_SEQ start 9000, above Schedule 8's >=8500 and Schedule 6's 8301-8399):
-- CAMP_REPORT_ID 8401-8410, ILCR_COST_REPORT_DETAIL_ID 8411-8480. Schedule 5 owns mills 670-676;
-- none are needed yet because the shared V2 context mills cover every guard and status case.

-- The CAMP_REPORT table (present in the real THE schema). Shape is delivery-faithful
-- (ALL_TAB_COLUMNS verified 2026-08-06): CAMP_NAME and ISOLATED_CAMP_IND are NOT NULL (the latter
-- DEFAULT 'N'), DISTANCE_TO_OPERATING_AREA is NUMBER(8,2) not an open Double, COMMENTS is
-- VARCHAR2(4000) -- ABOVE the screen's maxlength=3500, so unlike Schedule 6's per-record comment
-- there is no over-cap defect to inherit here -- and REVISION_COUNT plus all four audit columns are
-- NOT NULL with NO defaults, so an insert that skips any of them fails HERE like it would in
-- delivery (the Schedule 1 lax-snapshot lesson, which let the audit-column bug ship three times).
-- Delivery also has the composite FK CMP_RPT_ILCR_RCAT_FK -> ILCR_REPORT_CATEGORY; the V1 test
-- snapshot has no such table, matching V31/V32's review-approved shape -- not added here.
CREATE TABLE THE.CAMP_REPORT (
  CAMP_REPORT_ID             NUMBER(10) PRIMARY KEY,
  REPORT_YEAR                NUMBER(4) NOT NULL,
  ILCR_MILL_ID               NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID           VARCHAR2(5) NOT NULL,
  CAMP_NAME                  VARCHAR2(30) NOT NULL,
  DISTANCE_TO_OPERATING_AREA NUMBER(8,2),
  CAMP_SIZE_CAPACITY         NUMBER(3),
  ASSOCIATED_CAMP_VOLUME     NUMBER(7),
  ISOLATED_CAMP_IND          VARCHAR2(1) DEFAULT 'N' NOT NULL,
  COMMENTS                   VARCHAR2(4000),
  REVISION_COUNT             NUMBER(5) NOT NULL,
  ENTRY_USERID               VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP            DATE NOT NULL,
  UPDATE_USERID              VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP           DATE NOT NULL
);

-- ILCR_COST_REPORT_DETAIL carries one FK per report family; V1's snapshot only has
-- ILCR_REPORT_SUMMARY_ID (V31 added the road one), so add the camp FK column here.
ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD (CAMP_REPORT_ID NUMBER(10));

-- The category-'5' cost items (legacy Constant.REPORT_COST_ITEMS :336-342; every id, name,
-- category and subcategory verified against the delivery ILCR_REPORT_COST_ITEM rows, gate (iv)).
-- Item 68 ('Other', '5', '3') is DELIBERATELY ABSENT: V31:79 already seeds it as Schedule 6's
-- non-69 decoy. Cost items are shared master data -- define once, reference, never re-INSERT; a
-- blind re-insert here would be a PK violation at migrate() time and would red every IT.
-- Item 57 ('Food') IS registered in delivery but has no legacy dispatch branch and no rows
-- anywhere, so it is seeded here purely to drive the unknown-item drop path (see camp 8401).
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (56, 'Catering and Food', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (57, 'Food', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (58, 'Wages and Benefits', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (59, 'Depreciation/lease', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (60, 'General Camp Expenses', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (61, 'Recoveries', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (62, 'Other', '5', '2', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (63, 'Crew Transport', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (64, 'Equipment and Supply Transport (Land)', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (65, 'Equipment and Supply Transport (Rail)', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (66, 'Equipment and Supply Transport (Air)', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (67, 'Equipment and Supply Transport (Water)', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (141, 'Other Camp Expenses', '5', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (142, 'Other Access Expenses', '5', '1', 'SEED');

-- ================================================================================================
-- Mill 514 / 2021 -- ACT, Draft (Schedules 1-10 track "D"; context seeded in V2). Five camps,
-- INSERTED OUT OF ID ORDER so a missing ORDER BY cannot pass: rows go in 8403, 8401, 8405, 8402,
-- 8404 and the document must serve 8401, 8402, 8403, 8404, 8405 (AC7).
--
-- 8401 Cedar Flats Camp  -- fully populated, all twelve categories + both sub-page row sets.
-- 8402 Bare Ridge Camp   -- ZERO detail rows: every category {}, every total null (NOT 0), counts 0.
--                           This is the dominant REAL shape: delivery has 61 camps and zero
--                           camp-linked detail rows (gate (v)).
-- 8403 Salvage Camp      -- ONLY Recoveries: Sub-Total null, so Camp Total is null too (legacy
--                           bigDecimalCostSubtraction returns null when the total is null,
--                           regardless of the subtrahend) and Camp-and-Access is null.
-- 8404 Overrun Camp      -- Recoveries EXCEEDS Sub-Total -> NEGATIVE Camp Total, never clamped
--                           (BR-04/S09; the 0-floor is client-side only).
-- 8405 Zero Volume Camp  -- ASSOCIATED_CAMP_VOLUME = 0 -> every costPerVolume null (no
--                           divide-by-zero) while the costs themselves still add up.
-- ================================================================================================

-- 8403 Salvage Camp (inserted first, must be served third).
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8403, 2021, 514, '5', 'Salvage Camp', 8.25, 12, 50000, 'N', NULL, 2, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8430, 8403, 61, NULL, 5000, 0, 'SEED');

-- 8401 Cedar Flats Camp (inserted second, must be served first). Volume 120000.
--   56 catering       480000 -> 4.00     58 wages     960000 -> 8.00
--   59 depreciation   120000 -> 1.00     60 general    60000 -> 0.50
--   141 other-camp volume 120000; three item-62 rows 10000 + 10000 + 4000 = 24000
--       $/m3 is PER-TERM rounded: 0.08 + 0.08 + 0.03 = 0.19, which deliberately DIFFERS from the
--       ratio-of-sums 24000/120000 = 0.20, so the test can tell the two formulas apart.
--   61 recoveries      44000 (cost only, no volume, no $/m3)
--   63 crew           180000 -> 1.50     64 land       90000 -> 0.75
--   65 rail                0 -> 0.00     66 air        12000 -> 0.10
--   67 water            6000 -> 0.05
--   142 other-access volume 120000; one item-68 row 3000 -> $/m3 0.025 -> 0.03 (HALF_UP)
--   Counts: 3 camp rows vs 1 access row -- deliberately UNEQUAL so a swapped pair is caught.
-- Derived: subTotal 1644000 -> 13.70 | total 1600000 -> 13.33 | access 291000 -> 2.43
--          campAndAccess 1891000 -> 15.76
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8401, 2021, 514, '5', 'Cedar Flats Camp', 42.50, 60, 120000, 'Y', 'Seasonal camp, spring only.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8411, 8401, 56, 120000, 480000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8412, 8401, 58, 120000, 960000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8413, 8401, 59, 120000, 120000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8414, 8401, 60, 120000, 60000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8415, 8401, 141, 120000, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8416, 8401, 62, NULL, 10000, 'Satellite kitchen', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8417, 8401, 62, NULL, 10000, 'Laundry contract', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8418, 8401, 62, NULL, 4000, 'Waste removal', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8419, 8401, 61, NULL, 44000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8420, 8401, 63, 120000, 180000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8421, 8401, 64, 120000, 90000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8422, 8401, 65, 120000, 0, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8423, 8401, 66, 120000, 12000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8424, 8401, 67, 120000, 6000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8425, 8401, 142, 120000, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8426, 8401, 68, NULL, 3000, 'Barge charter', 0, 'SEED');
-- DECOY: item 57 is REGISTERED in delivery but has no legacy dispatch branch, so the read must drop
-- it with a warning and its cost must NOT reach any total (legacy Schedule5DAO.java:283-285). Using
-- 57 rather than an invented id keeps the row insertable against delivery's
-- ILCR_LCRD_ILCR_RCI_FK -> ILCR_REPORT_COST_ITEM.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8427, 8401, 57, 999999, 999999, 0, 'SEED');
-- DECOY: a SECOND item-56 row with a HIGHER detail id. First-by-detail-id-wins (deviation (f))
-- means 8411's 480000 survives and this 777777 never reaches Sub-Total; a last-wins port fails here.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8428, 8401, 56, 777777, 777777, 0, 'SEED');

-- 8405 Zero Volume Camp (inserted third, must be served fifth). Costs add up; every $/m3 is null.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8405, 2021, 514, '5', 'Zero Volume Camp', 1.00, 5, 0, 'N', 'No volume attributed this year.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8433, 8405, 56, 0, 25000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8434, 8405, 58, 0, 15000, 0, 'SEED');

-- 8402 Bare Ridge Camp (inserted fourth, must be served second). NO detail rows at all.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8402, 2021, 514, '5', 'Bare Ridge Camp', NULL, NULL, NULL, 'N', NULL, 1, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 8404 Overrun Camp (inserted fifth, must be served fourth). Volume 10000.
--   56 catering 30000 -> 3.00 | 61 recoveries 50000
--   subTotal 30000 -> 3.00 | total 30000-50000 = -20000 -> -2.00 | access null
--   campAndAccess = addCost(-20000, null) = -20000 -> -2.00
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8404, 2021, 514, '5', 'Overrun Camp', 999999.90, 999, 10000, 'Y', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8431, 8404, 56, 10000, 30000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8432, 8404, 61, NULL, 50000, 0, 'SEED');

-- ================================================================================================
-- Mill 517 / 2021 -- ACT, track "S" (Submitted, non-Draft; context in V2). AC5: the camp list is
-- served in full with editable:false. Volume 60000, catering 30000 -> 0.50; subTotal/total/
-- campAndAccess all 30000 -> 0.50.
-- ================================================================================================
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8408, 2021, 517, '5', 'Submitted Camp', 15.00, 30, 60000, 'N', 'Submitted for review.', 3, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8435, 8408, 56, 60000, 30000, 0, 'SEED');

-- ================================================================================================
-- DECOYS that make each SQL predicate load-bearing. Every one of these must be INVISIBLE to
-- GET /api/v1/schedule5?millId=514&year=2021.
--   8406 -- same mill, year 2020        -> pins AND REPORT_YEAR = :year
--   8407 -- same mill/year, category '4' -> pins AND ILCR_CATEGORY_ID = '5'
-- Mill 515 / 2021 deliberately gets NO camps: it has an ILCR_MILL_REPORT_STATUS row but no summary,
-- which for a summary-less schedule is the 200-with-empty-camps case (AC6), NOT a 404.
-- ================================================================================================
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8406, 2020, 514, '5', 'DECOY Other Year Camp', 5.00, 10, 111111, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8436, 8406, 56, 111111, 111111, 0, 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8407, 2021, 514, '4', 'DECOY Wrong Category Camp', 6.00, 11, 222222, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8437, 8407, 56, 222222, 222222, 0, 'SEED');
