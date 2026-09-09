-- Story 16.1, follow-up to the #427 review -- the ADMIN row of the role×status editability matrix
-- on EVERY shipped schedule, not just the three it originally reached.
--
-- WHAT THIS CLOSES. R__50 seeded the positive admin arm for Schedules 5 and 11 only (mills 734-736),
-- and Schedule 9's admin case was a Submitted POST on a mill that predates it (706). So ten of the
-- twelve schedules had ADMIN proven only in the REFUSED direction (admin-at-Draft -> 409). The
-- shared component is unit-tested as a truth table, but the wiring between a schedule and the
-- component is per-schedule: each service reads its OWN status column and threads its own
-- EditableStatuses through its own write and DELETE paths. Two defects live exactly in that gap and
-- are invisible to a suite that only ever asserts 409:
--   * a schedule wired to the wrong status column -- the silviculture code on a 1-10 schedule, say --
--     fails only when a caller who SHOULD be allowed is refused;
--   * a DELETE path still holding the pre-16.1 Draft-only literal while its sibling POST/PUT moved
--     to the matrix. A refused-at-Draft probe passes either way, because Draft-only and the matrix
--     agree that an administrator may not write at 'D'.
--
-- SO EVERY MILL HERE IS 1-10 'V' AND SILVICULTURE 'D'. Same falsifiability device as R__50: the
-- status under test on ONE track and 'D' on the other, so a gate reading the wrong column sees Draft,
-- refuses the administrator, and the test fails rather than passing vacuously. ('V' rather than 'S'
-- because Verified is the cell no 1-10 schedule proved at all before this file -- 676/706 already
-- cover admin@Submitted, and the matrix row is {S, V}, so V is the untested half.)
--
-- ONE MILL PER SUITE, CLASS-OWNED. This is the 8.2/12.2 lesson R__50's header records: an admin write
-- at 'V' SUCCEEDS and MUTATES, so a mill shared with any other suite means a @PreAuthorize or gate
-- regression corrupts that suite's fixture instead of failing here. Every pre-existing non-Draft
-- fixture stays untouched and still proves SUBMITTER-at-non-Draft (409).
--
-- REPEATABLE (R__), data band, prefix 51 -- immediately after R__50 (same subject, same reasoning)
-- and before R__70, which associates the canonical submitter to every ILCR_MILL_STATUS_XREF row with
-- a set-based insert and therefore needs these mills to exist already. An ILCR_ADMIN bypasses mill
-- scope outright (MillContextService.validateMillAccess), so the association is not what the admin
-- cases need; it is what keeps each suite's SUBMITTER half reaching the editability gate rather than
-- stopping at a mill-scope 403.
--
-- MILL_ID band 737-746, continuing R__50's 734-736 (the previous high-water mark) and clear of the
-- db-e2e anchor seed's mills (13, 9050-25054):
--
--   737  Schedule 1    Schedule1WriteAuthorizationIT
--   738  Schedule 2    Schedule2WriteAuthorizationIT
--   739  Schedule 3    Schedule3WriteAuthorizationIT
--   740  Schedule 4    Schedule4WriteAuthorizationIT
--   741  Schedule 6    Schedule6WriteAuthorizationIT
--   742  Schedule 7A   Schedule7aAuthorizationIT
--   743  Schedule 7B   Schedule7bAuthorizationIT
--   744  Schedule 8    Schedule8PageWriteAuthorizationIT
--   745  Schedule 9    Schedule9WriteAuthorizationIT
--   746  Schedule 10   Schedule10WriteAuthorizationIT
--
-- Schedules 5 and 11 are absent on purpose: R__50 already owns their positive arm (734/735/736).
--
-- DELETE TARGETS. Schedules 1, 2 and 3 delete the whole document, so each gets a seeded
-- category summary to remove:
--
--   ILCR_REPORT_SUMMARY       1280   mill 737   category '1'
--   ILCR_REPORT_SUMMARY       1281   mill 738   category '2'
--   ILCR_REPORT_SUMMARY       1282   mill 739   category '3'
--
-- A seeded row rather than one the test writes for itself, and that is not a style choice: their PUT
-- is a create-on-absent upsert that bumps REVISION_COUNT, so a delete test that PUT its own row first
-- would send revisionCount 0 against the 1 the sibling write test had already left behind and get a
-- StaleRevisionException 409 -- a 409 that looks exactly like the gate refusing, on a suite whose
-- whole subject is which 409s are real. Seeded, the two arms are independent in either method order:
-- the DELETE removes a committed row, and the PUT either updates it at revision 0 or re-creates it
-- after the delete ran first.
--
-- The seven schedules whose DELETE addresses a child row by id get one row each, so the delete
-- genuinely removes something rather than exercising the idempotent no-op arm:
--
--   TRANSPORTATION_REPORT     8090   mill 740   (max in use 8082)
--   ROAD_MAINTENANCE_REPORT   8410   mill 741   (max in use 8399)
--   BRIDGE_REPORT             7660   mill 742   (max in use 7651)
--   CULVERT_REPORT            7880   mill 743   (max in use 7871)
--   TREE_TO_TRUCK_REPORT      8990   mill 744   (max in use 8978; below the 9000 seq start)
--   CONTRACTUAL_WORK_REPORT   9195   mill 745   (max in use 9191)
--   ROAD_CONSTRUCTION_REPRT   8990   mill 746   (max in use 8958; below the 9600 seq start)
--
-- Every id was verified unused across db/ AND db-e2e/, and each sits below the sequence its table
-- draws new ids from (ILCR_REPORT_COMMON_SEQ 9500, TREE_TO_TRUCK_REPORT_SEQ 9000,
-- ROAD_CONSTRUCTION_REPORT_SEQ 9600) so a seeded id can never collide with a generated one.
--
-- No cost-detail children are seeded. The subject is the gate, not child-first delete ordering, which
-- each schedule's own write IT already covers on its Draft fixtures.
--
-- Report year 2021 throughout: THE.ILCR_REPORTING_PERIOD carries it (V1/V2) and every sibling write
-- fixture writes into it.
--
-- RE-RUNNABLE. Every INSERT below is guarded -- `SELECT ... FROM DUAL WHERE NOT EXISTS (<its own PK>)`,
-- the same semantics as MERGE ... WHEN NOT MATCHED, written this way so each statement keeps the
-- explicit column list that makes it reviewable. Flyway re-applies a repeatable migration whenever its
-- checksum changes, so an edit to THIS FILE -- one more mill, a corrected comment -- re-executes it
-- against a database that already holds rows 737-746. Unguarded that is ORA-00001 at migrate time, and
-- the fixture update that caused it looks like a broken build. AbstractOracleIT makes a fresh container
-- per JVM, so the *IT suite never took that path; a deliberately reused container, and anyone pointing
-- Flyway at a longer-lived local schema, did.
--
-- The guard is per row and on the row's own primary key, so a partly-applied run (a failure midway)
-- completes on the next attempt instead of wedging. What it does NOT do is repair a row a previous run
-- MUTATED: an admin write at 'V' succeeds by design, so on a reused container a delete target the suite
-- already removed stays removed and its test fails on the next run. That is the reused-container caveat
-- every fixture here carries (README convention 1b) and idempotency does not lift it -- the guard's job
-- is to keep a checksum change from failing the migration, not to make a dirty container clean.

