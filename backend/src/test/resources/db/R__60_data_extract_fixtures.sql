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
-- highest detail is 8984 plus e2e's 4001-4499; ILCR_COST_REPORT_DETAIL_SEQ was restarted at 10000 by
-- V20260818, so a runtime-drawn detail id cannot land in this band either).
--
-- The per-report tables the schedule walks read from get ONE shared band, 6600-6699, verified free of
-- any 66xx literal in db/ and db-e2e/ before it was claimed, and every sequence those tables draw from
-- starts at 9000 or above: TRANSPORTATION_REPORT 6600-6609, CAMP_REPORT 6610-6619,
-- TREE_TO_TRUCK_REPORT 6620-6629, TREE_TO_TRUCK_DETAIL_REPORT 6630-6639, ROAD_CONSTRUCTION_REPRT
-- 6640-6649, ROAD_CONSTRUCTION_REPRT_DTL 6650-6659, BASIC_SILVICULTURE_REPORT 6660-6669. Recorded
-- in db/README.md beside the summary and detail bands.
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
--        It is also the ONE mill that carries a row for every schedule whose extract walk the
--        generator dispatches by hand -- Schedule 1 Other Costs, the two Schedule 3 sub-pages,
--        Schedule 4 with a Towing row, Schedule 5 with a Camp Expenses row, Schedule 8 with a
--        sample, and Schedule 10 with one detailed page and one page with no road data -- so each
--        of those walks is executed against real figures at least once (code review 2026-09-14).
--   761  Verified on Schedules 1-10 but DRAFT on silviculture, with a Schedule 1 and a Schedule 2
--        summary but deliberately NO Schedule 3. Three jobs: it is the "*** NO SCHEDULE 3 ***"
--        sentinel case (the sentinel fills the Schedule-3-derived cells of a Schedule 1 row whose
--        pair has no Schedule 3 summary, and appears nowhere else); its track disagreement is
--        what proves Data Verified ANDs the two tracks instead of ORing them: a selection naming
--        both kinds of schedule must read "No" for this mill even though its main track is V; and
--        its ONE silviculture location is what proves the Schedule 11 STATUS cell reads the
--        SILVICULTURE track's description ("Draft") and not the main track's ("Verified").
--   762  SUBMITTED on Schedules 1-10, Verified on silviculture, and carries NO schedule figures at
--        all -- its only schedule row is a Schedule 1 summary with NO detail rows, which is legacy's
--        "every record empty" case (Schedule1Extract.java:38, isEmptyAllSchedule): ONE five-cell
--        whole-schedule marker, not a per-record one. Also the second Data Verified "No" arm (a
--        non-V code rather than a missing row), and the mill that makes a whole-schedule
--        "*** NO DATA FOUND ***" marker reachable while other mills in the same selection still
--        carry rows.
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

-- ============================================================================================
-- Added 2026-09-14 (Story 21.2 code review): the walks below had no fixture row anywhere, so
-- their builders had never run against the database. Every row is mill 760/761/762, REPORT_YEAR
-- 2020, and every figure is chosen so the formatted cell is hand-checkable in DataExtractCsvIT.
-- ============================================================================================

