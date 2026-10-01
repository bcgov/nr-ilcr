-- ============================================================================
-- Teardown for sch4/view-mode-amounts.sql — removes EXACTLY the rows that patch
-- added and nothing else.
--
-- Double-keyed on the sentinel: ENTRY_USERID = 'E2E_SEED' AND
-- LOCATION_DESCRIPTION = 'E2E View Location' (plus the category-"4" filter), so
-- it cannot touch a real extract row even if a future extract happened to reuse
-- one of the two sentinel values. Details go first (FK child), then the reports.
--
-- Idempotent: deleting nothing is a successful no-op.
-- ============================================================================

SET DEFINE OFF

DELETE FROM THE.ILCR_COST_REPORT_DETAIL
 WHERE TRANSPORTATION_REPORT_ID IN (
       SELECT TRANSPORTATION_REPORT_ID
         FROM THE.TRANSPORTATION_REPORT
        WHERE ILCR_CATEGORY_ID = '4'
          AND LOCATION_DESCRIPTION = 'E2E View Location'
          AND ENTRY_USERID = 'E2E_SEED');

DELETE FROM THE.TRANSPORTATION_REPORT
 WHERE ILCR_CATEGORY_ID = '4'
   AND LOCATION_DESCRIPTION = 'E2E View Location'
   AND ENTRY_USERID = 'E2E_SEED';

COMMIT;
