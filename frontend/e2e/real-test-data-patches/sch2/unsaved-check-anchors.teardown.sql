-- ============================================================================
-- Teardown for sch2/unsaved-check-anchors.sql — removes EXACTLY the rows that
-- patch added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_TRACK_SCH2' AND on the two (mill,
-- year) pairs, so it cannot touch a real extract row even if a future extract
-- reused the sentinel value.
--
-- NOTE the deliberate ORDER and scope: a scenario's own cleanup removes whatever
-- IT saved on these anchors through the app's own endpoints. This file removes
-- only the report-status row underneath. If a schedule summary is still present
-- when this runs (a killed run, so scenario teardown never fired), the DELETE
-- below is blocked by nothing — the tables are not FK-linked — but the anchor
-- would be left holding a summary with no track row. `apply-patches.sh` recreates
-- the track row, and `preflight/sch2-anchors.setup.ts` fails loudly on a
-- non-empty anchor, which is the designed net for exactly that case.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_REPORT_CATEGORY
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH2'
   AND ILCR_MILL_ID = 23052
   AND REPORT_YEAR IN (2015, 2016);

DELETE FROM THE.ILCR_MILL_REPORT_STATUS
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH2'
   AND (   (ILCR_MILL_ID = 23052 AND REPORT_YEAR = 2015)
        OR (ILCR_MILL_ID = 23052 AND REPORT_YEAR = 2016));

COMMIT;