-- ================================================================================================
-- Mill 737 -- Schedule 1. Owned SOLELY by Schedule1WriteAuthorizationIT's admin arm.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 737, 'Sch1 Verified Correction Milling', 737, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 737);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 737, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 737);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 737, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 737);

-- The whole-document DELETE target. REVISION_COUNT 0 so the sibling admin PUT, which sends 0,
-- updates it rather than colliding on a stale revision.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  SELECT 1280, 2021, 737, '1', 'DELETE target at V.', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = 1280);

-- ================================================================================================
-- Mill 738 -- Schedule 2. Owned SOLELY by Schedule2WriteAuthorizationIT's admin arm.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 738, 'Sch2 Verified Correction Milling', 738, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 738);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 738, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 738);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 738, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 738);

-- The whole-document DELETE target. REVISION_COUNT 0 so the sibling admin PUT, which sends 0,
-- updates it rather than colliding on a stale revision.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  SELECT 1281, 2021, 738, '2', 'DELETE target at V.', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = 1281);

-- ================================================================================================
-- Mill 739 -- Schedule 3. Owned SOLELY by Schedule3WriteAuthorizationIT's admin arm.
--
-- Schedule 3's save pushes Crown Timber volume into Schedule 1 (BR-09), so this mill deliberately
-- carries NO Schedule 1 summary: the push must create what it needs, exactly as it does on any other
-- first save, and the admin correction path must not depend on Schedule 1 having been written first.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 739, 'Sch3 Verified Correction Milling', 739, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 739);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 739, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 739);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 739, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 739);

