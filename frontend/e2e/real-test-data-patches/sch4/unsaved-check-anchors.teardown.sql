-- ============================================================================
-- Teardown for sch4/unsaved-check-anchors.sql — removes EXACTLY the row that
-- patch added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_TRACK_SCH4' AND on the one (mill,
-- year) pair, so it cannot touch a real extract row even if a future extract
-- reused the sentinel value.
--
-- Scope note: the scenario's own cleanup registry deletes the location family it
-- seeded through the app's own DELETE endpoint. This file removes only the
-- report-status row underneath. After a killed run an anchor could be left
-- holding a location with no track row; `apply-patches.sh` recreates the track row
-- and `preflight/sch4-anchors.setup.ts` then fails loudly on the non-empty anchor,
-- naming it and printing the exact DELETE to run — the designed net for that case.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_REPORT_CATEGORY
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH4'
   AND ILCR_MILL_ID = 9050
   AND REPORT_YEAR = 2015;

DELETE FROM THE.ILCR_MILL_REPORT_STATUS
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH4'
   AND ILCR_MILL_ID = 9050
   AND REPORT_YEAR = 2015;

COMMIT;
