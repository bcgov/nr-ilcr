-- ============================================================================
-- UC-SCH2-001 — two dedicated Draft mill-years for the BR-12 / #359 arms
-- ("Check Status must judge what is on screen, not the last saved schedule").
--
-- WHY REAL DATA FELL SHORT
-- These two scenarios must SEED their own state (one schedule that passes Check
-- Status, one saved-but-incomplete), so each needs a mill-year no other scenario
-- writes to. The extract has none left. Measured 2026-08-27:
--   * 114 (mill, year) keys are already pinned across the sch1/sch2/sch3/sch4/
--     sch11/sec fixtures;
--   * Home only offers reporting years 2015-2021 (`GET /api/v1/reporting-years`),
--     so an anchor outside that range cannot be selected by a scenario at all;
--   * across the 17 ACT mills x those 7 years, exactly FOUR unclaimed pairs are
--     openable — 24050/2015, 25050/2015, 25053/2015, 25054/2015 — and all four
--     are NON-DRAFT (Submitted/Verified), which DISABLES Check Status. Unusable
--     for a Check Status scenario by definition.
-- Reusing an existing check anchor was tried first and is NOT safe: sch2's
-- `check-met` and `saved-incomplete` are seeded by their own Givens, so a second
-- scenario on either one collides with S07/S08 under `fullyParallel` — observed
-- as four red tests on 2026-08-27 before this patch existed.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_MILL_REPORT_STATUS row per anchor — the openability gate.
--      MillContextService answers 404 "Schedule not found." without it, which is
--      why both pairs below 404 today.
--   2. Eleven THE.ILCR_REPORT_CATEGORY rows ('1'-'11') per anchor, which is what a
--      real reporting mill-year carries. FOUND THE HARD WAY on the sch4 anchor:
--      with the status row alone the page opens but the first SAVE fails with
--      HTTP 500 `scheduleNotSavedErrorMsg` / `DataIntegrityViolationException`,
--      because a working mill-year holds 11 category rows and a bare one holds
--      none. All eleven are seeded rather than just category '2' — that is the
--      shape the extract gives every reporting mill-year, and seeding one would
--      invent a state the app has never seen.
-- Nothing else is seeded — no summary, no details — so the anchors are EMPTY at
-- rest, which is what `preflight/sch2-anchors.setup.ts` asserts and what the
-- scenarios' own seeding then builds on.
--   23052/2015 — the false-GREEN arm (S17): its Given saves a complete schedule,
--                then the scenario empties the cost on screen without saving.
--   23052/2016 — the false-RED arm (S18): its Given saves a schedule with no
--                purchased-log cost, then the scenario supplies it on screen.
-- Mill 23052 is deliberate: sch2 already owns that mill (it pins 23052/2017 as
-- its persistence anchor), so these anchors stay inside the domain that uses them
-- rather than borrowing another suite's mill.
--
-- BOTH TRACK CODES ARE SET TO 'D'. The Schedules 1-10 code is what Schedule 2
-- reads; the silviculture code is set too so the row is a complete, ordinary
-- Draft rather than a half-populated shape the app has never seen. The side
-- effect is that Schedules 3/4/11 also become openable on these pairs — harmless,
-- because ownership is declared in `fixtures/sch2/schedule2-test-data.ts` and
-- asserted by preflight, and no other fixture references them.
--
-- IDEMPOTENT: the insert is guarded on its own existence check, so re-running is
-- a no-op.
--
-- SENTINEL: every row carries ENTRY_USERID / UPDATE_USERID = 'E2E_TRACK_SCH2'.
-- The teardown keys on it, so it can only ever remove what this file added.
--
-- RE-VERIFY ON RE-EXTRACT: if a future extract frees a real Draft mill-year in
-- 2015-2021 that no fixture claims, prefer it and retire the matching insert.
-- `preflight/sch2-anchors.setup.ts` fails the run with one clear message if these
-- anchors stop resolving.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_TRACK_SCH2';
  l_n    NUMBER;
BEGIN
  FOR a IN (
    --      mill    year   -- anchor key (fixtures/sch2/schedule2-test-data.ts)
    SELECT 23052 mill, 2015 yr FROM DUAL UNION ALL  -- check-unsaved-violation (S17)
    SELECT 23052,      2016    FROM DUAL            -- check-unsaved-fix       (S18)
  ) LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_MILL_REPORT_STATUS
     WHERE REPORT_YEAR = a.yr AND ILCR_MILL_ID = a.mill;

    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_MILL_REPORT_STATUS
        (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE,
         MILL_SILVICULTUR_STATUS_CODE, REPORT_COMPLETED_IND, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
        (a.yr, a.mill, 'D', 'D', 'N', 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    -- The eleven category rows a real reporting mill-year carries. Guarded per row, so a partially
    -- applied patch completes cleanly on a re-run. CATEGORY_STATE_CODE 'D' / REPORTABLE_DETAIL_IND 'Y'
    -- copy a real row verbatim (9050/2018 category '4').
    FOR c IN (SELECT TO_CHAR(LEVEL) cat FROM DUAL CONNECT BY LEVEL <= 11) LOOP
      SELECT COUNT(*) INTO l_n
        FROM THE.ILCR_REPORT_CATEGORY
       WHERE REPORT_YEAR = a.yr AND ILCR_MILL_ID = a.mill AND ILCR_CATEGORY_ID = c.cat;

      IF l_n = 0 THEN
        INSERT INTO THE.ILCR_REPORT_CATEGORY
          (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CATEGORY_STATE_CODE,
           REPORTABLE_DETAIL_IND, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES
          (a.yr, a.mill, c.cat, 'D', 'Y', NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
      END IF;
    END LOOP;
  END LOOP;
  COMMIT;
END;
/
