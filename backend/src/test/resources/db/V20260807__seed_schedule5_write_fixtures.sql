-- Story 7.2 (UC-SCH5-001 write side) seed: Schedule 5 WRITE + Check Status fixtures. TEST-SCOPE ONLY.
-- Seeds into the THE.CAMP_REPORT table and CAMP_REPORT_ID FK column that V34 created; never edits it.
--
-- ================================================================================================
-- TIMESTAMP VERSION, DELIBERATELY. The next free integer was V35 (V34 is the highest on disk, V33 is
-- held by the unmerged chore/sched-2-4-cleanup at f088eef). It is not taken, because the integer
-- convention has now collided FOUR times -- schedules 2, 11, 6, and V34's own near-miss -- and each
-- collision was caught only after CI went red on a branch that was otherwise green. Both
-- db/README.md:26-28 and V34's own header (:9-11) recommend this escape hatch; V34 said it "is worth
-- taking next time", and this is next time. Flyway orders 20260807 after 34 numerically, so this
-- still applies immediately after the V34 snapshot it depends on, and no future merge can force a
-- renumber of this file or of anyone else's.
-- ================================================================================================
--
-- Delivery-faithfulness (Task 1, seeded real-data image, 2026-08-07). Every insert supplies
-- REVISION_COUNT and both audit pairs, because all five are NOT NULL with NO defaults on both tables
-- (gate (vi): CAMP_REPORT's only constraints are its PK, the composite ILCR_REPORT_CATEGORY FK, and
-- eleven IS NOT NULL checks -- there is NOT ONE value CHECK constraint, so every range in the story's
-- VALIDATION table is enforced by the application alone and the database will not backstop one).
--
-- THREE delivery objects this local snapshot CANNOT reproduce, so no IT here can falsify them:
--   * CMP_RPT_ILCR_RCAT_FK -> ILCR_REPORT_CATEGORY (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID).
--     The V1 snapshot has no such table. Harmless: delivery carries a category row for every category
--     1-11 across all 118 mill/years and ZERO mill/years with a status row lack a category-'5' row
--     (gate (i)), so the write path must NOT create one and cannot trip the FK.
--   * ILCR_LCRD_CMP_RPT_FK is ON DELETE NO ACTION in delivery (gate (ii)), as are all nine parent FKs
--     on ILCR_COST_REPORT_DETAIL. That makes AC5's child-then-parent delete order MANDATORY, not
--     stylistic -- deleting the camp first raises ORA-02292 in delivery. The local snapshot has no
--     such FK, so ONLY code review protects that ordering here; Schedule5RepositoryWriteIT asserts
--     the order explicitly rather than relying on the FK to enforce it.
--   * The triggers ICRD_CHK_B_I_U / ILCR_CRDA_B_I_U / CAMP_RAUD_B_I_U. Gate (iii) confirmed the
--     one-parent-FK check accepts a row whose only populated parent FK is CAMP_REPORT_ID (and, in
--     fact, that the check is inert -- it catches its own RAISE_APPLICATION_ERROR and only prints
--     it). Gate (iv) confirmed CAMP_RAUD_B_I_U is BEFORE INSERT OR UPDATE and never fires on DELETE,
--     and that on a Draft mill the D/S/A/V matrix resolves to 'D' so NO audit shadow row is written
--     at all.
--
-- ID ranges. A NEW block, claimed after verifying on disk that no seed migration references any 82xx
-- value in any column: CAMP_REPORT_ID 8200-8229, ILCR_COST_REPORT_DETAIL_ID 8230-8299. Both sit below
-- the sequence starts (ILCR_REPORT_COMMON_SEQ 9500, ILCR_COST_REPORT_DETAIL_SEQ 9000) so ids the app
-- draws at runtime cannot collide with a fixture, and clear of Schedule 6's 8301-8399, Schedule 5
-- read's own 8401-8438 (V34), and Schedule 8's >= 8500. Registered in db/README.md.
--
-- Isolation model (the V21/V32 lesson): the shared container makes cleanup-free isolation
-- context-based, and a context is (mill, YEAR). Mill 670 is the write playground and carries EIGHT
-- Draft years so each destructive test method claims its own; tests that only assert REJECTIONS
-- mutate nothing and may share a year. Check-status mills are read-only by contract (the endpoint
-- mutates nothing). The authorization IT gets its OWN mill (the 8.2/12.2 lesson).