-- Mill 762/2020: a Schedule 1 summary with NO detail rows. findStoredSchedule1 finds it, every
-- figure is null, so Schedule1Section.isEmpty is true for the only pair -> the section body is
-- exactly one whole-schedule marker (legacy's all-empty branch), never a per-record marker.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ENTRY_USERID)
  VALUES (1320, 2020, 762, '1', 'SEED');

-- Mill 760's Schedule 1 Other Costs (V6 shape): item 19 with a NULL description is the SHARED
-- volume row; an item-19 row WITH a description is an itemized cost and carries the same volume.
-- 3000 / 5000 -> CPU 0.60; the Total: row shows the volume to two decimals (5,000.00) and the cost
-- to none (3,000), legacy's inverted formatting kept by Schedule1OtherSection.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9003, 1300, 19, 5000, NULL, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9004, 1300, 19, 5000, 3000, 'Extract Other Cost', 'SEED');

-- Mill 760's Schedule 3 sub-pages on summary 1301 (V19 shape). Other Acceptable is a TOT + PO&P
-- PAIR of item-124 rows sharing a description and a SCH3_2_{TOT|POP}_GRP{n} COMMENTS key:
-- 800 / 300 -> derived CROWN 500. Included Unacceptable is one item-38 row (250), and the section's
-- fixed first row reads the item-29 Annual Rents harvest figure (1,000), so its Total: is 1,250.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, COMMENTS, ENTRY_USERID)
  VALUES (9011, 1301, 124, NULL, 800, 'Consulting Fees', 'SCH3_2_TOT_GRP1', 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, COMMENTS, ENTRY_USERID)
  VALUES (9012, 1301, 124, NULL, 300, 'Consulting Fees', 'SCH3_2_POP_GRP1', 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9013, 1301, 38, NULL, 250, 'Penalty Fees', 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9014, 1301, 29, NULL, 1000, NULL, 'SEED');

-- Mill 760's Schedule 4 (V13/V15 shape): a location is a FAMILY of TRANSPORTATION_REPORT rows
-- sharing LOCATION_DESCRIPTION. 6600 is the primary (DISTANCE NULL) carrying the fixed category 40
-- (2000 m3 / 100,000 -> ="50.00"); 6601 is the Towing sub-page row -- its own report with its own
-- DISTANCE 30 and one item-43 detail with ITEM_DESCRIPTION (100 m3 / 3,000 -> ="30.00"). The main
-- row's TOW_TOT_* cells are summed from that one row, so both sections show the same figures.
INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, ENTRY_USERID)
  VALUES (6600, 2020, 760, '4', 'Extract Dump', NULL, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9040, 6600, 40, 2000, 100000, NULL, 'SEED');
INSERT INTO THE.TRANSPORTATION_REPORT (TRANSPORTATION_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION_DESCRIPTION, DISTANCE, TRANSPORTATION_CYCLE_TIME, ENTRY_USERID)
  VALUES (6601, 2020, 760, '4', 'Extract Dump', 30, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, TRANSPORTATION_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9041, 6601, 43, 100, 3000, 'Extract Towing', 'SEED');

