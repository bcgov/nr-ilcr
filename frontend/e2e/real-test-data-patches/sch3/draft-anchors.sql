-- ============================================================================
-- UC-SCH3-001 (Schedule 3) — the Schedule 3 summaries legacy's first Save would
-- have created, on mill-years where the schedule is REQUIRED but never started.
--
-- WHY REAL DATA FELL SHORT
-- Schedule 3 in the rewrite has NO create path. `Schedule3Service` resolves the
-- category-"3" ILCR_REPORT_SUMMARY first on EVERY operation — read, save,
-- delete, check-status, both sub-pages — and 404s "Schedule not found." when it
-- is absent (Schedule3Service.java:170, :1070). Legacy created that row on the
-- first Save (which is what `Schedule3MB.isScheduleOpen()` reports on), so the
-- extract only carries one where a reporter actually saved:
--     ILCR_REPORT_CATEGORY  rows with ILCR_CATEGORY_ID='3' : 118   (required)
--     ILCR_REPORT_SUMMARY   rows with ILCR_CATEGORY_ID='3' :  31   (started)
-- Probed 2026-08-24 through the app's own API (357 GETs, every mill x
-- 2010-2026): 28 pairs answer 200, 322 answer 404, 7 answer 409 (closed mill).
-- Of the 98 pairs carrying a Draft Schedules 1-10 track, only 15 open Schedule 3
-- — and ALL 15 are already pinned by the sch1 / sch2 fixtures:
--     17052/2016 17052/2017 12050/2017 13050/2016 13050/2017 24050/2016
--     24050/2017 25050/2017 25052/2015 25052/2016 25052/2017 25054/2016
--     22050/2016 22050/2017 22051/2017
-- Sharing one is NOT parallel-safe, and not merely as ordinary hygiene: a
-- Schedule 3 save moves numbers the sch1 and sch2 suites read (Schedule 1 pulls
-- its item-143 / item-139 costs from Schedule 3; Schedule 2 carries its
-- purchased-log volume and wood overhead from Schedule 3), and the BR-09 Crown
-- Timber push WRITES Schedule 1's own volume rows
-- (Schedule1Service.applyCrownTimberVolume). A concurrent sch1/sch2 scenario on
-- the same pair would watch its anchor move under it.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. An EMPTY category-3 ILCR_REPORT_SUMMARY on 17 Draft mill-years that
--      already carry the ILCR_REPORT_CATEGORY '3' row (i.e. the schedule IS
--      required there) but no summary. This is exactly the row legacy's first
--      Save wrote, and nothing else: LOCATION = the Override Harvest/Total PO&P
--      flag, COMMENTS null, REVISION_COUNT 0, no detail rows.
--   2. ONE category-1 (Schedule 1) summary, on the BR-09 `crown-applied` anchor
--      only — WRN-001 requires Schedule 1 to be "opened"
--      (applyCrownTimberVolume returns false without it). The `crown-not-opened`
--      anchor deliberately has none, so the same save yields WRN-002.
--   3. Stored amounts on FIVE READ-ONLY check-status / accessibility anchors,
--      so their Check Status outcome is a property of the seed rather
--      than something a scenario has to write (they are never written to):
--        22050/2020 — complete, Wages/Salaries Harvest 40,000 < PO&P 50,000,
--                     Override N -> the single BR-03 fixed-line error (S11)
--        23050/2017 — complete and BR-03-clean on every fixed line, plus ONE
--                     other-acceptable group whose Total 1,000 < PO&P 2,500,
--                     Override N -> the single BR-03 other-acceptable error
--                     (the S12 mirror)
--        22050/2021 — BOTH violations above, Override "Y" -> requirements MET,
--                     i.e. the BR-10 suppression (S12)
--        23050/2018 — complete and clean + one other-acceptable group + one
--                     included-unacceptable row -> a populated page and two
--                     populated sub-pages for the axe sweeps
--        23050/2019 — complete and clean, plus a DELIBERATELY INCOMPLETE group
--                     and unacceptable row ('M') -> the BR-11 sub-page checks
--                     (a group with no description and no PO&P; a row with no
--                     Total), which cannot be produced through the UI at all
--      "Complete" = all 11 Harvest costs, the 8 PO&P costs the check requires,
--      and both timber volumes — exactly the set `Schedule3Service.CHECK_LINES`
--      plus the two volume checks read, so nothing else can fail the check.
--
--   The row shapes mirror the app's own writes exactly (Schedule3Repository
--   upsertFixedDetailCost / upsertVolume / insertSubPageRow): cost rows carry
--   COST with VOLUME null, the two timber rows (118/119) carry VOLUME with COST
--   null, and an item-124 group is a TOT row plus a PO&P row sharing the
--   SCH3_2_{TOT,POP}_GRP<n> COMMENTS key. So the read model assembles them
--   identically to an app-created schedule.
--
-- ANCHOR CHOICE / CROSS-DOMAIN SAFETY
-- Every (mill, year) below is pinned by AT MOST the sch4 or sch11 fixtures, and
-- never by sch1 / sch2 / sec (checked pair-by-pair, not mill-by-mill). That is
-- safe in both directions and structurally, not just by convention: no backend
-- code path links Schedule 3 to Schedule 4 or Schedule 11 (`grep -rl
-- Schedule3Service backend/src/main/java` -> schedule1, schedule2, schedule5,
-- reporting only), Schedule 4 writes category-"4" TRANSPORTATION_REPORT rows and
-- Schedule 11 writes category-"11" rows, and this suite writes only category-3
-- rows plus (on ONE anchor) the patched category-1 Schedule 1.
--
-- IDEMPOTENT: every insert is guarded on its own existence check, so re-running
-- is a no-op. `steps/sch3/schedule3DbRestore.ts` still re-runs this file at
-- teardown, but since #296 only for the ONE thing the app cannot recreate: the
-- crown anchor's category-1 Schedule 1 summary (`schedule3Api.restoreAnchor`
-- calls it when `alsoRestoreSchedule1` and that summary is missing). The
-- destructive S08 delete scenario no longer needs it — an empty PUT recreates
-- the category-3 summary now.
--
-- SENTINEL: every row this file inserts carries
-- ENTRY_USERID / UPDATE_USERID = 'E2E_SEED_SCH3'. The teardown keys on it, so it
-- can only ever remove what this file added.
--
-- RE-VERIFY ON RE-EXTRACT: `preflight/sch3-anchors.setup.ts` asserts every
-- anchor (Draft, editable, the expected at-rest emptiness or the expected stored
-- amounts) and both guard responses before a browser opens. If a future extract
-- carries free Draft Schedule 3 pairs, discover real anchors and retire the
-- corresponding inserts.
--
-- PART 1 IS NOW RETIRABLE, AND DELIBERATELY NOT RETIRED. The create-on-save path
-- this header used to wish for arrived with defect #296 (2026-08-26): Save
-- creates the category-3 summary, so a scenario CAN make its own schedule and
-- DIV-1 is closed. Part 1 is kept because parts 2 and 3 depend on those
-- summaries existing first (the amounts in part 3 attach to them, and part 2's
-- Schedule 1 pairs with one), and because the read-only anchors must not be
-- written to by any scenario. Retire part 1 only together with a rework of the
-- read-only fixtures.
-- ============================================================================

