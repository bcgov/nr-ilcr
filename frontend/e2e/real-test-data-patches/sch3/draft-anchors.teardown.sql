-- ============================================================================
-- Teardown for sch3/draft-anchors.sql — removes ONLY what that patch added.
--
-- Keyed on the sentinel ENTRY_USERID = 'E2E_SEED_SCH3', which every row the
-- patch inserts carries, so it can never touch an extract row. Deleted
-- child-first (details -> summaries) to respect ILCR_LCRD_ILCR_SMRY_FK.
--
-- NOTE on rows the SUITE wrote: a scenario's own saves land on these patched
-- summaries through the app, which stamps its OWN ENTRY_USERID (the mock
-- principal), so those detail rows are not sentinel-marked. Each scenario's
-- cleanup fixture already removes them; this teardown deletes every detail row
-- under a patched summary regardless of who wrote it, because the summary is
-- about to go and leaving them would orphan them (and fail the FK).
--
-- After running this, every patched (mill, year) answers 404 "Schedule not
-- found." on `GET /api/v1/schedule3` again — the state the extract shipped.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_SCH3';
BEGIN
  -- 1. every detail row under a patched summary (whoever wrote it)
  DELETE FROM THE.ILCR_COST_REPORT_DETAIL
   WHERE ILCR_REPORT_SUMMARY_ID IN (
           SELECT ILCR_REPORT_SUMMARY_ID
             FROM THE.ILCR_REPORT_SUMMARY
            WHERE ENTRY_USERID = c_user);

  -- 2. the patched summaries (the 14 category-"3" rows + the one category-"1")
  DELETE FROM THE.ILCR_REPORT_SUMMARY
   WHERE ENTRY_USERID = c_user;

  COMMIT;
END;
/