-- ================================================================================================
-- Mill 670 -- the write playground (Schedule5WriteIT). ACT + Draft 2016-2024, one destructive
-- concern per year:
--   2016  camp 8218 -> the CLEAR-target year (see its own block comment below)
--   2017  camp 8201 WITH all twelve category rows  -> the detail upsert's UPDATE-IN-PLACE branch
--   2018  camp 8202 with ZERO category rows        -> the upsert's INSERT branch ON AN EDIT. This is
--                                                     the DELIVERY-REAL shape: all 61 real camps are
--                                                     zero-detail camps (gate (vii)), so the first
--                                                     edit of a real camp takes this path 12 times.
--   2019  no camps                                 -> addCamp, incl. the same-name-in-another-YEAR
--                                                     pass (S08) against 2023's incumbent
--   2020  camp 8203 + twelve rows + an item-62 and an item-68 row -> delete removes the whole family
--   2021  camp 8204, REVISION_COUNT 3              -> the AR11 stale-vs-valid token tests
--   2022  camp 8205 + twelve rows                  -> the renamed-copy round-trip (BR-10/S03)
--   2023  camp 8206 'Duplicate Name Camp'          -> BR-02 409s and every AC6 validation 400.
--                                                     Shared deliberately: both only assert
--                                                     REJECTIONS, so neither mutates anything, and
--                                                     the per-row fingerprint of 8206 is what proves
--                                                     it.
--   2024  camp 8207                                -> the unknown-id and foreign-id 404 probes,
--                                                     which also mutate nothing
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (670, 'Sch5 Write Milling', 670, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (670, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2016, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2017, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2018, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2019, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2022, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2023, 670, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2024, 670, 'D', 'SEED');

-- 2017: the edit target, fully populated. Its twelve rows are seeded with DISTINCT costs so an
-- upsert that wrote the right value to the wrong item id would change the served grid detectably,
-- and with a category volume (96000) DIFFERENT from the camp volume (120000) so deviation (A) is
-- provable: the server must store the submitted per-category volumes verbatim and never re-derive
-- them from the camp volume.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8201, 2017, 670, '5', 'Edit Target Camp', 42.50, 60, 120000, 'Y', 'Seeded edit target.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8230, 8201,  56,  96000, 480000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8231, 8201,  58, 120000, 960000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8232, 8201,  59, 120000, 120000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8233, 8201,  60, 120000,  60000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8234, 8201, 141,  80000,   NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8235, 8201,  61,   NULL,  44000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8236, 8201,  63,  90000, 180000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8237, 8201,  64, 120000,  90000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8238, 8201,  65, 120000,  15000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8239, 8201,  66, 120000,  12000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8240, 8201,  67, 120000,   6000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8241, 8201, 142,  60000,   NULL, 0, 'SEED');

-- 2016: the CLEAR target, owned solely by the "a cleared value writes NULL and the row survives"
-- test (deviation (N)). It gets its own year because it is destructive in a way no other edit test
-- is -- it nulls every descriptor and every category -- and leaving it in 2017 alongside the three
-- other edit tests would make their expectations depend on execution order. NO detail rows: the
-- upsert creates all twelve on the way through, so what the test observes is the NULLs it wrote, not
-- values it happened to overwrite.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8218, 2016, 670, '5', 'Clear Target Camp', 55.25, 70, 65000, 'Y', 'Clear me.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2018: the zero-detail edit target. NO detail rows at all -- deliberately, because that is what a
-- real delivery camp looks like.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8202, 2018, 670, '5', 'Bare Edit Target Camp', NULL, NULL, NULL, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2020: the delete target. Twelve fixed rows PLUS one item-62 and one item-68 sub-page row, so AC5's
-- "the camp family goes together" is real: a delete that only removed the twelve fixed rows would
-- leave orphaned sub-page rows behind and this fixture catches it.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8203, 2020, 670, '5', 'Delete Target Camp', 10.00, 20, 50000, 'N', 'Seeded delete target.', 1, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8242, 8203,  56, 50000, 10000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8243, 8203,  58, 50000, 20000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8244, 8203,  59, 50000,  3000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8245, 8203,  60, 50000,  4000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8246, 8203, 141, 40000,  NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8247, 8203,  61,  NULL,  5000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8248, 8203,  63, 50000,  6000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8249, 8203,  64, 50000,  7000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8250, 8203,  65, 50000,  8000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8251, 8203,  66, 50000,  9000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8252, 8203,  67, 50000,  1000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8253, 8203, 142, 30000,  NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8254, 8203, 62, NULL, 2000, 'Sub-page camp row', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8255, 8203, 68, NULL, 2500, 'Sub-page access row', 0, 'SEED');

