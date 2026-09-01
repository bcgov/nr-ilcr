-- ============================================================================
-- Teardown for sch11/unsaved-check-anchors.sql — removes EXACTLY the rows that
-- patch added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_TRACK_SCH11' AND on the two (mill,
-- year) pairs, so it cannot touch a real extract row even if a future extract
-- reused the sentinel value.
--
-- Scope note: a scenario's own cleanup registry removes the locations IT seeded
-- through the app's endpoints. This file removes only the report-status row
-- underneath. After a killed run (scenario teardown never fired) an anchor could
-- be left holding a location with no track row; `apply-patches.sh` recreates the
-- track row and `preflight/sch11-anchors.setup.ts` then fails loudly on the
-- non-empty anchor, naming it — which is the designed net for that case.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_REPORT_CATEGORY
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH11'
   AND ILCR_MILL_ID = 10050
   AND REPORT_YEAR IN (2015, 2016);

DELETE FROM THE.ILCR_MILL_REPORT_STATUS
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH11'
   AND (   (ILCR_MILL_ID = 10050 AND REPORT_YEAR = 2015)
        OR (ILCR_MILL_ID = 10050 AND REPORT_YEAR = 2016));

COMMIT;
