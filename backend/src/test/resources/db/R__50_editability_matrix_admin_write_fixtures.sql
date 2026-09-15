-- Story 16.1 -- the positive arm of the role×status editability matrix.
--
-- Every write-gate fixture that existed before this story rests on one assumption: "a non-Draft
-- write mutates nothing". It is stated outright in Schedule11WriteAuthorizationIT:30-32 -- the
-- authorized proof POSTs to 615/'S' so "the service's Draft gate rejects it 409 WITHOUT mutating
-- anything -- no fixture churn". Under the matrix an ADMIN write at 'S' or 'V' SUCCEEDS and mutates
-- seeded data, so the admin-write arm needs mills of its own. Every pre-existing non-Draft fixture
-- (623, 583, 593, 725, 517, 615, 671, 705, ...) is deliberately left refusing: it now proves
-- SUBMITTER-at-non-Draft, which is still a 409, and each is read or asserted by a sibling suite.
--
-- REPEATABLE (R__), NOT a versioned migration. The story's Task 6 says to claim "the next free
-- V<n>"; that instruction predates the 2026-08-20 Flyway fixture decision
-- (docs/decisions/flyway-test-fixture-strategy.md), which moved seed data out of the versioned
-- namespace entirely. FlywayMigrationConventionTest.newVersionedMigrationsCarryNoSeedData now FAILS
-- the build on INSERTs in a new V__ file, and adding this file to grandfathered-seeded-versions.txt
-- to get around that is the escape hatch the convention exists to close. So: R__, data band.
--
-- Prefix 50 puts this in the 10-80 data band and, deliberately, BEFORE R__70 -- that file associates
-- the canonical submitter to every ILCR_MILL_STATUS_XREF row with a set-based insert, so these mills
-- must already exist when it runs. (An ILCR_ADMIN bypasses mill scope outright --
-- MillContextService.validateMillAccess:81-83 returns early on the concrete admin role -- so the
-- association is not what these tests need. It is seeded anyway, for free, and keeps the submitter
-- arm of each test reaching the editability gate rather than a mill-scope 403.)
--
-- MILL_ID band 734-736. The README's range table stops at 733 (R__40's Mill Information fixtures),
-- which is the real high-water mark; 727-733 are taken by schedule 10 write / schedule 6 correction
-- / R__40. Clear of the db-e2e anchor seed's mills (13, 9050-25054).
--
-- CROSS-TRACK FALSIFIABILITY -- the point of the status pairs below. Each mill carries the status
-- under test on ONE track and 'D' on the other, so a gate that reads the wrong column gets Draft,
-- refuses the administrator, and the test fails. Legacy duplicated disableUserInput() verbatim per
-- track and changed only the getter it read, which is exactly the mistake worth catching:
--
--   734  1-10 'V' + silviculture 'D'   admin corrects a VERIFIED report on the 1-10 track
--   735  1-10 'D' + silviculture 'S'   admin corrects a SUBMITTED report on the silviculture track
--   736  1-10 'D' + silviculture 'V'   admin corrects a VERIFIED report on the silviculture track
--
-- Mill 734 is Schedule 5's; 735 and 736 are Schedule 11's. The 1-10 track's admin@Submitted case
-- already has fixtures that predate this file -- mills 676 (Schedule 5) and 706 (Schedule 9), both
-- class-owned -- so it is not repeated here.
--
-- Report year 2021 throughout: THE.ILCR_REPORTING_PERIOD carries it (V2) and every sibling write
-- fixture uses it.
--
-- PK ranges, each verified unused across every migration in this directory and below the
-- ILCR_REPORT_COMMON_SEQ start (9500) that both write paths draw new ids from:
--   CAMP_REPORT_ID                8250-8251  (clear of V20260807's reserved 8200-8229, V34's
--                                             8401-8410 and V20260814's 8700-8719)
--   BASIC_SILVICULTURE_REPORT_ID  9401-9402  (clear of V21's 92xx, V20260816's 93xx and the
--                                             db-e2e anchor seed's 9351-9399)
-- No cost-detail children are seeded: the subject here is the gate, not the child-first delete
-- ordering, which Story 7.2's Draft fixtures already exercise.

-- ================================================================================================
-- Mill 734 -- 1-10 track VERIFIED. Owned SOLELY by Schedule5WriteAuthorizationIT's admin arm.
-- Silviculture 'D' so a gate reading the wrong track's column refuses and the test fails.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (734, 'Sch5 Verified Correction Milling', 734, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (734, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 734, 'V', 'D', 'SEED');

-- Two camps, so the PUT arm and the DELETE arm each own one and the suite stays order-independent.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8250, 2021, 734, '5', 'Verified Update Camp', 6.00, 12, 30000, 'N', 'PUT target at V.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8251, 2021, 734, '5', 'Verified Delete Camp', 3.00, 5, 10000, 'N', 'DELETE target at V.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- ================================================================================================
-- Mill 735 -- silviculture track SUBMITTED. Owned SOLELY by Schedule11WriteAuthorizationIT's admin
-- arm. 1-10 'D' -- the inverse of mill 615, and the pair that makes the track split falsifiable.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (735, 'Silviculture Submitted Correction Milling', 735, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (735, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 735, 'D', 'S', 'SEED');

-- Biogeo 8801 exists (V28's catalogue). Location text differs from every other fixture's so the
-- per-mill/year duplicate-location rule cannot be tripped from a sibling suite's rows.
INSERT INTO THE.BASIC_SILVICULTURE_REPORT (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (9401, 2021, 735, '11', 'Submitted Correction Block', 8801, 40, 'N', NULL, 0, 'SEED');

-- ================================================================================================
-- Mill 736 -- silviculture track VERIFIED. Owned SOLELY by Schedule11WriteAuthorizationIT's admin
-- arm. Completes the positive arm: both tracks × both administrator-editable statuses.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (736, 'Silviculture Verified Correction Milling', 736, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (736, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 736, 'D', 'V', 'SEED');

INSERT INTO THE.BASIC_SILVICULTURE_REPORT (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (9402, 2021, 736, '11', 'Verified Correction Block', 8801, 25, 'N', NULL, 0, 'SEED');