-- 2021: the optimistic-lock target. REVISION_COUNT 3 rather than 0, so a test that hard-codes 0 as
-- "the current token" fails instead of accidentally passing.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8204, 2021, 670, '5', 'Lock Target Camp', 5.00, 10, 25000, 'N', NULL, 3, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2022: the copy source. A renamed copy is an ordinary POST (deviation (B) -- there is no server copy
-- endpoint), so the test reads this camp, renames it, POSTs it back, and asserts the twelve category
-- values round-trip AND that the source's own sub-page rows were NOT duplicated onto the new camp
-- (legacy's copy constructor skips them, CampReportType.java:150-153). The item-62 row below is what
-- makes that second assertion falsifiable.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8205, 2022, 670, '5', 'Copy Source Camp', 33.75, 45, 90000, 'Y', 'Copy me.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8256, 8205,  56, 90000, 11000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8257, 8205,  58, 90000, 12000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8258, 8205,  59, 90000, 13000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8259, 8205,  60, 90000, 14000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8260, 8205, 141, 70000,  NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8261, 8205,  61,  NULL, 15000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8262, 8205,  63, 90000, 16000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8263, 8205,  64, 90000, 17000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8264, 8205,  65, 90000, 18000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8265, 8205,  66, 90000, 19000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8266, 8205,  67, 90000, 21000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8267, 8205, 142, 50000,  NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8272, 8205, 62, NULL, 2200, 'Source sub-page row', 0, 'SEED');

-- 2023: the BR-02 duplicate-name incumbent AND the AC6 validation target. Nothing here is ever
-- mutated -- every test in this year asserts a 409 or a 400 -- so the two concerns share it safely,
-- and the per-row fingerprint of 8206 (both audit pairs + REVISION_COUNT) is the nothing-persisted
-- proof for both.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8206, 2023, 670, '5', 'Duplicate Name Camp', 7.25, 15, 30000, 'N', 'Do not modify.', 2, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2024: the 404 probe year. The unknown-id probe uses an id no fixture holds; the FOREIGN-id probe
-- aims at mill 675's camp 8216 from here, which must 404 rather than reach across the tenancy scope
-- (deviation (M)). Neither mutates anything.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8207, 2024, 670, '5', 'Probe Camp', 1.00, 1, 1000, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- ================================================================================================
-- Mill 671 -- 1-10 track 'S' (non-Draft) -> the BR-06 write-gate 409 (AC7). One camp with two detail
-- rows so all three mutations have a real target whose untouched fingerprint is the
-- nothing-persisted proof. Check Status must still SUCCEED here: it is VIEW-gated, not Draft-gated.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (671, 'Sch5 Locked Milling', 671, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (671, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 671, 'S', 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8208, 2021, 671, '5', 'Locked Camp', 12.00, 25, 60000, 'N', 'Submitted, not editable.', 4, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8268, 8208, 56, 60000, 30000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8269, 8208, 61,  NULL,  5000, 0, 'SEED');

-- ================================================================================================
-- Mill 672 -- Check Status ALL MET (S06). Two camps, both with all four descriptors non-null.
-- 8210 additionally carries a COMPLETE item-62 and item-68 row (description AND cost both present),
-- so the four sub-list conditions are exercised in their PASSING direction -- without them, an
-- implementation that never evaluated the sub-lists at all would still report MET here.
-- Expected: outcome MET, ONE scheduleRequirementsMetMsg banner, and camps: [] -- no per-camp results
-- whatsoever (deviation (C)).
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (672, 'Sch5 Complete Milling', 672, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (672, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 672, 'D', 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8209, 2021, 672, '5', 'Complete Camp One', 20.00, 30, 70000, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8210, 2021, 672, '5', 'Complete Camp Two', 21.00, 31, 71000, 'Y', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8270, 8210, 62, NULL, 1500, 'Complete camp expense', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8271, 8210, 68, NULL, 1600, 'Complete access expense', 0, 'SEED');

-- ================================================================================================
-- Mill 673 -- Check Status ISSUES / mixed (S20). Camps in CAMP_REPORT_ID order:
--   8211 Zero Descriptor Camp  -> MET, and it proves TWO parity rules at once:
--                                 * distance 0 and volume 0 PASS -- the three numeric descriptor
--                                   tests are PURE null tests (Schedule5CheckStatus.java:18-20), the
--                                   D2 precedent. A >0 test would fail this camp.
--                                 * its item-62 row's description is a SINGLE SPACE, which also
--                                   passes: legacy compares `"".equals(description)`
--                                   (CheckStatusUtil.java:134) and does NOT trim. Using isBlank in
--                                   the port would fail this camp.
--                                 Being MET while the schedule is ISSUES, it is the camp that carries
--                                 the per-camp campRequirementsMetMsg (SUC-005).
--   8212 Bare Descriptor Camp  -> THREE lines, in emission order: Road Distance to Operating Area,
--                                 Size of Camp, Associated Camp Volume.
--   8213 '   ' (whitespace)    -> ONE line: Camp name. CAMP_NAME is NOT NULL in delivery, so a NULL
--                                 name is impossible and legacy's null branch is unreachable from
--                                 stored data -- but a whitespace-only name IS storable and the test
--                                 is TRIMMED (CoreUtil.isNullOrEmptyString(name, true) at :17), so
--                                 this is the only reachable way to exercise that condition. Its
--                                 composed line embeds the raw name verbatim.
--   8214 Sub Page Issue Camp   -> all four descriptors present, but FOUR sub-list lines: an item-62
--                                 row with a NULL description, another item-62 row with a NULL cost,
--                                 and the same pair for item 68.
--
-- NOTE recorded from this fixture: Oracle stores the empty string AS NULL, so the `"".equals(...)`
-- half of legacy's description condition is UNREACHABLE against this column -- only the null half can
-- ever fire. The single-space row on 8211 is what distinguishes the two implementations.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (673, 'Sch5 Incomplete Milling', 673, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (673, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 673, 'D', 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8211, 2021, 673, '5', 'Zero Descriptor Camp', 0, 1, 0, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8273, 8211, 62, NULL, 100, ' ', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8274, 8211, 68, NULL, 200, 'Access row', 0, 'SEED');

INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8212, 2021, 673, '5', 'Bare Descriptor Camp', NULL, NULL, NULL, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8213, 2021, 673, '5', '   ', 2.00, 2, 2000, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8214, 2021, 673, '5', 'Sub Page Issue Camp', 3.00, 3, 3000, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8275, 8214, 62, NULL,  300, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8276, 8214, 62, NULL, NULL, 'Camp row with no cost', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8277, 8214, 68, NULL,  400, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8278, 8214, 68, NULL, NULL, 'Access row with no cost', 0, 'SEED');

-- ================================================================================================
-- Mill 674 -- Check Status with ZERO camps -> vacuously MET. isSchedule5Valid ANDs over an empty
-- collection and returns true before its loop runs (Schedule5CheckStatus.java:89-97), so this must
-- answer MET with the banner and camps: [] -- not ISSUES, and not a 404.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (674, 'Sch5 Empty Milling', 674, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (674, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 674, 'D', 'SEED');

-- ================================================================================================
-- Mill 675 -- the NEIGHBOUR, which makes two separate assertions real:
--   * BR-02 is scoped per (mill, year): camp 8216 holds the SAME name as mill 670/2023's 8206, so a
--     save of that name here (and there) must succeed -- S08. Seeded with different letter case than
--     8206 on purpose, so a comparison that is case-SENSITIVE would also pass the duplicate test in
--     670 and only this fixture reveals it.
--   * AC5's untouched-neighbour proof: after deleting 670/2020's camp 8203, this camp and its two
--     detail rows must be byte-for-byte unchanged. Proving scoping by asserting the NEIGHBOUR
--     SURVIVED is the requirement -- asserting only that the target vanished cannot distinguish a
--     correctly scoped delete from one that deleted more than it should.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (675, 'Sch5 Neighbour Milling', 675, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (675, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 675, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2023, 675, 'D', 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8216, 2020, 675, '5', 'duplicate name camp', 9.50, 18, 45000, 'N', 'Neighbour, must survive.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8290, 8216, 56, 45000, 22000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID) VALUES (8291, 8216, 62, NULL, 2300, 'Neighbour sub-page row', 0, 'SEED');

-- ================================================================================================
-- Mill 676 -- owned SOLELY by Schedule5WriteAuthorizationIT (the 8.2/12.2 lesson: its writes used to
-- fire at a year another suite's lock target owned, so a @PreAuthorize regression mutated that
-- test's fixture instead of failing locally).
--
-- Track 'S' DELIBERATELY. The "authorized" proof needs authorization to PASS and then something else
-- to stop the write, or the test would have to mutate real state to show that 403 was not returned.
-- With a non-Draft track, an authorized caller gets 409 from the Draft gate -- proof that
-- @PreAuthorize let it through -- while nothing is written either way.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (676, 'Sch5 Authz Milling', 676, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (676, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 676, 'S', 'SEED');
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8217, 2021, 676, '5', 'Authz Probe Camp', 4.00, 8, 20000, 'N', 'Authz target.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8292, 8217, 56, 20000, 4000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID) VALUES (8293, 8217, 61,  NULL, 1000, 0, 'SEED');

-- Reused READ-ONLY, never mutated by any 7.2 test: mill 516/2021 (V2, CLS) for the closed-mill 409
-- context guard, and an unknown mill id for the 404 context guard. V34's read fixtures (mills 514/517,
-- camps 8401-8408) are NOT touched -- Schedule5DocumentIT fingerprints them.

COMMIT;
