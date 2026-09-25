-- ============================================================================
-- Teardown for sch6/draft-anchors.sql — removes EXACTLY the rows that patch
-- added and nothing else.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_TRACK_SCH6' AND on reporting year
-- >= 2024, so it cannot touch a real extract row even if a future extract reused
-- the sentinel value. "Year >= 2024 belongs to sch6" is the structural invariant
-- the patch rests on — every other domain pins <= 2023 — which is what makes the
-- year predicate safe as a second key here.
--
-- WIDENED FROM `= 2024` TO `>= 2024` on 2026-09-18, when S23 took the first cell
-- in 2025 because 2024's ACT mills were exhausted. Left at `= 2024` this teardown
-- would have silently stopped removing everything it created — the equality read
-- as "sch6's range" when it was really "sch6's range so far".
--
-- ORDER MATTERS: the category rows go first. ROAD_MAINTENANCE_REPORT carries the
-- composite FK RM_RPT_ILCR_RCAT_FK onto ILCR_REPORT_CATEGORY, so any road record
-- left behind on one of these anchors would block the category delete — which is
-- the LOUD outcome we want, not a silent orphan. See the scope note below.
--
-- Scope note: each scenario's own cleanup registry deletes the record it created
-- through the app's own DELETE endpoint. This file removes only the category and
-- report-status rows underneath. After a killed run an anchor could be left
-- holding a record with no track row; `apply-patches.sh` recreates both and
-- `preflight/sch6-anchors.setup.ts` then fails loudly on the non-empty anchor,
-- naming it and printing the exact DELETE to run — the designed net for that case.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_REPORT_CATEGORY
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH6'
   AND REPORT_YEAR >= 2024;

DELETE FROM THE.ILCR_MILL_REPORT_STATUS
 WHERE ENTRY_USERID = 'E2E_TRACK_SCH6'
   AND REPORT_YEAR >= 2024;

-- The reporting year goes LAST and only if nothing references it any more. Deleting a period whose
-- mill-years still exist would strand them, so the guard is a NOT EXISTS rather than an unconditional
-- DELETE — and it keeps this teardown safe to run against a partially cleaned database.
DELETE FROM THE.ILCR_REPORTING_PERIOD p
 WHERE p.ENTRY_USERID = 'E2E_TRACK_SCH6'
   AND p.REPORT_YEAR >= 2024
   AND NOT EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s WHERE s.REPORT_YEAR = p.REPORT_YEAR);

COMMIT;
