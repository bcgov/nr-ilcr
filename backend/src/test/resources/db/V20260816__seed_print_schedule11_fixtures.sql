-- Story 20.2 (combined Print Schedules PDF) seed EXTENSION (never edit an earlier migration, AR8).
-- Timestamped version (db/README.md escape hatch): the next free timestamp after V20260815.
--
-- Purpose: give mill 517 / 2021 data across ALL SIX in-scope print schedules so PrintScheduleIT can
-- assemble one combined PDF and assert one section per selected schedule. Mill 517 / 2021 already
-- carries Schedule 5 (V34), Schedule 6 (V31), Schedule 7A (V27), Schedule 7B (V20260811) and
-- Schedule 9 (V20260813) rows; the ONLY in-scope schedule it lacks is Schedule 11. This migration
-- adds two Schedule 11 locations (with cost children) for 517 / 2021, reusing the biogeo catalogue
-- rows seeded in V20 (8801 -> 'ICHdw1', 8802 -> 'CWHvm').
--
-- Mill 517 (NOT 514) is chosen deliberately: Schedule11DocumentIT pins 514 / 2021 as the
-- NULL-silviculture-code / zero-locations case, so adding locations there would regress it. 517 is
-- Submitted (track 'S'); editability does not gate printing (read-only, BR-01), so the section still
-- renders. Mill 514 / 2021 therefore stays Schedule-11-empty and drives PrintScheduleIT's skip-empty
-- case (select Schedule 5 + 11 -> 5 prints, 11 omitted). The all-empty ERR-005 case uses mill
-- 515 / 2021 (a valid, ACTIVE Draft context with zero rows in every schedule).
--
-- Test-scope id ranges (free gaps under the 9000/9500 sequence starts): BASIC_SILVICULTURE_REPORT_ID
-- 9301-9303, ILCR_COST_REPORT_DETAIL_ID 6301-6304. Category '11'.

-- Two Schedule 11 locations for 517 / 2021, inserted OUT OF ID ORDER so a missing ORDER BY cannot
-- pass: rows go in 9302, 9301 and the section must serve 9301 then 9302.
--   9301 Cedar Ridge Reforest -- ICHdw1, NAR 40.0, both costs -> actual 20000 + planned 12000 = 32000
--   9302 Bare Slope Planting  -- CWHvm,  NAR 15.5, planned only 8000 (actual absent -> null-tolerant)
INSERT INTO THE.BASIC_SILVICULTURE_REPORT (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (9302, 2021, 517, '11', 'Bare Slope Planting', 8802, 15.5, 'N', NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, BASIC_SILVICULTURE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (6303, 9302, 23, NULL, 8000, NULL, 'SEED');

INSERT INTO THE.BASIC_SILVICULTURE_REPORT (BASIC_SILVICULTURE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, LOCATION, BECBIOGEOCLIMATIC_CATALOGUE_ID, REFORESTED_NET_AREA, ENHANCED_IND, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (9301, 2021, 517, '11', 'Cedar Ridge Reforest', 8801, 40.0, 'Y', 'Enhanced silviculture block.', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, BASIC_SILVICULTURE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (6301, 9301, 24, NULL, 20000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, BASIC_SILVICULTURE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (6302, 9301, 23, NULL, 12000, NULL, 'SEED');
