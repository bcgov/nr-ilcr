-- ============================================================================
-- UC-SCH4-001 (Schedule 4) — a NON-DRAFT location that actually carries amounts.
--
-- WHY REAL DATA FELL SHORT
-- The extract loaded into the local seeded image contains 289 category-"4"
-- TRANSPORTATION_REPORT rows (68 of them visible as Schedule 4 locations) but
-- ZERO ILCR_COST_REPORT_DETAIL rows for any Schedule 4 cost item (40-55).
-- Verified 2026-08-17 against the seeded DB:
--     SELECT COUNT(*) FROM THE.TRANSPORTATION_REPORT WHERE ILCR_CATEGORY_ID='4';       -- 289
--     SELECT ILCR_REPORT_COST_ITEM_ID, COUNT(*) FROM THE.ILCR_COST_REPORT_DETAIL
--       GROUP BY ILCR_REPORT_COST_ITEM_ID;   -- no row in 40..55
-- So every seeded location renders with an empty category grid and no sub-page
-- rows. Draft scenarios don't care — they create their own state through the
-- app's own PUT/POST and delete it again. The READ-ONLY (S18 / STA-001) arm
-- cannot: the Draft gate (409 `scheduleNotEditableErrorMsg`) makes it
-- impossible to put amounts on a Submitted/Verified mill-year through the app,
-- and no non-Draft location in the extract has any. Without this patch,
-- "a stored amount renders as read-only TEXT, not an input" and the read-only
-- sub-page render are unassertable.
--
-- WHAT IT ADDS (per anchor: 3 reports + 3 details, all NEW rows — no existing
-- row is ever modified):
--   * the primary report (DISTANCE null) + its fixed category 40 detail
--     (Lakeside Dry Dump, volume 1200 / cost 3600 -> $/m3 3.00), plus COMMENTS
--   * a distance-child report (DISTANCE 50) + its category 47 detail
--     (Truck Barge/Ferry, volume 800 / cost 4000 -> $/m3 5.00)
--   * a Towing Total sub-page-row report (DISTANCE 12.5) + its item 43 detail
--     (volume 500 / cost 1500 -> $/m3 3.00, ITEM_DESCRIPTION 'Camp haul')
-- The shape mirrors the app's own writes exactly (Schedule4Repository
-- insertReportRow / insertDetailRow / insertDetailWithDescription), so the read
-- model assembles it identically to an app-created family.
--
-- ANCHORS (both re-verified by preflight/sch4-anchors.setup.ts)
--   mill 22050 / 2015 — Schedules 1-10 track "S" (Submitted)
--   mill 23050 / 2015 — Schedules 1-10 track "V" (Verified)
-- Both arms of the non-Draft mirror are patched so the read-only outline proves
-- BOTH status codes rather than one. Neither (mill, year) is pinned by any other
-- domain's fixtures, and this suite never writes to them.
--
-- IDEMPOTENT: guarded on the sentinel location name, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_SEED' *and*
-- LOCATION_DESCRIPTION = 'E2E View Location'. The teardown keys on both, so it
-- can only ever remove what this file added.
--
-- RE-VERIFY ON RE-EXTRACT: confirm 22050/2015 is still "S" and 23050/2015 still
-- "V" (preflight does), and that Schedule 4 detail rows are still absent from
-- the extract (the query above) — if a future extract DOES carry them, discover
-- a real non-Draft location with amounts and retire this patch.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_name     CONSTANT VARCHAR2(30) := 'E2E View Location';
  c_user     CONSTANT VARCHAR2(30) := 'E2E_SEED';
  c_comments CONSTANT VARCHAR2(4000) := 'Read-only sample comments (E2E_SEED).';
  l_existing NUMBER;
  l_primary  NUMBER;
  l_distance NUMBER;
  l_towing   NUMBER;
BEGIN
  FOR anchor IN (
    SELECT 22050 AS mill_id, 2015 AS report_year FROM DUAL
    UNION ALL
    SELECT 23050 AS mill_id, 2015 AS report_year FROM DUAL
  ) LOOP
    SELECT COUNT(*)
      INTO l_existing
      FROM THE.TRANSPORTATION_REPORT
     WHERE ILCR_MILL_ID = anchor.mill_id
       AND REPORT_YEAR = anchor.report_year
       AND ILCR_CATEGORY_ID = '4'
       AND LOCATION_DESCRIPTION = c_name;

    IF l_existing = 0 THEN
      -- 1. primary report (distance null) + comments, then its fixed-category detail
      l_primary := THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL;
      INSERT INTO THE.TRANSPORTATION_REPORT
          (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (l_primary, anchor.report_year, anchor.mill_id, '4',
           c_name, NULL, NULL, c_comments, 1,
           c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP,
           UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, l_primary, 40,
           1200, 3600, NULL, 0, c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);

      -- 2. distance-child report (its own DISTANCE) + its distance-category detail
      l_distance := THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL;
      INSERT INTO THE.TRANSPORTATION_REPORT
          (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (l_distance, anchor.report_year, anchor.mill_id, '4',
           c_name, 50, NULL, NULL, 0,
           c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP,
           UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, l_distance, 47,
           800, 4000, NULL, 0, c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);

      -- 3. one Towing Total sub-page row (its own report + described detail)
      l_towing := THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL;
      INSERT INTO THE.TRANSPORTATION_REPORT
          (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (l_towing, anchor.report_year, anchor.mill_id, '4',
           c_name, 12.5, NULL, NULL, 0,
           c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP,
           UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, l_towing, 43,
           500, 1500, 'Camp haul', 0, c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
    END IF;
  END LOOP;
  COMMIT;
END;
/
