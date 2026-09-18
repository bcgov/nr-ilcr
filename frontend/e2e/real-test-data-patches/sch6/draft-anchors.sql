-- ============================================================================
-- UC-SCH6-001 (Schedule 6 — Report Road Management Costs) — the mutating anchors,
-- minted by opening reporting year 2024.
--
-- WHY REAL DATA FELL SHORT: THERE IS NOTHING LEFT.
-- Like Schedule 5, Schedule 6 needs no summary row of its own — a valid ACTIVE
-- mill-year holding no road records is the legitimate empty state and answers
-- 200 `roadRecords: []`, never a 404 (Schedule6Api.java:37 — "zero road records
-- is a valid 200"). So the problem is anchor EXCLUSIVITY, and by now it is total.
--
-- Measured 2026-09-17 against the seeded image and the app's own API, using the
-- suite's OWN scanner (preflight/anchor-keys.ts collectAnchorKeys, not a fresh
-- regex — re-deriving those patterns is the VER-8 dead-guard class):
--     ILCR_MILL_REPORT_STATUS rows                    : 153
--     opened reporting years                          : 2015-2023
--     (mill, year) keys pinned by sch1/sch2/sch3/sch4/
--       sch5/sch11/sec                                : 151
--       (sch4 60, sch5 32, sch11 20, sch2 22, sch3 22, sch1 19, sec 5)
--     Draft cells in the DB                           : 136
--     Draft + pinned by NOBODY                        :   2
--       -> 1/2017, 14050/2018
--     ...of those, usable                             :   0
--       both are CLS mills and answer HTTP 409 (verified through
--       GET /api/v1/schedule6; 13050/2017 answers 200 editable with
--       roadRecords: [], so the probe itself is sound).
-- Every one of the ten Draft cells that HOLDS road records is pinned by another
-- domain (eight of them by sch1), so none can be borrowed for a read scenario
-- either. Schedule 6 therefore had to MINT capacity, exactly as the FAN-OUT NOTE
-- in sch5/draft-anchors.sql anticipated.
--
-- WHY REPORTING YEAR 2024, following that note's reasoning verbatim:
--   * THE.ILCR_REPORTING_PERIOD is purely ADDITIVE — no existing row is touched.
--   * It cannot shift any existing test's starting state: the app has NO default
--     working context (MillYearProvider stopped falling back to
--     DEFAULT_MILL_ID / DEFAULT_YEAR in commit e37649b), so Home lands on its
--     "Select Mill" / "Select Reporting Year" placeholders every time.
--   * Nothing asserts the year LIST. The only step that inspects it
--     (steps/sec/working-context.steps.ts:24) asserts a KNOWN option is present,
--     not a count.
--   * Every (mill, year) any fixture pins today is <= 2023 — sch5 took 2022-2023.
--     So "year >= 2024 belongs to sch6" is a STRUCTURAL invariant rather than a
--     convention someone has to remember, and the cross-domain guard cannot be
--     violated by accident.
-- Rejected, for the same reasons sch5 rejected them: opening the CLS mills (1,
-- 13, 14050, 25051) would flip ILCR_MILL_STATUS_XREF and silently redden the
-- HTTP-409 guard anchors sch2/sch4/sch5 pin on exactly those mills.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_REPORTING_PERIOD row for 2024.
--   2. One THE.ILCR_MILL_REPORT_STATUS row per anchor below — the openability
--      gate. Without it MillContextService answers 404 (ERR-003).
--   3. Eleven THE.ILCR_REPORT_CATEGORY rows (categories '1'-'11') per anchor.
--      NOT OPTIONAL, and verified rather than assumed for THIS table:
--      ROAD_MAINTENANCE_REPORT carries the composite FK RM_RPT_ILCR_RCAT_FK ->
--      ILCR_RCAT_PK, confirmed ENABLED in all_constraints on 2026-09-17. With
--      the status row alone the page opens but the first record save fails
--      DataIntegrityViolationException — the same way sch4 found it on 9050/2015
--      and sch5 on 9050/2016. All eleven are seeded rather than just category
--      '6', because that is the shape the extract gives every reporting
--      mill-year; seeding one category would invent a state the app has never
--      seen.
-- Nothing else is seeded, so each anchor holds NO road records at rest, which
-- preflight asserts and the S01 scenario's own Given then builds on.
--
-- BOTH TRACK CODES ARE SET TO 'D' so the row is an ordinary complete Draft
-- rather than a half-populated shape the app has never seen. Schedule 6 reads
-- the Schedules 1-10 code (there is no category-'6' ILCR_REPORT_SUMMARY row, so
-- trackStatus comes straight from ILCR_MILL_REPORT_STATUS —
-- Schedule6Repository.java:24-25); the silviculture code is incidental here.
-- The side effect is that Schedules 1-5 and 7-11 also become openable on these
-- pairs, which is harmless: ownership is declared in
-- fixtures/sch6/schedule6-test-data.ts, asserted by preflight, and referenced by
-- no other fixture.
--
-- ANCHOR OWNERSHIP
--   9050/2024 — `add` (S01, "Add a Road Maintenance Record by TSA and Supply
--               Block"). Mutating: the scenario creates a record and deletes it
--               again through the app's own DELETE endpoint
--               (DELETE /api/v1/schedule6/records/{recordId}).
-- Mill 9050 ("760 WESTEROS") is deliberate and mirrors sch5's own first choice:
-- sch4 owns that mill in 2015 and 2018-2021, sch1 in 2017 and sch5 in 2016/2022/
-- 2023, so the YEAR is new but the mill is one the suite already exercises.
-- Anchors are (mill, year) PAIRS — the cross-domain guard compares pairs, not
-- mills — so this collides with nothing.
--
-- CI PARITY IS HALF OF THIS CHANGE, NOT A FOLLOW-UP. CI has no sqlplus step, so
-- a patch that is not folded into backend/src/test/resources/db-e2e/
-- R__80_e2e_anchor_seed.sql does not exist there. Story 28.4's CI red was a
-- COLUMN-completeness miss in exactly that mirror (an omitted NOT NULL trio on
-- CAMP_REPORT), and the parity gate compares anchors, not columns — so mirror
-- column-for-column. For reference, ROAD_MAINTENANCE_REPORT's NOT NULL set is:
-- ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
-- REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,
-- UPDATE_TIMESTAMP (all_tab_columns, 2026-09-17). Nothing in THIS file inserts a
-- road record, so that list is a warning for whoever seeds one later.
--
-- IDEMPOTENT: guarded on its own existence check, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_TRACK_SCH6'; the teardown keys on
-- it, so it can only ever remove what this file added.
-- RE-VERIFY ON RE-EXTRACT: if a future extract frees a real Draft mill-year that
-- no fixture claims, prefer it and retire the corresponding insert.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_TRACK_SCH6';
  l_n    NUMBER;

  TYPE t_anchor  IS RECORD (mill NUMBER, yr NUMBER, code VARCHAR2(1));
  TYPE t_anchors IS TABLE OF t_anchor;

  -- Every (mill, year) sch6 owns, with its Schedules 1-10 track code. Order matches the anchor table
  -- in fixtures/sch6/schedule6-test-data.ts; the two files are transcribed from each other and the
  -- CI-seed parity gate fails the run if they disagree with db-e2e/R__80_e2e_anchor_seed.sql.
  l_anchors t_anchors := t_anchors(
    t_anchor(9050, 2024, 'D')   -- S01 add (TSA + Supply Block happy path)
  );
BEGIN
  -- The new reporting year. Additive: 2015-2023 already exist and are untouched.
  SELECT COUNT(*) INTO l_n FROM THE.ILCR_REPORTING_PERIOD WHERE REPORT_YEAR = 2024;
  IF l_n = 0 THEN
    INSERT INTO THE.ILCR_REPORTING_PERIOD
      (REPORT_YEAR, REPORT_OFFICIAL_START_DATE, REPORT_OFFICIAL_END_DATE, COMMENTS,
       REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES
      (2024, TO_DATE('2024-01-01', 'YYYY-MM-DD'), TO_DATE('2024-12-31', 'YYYY-MM-DD'),
       '2024 - reporting period', 0, c_user, SYSDATE, c_user, SYSDATE);
  END IF;

  FOR i IN 1 .. l_anchors.COUNT LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_MILL_REPORT_STATUS
     WHERE REPORT_YEAR = l_anchors(i).yr AND ILCR_MILL_ID = l_anchors(i).mill;

    IF l_n = 0 THEN
      -- MILL_SILVICULTUR_STATUS_CODE is held at 'D' independently of the 1-10 code: the two tracks are
      -- independent by design (PRD) and Schedule 6 reads only the 1-10 one, so moving the silviculture
      -- code in step would misrepresent the shape rather than match it.
      INSERT INTO THE.ILCR_MILL_REPORT_STATUS
        (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE,
         MILL_SILVICULTUR_STATUS_CODE, REPORT_COMPLETED_IND, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
        (l_anchors(i).yr, l_anchors(i).mill, l_anchors(i).code, 'D', 'N', 0,
         c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    -- The eleven category rows a real reporting mill-year carries. Guarded per row, so a partially
    -- applied patch completes cleanly on a re-run. CATEGORY_STATE_CODE 'D' / REPORTABLE_DETAIL_IND 'Y'
    -- copy a real row verbatim (9050/2018 category '4'), the same source sch4's and sch5's patches used.
    -- NOT OPTIONAL: delivery's ROAD_MAINTENANCE_REPORT carries the composite FK RM_RPT_ILCR_RCAT_FK onto
    -- this table (verified ENABLED), so without them the page opens and the first save 500s.
    FOR c IN (SELECT TO_CHAR(LEVEL) cat FROM DUAL CONNECT BY LEVEL <= 11) LOOP
      SELECT COUNT(*) INTO l_n
        FROM THE.ILCR_REPORT_CATEGORY
       WHERE REPORT_YEAR = l_anchors(i).yr
         AND ILCR_MILL_ID = l_anchors(i).mill
         AND ILCR_CATEGORY_ID = c.cat;

      IF l_n = 0 THEN
        INSERT INTO THE.ILCR_REPORT_CATEGORY
          (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CATEGORY_STATE_CODE,
           REPORTABLE_DETAIL_IND, COMMENTS, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES
          (l_anchors(i).yr, l_anchors(i).mill, c.cat, 'D', 'Y', NULL, 0,
           c_user, SYSDATE, c_user, SYSDATE);
      END IF;
    END LOOP;
  END LOOP;
  COMMIT;
END;
/
