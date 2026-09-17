-- ============================================================================
-- Teardown for sch5/draft-anchors.sql — removes EXACTLY the rows that patch
-- added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_TRACK_SCH5' AND on the one (mill,
-- year) pair, so it cannot touch a real extract row even if a future extract
-- reused the sentinel value.
--
-- ORDER MATTERS: the category rows go first. CAMP_REPORT carries the composite FK
-- CMP_RPT_ILCR_RCAT_FK onto ILCR_REPORT_CATEGORY, so any camp left behind on this
-- anchor would block the category delete — which is the LOUD outcome we want, not
-- a silent orphan. See the scope note below.
--
-- Scope note: the S01 scenario's own cleanup registry deletes the camp it created
-- through the app's own DELETE endpoint. This file removes only the category and
-- report-status rows underneath. After a killed run the anchor could be left
-- holding a camp with no track row; `apply-patches.sh` recreates both and
-- `preflight/sch5-anchors.setup.ts` then fails loudly on the non-empty anchor,
-- naming it and printing the exact DELETE to run — the designed net for that case.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

-- Scoped to the sentinel AND to the years this patch touches (2016 for the original anchor, 2022-2023
-- for the fan-out), so it can never reach a real extract row.
DELETE FROM THE.ILCR_REPORT_CATEGORY
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH5'
   AND ((REPORT_YEAR = 2016 AND ILCR_MILL_ID = 9050) OR REPORT_YEAR IN (2022, 2023));

DELETE FROM THE.ILCR_MILL_REPORT_STATUS
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH5'
   AND ((REPORT_YEAR = 2016 AND ILCR_MILL_ID = 9050) OR REPORT_YEAR IN (2022, 2023));

-- The reporting years go LAST and only if nothing references them any more. Deleting a period whose
-- mill-years still exist would strand them, so the guard is a count rather than an unconditional
-- DELETE — and it keeps this teardown safe to run against a partially cleaned database.
DELETE FROM THE.ILCR_REPORTING_PERIOD p
 WHERE p.ENTRY_USERID = 'E2E_TRACK_SCH5'
   AND p.REPORT_YEAR IN (2022, 2023)
   AND NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s WHERE s.REPORT_YEAR = p.REPORT_YEAR);

COMMIT;
