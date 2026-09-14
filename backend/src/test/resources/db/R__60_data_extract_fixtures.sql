-- Data Extract CSV fixtures (UC-EXT-001, the generation half).
--
-- REPEATABLE (R__) seed per the 2026-08-20 Flyway fixture convention: data never rides a versioned
-- migration. Prefix 60 puts this in the 10-80 data band and, deliberately, BELOW R__70 -- an extract
-- read needs no user assignment, so inheriting R__70's set-based submitter association is harmless
-- here, and sitting below it keeps this file clear of the maintain-mills band at 75 which must NOT
-- inherit one.
--
-- MILL_ID band 760-762, claimed here and recorded in db/README.md. The README's table stops at
-- 750-756 (R__75), so 757+ is the real high-water mark; clear of the db-e2e anchor seed's mills
-- (13, 9050-25054) too. PK bands: ILCR_REPORT_SUMMARY 1300-1399, ILCR_COST_REPORT_DETAIL 9000-9099,
-- both previously unclaimed (the highest summary in this directory is 1203 plus e2e's 3001-3199; the
-- highest detail is 8984 plus e2e's 4001-4499).
--
-- ============================================================================================
-- EVERY ROW HERE IS REPORT YEAR 2020. NOTHING TOUCHES 2021, AND THAT IS LOAD-BEARING.
-- ============================================================================================
-- MillReportStatusIT asserts the 2021 ILCR_MILL_REPORT_STATUS rows are exactly five AND names them
-- in order -- jsonPath("$.length()").value(5) plus contains(514, 730, 731, 732, 733). A 2021 status
-- row seeded here would fail both assertions in an unrelated area, so the second year of every
-- multi-year selection below is deliberately a year with NO status row and NO schedule data.
--
-- That absence is not a gap in the fixture; it is three of the cases under test:
--   * a (mill, year) with no status row must render the verbatim "** NO STATUS **" status cell;
--   * a missing status row must make "Data Verified: No" (the authored rule -- legacy printed Yes
--     vacuously when it found no rows at all);
--   * in the combined Schedule 1+2 layout, a year with no rows must emit NO mini-table header,
--     which is the only way to tell the ratified behaviour from "a header for every year in range".
--
-- A mill needs three things to be resolvable by the extract at all, because the selection list
-- inner-joins them: a THE.MILL row, an ILCR_MILL_STATUS_XREF row, and at least ONE
-- ILCR_MILL_REPORT_STATUS row in any year. All three mills below carry a 2020 status row, which is
-- what makes them visible while leaving 2021 alone.
--
-- The three mills, each earning its place:
--   760  Verified on BOTH tracks, with a Schedule 1 AND a Schedule 3 summary. The Data Verified
--        "Yes" arm, and the only mill whose Schedule 1 row can show real Schedule-3-derived cells.
--   761  Verified on Schedules 1-10 but DRAFT on silviculture, with a Schedule 1 and a Schedule 2
--        summary but deliberately NO Schedule 3. Two jobs: it is the "*** NO SCHEDULE 3 ***"
--        sentinel case (the sentinel fills the Schedule-3-derived cells of a Schedule 1 row whose
--        pair has no Schedule 3 summary, and appears nowhere else), and its track disagreement is
--        what proves Data Verified ANDs the two tracks instead of ORing them: a selection naming
--        both kinds of schedule must read "No" for this mill even though its main track is V.
--   762  SUBMITTED on Schedules 1-10, Verified on silviculture, and carries NO schedule data at all.
--        The second Data Verified "No" arm (a non-V code rather than a missing row), and the mill
--        that makes a whole-schedule "*** NO DATA FOUND ***" marker reachable while other mills in
--        the same selection still carry rows.
--
-- Mill NUMBERs are deliberately NOT in mill-id order (7620, 7600, 7610). The title block's
-- "Included Mills:" line and the MILL_NUMBER column both render the NUMBER while section rows are
-- ordered by mill ID, so equal orderings would let an id/number mix-up pass unnoticed.
--
-- Cost figures are chosen to land on formatter boundaries rather than to be realistic: 1234500 and
-- 1234.5 exercise thousands grouping and the HALF_EVEN-to-even rounding that legacy's DecimalFormat
-- default produces, and a NULL volume on one detail row exercises the "-" null marker.

INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (760, 'EXTRACT VERIFIED BOTH', 7600, NULL, NULL, NULL, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (761, 'EXTRACT NO SCHEDULE 3', 7610, NULL, NULL, NULL, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (762, 'EXTRACT NO DATA', 7620, NULL, NULL, NULL, 'SEED');

-- All three ACTIVE. Mill status is not a gate on the extract -- closed mills stay selectable, a
-- recorded deviation carried from the selection story -- so an ACT/CLS spread would add no coverage
-- here and is left to the areas that do gate on it.
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (760, 'ACT', NULL, NULL, NULL, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (761, 'ACT', NULL, NULL, NULL, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (762, 'ACT', NULL, NULL, NULL, 'SEED');

-- The two tracks are INDEPENDENT, and the three code pairs below are the Data Verified truth table:
-- V/V passes whichever schedules are selected; V/D passes a Schedules 1-10 selection and fails one
-- that also names Schedule 11; S/V does the reverse. Nothing here is 2021.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  VALUES (2020, 760, 'V', 'V', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  VALUES (2020, 761, 'V', 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  VALUES (2020, 762, 'S', 'V', 'SEED');

-- Mill 760/2020: a Schedule 1 (category '1') and a Schedule 3 (category '3') summary. The Schedule 3
-- CROWN_VOLUME is what the Schedule 1 row's Schedule-3-derived cells read, so its presence here and
-- its absence on 761 are the two halves of the sentinel test.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ENTRY_USERID)
  VALUES (1300, 2020, 760, '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CROWN_VOLUME, ENTRY_USERID)
  VALUES (1301, 2020, 760, '3', 54321, 'SEED');

-- Mill 761/2020: a Schedule 1 and a Schedule 2 summary, and NO category '3' row. The Schedule 2
-- summary also gives the combined Schedule 1+2 layout a second mill to group, which its trigger
-- requires (two or more mills AND a multi-year range).
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ENTRY_USERID)
  VALUES (1310, 2020, 761, '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ENTRY_USERID)
  VALUES (1311, 2020, 761, '2', 'SEED');

-- Mill 760's Schedule 1 detail. Item 12 is Standing Tree to Loaded Truck, whose volume is the single
-- column the combined Schedule 1 mini-table shows, so this row is what makes that layout assertable.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9000, 1300, 12, 1234500, 9876500, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9001, 1300, 13, 1000, 50000, NULL, 'SEED');
-- A NULL volume beside a real cost: the volume cell must render "-" and the derived per-unit cell
-- must render "-" too rather than dividing by nothing.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9002, 1300, 14, NULL, 25000, NULL, 'SEED');

-- Mill 760's Schedule 3 PO&P timber line (item 118), the source of the Schedule-3-derived cells.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9010, 1301, 118, 7500, 450000, NULL, 'SEED');

-- Mill 761's Schedule 1 detail: item 12 again, so both mills appear in the combined layout's
-- Schedule 1 mini-table and their ORDER within the year is observable.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9020, 1310, 12, 2000, 100000, NULL, 'SEED');

-- Mill 761's Schedule 2 purchased-log line (item 25), the single column the combined Schedule 2
-- mini-table shows.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9030, 1311, 25, 3000, 777000, NULL, 'SEED');