-- Mill 760's Schedule 5 (V34 shape; CAMP_REPORT's audit quartet and REVISION_COUNT are NOT NULL
-- with no defaults). One camp, volume 10000, one fixed category (56 catering: 10,000 m3 / 50,000
-- -> 5.00) and the Camp Expenses sub-page: item 141 holds the volume every item-62 row is STAMPED
-- with at read (4000, never stored per row), and one item-62 row of 2,000 -> CPU 0.50. No item-68
-- row, so the Access Expenses section shows this camp's per-camp marker -- and its position AFTER
-- Camp Expenses is legacy's dispatch order, which the title line lists the other way round.
INSERT INTO THE.CAMP_REPORT (CAMP_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CAMP_NAME, DISTANCE_TO_OPERATING_AREA, CAMP_SIZE_CAPACITY, ASSOCIATED_CAMP_VOLUME, ISOLATED_CAMP_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (6610, 2020, 760, '5', 'Extract Camp', 12, 20, 10000, 'N', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID)
  VALUES (9050, 6610, 56, 10000, 50000, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, REVISION_COUNT, ENTRY_USERID)
  VALUES (9051, 6610, 141, 4000, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, CAMP_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, REVISION_COUNT, ENTRY_USERID)
  VALUES (9052, 6610, 62, NULL, 2000, 'Extract Kitchen', 0, 'SEED');

-- Mill 760's Schedule 8 (V22 shape, codes from V22's code tables): one TSA-located page with one
-- sample and NO rate rows, so ADDITIONS and DEDUCTIONS roll up to 0 and FINAL_TTT_RATE equals the
-- 25.00 original (rendered whole: 25). 60 + 40 = TOTAL_% 100; 700 + 300 = ACTUAL_M3 1,000.
-- CUTTING_PERMIT_NUMBER is left NULL so legacy's " - " page-title placeholder is exercised.
INSERT INTO THE.TREE_TO_TRUCK_REPORT (TREE_TO_TRUCK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_SUPPORT_CENTRE_CODE, ILCR_FOREST_REGION_CODE, BEC_ZONE_CODE, TSA_NUMBER, TSB_NUMBER_CODE, HARVEST_LICENSE_NUMBER, DIVISION_LOCATION, CONTACT_NAME, CONTACT_PHONE_NUMBER, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (6620, 2020, 760, '8', 'SC1', 'R1', 'BZ1', 'TSA5', 'B', 'L760', 'Extract Div', 'Extract Contact', '2505550100', 'Extract page', 0, 'SEED');
INSERT INTO THE.TREE_TO_TRUCK_DETAIL_REPORT (TREE_TO_TRUCK_DETAIL_REPORT_ID, TREE_TO_TRUCK_REPORT_ID, CONTRACTOR_ID, CUT_BLOCK, GROUND_BASE_PCT, GRAPPLE_PCT, SKYLINE_PCT, HIGHLEAD_PCT, HELICOPTER_PCT, OTHER_SKIDDING_PCT, ILCR_SKID_TYPE_CODE, CONIFEROUS_VOLUME, DECIDUOUS_VOLUME, ORIGINAL_TREE_TO_TRUCK_RATE, WATER_DUMP_DESTINATION_IND, UPHILL_DIRECTION_IND, REVISION_COUNT, ENTRY_USERID)
  VALUES (6630, 6620, 'EXC1', 'CB7', 60, 40, 0, 0, 0, 0, 'ST1', 700, 300, 25.00, 'N', 'Y', 0, 'SEED');

-- Mill 760's Schedule 10 (V20260817 shape; audit quartet and REVISION_COUNT NOT NULL, five
-- classification FKs ENABLED, so every code below is one V20260817 seeds). Page 6640 is TSA '01' +
-- TSB '01A', whose derived Road Group legacy pins to "11", and carries ONE road detail; page 6641
-- has NO detail. Legacy's Road Data builder collected the no-detail pages and appended them AFTER
-- every detail row of the section, so 6641's marker must follow 6640's detail row even though the
-- pages themselves are listed in id order. CONSTRUCTION_DIVISION_NAME is VARCHAR2(20).
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_DATE, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (6640, 2020, 760, '10', DATE '2020-06-15', '2020-06', 'Extract Div', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_DATE, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (6641, 2020, 760, '10', DATE '2020-07-15', '2020-07', 'Extract Empty', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, BOULDER_AREA_PCT, ILCR_ROAD_LIFETIME_CODE, ILCR_SOIL_MOISTURE_CODE, RIPPABLE_ROCK_PCT, RELATIVE_SOIL_MOISTUR_RGM_CODE, SOLID_ROCK_PCT, COARSE_MATERIAL_PCT, BECBIOGEO_CATALOGUE_ID, FINE_MATERIAL_PCT, ORGANIC_MATERIAL_PCT, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, SUB_GRADE_SURFACE_WIDTH, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (6650, 6640, 'Extract Mainline', 25, NULL, 'P', 'SM1', 20, 'ASM1', 10, 30, 8801, 25, 15, 2.500, 'N', 'C', 6.5, 'GR', NULL, 'Extract road comment', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Mill 761/2020's ONE silviculture location (V20 shape: the cost children hang off
-- BASIC_SILVICULTURE_REPORT_ID with a NULL summary; item 24 = Actual, 23 = Planned). 5000 + 2500 =
-- 7,500.00 over 100 ha -> 75.00. The mill is V on the main track and D on silviculture, so this
-- row's STATUS cell can only read "Draft" if the Schedule 11 walk builds its context from the
-- silviculture code -- a main-track read would print "Verified" and fail the assertion.
INSERT INTO THE.BASIC_SILVICULTURE_REPORT (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (6660, 2020, 761, '11', 'Extract Block', 8801, 100, 'N', NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, BASIC_SILVICULTURE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9060, NULL, 6660, 24, NULL, 5000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, BASIC_SILVICULTURE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9061, NULL, 6660, 23, NULL, 2500, NULL, 'SEED');
