-- Test-scope Flyway migration (Schedules 7A + 7B).
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
-- NO ON DELETE CASCADE, deliberately: the delivery constraints have none, and adding one here would
-- re-hide exactly the bug this migration exists to catch.

ALTER TABLE THE.ILCR_COST_REPORT_DETAIL
  ADD CONSTRAINT ILCR_LCRD_BRG_RPT_FK
  FOREIGN KEY (BRIDGE_REPORT_ID) REFERENCES THE.BRIDGE_REPORT (BRIDGE_REPORT_ID);

ALTER TABLE THE.ILCR_COST_REPORT_DETAIL
  ADD CONSTRAINT ILCR_LCRD_CLV_RPT_FK
  FOREIGN KEY (CULVERT_REPORT_ID) REFERENCES THE.CULVERT_REPORT (CULVERT_REPORT_ID);
