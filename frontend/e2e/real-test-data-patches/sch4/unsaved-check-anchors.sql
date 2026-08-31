-- ============================================================================
-- UC-SCH4-001 — ONE dedicated Draft mill-year for the BR-12 / #359 scenario
-- ("Check Status must judge the open panel, not the last saved location").
--
-- WHY REAL DATA FELL SHORT — the same measurement as sch2's patch of the same
-- name; read that file's header for the full figures. In short (2026-08-27): 114
-- (mill, year) keys are already pinned across the six fixtures, Home only offers
-- reporting years 2015-2021, and every unclaimed openable pair in that range is
-- NON-DRAFT, which disables Check Status.
--
-- Schedule 4 is the strictest of the three: `preflight/sch4-anchors.setup.ts`
-- asserts that anchors are all distinct, empty at rest, AND "used in at most one
-- feature file". A first attempt reused 12050/2015 and preflight caught it
-- immediately — that pair is already `nav-subpage-back`, declared across four
-- lines (`at(\n MILL_987,\n 12050,\n 2015,`), which a line-based search misses.
-- The guard did its job; the anchor below is genuinely new.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_MILL_REPORT_STATUS row — the openability gate.
--      MillContextService answers 404 without it, which is why 9050/2015 404s.
--   2. Eleven THE.ILCR_REPORT_CATEGORY rows (categories '1'-'11'), which is what
--      a real reporting mill-year carries. FOUND THE HARD WAY: with the status row
--      alone the page opens fine but the first location save fails with
--      HTTP 500 `scheduleNotSavedErrorMsg`, logged as
--      `DataIntegrityViolationException` (backend log, 2026-08-27). The comparison
--      that explained it: 9050/**2018** (a working anchor) holds 11
--      ILCR_REPORT_CATEGORY rows and 9050/**2015** held none. All eleven are
--      seeded rather than just category '4', because that is the shape the extract
--      gives every reporting mill-year — seeding one category would invent a state
--      the app has never seen.
-- Nothing else is seeded, so the anchor holds NO locations at rest, which
-- preflight asserts and the scenario's own Given then builds on.
--   9050/2015 — `check-unsaved` (S33/S34). Its Given saves a location with a
--               Volume but no Cost; the scenario then supplies the Cost in the
--               open panel and re-checks WITHOUT saving (the false-RED arm), and
--               empties it again and re-checks (the false-GREEN arm). Both arms
--               share one scenario because this is the only free anchor — see
--               `features/sch4/.../check-status-unsaved.feature` for that note.
-- Mill 9050 ("760 WESTEROS") is deliberate: sch4 already owns that mill in
-- 2018-2021, so this anchor stays inside the domain that uses it.
--
-- BOTH TRACK CODES ARE SET TO 'D' so the row is an ordinary complete Draft rather
-- than a half-populated shape the app has never seen. Schedule 4 reads the
-- Schedules 1-10 code; the silviculture code is incidental here. The side effect
-- is that Schedules 2/3/11 also become openable on this pair, which is harmless:
-- ownership is declared in `fixtures/sch4/schedule4-test-data.ts`, asserted by
-- preflight, and referenced by no other fixture.
--
-- IDEMPOTENT: guarded on its own existence check, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_TRACK_SCH4'; the teardown keys on
-- it, so it can only ever remove what this file added.
-- RE-VERIFY ON RE-EXTRACT: if a future extract frees a real Draft mill-year in
-- 2015-2021 that no fixture claims, prefer it, retire this insert, and split the
-- two arms into their own scenarios as every other schedule has them.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_TRACK_SCH4';
  l_n    NUMBER;
BEGIN
  SELECT COUNT(*) INTO l_n
    FROM THE.ILCR_MILL_REPORT_STATUS
   WHERE REPORT_YEAR = 2015 AND ILCR_MILL_ID = 9050;

  IF l_n = 0 THEN
    INSERT INTO THE.ILCR_MILL_REPORT_STATUS
      (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE,
       MILL_SILVICULTUR_STATUS_CODE, REPORT_COMPLETED_IND, REVISION_COUNT,
       ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES
      (2015, 9050, 'D', 'D', 'N', 0, c_user, SYSDATE, c_user, SYSDATE);
  END IF;

  -- The eleven category rows a real reporting mill-year carries. Guarded per row, so a partially
  -- applied patch completes cleanly on a re-run. CATEGORY_STATE_CODE 'D' / REPORTABLE_DETAIL_IND 'Y'
  -- copy a real row verbatim (9050/2018 category '4').
  FOR c IN (SELECT TO_CHAR(LEVEL) cat FROM DUAL CONNECT BY LEVEL <= 11) LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_REPORT_CATEGORY
     WHERE REPORT_YEAR = 2015 AND ILCR_MILL_ID = 9050 AND ILCR_CATEGORY_ID = c.cat;

    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_REPORT_CATEGORY
        (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CATEGORY_STATE_CODE,
         REPORTABLE_DETAIL_IND, COMMENTS, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
        (2015, 9050, c.cat, 'D', 'Y', NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END LOOP;
  COMMIT;
END;
/
