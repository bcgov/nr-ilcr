-- ============================================================================
-- UC-SCH5-001 (Schedule 5) — a camp on the NON-DRAFT anchor, so S19 has
-- something to render read-only.
--
-- WHY A SECOND PATCH RATHER THAN A LINE IN draft-anchors.sql
-- That file mints ANCHORS (report-status + category rows). This one seeds
-- CONTENT into one of them, and only one of them. Keeping them apart matters
-- because the two have opposite invariants: preflight asserts every anchor
-- draft-anchors.sql opens holds NO camps at rest, and this file exists to put a
-- camp on the single anchor exempt from that rule. Folding it in would have
-- buried the exception inside the file whose header promises the opposite.
--
-- WHY REAL DATA FELL SHORT
-- S19 asserts STA-001: on a non-Draft document the row-action column collapses
-- to a single `View`, `Add New Camp` and `Check Status` are disabled, and the
-- panel opens read-only. Every one of those assertions needs a CAMP in the
-- Existing Camps table — an empty read-only schedule proves only the two
-- disabled buttons, which is the weakest half of the slice.
--
-- Probed 2026-09-10 through the app's own API:
--     GET /api/v1/schedule5?millId=16050&year=2023
--       -> 200, trackStatus "S", editable false, camps: []
-- and the camp cannot be created through the app: the Draft gate rejects every
-- write to a non-Draft document (Schedule5Service, `scheduleNotEditableErrorMsg`
-- -> HTTP 409), which is precisely the condition S19 is about. So the row has to
-- arrive as SQL, exactly as sch4 hit the same wall on its own read-only arm
-- (real-test-data-patches/sch4/view-mode-amounts.sql — same reasoning, same
-- shape, and the precedent this file follows deliberately).
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   * one THE.CAMP_REPORT row on 16050/2023, category '5'
--   * its TWELVE THE.ILCR_COST_REPORT_DETAIL rows — the fixed grid
--     (56/58/59/60/61/63-67/141/142). Items 62/68 (the sub-page rows) are
--     deliberately NOT seeded: S19 asserts the read-only render of the camp
--     panel, and an empty Other-Expenses list is the simpler fixture.
-- The insert shape mirrors the app's own writes column for column
-- (Schedule5Repository.insertCamp :265-278 / insertCostDetail :431-441), so the
-- read model assembles it identically to an app-created camp — the same
-- guarantee sch4's patch relies on.
--
-- THE AMOUNTS ARE S01'S, ON PURPOSE. Volume 5000 with catering 1000 / wages 2000
-- / depreciation 500 / general 300 / crew 700 / land 400 and zeroes elsewhere is
-- the exact arithmetic already MEASURED and documented for the happy path
-- (NEW_CAMP_EXPECTED_TOTALS): campSubTotal 3800, campTotal 3800,
-- accessExpenseTotal 1100, campAndAccessTotal 4900, and $/m3 0.76 / 0.98. So the
-- read-only figures S19 asserts are not a fresh set of hand-computed numbers
-- that could be quietly wrong — they are a set the suite already proves the
-- server derives, now read back through a different route.
--
-- NOTE `recoveries` (item 61) is seeded COST-ONLY at 0 and `otherCampExpenses` /
-- `otherAccessExpenses` (141/142) VOLUME-ONLY, because that is what those three
-- categories are: 61 is the volume-less twelfth category and the other two carry
-- no cost of their own (their cost is the item-62/68 row sum, BR-04). Seeding
-- the missing halves would invent a shape the app never writes.
--
-- ANCHOR
--   mill 16050 / 2023 — Schedules 1-10 track "S" (Submitted), opened by
--   draft-anchors.sql. Pinned by no other domain, and this suite never writes to
--   it: S19 is a pure read. Its ILCR_REPORT_CATEGORY rows already exist (that
--   file seeds all eleven per anchor), so the composite FK
--   CMP_RPT_ILCR_RCAT_FK is satisfied and this insert cannot trip it.
--   ORDERING: this file therefore DEPENDS on draft-anchors.sql having run.
--   apply-patches.sh iterates `<domain>/*.sql` in shell glob order, which is
--   alphabetical — "draft-anchors.sql" < "view-mode-camp.sql" — so the
--   dependency holds by name rather than by luck. It is also self-healing: the
--   guard below finds no status row on a bare DB, inserts nothing, and preflight
--   then fails with the "run apply-patches.sh" message rather than an ORA error.
--
-- IDEMPOTENT: guarded on the sentinel camp name, so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_SEED' *and*
-- CAMP_NAME = 'E2E View Camp'. The teardown keys on both, so it can only ever
-- remove what this file added.
--
-- RE-VERIFY ON RE-EXTRACT: confirm 16050/2023 is still non-Draft (preflight
-- does) and that this camp is still the ONLY one on it (preflight does that too
-- — an extra camp would make the "single View button" assertion ambiguous).
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_mill     CONSTANT NUMBER := 16050;
  c_year     CONSTANT NUMBER := 2023;
  c_name     CONSTANT VARCHAR2(30) := 'E2E View Camp';
  c_user     CONSTANT VARCHAR2(30) := 'E2E_SEED';
  c_comments CONSTANT VARCHAR2(4000) := 'Read-only sample comments (E2E_SEED).';
  c_volume   CONSTANT NUMBER := 5000;

  l_anchor NUMBER;
  l_camp   NUMBER;

  -- The fixed grid, keyed by ILCR_REPORT_COST_ITEM_ID. NULL volume / NULL cost are meaningful:
  -- 61 (recoveries) is cost-only, 141/142 (the two Other ... rows) are volume-only.
  TYPE t_item  IS RECORD (item NUMBER, vol NUMBER, cost NUMBER);
  TYPE t_items IS TABLE OF t_item;
  l_items t_items := t_items(
    t_item( 56, c_volume, 1000),  -- Catering and Food
    t_item( 58, c_volume, 2000),  -- Wages and Benefits
    t_item( 59, c_volume,  500),  -- Depreciation/Lease
    t_item( 60, c_volume,  300),  -- General Camp Expenses
    t_item(141, c_volume, NULL),  -- Other Camp Expenses   — volume only
    t_item( 61, NULL,        0),  -- Recoveries            — cost only, no volume cell
    t_item( 63, c_volume,  700),  -- Crew Transportation
    t_item( 64, c_volume,  400),  -- Equipment and Supplies — Land
    t_item( 65, c_volume,    0),  -- Equipment and Supplies — Rail
    t_item( 66, c_volume,    0),  -- Equipment and Supplies — Air
    t_item( 67, c_volume,    0),  -- Equipment and Supplies — Water
    t_item(142, c_volume, NULL)   -- Other Access Expenses — volume only
  );
