-- ============================================================================
-- Teardown for sch6/view-mode-road-records.sql — removes EXACTLY the rows that
-- patch added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_SEED_SCH6_VIEW', which only that
-- file writes. Deliberately a DIFFERENT sentinel from draft-anchors.sql's
-- 'E2E_TRACK_SCH6': the two patches are torn down independently, and sharing one
-- sentinel would let this file delete the anchor's own status/category rows (or
-- let that file's teardown take these records) depending on which ran first.
--
-- ORDER MATTERS: the detail rows go first. ILCR_COST_REPORT_DETAIL carries the
-- FK onto ROAD_MAINTENANCE_REPORT, so deleting the parents first would raise —
-- and the child delete is scoped by a sub-select on the same sentinel rather
-- than by id, so it cannot strand a row if the sequence values differ between
-- machines (the patch uses NEXTVAL, so the ids are not fixed).
--
-- NOTE it does NOT delete the anchor itself. 24051/2024's report-status and
-- category rows belong to draft-anchors.sql and are removed by ITS teardown, in
-- the right order relative to this one: apply/teardown scripts iterate the
-- domain's files, and this file's rows are the children of that file's anchor.
-- Running only this teardown leaves an empty non-Draft anchor, which is a
-- coherent state — preflight then fails naming the missing records rather than
-- an ORA.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_COST_REPORT_DETAIL
 WHERE ENTRY_USERID = 'E2E_SEED_SCH6_VIEW'
   AND ROAD_MAINTENANCE_REPORT_ID IN
       (SELECT ROAD_MAINTENANCE_REPORT_ID
          FROM THE.ROAD_MAINTENANCE_REPORT
         WHERE ENTRY_USERID = 'E2E_SEED_SCH6_VIEW');

DELETE FROM THE.ROAD_MAINTENANCE_REPORT
 WHERE ENTRY_USERID = 'E2E_SEED_SCH6_VIEW';

COMMIT;
