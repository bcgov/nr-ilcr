-- ============================================================================
-- UC-SCH11-001 — two dedicated Draft mill-years for the BR-12 / #359 arms
-- ("Check Status must judge what is on screen, not the last saved data").
--
-- WHY REAL DATA FELL SHORT — the same measurement as sch2's patch of the same
-- name; read that file's header for the full figures. In short (2026-08-27): 114
-- (mill, year) keys are already pinned across the six fixtures, Home only offers
-- reporting years 2015-2021, and of the four unclaimed openable pairs in that
-- range every one is NON-DRAFT, which disables Check Status.
--
-- Reusing an existing anchor was tried first and is not safe here either:
-- Schedule 11's `check-met` and `check-missing-actual` are SEEDED by their own
-- Givens (each adds a location through the API), so a second scenario on either
-- collides with S04/S05 under `fullyParallel` — observed as red tests on
-- 2026-08-27 before this patch existed.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_MILL_REPORT_STATUS row per anchor — the openability gate.
--      MillContextService answers 404 without it, which is why both pairs below
--      404 today.
--   2. Eleven THE.ILCR_REPORT_CATEGORY rows ('1'-'11') per anchor, which is what a
--      real reporting mill-year carries. FOUND THE HARD WAY on the sch4 anchor:
--      with the status row alone the page opens but the first SAVE fails with
--      HTTP 500 `scheduleNotSavedErrorMsg` / `DataIntegrityViolationException`,
--      because a working mill-year holds 11 category rows and a bare one holds
--      none. All eleven are seeded rather than just category '11' — that is the
--      shape the extract gives every reporting mill-year.
-- Nothing else is seeded, so both anchors hold NO locations at rest, which
-- `preflight/sch11-anchors.setup.ts` asserts and the scenarios' own seeding then
-- builds on.
--   10050/2015 — the false-GREEN arm (S21): its Given seeds a location carrying
--                both costs, then the scenario empties the Actual Cost in the
--                INLINE ROW EDITOR without saving the row.
--   10050/2016 — the false-RED arm (S22): its Given seeds a location with no
--                actual cost, then the scenario types one in the inline editor.
-- Mill 10050 ("2121 SESAME STREET") is deliberate: sch11 already owns that mill
-- in other years, so these anchors stay inside the domain that uses them.
--
-- WHY THE SILVICULTURE CODE MATTERS HERE SPECIFICALLY. Schedule 11's editability
-- is `editable = callerMayEdit AND silvicultureTrack == "D"` — `Schedule11Service`
-- never reads the Schedules 1-10 track (that independence is S10's whole point).
-- So MILL_SILVICULTUR_STATUS_CODE = 'D' is the load-bearing value below. The 1-10
-- code is set to 'D' as well so the row is an ordinary complete Draft rather than
-- a half-populated shape the app has never seen; the side effect is that
-- Schedules 2/3/4 also become openable on these pairs, which is harmless because
-- ownership is declared in `fixtures/sch11/schedule11-test-data.ts`, asserted by
-- preflight, and referenced by no other fixture.
--
-- IDEMPOTENT: guarded on its own existence check, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_TRACK_SCH11'; the teardown keys
-- on it, so it can only ever remove what this file added.
-- RE-VERIFY ON RE-EXTRACT: if a future extract frees a real Draft mill-year in
-- 2015-2021 that no fixture claims, prefer it and retire the matching insert.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_TRACK_SCH11';
  l_n    NUMBER;
BEGIN
  FOR a IN (
    --      mill    year   -- anchor key (fixtures/sch11/schedule11-test-data.ts)
    SELECT 10050 mill, 2015 yr FROM DUAL UNION ALL  -- check-unsaved-violation (S21)
    SELECT 10050,      2016    FROM DUAL            -- check-unsaved-fix       (S22)
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
