-- ============================================================================
-- UC-SCH5-001 (Schedule 5) — the one free Draft mill-year the extract still has,
-- opened as Schedule 5's first MUTATING anchor.
--
-- WHY REAL DATA FELL SHORT
-- Schedule 5 needs no summary row of its own: a valid, ACTIVE mill-year holding
-- no camps is the legitimate empty state and answers 200 `camps: []`, never a
-- 404 (Schedule5Service.java:46-47, deviation (a)). So unlike Schedule 3 the
-- problem here is not missing schedule data — it is anchor EXCLUSIVITY.
--
-- Measured 2026-09-08 against the seeded image and the app's own API:
--     ILCR_MILL_REPORT_STATUS rows                    : 123  (107 D, 9 S, 7 V)
--     opened reporting years                          : 2015-2021 only
--     (mill, year) keys already pinned by sch1/sch2/
--       sch3/sch4/sch11/sec (preflight/anchor-keys.ts): 119
--     Draft + zero-camp + pinned by NOBODY            :   3
--       -> 1/2017, 14050/2018, 25051/2017
--     ...of those, usable                             :   0
--       all three are CLS mills and answer HTTP 409 (verified through
--       GET /api/v1/schedule5; 16050/2018, 23052/2019 and 13050/2018 answer 200
--       editable with camps=0, so the probe itself is sound).
-- The 17 ACT listable mills x 7 opened years is a 119-cell grid with only THREE
-- empty cells: 9050/2016, 16050/2015 and 16050/2016. The latter two are already
-- pinned — 16050/2015 is sch3's `never-started` 404 guard (whose whole fixture is
-- the ABSENCE of a row) and 16050/2016 is shared by sch11 and sec. That leaves
-- exactly one.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_MILL_REPORT_STATUS row on 9050/2016 — the openability gate.
--      Without it MillContextService answers 404, which is why 9050/2016 404s
--      today.
--   2. Eleven THE.ILCR_REPORT_CATEGORY rows (categories '1'-'11'), the shape a
--      real reporting mill-year carries. NOT optional for Schedule 5: CAMP_REPORT
--      carries the composite FK CMP_RPT_ILCR_RCAT_FK -> ILCR_REPORT_CATEGORY
--      (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID) in delivery
--      (Schedule5Repository.java:260), so with the status row alone the page opens
--      but the first camp save fails DataIntegrityViolationException — precisely
--      the failure sch4's unsaved-check-anchors.sql found the hard way on
--      9050/2015. All eleven are seeded rather than just category '5', because
--      that is the shape the extract gives every reporting mill-year; seeding one
--      category would invent a state the app has never seen.
-- Nothing else is seeded, so the anchor holds NO camps at rest, which preflight
-- asserts and the S01 scenario's own Given then builds on.
--
-- ANCHOR OWNERSHIP
--   9050/2016 — `add` (S01, "Add a New Camp With Descriptors and Fixed-Category
--               Expenses"). Mutating: the scenario creates a camp and deletes it
--               again through the app's own DELETE endpoint.
-- Mill 9050 ("760 WESTEROS") is deliberate: sch4 owns that mill in 2015 and
-- 2018-2021 and sch1 in 2017, so the YEAR is new but the mill is one the suite
-- already exercises. Anchors are (mill, year) PAIRS — the cross-domain guard in
-- sch4-anchors.setup.ts compares pairs, not mills — so this collides with nothing.
--
-- BOTH TRACK CODES ARE SET TO 'D' so the row is an ordinary complete Draft rather
-- than a half-populated shape the app has never seen. Schedule 5 reads the
-- Schedules 1-10 code; the silviculture code is incidental here. The side effect
-- is that Schedules 1/2/3/4/6-11 also become openable on this pair, which is
-- harmless: ownership is declared in `fixtures/sch5/schedule5-test-data.ts`,
-- asserted by preflight, and referenced by no other fixture.
--
-- FAN-OUT — OPENING REPORTING YEARS 2022 AND 2023 (added 2026-09-09).
-- 9050/2016 above was the LAST free cell in the 2015-2021 grid, so the other 24
-- slices could not be authored without MINTING capacity. This file now does that
-- by opening two new reporting years and claiming 21 cells in them.
--
-- WHY A NEW YEAR RATHER THAN ANYTHING ELSE:
--   * THE.ILCR_REPORTING_PERIOD is purely ADDITIVE — no existing row is touched.
--   * It cannot shift any existing test's starting state: the app has NO default
--     working context. DEFAULT_MILL_ID / DEFAULT_YEAR in
--     context/millYear/millYearDefaults.ts are a TEST fixture, and MillYearProvider
--     stopped falling back to them (commit e37649b), so Home now lands on its
--     "Select Mill" / "Select Reporting Year" placeholders every time.
--   * Nothing asserts the year LIST. The only step that inspects it
--     (steps/sec/working-context.steps.ts:24 "the mill and reporting-year option
--     lists are populated") asserts that a KNOWN option is present, not a count.
--   * Every (mill, year) any fixture pins today is <= 2021. So "year >= 2022
--     belongs to sch5" is a STRUCTURAL invariant rather than a convention someone
--     has to remember — the cross-domain guard cannot be violated by accident.
-- Rejected: opening the CLS mills (1, 13, 14050, 25051) — sch2 and sch4 pin
-- closed mills as their HTTP-409 guard anchors, so flipping ILCR_MILL_STATUS_XREF
-- would silently redden those guards. 25051/2017 is instead REUSED AS-IS below as
-- sch5's own 409 guard: it stays closed, which is the whole point of it.
--
-- WHAT THE FAN-OUT ADDS
--   1. THE.ILCR_REPORTING_PERIOD rows for 2022 and 2023.
--   2. 22 report-status rows + their eleven category rows each:
--        2022 — 16 of the 17 ACT mills (every one EXCEPT 16050)
--        2023 —  6 mills (9050, 10050, 12050, 13050, 17052, 16050)
--      Twenty-one are Draft ("D"); ONE (16050/2023) is Submitted ("S") because
--      S19 needs a non-Draft document to prove the read-only render.
--      17052/2023 was added 2026-09-09, after S12 was authored: S12 and S15 were
--      to share the one validate-only anchor, but S12's SECOND arm corrects the
--      blank field and SAVES. A writer cannot share a key with anything under
--      `fullyParallel`, so S12 took its own and 13050/2023 stayed validate-only.
--      This is the fan-out working as designed — 2023 is sch5's own range, so
--      minting one more cell costs nothing and collides with nobody.
--   3. Nothing on 16050/2022 — DELIBERATELY. Its ABSENCE is S18's fixture ("No
--      Schedule 5 Record Found"): with 2022 open but no report-status row, the GET
--      404s. It is registered in DELIBERATELY_ABSENT in
--      preflight/ci-seed-parity.setup.ts, which FAILS if anyone ever seeds it.
--
-- TWO GUARD ANCHORS NEED NO CAPACITY AND ARE NOT SEEDED HERE:
--   S17 (mill not active -> 409) = 25051/2017, an existing CLS mill-year that no
--       other fixture pins (verified through the API: it answers 409, while
--       16050/2018 and 13050/2018 answer 200).
--   S18 (no schedule -> 404)     = 16050/2022, the hole described above.
--
-- IDEMPOTENT: guarded on its own existence check, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_TRACK_SCH5'; the teardown keys on
-- it, so it can only ever remove what this file added.
-- RE-VERIFY ON RE-EXTRACT: if a future extract frees a real Draft mill-year in
-- 2015-2021 that no fixture claims, prefer it and retire this insert.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_TRACK_SCH5';
  l_n    NUMBER;

  TYPE t_anchor  IS RECORD (mill NUMBER, yr NUMBER, code VARCHAR2(1));
  TYPE t_anchors IS TABLE OF t_anchor;

  -- Every (mill, year) sch5 owns, with its Schedules 1-10 track code. Order matches the anchor table
  -- in fixtures/sch5/schedule5-test-data.ts; the two files are transcribed from each other and the
  -- CI-seed parity gate fails the run if they disagree with db-e2e/R__80_e2e_anchor_seed.sql.
  l_anchors t_anchors := t_anchors(
    -- S01, the original — the last free cell of the 2015-2021 grid.
    t_anchor(9050, 2016, 'D'),
    -- 2022: 16 of the 17 ACT mills. 16050 is ABSENT on purpose (S18's 404 fixture).
    t_anchor( 9050, 2022, 'D'),  -- S02 edit
    t_anchor(10050, 2022, 'D'),  -- S03 copy
    t_anchor(12050, 2022, 'D'),  -- S04 sub-page, existing camp
    t_anchor(13050, 2022, 'D'),  -- S05 sub-page, new unsaved camp
    t_anchor(17052, 2022, 'D'),  -- S06 check status met
    t_anchor(22050, 2022, 'D'),  -- S07 delete
    t_anchor(22051, 2022, 'D'),  -- S08 same name, mill A
    t_anchor(23050, 2022, 'D'),  -- S08 same name, mill B
    t_anchor(23051, 2022, 'D'),  -- S09 recoveries
    t_anchor(23052, 2022, 'D'),  -- S10 discard confirm on close
    t_anchor(24050, 2022, 'D'),  -- S11 camp switch while editing
    t_anchor(24051, 2022, 'D'),  -- S13 duplicate name
    t_anchor(25050, 2022, 'D'),  -- S14 copy without renaming
    t_anchor(25052, 2022, 'D'),  -- S20 check status missing values
    t_anchor(25053, 2022, 'D'),  -- S21 access expense description blank
    t_anchor(25054, 2022, 'D'),  -- S22 camp expense description blank
    -- 2023: the overflow, plus the one non-Draft document the suite needs.
    t_anchor( 9050, 2023, 'D'),  -- S23 invalid cost on the sub-page
    t_anchor(10050, 2023, 'D'),  -- S24 check status includes unsaved violation
    t_anchor(12050, 2023, 'D'),  -- S25 check status clears on unsaved correction
    t_anchor(13050, 2023, 'D'),  -- S15 validate-only (nothing is ever saved here)
    t_anchor(17052, 2023, 'D'),  -- S12 required-field: its recovery arm DOES save
    t_anchor(16050, 2023, 'S')   -- S19 read-only: Submitted, so the schedule is not editable
  );
BEGIN
  -- The two new reporting years. Additive: 2015-2021 already exist and are untouched.
  FOR y IN 2022 .. 2023 LOOP
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_REPORTING_PERIOD WHERE REPORT_YEAR = y;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_REPORTING_PERIOD
        (REPORT_YEAR, REPORT_OFFICIAL_START_DATE, REPORT_OFFICIAL_END_DATE, COMMENTS,
         REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
        (y, TO_DATE(y || '-01-01', 'YYYY-MM-DD'), TO_DATE(y || '-12-31', 'YYYY-MM-DD'),
         y || ' - reporting period', 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END LOOP;

  FOR i IN 1 .. l_anchors.COUNT LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_MILL_REPORT_STATUS
     WHERE REPORT_YEAR = l_anchors(i).yr AND ILCR_MILL_ID = l_anchors(i).mill;

    IF l_n = 0 THEN
      -- MILL_SILVICULTUR_STATUS_CODE is held at 'D' even on the Submitted anchor: Schedule 5 reads the
      -- Schedules 1-10 track only, and the two tracks are independent by design (PRD), so moving the
      -- silviculture code in step would misrepresent the shape rather than match it.
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
    -- copy a real row verbatim (9050/2018 category '4'), the same source sch4's patch used.
    -- NOT OPTIONAL: delivery's CAMP_REPORT carries the composite FK CMP_RPT_ILCR_RCAT_FK onto this
    -- table, so without them the page opens and the first save 500s.
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
