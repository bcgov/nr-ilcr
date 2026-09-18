-- ============================================================================
-- UC-SCH6-001 (Schedule 6) — two road records plus a general comment on the
-- NON-DRAFT anchor, so S17 has something to render read-only.
--
-- WHY A SECOND PATCH RATHER THAN A LINE IN draft-anchors.sql
-- That file mints ANCHORS (report-status + category rows). This one seeds
-- CONTENT into exactly one of them, and the two have OPPOSITE invariants:
-- preflight asserts every anchor in EDITABLE_DRAFT_ANCHORS holds NO road
-- records at rest, and this file exists to put records on the single cell that
-- is exempt. Folding it in would bury the exception inside the file whose
-- header promises the opposite. Same split sch5 made for its own read-only arm
-- (real-test-data-patches/sch5/view-mode-camp.sql — the precedent this file
-- follows deliberately, down to the sequence-plus-sentinel shape below).
--
-- WHY REAL DATA FELL SHORT, AND WHY THE APP CANNOT SEED THIS
-- S17 asserts that on a non-Draft document the existing records, the running
-- totals and the general comment all REMAIN VISIBLE while every control is
-- disabled. An EMPTY read-only schedule proves only the disabled buttons, which
-- is the weaker half of the slice — and the stronger half needs rows. Those rows
-- cannot be created through the app: every write to a non-Draft document is
-- refused (ILCR_SUBMITTER edits at Draft only, ScheduleEditability:63), which is
-- precisely the condition S17 is about. So they have to arrive as SQL.
--
-- THE INSERT SHAPE MIRRORS THE APP'S OWN WRITES, column for column
-- (Schedule6Repository.insertRoadReport :241-258 / insertCostDetail :370-376),
-- so the read model assembles these rows identically to app-created ones. That
-- is the guarantee that makes the read-only render a fair test rather than a
-- test of a hand-built shape the app never produces:
--   * ILCR_CATEGORY_ID '6' on the report rows;
--   * ILCR_REPORT_COST_ITEM_ID 69 on the detail rows — the single Schedule 6
--     cost item (the read side filters on it, :96), unlike the fixed twelve-row
--     grid sch5 seeds;
--   * ILCR_REPORT_SUMMARY_ID NULL, because a road detail hangs off its report
--     and not off a summary. The delivery trigger ICRD_CHK_B_I_U requires
--     EXACTLY ONE parent FK, which ROAD_MAINTENANCE_REPORT_ID alone satisfies;
--   * ITEM_DESCRIPTION NULL — legacy never sets it.
-- Item 69 needs no registration: it already exists in delivery (every S01-S16
-- scenario writes an item-69 row through the API), and the Flyway test schema
-- models ILCR_REPORT_COST_ITEM_ID as a plain NUMBER with no FK.
--
-- TWO RECORDS, NOT ONE, AND ONE OF EACH BRANCH. This is the fixture decision
-- that carries the most weight, so it is worth stating why:
--   * TWO rows make the running totals a genuine SUM. With a single record the
--     totals equal that record's own figures, so a page that echoed one row into
--     the totals strip would pass. The figures are chosen so the TOTAL rate
--     differs from BOTH record rates (see below).
--   * ONE TSA ROW AND ONE TFL ROW, so the read-only render is proved for both
--     halves of the BR-02 classification — the TSA/Supply-Block branch and the
--     TFL branch, which render different fields (and derive RMG by different
--     routes: the supply block vs the fixed RoadGroupLookup table).
--   * TWO IS ALSO UNDER THE 5-ROW PAGE SIZE, deliberately. The totals'
--     pagination scope is unresolved in legacy source (defects.md SPEC-3), so a
--     fixture of six or more rows would force this slice to assert an answer to
--     an open question. Two keeps S17 about the read-only render.
--
-- THE FIGURES, and why these:
--     record 1 (TSA 01 / block 01B) : volume 10,000  cost 30,000  -> 3.00
--     record 2 (TFL 48)             : volume 20,000  cost 90,000  -> 4.50
--     totals                        : volume 30,000  cost 120,000 -> 4.00
-- Every division is EXACT, so no assertion turns on a rounding decision, and the
-- total 4.00 equals NEITHER record's rate — so a bug that showed a single row's
-- rate as the total fails rather than passing on a coincidence. The two rates
-- also differ from each other, so the per-row cells cannot be transposed
-- unnoticed. The derived RMGs are the ones the suite already pins elsewhere
-- (block 01B -> "15" from S01; TFL 48 -> "10" from S03), so they are values the
-- suite has already proved the server derives, now read back on a locked page.
--
-- THE GENERAL COMMENT IS REPLICATED ONTO BOTH ROWS, and that is not redundancy:
-- BR-09 is a replication invariant — every cat-6 row of a mill/year stores the
-- SAME schedule-level comment (Schedule6DAO.java:229, mirrored by
-- Schedule6Repository.updateAllComments :393-397), and the read side takes the
-- highest-id row's copy. Seeding it on only one row would be a shape the app
-- never writes, and which row won would depend on sequence order.
--
-- NOTE the two COMMENTS columns are DIFFERENT FIELDS (deviation E):
-- ROAD_MAINTENANCE_REPORT.COMMENTS is the schedule-level general comment
-- (4000-wide), while ILCR_COST_REPORT_DETAIL.COMMENTS is the per-record comment
-- (400-wide). Both are seeded here, with distinct text so a test cannot confuse
-- them and so neither can be satisfied by the other.
--
-- ANCHOR
--   mill 24051 / 2024 — Schedules 1-10 track 'S' (Submitted), opened by
--   draft-anchors.sql. Pinned by no other domain (the only 2024+ keys anywhere
--   are sch6's), and this suite never WRITES to it: S17 is a pure read. Its
--   eleven ILCR_REPORT_CATEGORY rows already exist (draft-anchors.sql seeds all
--   eleven per anchor), so the composite FK RM_RPT_ILCR_RCAT_FK is satisfied and
--   these inserts cannot trip it.
--   ORDERING: this file DEPENDS on draft-anchors.sql having run.
--   apply-patches.sh iterates `<domain>/*.sql` in shell glob order, which is
--   alphabetical — "draft-anchors.sql" < "view-mode-road-records.sql" — so the
--   dependency holds by name rather than by luck. It is also self-healing: the
--   guard below finds no status row on a bare DB, seeds nothing, and preflight
--   then fails with its own "run apply-patches.sh" message rather than an ORA
--   error pointing at the wrong thing.
--
-- IDEMPOTENT: guarded on the sentinel ENTRY_USERID over the anchor's cat-6 rows,
-- so re-running is a no-op.
-- SENTINEL: ENTRY_USERID / UPDATE_USERID = 'E2E_SEED_SCH6_VIEW' on both tables.
-- The teardown keys on it, so it can only ever remove what this file added.
-- RE-VERIFY ON RE-EXTRACT: preflight confirms 24051/2024 is still non-Draft and
-- still holds EXACTLY these two records — an extra record would make the totals
-- assertions wrong, and a Draft status would silently turn the whole slice into
-- a test of the editable page.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_mill    CONSTANT NUMBER := 24051;
  c_year    CONSTANT NUMBER := 2024;
  c_user    CONSTANT VARCHAR2(30) := 'E2E_SEED_SCH6_VIEW';
  -- The schedule-level general comment, replicated onto every cat-6 row (BR-09).
  c_general CONSTANT VARCHAR2(4000) :=
    'E2E S17 read-only schedule general comment.';

  l_anchor NUMBER;
  l_seeded NUMBER;
  l_record NUMBER;

  -- One entry per road record. NULLs are meaningful and mirror BR-02: a TSA row carries no
  -- TFL_NUMBER_CODE and a TFL row carries neither TSA_NUMBER nor TSB_NUMBER_CODE, because the
  -- service clears the counterpart on write (Schedule6Service:597).
  TYPE t_rec  IS RECORD (
    tsa      VARCHAR2(2),
    tsb      VARCHAR2(3),
    tfl      VARCHAR2(2),
    vol      NUMBER,
    cost     NUMBER,
    comments VARCHAR2(400));
  TYPE t_recs IS TABLE OF t_rec;
  l_recs t_recs := t_recs(
    -- The TSA / Supply Block branch. Block 01B derives RMG "15" (pinned by S01).
    t_rec('01', '01B', NULL, 10000, 30000, 'E2E S17 read-only TSA record'),
    -- The TFL branch. TFL 48 derives RMG "10" (pinned by S03) — deliberately different from "15",
    -- so a render that confused the two branches fails instead of matching by coincidence.
    t_rec(NULL, NULL, '48', 20000, 90000, 'E2E S17 read-only TFL record')
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
    INTO l_seeded
    FROM THE.ROAD_MAINTENANCE_REPORT
   WHERE ILCR_MILL_ID = c_mill
     AND REPORT_YEAR = c_year
     AND ILCR_CATEGORY_ID = '6'
     AND ENTRY_USERID = c_user;

  IF l_seeded = 0 THEN
    FOR i IN 1 .. l_recs.COUNT LOOP
      l_record := THE.ILCR_REPORT_COMMON_SEQ.NEXTVAL;

      -- COMMENTS carries the SCHEDULE-level general comment on every row (BR-09 replication),
      -- exactly as the app's own insert does by sub-selecting it from the sibling rows.
      INSERT INTO THE.ROAD_MAINTENANCE_REPORT
          (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID,
           TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (l_record, c_year, c_mill, '6',
           l_recs(i).tsa, l_recs(i).tsb, l_recs(i).tfl, c_general,
           0, c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);

      -- The single Schedule 6 cost item (69). SUMMARY_ID NULL and ROAD_MAINTENANCE_REPORT_ID set is
      -- the one-parent shape the ICRD_CHK_B_I_U delivery trigger requires.
      INSERT INTO THE.ILCR_COST_REPORT_DETAIL
          (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ROAD_MAINTENANCE_REPORT_ID,
           ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ITEM_DESCRIPTION, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES
          (THE.ILCR_COST_REPORT_DETAIL_SEQ.NEXTVAL, NULL, l_record,
           69, l_recs(i).vol, l_recs(i).cost, l_recs(i).comments, NULL, 0,
           c_user, SYSTIMESTAMP, c_user, SYSTIMESTAMP);
    END LOOP;
  END IF;

  COMMIT;
END;
/
