-- Structural backstop for the Schedule 2 create-on-absent duplicate-summary race (review blocking #5).
--
-- The create path (Schedule2Repository.mergeSummaryRow) treats (REPORT_YEAR, ILCR_MILL_ID,
-- ILCR_CATEGORY_ID) as the natural key of a report summary, but the REAL THE schema has NO unique
-- constraint on that triple, so a bare MERGE ... WHEN NOT MATCHED does not serialize concurrent
-- first-saves. Runtime serialization is handled application-side (a FOR UPDATE lock on the parent
-- report-status row — see Schedule2Service#requireDraft / findTrackStatusForUpdate); this index is
-- the belt-and-suspenders backstop that turns a slipped-through duplicate into an immediate ORA-00001
-- rather than a silent double row.
--
-- Test-scope only. FLAGGED FOR THE SCHEMA OWNER: add the equivalent unique index to the real THE
-- schema so prod gets the same guarantee (AD-2 keeps runtime DDL out of the app). All current
-- fixtures already satisfy uniqueness on this triple, so this applies cleanly as the last migration.
CREATE UNIQUE INDEX THE.ILCR_REPORT_SUMMARY_YMC_UX
  ON THE.ILCR_REPORT_SUMMARY (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID);