-- The whole-document DELETE target. REVISION_COUNT 0 so the sibling admin PUT, which sends 0,
-- updates it rather than colliding on a stale revision.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  SELECT 1282, 2021, 739, '3', 'DELETE target at V.', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_REPORT_SUMMARY WHERE ILCR_REPORT_SUMMARY_ID = 1282);

-- ================================================================================================
-- Mill 740 -- Schedule 4. Owned SOLELY by Schedule4WriteAuthorizationIT's admin arm.
-- Location 8090 is the DELETE target; the admin PUT creates a second location of its own, so the
-- two arms never contend for one row.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 740, 'Sch4 Verified Correction Milling', 740, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 740);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 740, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 740);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 740, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 740);

INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, REVISION_COUNT, ENTRY_USERID)
  SELECT 8090, 2021, 740, '4', 'Verified Delete Dump', NULL, NULL, 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.TRANSPORTATION_REPORT WHERE TRANSPORTATION_REPORT_ID = 8090);

-- ================================================================================================
-- Mill 741 -- Schedule 6. Owned SOLELY by Schedule6WriteAuthorizationIT's admin arm.
-- Road record 8410 is the DELETE target. TSA '01' / TSB '01B' are the same pair every Schedule 6
-- fixture uses, so no new code-table row is needed.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 741, 'Sch6 Verified Correction Milling', 741, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 741);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 741, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 741);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 741, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 741);

INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  SELECT 8410, 2021, 741, '6', '01', '01B', NULL, 'DELETE target at V.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ROAD_MAINTENANCE_REPORT WHERE ROAD_MAINTENANCE_REPORT_ID = 8410);

-- ================================================================================================
-- Mill 742 -- Schedule 7A. Owned SOLELY by Schedule7aAuthorizationIT's admin arm.
-- Bridge 7660 is the DELETE target; its code values mirror V27's 7601 so no code-table row is new.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 742, 'Sch7A Verified Correction Milling', 742, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 742);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 742, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 742);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 742, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 742);

INSERT INTO THE.BRIDGE_REPORT (BRIDGE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_NAME, BUILT_DATE, EXPECTED_BRIDGE_LIFE_SPAN, HEIGHT, LENGTH, DECK_WIDTH, DISTANCE_FROM_STORAGE, ILCR_BRIDGE_CNSTRCTN_TYPE_CODE, ILCR_BRIDGE_SUPERSTRUCTR_CODE, ILCR_DECK_CODE, ILCR_BRIDGE_ABUTMENT_TYPE_CODE, ILCR_BRIDGE_LOAD_RATING_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  SELECT 7660, 2021, 742, '7', 'Verified Delete Span', DATE '2020-06-01', 50, 5.0, 20.0, 4.0, 12, 'N', 'STL', 'WD', 'CONC', 'L100', 'DELETE target at V.', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.BRIDGE_REPORT WHERE BRIDGE_REPORT_ID = 7660);