BEGIN
  -- The anchor must already exist (draft-anchors.sql). If it does not, seed nothing rather than
  -- raising: preflight's own message names the fix, and an ORA here would obscure it.
  SELECT COUNT(*)
    INTO l_anchor
    FROM THE.ILCR_MILL_REPORT_STATUS
   WHERE ILCR_MILL_ID = c_mill
     AND REPORT_YEAR = c_year;

  IF l_anchor = 0 THEN
    RETURN;
  END IF;

  SELECT COUNT(*)
    INTO l_camp
    FROM THE.CAMP_REPORT
   WHERE ILCR_MILL_ID = c_mill
     AND REPORT_YEAR = c_year
     AND ILCR_CATEGORY_ID = '5'
     AND CAMP_NAME = c_name;

  IF l_camp = 0 THEN
    l_camp := THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL;

    -- ISOLATED_CAMP_IND 'Y' so the read-only panel has a non-default value to render: 'N' is the
    -- column DEFAULT, so it could not distinguish "stored Yes" from "never set".
    INSERT INTO THE.CAMP_REPORT
        (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
         CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME,
         ISOLATED_CAMP_IND, COMMENTS,
         REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES
        (l_camp, c_year, c_mill, '5',
         c_name, 12.5, 40, c_volume,
         'Y', c_comments,
         0, c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);

    FOR i IN 1 .. l_items.COUNT LOOP
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID,
           VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, l_camp, l_items(i).item,
           l_items(i).vol, l_items(i).cost, NULL, 0,
           c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
    END LOOP;
  END IF;

  COMMIT;
END;
/
