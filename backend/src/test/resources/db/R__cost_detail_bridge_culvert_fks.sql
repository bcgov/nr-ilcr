-- Test-scope Flyway migration (Schedules 7A + 7B).
--
-- REPEATABLE (R__), not versioned (V…), on purpose. Flyway applies every repeatable migration AFTER
-- all pending versioned ones, which is exactly the ordering these constraints need: they must land
-- after every fixture that populates BRIDGE_REPORT_ID / CULVERT_REPORT_ID, and that set grows every
-- time a schedule adds a snapshot. As a versioned file this had to claim the highest V number on the
-- tree, which made it lose a race twice in one day — V20260814 to schedule 5's subpage fixtures, then
-- V20260815 to schedule 9's write fixtures — because a version number is a scarce shared resource and
-- "highest" is only knowable at merge time. A repeatable migration has no version to collide with and
-- stays last no matter how many V-migrations land after it. FlywayMigrationVersionUniquenessTest
-- deliberately ignores R__ files (its regex matches V-versioned names only).
--
-- Safe to re-run: the IT container is created fresh per JVM (AbstractOracleIT has no withReuse), so
-- this applies exactly once per run. Flyway re-runs a repeatable migration only when its checksum
-- changes, so editing this file against a reused container would need a clean container — the same
-- caveat every DDL fixture here already carries.
--
-- The two REAL foreign keys from the shared cost-detail table to the Schedule 7 parent report tables.
-- Verified against the delivery database (all_constraints on THE, 2026-08-13): the column pair is
-- backed by nine ENABLED constraints on THE.ILCR_COST_REPORT_DETAIL, every one DELETE_RULE = 'NO
-- ACTION'. The two that Schedules 7A/7B write through are reproduced here, delivery names included so
-- an ORA-02292 in a test names the same constraint it would name in production.
--
-- WHY THIS EXISTS: the 7A/7B snapshots (V27:18-20, V20260811:27-29) added the FK *columns* while
-- asserting in a comment that delivery carried no FK *constraint*. That claim was wrong, and it cost
-- a production defect — both services deleted the parent report row BEFORE its cost children, which
-- every Oracle IT accepted (no constraint to violate) and the real database rejected with ORA-02292,
-- surfacing as a 500 "Schedule could not be saved." on an ordinary delete. Declaring the constraints
-- here makes a parent-first delete fail the IT suite instead of the reporter's screen.
--
-- NOTE for later snapshots: with these in place, any fixture or test that writes cost-detail rows for
-- a bridge/culvert must insert the parent first and delete the children first. Every current fixture
-- and IT teardown already does (Schedule7aWriteIT:46-53, Schedule7bWriteIT:66-74).
--
-- THE FULL DELIVERY PICTURE, so the next schedule does not have to re-derive it. Of the 20 columns on
-- THE.ILCR_COST_REPORT_DETAIL, NINE carry an ENABLED FK, all DELETE_RULE = 'NO ACTION':
--
--   ILCR_REPORT_SUMMARY_ID          -> ILCR_REPORT_SUMMARY           ILCR_LCRD_ILCR_SMRY_FK
--   ILCR_REPORT_COST_ITEM_ID        -> ILCR_REPORT_COST_ITEM         ILCR_LCRD_ILCR_RCI_FK
--   TRANSPORTATION_REPORT_ID        -> TRANSPORTATION_REPORT         ILCR_LCRD_ILCR_TR_FK    (S4)
--   CAMP_REPORT_ID                  -> CAMP_REPORT                   ILCR_LCRD_CMP_RPT_FK    (S5)
--   ROAD_MAINTENANCE_REPORT_ID      -> ROAD_MAINTENANCE_REPORT        ILCR_LCRD_RM_RPT_FK     (S6)
--   BRIDGE_REPORT_ID                -> BRIDGE_REPORT                 ILCR_LCRD_BRG_RPT_FK    (7A) <-- below
--   CULVERT_REPORT_ID               -> CULVERT_REPORT                ILCR_LCRD_CLV_RPT_FK    (7B) <-- below
--   CONTRACTUAL_WORK_REPORT_ID      -> CONTRACTUAL_WORK_REPORT        ILCR_LCRD_CW_RPT_FK     (S9)
--   ROAD_CONSTRUCTION_REPRT_DTL_ID  -> ROAD_CONSTRUCTION_REPRT_DTL    ILCR_LCRD_RCR_DTL_FK    (S10)
--
-- BASIC_SILVICULTURE_REPORT_ID (S11) is the ONE per-report column with NO FK — so V20's comment is
-- correct for that column, and Schedule 11's parent-first delete is safe on schema grounds, not on
-- ordering grounds. Do not "fix" V20 to match this file.
--
-- Only 7A/7B are declared here, because this is a 7A/7B change: declaring the other seven means
-- re-verifying every fixture insert order and every IT teardown across S1-S11. Today nothing else is
-- broken — S4 deletes children first (Schedule4Repository.deleteFamily), S5 likewise
-- (Schedule5Service:668-674), S6 and S9 have no row-delete path, S10 is not built — but nothing stops
-- the next one repeating the mistake. That audit is the follow-up.
--
-- NO ON DELETE CASCADE, deliberately: the delivery constraints have none, and adding one here would
-- re-hide exactly the bug this migration exists to catch.

ALTER TABLE THE.ILCR_COST_REPORT_DETAIL
  ADD CONSTRAINT ILCR_LCRD_BRG_RPT_FK
  FOREIGN KEY (BRIDGE_REPORT_ID) REFERENCES THE.BRIDGE_REPORT (BRIDGE_REPORT_ID);

ALTER TABLE THE.ILCR_COST_REPORT_DETAIL
  ADD CONSTRAINT ILCR_LCRD_CLV_RPT_FK
  FOREIGN KEY (CULVERT_REPORT_ID) REFERENCES THE.CULVERT_REPORT (CULVERT_REPORT_ID);