-- ================================================================================================
-- Mill 743 -- Schedule 7B. Owned SOLELY by Schedule7bAuthorizationIT's admin arm.
-- Culvert 7880 is the DELETE target; type 'R' is V20260811's own catalogue code.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 743, 'Sch7B Verified Correction Milling', 743, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 743);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 743, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 743);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 743, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 743);

INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  SELECT 7880, 2021, 743, '7', 'R', 1200, 900, 12.5, 3, 'DELETE target at V.', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.CULVERT_REPORT WHERE CULVERT_REPORT_ID = 7880);

-- ================================================================================================
-- Mill 744 -- Schedule 8. Owned SOLELY by Schedule8PageWriteAuthorizationIT's admin arm.
-- Page 8990 is the DELETE target and carries NO samples, so the delete needs no child ordering.
-- License 'LVER' is unique to this mill, so the admin PUT creates its own page rather than
-- colliding with the delete target on the per-mill license key.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 744, 'Sch8 Verified Correction Milling', 744, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 744);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 744, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 744);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 744, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 744);

INSERT INTO THE.TREE_TO_TRUCK_REPORT (TREE_TO_TRUCK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_SUPPORT_CENTRE_CODE, ILCR_FOREST_REGION_CODE, BEC_ZONE_CODE, TSA_NUMBER, HARVEST_LICENSE_NUMBER, REVISION_COUNT, ENTRY_USERID)
  SELECT 8990, 2021, 744, '8', 'SC1', 'R1', 'BZ1', 'TSA5', 'LDEL', 0, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.TREE_TO_TRUCK_REPORT WHERE TREE_TO_TRUCK_REPORT_ID = 8990);

-- ================================================================================================
-- Mill 745 -- Schedule 9. Owned SOLELY by Schedule9WriteAuthorizationIT's admin arm.
-- Record 9195 is the DELETE target. 706 (Submitted) stays this suite's admin@S mill; this one adds
-- the Verified cell and the positive DELETE the suite had on neither status.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 745, 'Sch9 Verified Correction Milling', 745, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 745);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 745, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 745);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 745, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 745);

INSERT INTO THE.CONTRACTUAL_WORK_REPORT (CONTRACTUAL_WORK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONTRACTOR_ID, SIDE_SLOPE_PCT, PERFORMED_UNIT, ILCR_UNIT_CODE, UNIT_DESCRIPTION, ILCR_CONTRACTUAL_SOURCE_CODE, SOURCE_DESCRIPTION, BEC_ZONE_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  SELECT 9195, 2021, 745, '9', 'CTR-DEL', 10, 50.0, 'M3', NULL, 'A', NULL, 'BZ1', 'DELETE target at V.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.CONTRACTUAL_WORK_REPORT WHERE CONTRACTUAL_WORK_REPORT_ID = 9195);

-- ================================================================================================
-- Mill 746 -- Schedule 10. Owned SOLELY by Schedule10WriteAuthorizationIT's admin arm.
-- Page 8990 is the DELETE target and carries NO road details, so the delete needs no child ordering.
-- Positional VALUES, matching this table's own snapshot fixtures (V20260817) and its column order:
-- id, year, mill, category, construction date, period, division, region, TSB, TSA, TFL, revision,
-- audit quartet.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  SELECT 746, 'Sch10 Verified Correction Milling', 746, 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.MILL WHERE MILL_ID = 746);
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  SELECT 746, 'ACT', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = 746);
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  SELECT 2021, 746, 'V', 'D', 'SEED' FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS WHERE REPORT_YEAR = 2021 AND ILCR_MILL_ID = 746);

INSERT INTO THE.ROAD_CONSTRUCTION_REPRT
  SELECT 8990, 2021, 746, '10', DATE '2021-06-15', '2021-06', 'Verified Division', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE FROM DUAL
   WHERE NOT EXISTS (SELECT 1 FROM THE.ROAD_CONSTRUCTION_REPRT WHERE ROAD_CONSTRUCTION_REPRT_ID = 8990);
