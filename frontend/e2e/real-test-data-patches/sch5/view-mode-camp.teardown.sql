-- ============================================================================
-- Teardown for sch5/view-mode-camp.sql — removes EXACTLY the camp that patch
-- added, and its twelve cost-detail rows, and nothing else.
--
-- Keyed on BOTH sentinels (ENTRY_USERID = 'E2E_SEED' AND CAMP_NAME =
-- 'E2E View Camp') AND on the one (mill, year) pair, so it cannot reach a real
-- extract row even if a future extract reused either value on its own.
--
-- ORDER MATTERS, TWICE OVER:
--   * WITHIN this file: the detail rows go first. They carry the CAMP_REPORT_ID
--     FK, so deleting the parent first would fail.
--   * ACROSS files: this must run BEFORE draft-anchors.teardown.sql, which
--     deletes the ILCR_REPORT_CATEGORY rows that CAMP_REPORT's composite FK
--     CMP_RPT_ILCR_RCAT_FK depends on. `teardown-patches.sh` iterates the
--     discovered teardowns in REVERSE glob order (:68) and glob order is
--     alphabetical, so "view-mode-camp" runs before "draft-anchors" — the same
--     way sch4's view-mode-amounts teardown precedes its own anchor teardown.
--     Renaming either file without checking that ordering would break it.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

-- The twelve fixed-grid detail rows, reached through their parent camp so the sentinel scoping on
-- CAMP_REPORT governs both deletes and this one cannot widen on its own.
DELETE FROM THE.ILCR_COST_REPORT_DETAIL d
 WHERE d.ENTRY_USERID = 'E2E_SEED'
   AND EXISTS (
     SELECT 1
       FROM THE.CAMP_REPORT c
      WHERE c.CAMP_REPORT_ID = d.CAMP_REPORT_ID
        AND c.ILCR_MILL_ID = 16050
        AND c.REPORT_YEAR = 2023
        AND c.ILCR_CATEGORY_ID = '5'
        AND c.CAMP_NAME = 'E2E View Camp'
        AND c.ENTRY_USERID = 'E2E_SEED');

DELETE FROM THE.CAMP_REPORT
 WHERE ILCR_MILL_ID = 16050
   AND REPORT_YEAR = 2023
   AND ILCR_CATEGORY_ID = '5'
   AND CAMP_NAME = 'E2E View Camp'
   AND ENTRY_USERID = 'E2E_SEED';

COMMIT;