SET DEFINE OFF

-- 1-2. The empty Schedule 3 summaries (+ the one Schedule 1 summary) ----------
DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_SCH3';
  l_n    NUMBER;
BEGIN
  FOR a IN (
    --      mill    year  override  schedule-1-too   -- anchor key (fixtures/sch3/schedule3-test-data.ts)
    SELECT 16050 mill, 2017 yr, 'N' ovr, 'N' sch1 FROM DUAL UNION ALL  -- happy-path
    SELECT 16050,      2019,     'N',     'N'      FROM DUAL UNION ALL  -- check-met
    SELECT 16050,      2020,     'N',     'N'      FROM DUAL UNION ALL  -- delete
    SELECT 16050,      2021,     'N',     'Y'      FROM DUAL UNION ALL  -- crown-applied
    SELECT 17052,      2018,     'N',     'N'      FROM DUAL UNION ALL  -- crown-not-opened
    SELECT 17052,      2019,     'N',     'N'      FROM DUAL UNION ALL  -- other-acceptable
    SELECT 17052,      2020,     'N',     'N'      FROM DUAL UNION ALL  -- unacceptable
    SELECT 17052,      2021,     'N',     'N'      FROM DUAL UNION ALL  -- retry
    SELECT 22050,      2018,     'N',     'N'      FROM DUAL UNION ALL  -- validate          (read-only)
    SELECT 22050,      2019,     'N',     'N'      FROM DUAL UNION ALL  -- check-empty       (read-only)
    SELECT 22050,      2020,     'N',     'N'      FROM DUAL UNION ALL  -- check-harvest-pop (read-only)
    SELECT 22050,      2021,     'Y',     'N'      FROM DUAL UNION ALL  -- check-override    (read-only)
    SELECT 23050,      2017,     'N',     'N'      FROM DUAL UNION ALL  -- check-oa-pop      (read-only)
    SELECT 23050,      2018,     'N',     'N'      FROM DUAL UNION ALL  -- a11y              (read-only)
    SELECT 23050,      2019,     'N',     'N'      FROM DUAL UNION ALL  -- check-subpage-missing (read-only)
    SELECT 25054,      2017,     'N',     'N'      FROM DUAL UNION ALL  -- row-delete-confirm (DIV-5)
    SELECT 12050,      2018,     'N',     'N'      FROM DUAL            -- stale-edit (GAP-2, optimistic lock)
  ) LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_REPORT_SUMMARY
     WHERE REPORT_YEAR = a.yr AND ILCR_MILL_ID = a.mill AND ILCR_CATEGORY_ID = '3';
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_REPORT_SUMMARY
          (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION,
           CROWN_VOLUME, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL, a.yr, a.mill, '3', a.ovr,
           NULL, NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    IF a.sch1 = 'Y' THEN
      SELECT COUNT(*) INTO l_n
        FROM THE.ILCR_REPORT_SUMMARY
       WHERE REPORT_YEAR = a.yr AND ILCR_MILL_ID = a.mill AND ILCR_CATEGORY_ID = '1';
      IF l_n = 0 THEN
        INSERT INTO THE.ILCR_REPORT_SUMMARY
            (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION,
             CROWN_VOLUME, COMMENTS, REVISION_COUNT,
             ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES
            (THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL, a.yr, a.mill, '1', NULL,
             NULL, NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
      END IF;
    END IF;
  END LOOP;
  COMMIT;
END;
/

-- 3. Stored amounts on the four read-only check-status / a11y anchors ---------
DECLARE
  c_user  CONSTANT VARCHAR2(30) := 'E2E_SEED_SCH3';
  c_desc  CONSTANT VARCHAR2(30) := 'E2E seeded group';
  c_unacc CONSTANT VARCHAR2(30) := 'E2E seeded unacceptable';
  l_summary NUMBER;
  l_n       NUMBER;

  PROCEDURE add_detail(p_summary NUMBER, p_item NUMBER, p_volume NUMBER, p_cost NUMBER,
                       p_description VARCHAR2, p_comments VARCHAR2) IS
  BEGIN
    INSERT INTO THE.ILCR_COST_REPORT_DETAIL
        (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID,
         VOLUME, COST, ITEM_DESCRIPTION, COMMENTS, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES
        (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, p_summary, p_item,
         p_volume, p_cost, p_description, p_comments, 0, c_user, SYSDATE, c_user, SYSDATE);
  END;
BEGIN
  FOR a IN (
    -- wages_h / wages_p is the ONE line that decides the BR-03 fixed-line outcome:
    -- 40,000 < 50,000 violates it; 50,000 >= 40,000 does not.
    -- `oa`/`un`: 'Y' seeds a COMPLETE sub-page row; 'M' seeds a deliberately INCOMPLETE one, for the
    -- BR-11 sub-page checks (a group with no description and no PO&P; a row with no Total).
    --      mill    year  wages_h  wages_p  other-acceptable  unacceptable
    SELECT 22050 mill, 2020 yr, 40000 wages_h, 50000 wages_p, 'N' oa, 'N' un FROM DUAL UNION ALL
    SELECT 22050,      2021,     40000,        50000,         'Y',    'N'    FROM DUAL UNION ALL
    SELECT 23050,      2017,     50000,        40000,         'Y',    'N'    FROM DUAL UNION ALL
    SELECT 23050,      2018,     50000,        40000,         'Y',    'Y'    FROM DUAL UNION ALL
    SELECT 23050,      2019,     50000,        40000,         'M',    'M'    FROM DUAL
  ) LOOP
    SELECT MAX(ILCR_REPORT_SUMMARY_ID) INTO l_summary
      FROM THE.ILCR_REPORT_SUMMARY
     WHERE REPORT_YEAR = a.yr AND ILCR_MILL_ID = a.mill AND ILCR_CATEGORY_ID = '3';

    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_COST_REPORT_DETAIL
     WHERE ILCR_REPORT_SUMMARY_ID = l_summary;

    IF l_summary IS NOT NULL AND l_n = 0 THEN
      -- The 11 fixed Harvest costs (items 27-37, legacy form order).
      add_detail(l_summary, 27,  NULL, 100000,    NULL, NULL);
      add_detail(l_summary, 28,  NULL, 20000,     NULL, NULL);
      add_detail(l_summary, 29,  NULL, 5000,      NULL, NULL);  -- Annual Rents (Harvest-only)
      add_detail(l_summary, 30,  NULL, a.wages_h, NULL, NULL);  -- Wages/Salaries — the BR-03 lever
      add_detail(l_summary, 31,  NULL, 30000,     NULL, NULL);
      add_detail(l_summary, 32,  NULL, 25000,     NULL, NULL);
      add_detail(l_summary, 33,  NULL, 15000,     NULL, NULL);  -- Scaling (PO&P derived from volumes)
      add_detail(l_summary, 34,  NULL, 12000,     NULL, NULL);
      add_detail(l_summary, 35,  NULL, 8000,      NULL, NULL);
      add_detail(l_summary, 36,  NULL, 6000,      NULL, NULL);
      add_detail(l_summary, 37,  NULL, 4000,      NULL, NULL);  -- Silviculture Admin (Harvest-only)
      -- The 8 PO&P costs the check requires (29/33/37 have none by design, BR-04).
      add_detail(l_summary, 125, NULL, 10000,     NULL, NULL);
      add_detail(l_summary, 126, NULL, 2000,      NULL, NULL);
      add_detail(l_summary, 128, NULL, a.wages_p, NULL, NULL);
      add_detail(l_summary, 129, NULL, 3000,      NULL, NULL);
      add_detail(l_summary, 130, NULL, 2500,      NULL, NULL);
      add_detail(l_summary, 132, NULL, 1200,      NULL, NULL);
      add_detail(l_summary, 133, NULL, 800,       NULL, NULL);
      add_detail(l_summary, 134, NULL, 600,       NULL, NULL);
      -- Both timber volumes (BR-11 requires them present).
      add_detail(l_summary, 118, 50000,  NULL, NULL, NULL);     -- PO&P Timber
      add_detail(l_summary, 119, 150000, NULL, NULL, NULL);     -- Crown Timber

      IF a.oa = 'Y' THEN
        -- One item-124 group: Total 1,000 < PO&P 2,500 -> the BR-03 other-acceptable violation.
        add_detail(l_summary, 124, NULL, 1000, c_desc, 'SCH3_2_TOT_GRP1');
        add_detail(l_summary, 124, NULL, 2500, c_desc, 'SCH3_2_POP_GRP1');
      ELSIF a.oa = 'M' THEN
        -- An INCOMPLETE group: no description on either row and no PO&P cost, so Check Status reports
        -- "Subtotal Other Costs (Description): Value Required" and "(PO&P $): Value Required". Neither
        -- state is reachable through the UI (the Add panel requires a description and the PO&P row is
        -- always written), so it has to be seeded — see coverage.md.
        add_detail(l_summary, 124, NULL, 1000, NULL, 'SCH3_2_TOT_GRP1');
        add_detail(l_summary, 124, NULL, NULL, NULL, 'SCH3_2_POP_GRP1');
      END IF;

      IF a.un = 'Y' THEN
        add_detail(l_summary, 38, NULL, 7500, c_unacc, NULL);
      ELSIF a.un = 'M' THEN
        -- An item-38 row with a description but NO Total -> "Included Unacceptable Costs (Total $)".
        add_detail(l_summary, 38, NULL, NULL, c_unacc, NULL);
      END IF;
    END IF;
  END LOOP;
  COMMIT;
END;
/
