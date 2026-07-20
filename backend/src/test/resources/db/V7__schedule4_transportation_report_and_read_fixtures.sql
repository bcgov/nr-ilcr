-- Story Schedule 4 (read) seed EXTENSION (never edit V1-V6). Adds the THE.TRANSPORTATION_REPORT
-- table (a location per row: LOCATION_DESCRIPTION + a single per-location DISTANCE +
-- TRANSPORTATION_CYCLE_TIME, keyed by ILCR_MILL_ID + REPORT_YEAR + ILCR_CATEGORY_ID='4'), the
-- TRANSPORTATION_REPORT_ID FK column on ILCR_COST_REPORT_DETAIL (absent from the V1 snapshot -> added
-- here), the Schedule 4 cost items, and read fixtures. V7 is the next free migration number.
--
-- STEP 0 data-model resolution (from legacy v2.0.4 Schedule4DAO.buildSchedule4Results / saveReport,
-- recorded in the spec Completion Notes): ONE TransportationReport row = ONE location; its category
-- amounts are ILCR_COST_REPORT_DETAIL rows joined by TRANSPORTATION_REPORT_ID (NOT
-- ILCR_REPORT_SUMMARY_ID). DISTANCE / TRANSPORTATION_CYCLE_TIME are per-location columns shared by
-- the distance-based categories. In-scope codes: fixed 40,41,42,44,45,49,50,51,53; distance-based 47
-- (TruckBargeFerry), 48 (CrewBargeFerry), 52 (RailHaul). Deferred sub-page codes 43,46,54,55 are
-- excluded by the repository detail query (and one is seeded below to prove it is filtered out).

-- The TRANSPORTATION_REPORT table (present in the real THE schema; test-scope shape here).
CREATE TABLE THE.TRANSPORTATION_REPORT (
  TRANSPORTATION_REPORT_ID  NUMBER(10)  PRIMARY KEY,
  REPORT_YEAR               NUMBER(10),
  ILCR_MILL_ID              NUMBER(10),
  ILCR_CATEGORY_ID          VARCHAR2(3),
  LOCATION_DESCRIPTION      VARCHAR2(120),
  DISTANCE                  NUMBER(18,4),
  TRANSPORTATION_CYCLE_TIME NUMBER(10),
  COMMENTS                  VARCHAR2(2000),
  REVISION_COUNT            NUMBER(10) DEFAULT 0,
  ENTRY_USERID              VARCHAR2(30),
  ENTRY_TIMESTAMP           TIMESTAMP DEFAULT SYSTIMESTAMP,
  UPDATE_USERID             VARCHAR2(30),
  UPDATE_TIMESTAMP          TIMESTAMP
);

-- Sequence the legacy TransportationReport generator uses (test-scope; the read never inserts).
CREATE SEQUENCE THE.ILCR_REPORT_COMMON_SEQ START WITH 9000 INCREMENT BY 1;

-- ILCR_COST_REPORT_DETAIL gained a per-report FK per report family (camp/bridge/transportation/...).
-- V1's test snapshot only carries ILCR_REPORT_SUMMARY_ID, so add the transportation FK column here.
ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD (TRANSPORTATION_REPORT_ID NUMBER(10));

-- Schedule 4 cost items (legacy Constant.REPORT_COST_ITEMS; category '4'). In-scope + one deferred
-- (43) to prove the detail query filters it out.
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (40, 'Lakeside Dry Dump', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (41, 'Water Dump', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (42, 'Water Boom', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (43, 'Towing Total', '4', '3', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (44, 'Williston Lake Dewater Only', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (45, 'Dewater and Reload', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (47, 'Truck Barge/Ferry', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (48, 'Crew Barge/Ferry', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (49, 'Hydro Dam Log Transfer', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (50, 'Truck to Truck Transfer', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (51, 'Truck to Rail Transfer', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (52, 'Rail Haul', '4', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (53, 'Low Water Bridge', '4', '1', 'SEED');

-- ================================================================================================
-- Mill 514 / 2021 — ACT, Draft (Schedules 1-10 track "D"; seeded in V2). Two locations.
--   Location A (TR 7001): fixed + distance categories, one distance-based (52 Rail Haul) with a
--     VOLUME but NULL cost (missing-category-data case). Distance 120.5 (shared by 47/48/52).
--   Location B (TR 7002): name-only, no ILCR_COST_REPORT_DETAIL rows (name-only location case).
--   A deferred code (43 Towing Total) is seeded on TR 7001 to prove the read filters it OUT.
-- Ordered by TRANSPORTATION_REPORT_ID (legacy order): A (7001) then B (7002).
-- ================================================================================================
INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, ENTRY_USERID)
  VALUES (7001, 2021, 514, '4', 'Harbour Dump', 120.5, 3, 'SEED');
-- Location A fixed categories:
--   40 Lakeside Dry Dump: VOLUME 2000 / COST 100000 -> perUnit 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7101, 7001, 40, 2000, 100000, NULL, 'SEED');
--   41 Water Dump: VOLUME 4000 / COST 60000 -> perUnit 15.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7102, 7001, 41, 4000, 60000, NULL, 'SEED');
-- Location A distance-based categories (share DISTANCE 120.5):
--   47 Truck Barge/Ferry: VOLUME 500 / COST 25000 -> perUnit 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7103, 7001, 47, 500, 25000, NULL, 'SEED');
--   52 Rail Haul: VOLUME 300 / COST NULL (missing-category-data: present values shown, perUnit null)
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7104, 7001, 52, 300, NULL, NULL, 'SEED');
-- Deferred code 43 (Towing Total) on the SAME TR row — MUST be filtered out of the read grid.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7105, 7001, 43, 999, 99999, 'Deferred towing row', 'SEED');

-- Location B: name-only, no detail rows.
INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, ENTRY_USERID)
  VALUES (7002, 2021, 514, '4', 'Empty Landing', NULL, NULL, 'SEED');

-- ================================================================================================
-- Mill 517 / 2021 — ACT, non-Draft (Schedules 1-10 track "S"; seeded in V2). One location; the read
-- must still list it with editable:false.
-- ================================================================================================
INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, ENTRY_USERID)
  VALUES (7003, 2021, 517, '4', 'Submitted Dump', 42.0, 1, 'SEED');
--   42 Water Boom: VOLUME 1000 / COST 20000 -> perUnit 20.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7301, 7003, 42, 1000, 20000, NULL, 'SEED');

-- NOTE: mill 515/2021 (ACT + Draft, seeded in V2, no schedule data) is reused for the no-locations
-- 200-empty-list path — it has no category-"4" TRANSPORTATION_REPORT rows.

COMMIT;
