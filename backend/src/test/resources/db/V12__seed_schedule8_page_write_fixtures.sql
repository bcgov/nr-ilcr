-- Story 14.2 (Schedule 8 page write) test fixtures. DEDICATED mills so the mutating PUT/DELETE cases
-- never clobber the read-only V11 fixtures (570/571/572). Mirrors the Schedule 4 V8 write-fixture
-- approach: each mill serves one destructive concern; cases read REVISION_COUNT at runtime to stay
-- order-independent. Never edit V1–V11. V12 is the next free migration number.

-- ================================================================================================
-- 580 / 2021 — ACT, Draft, NO pages: the create-page target (S01/S02 create + required-field 400s +
-- TFL/supply-block normalization, all asserted per-call so accumulation across cases is harmless).
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (580, 'Sch8 Create Milling', 580, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (580, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 580, 'D', 'SEED');

-- ================================================================================================
-- 581 / 2021 — ACT, Draft, one existing page 8800 (TSA variant, rev 0): edit-in-place + stale-409.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (581, 'Sch8 Edit Milling', 581, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (581, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 581, 'D', 'SEED');
INSERT INTO THE.TREE_TO_TRUCK_REPORT (TREE_TO_TRUCK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_SUPPORT_CENTRE_CODE, ILCR_FOREST_REGION_CODE, BEC_ZONE_CODE, TSA_NUMBER, TSB_NUMBER_CODE, HARVEST_LICENSE_NUMBER, DIVISION_LOCATION, REVISION_COUNT, ENTRY_USERID)
  VALUES (8800, 2021, 581, '8', 'SC1', 'R1', 'BZ1', 'TSA5', 'B', 'L581', 'Edit Div', 0, 'SEED');

-- ================================================================================================
-- 582 / 2021 — ACT, Draft, a full page 8810 -> sample 8811 -> rate 8812 hierarchy: delete cascade
-- (page + sample + rate all removed) + idempotent re-delete.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (582, 'Sch8 Delete Milling', 582, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (582, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 582, 'D', 'SEED');
INSERT INTO THE.TREE_TO_TRUCK_REPORT (TREE_TO_TRUCK_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_SUPPORT_CENTRE_CODE, ILCR_FOREST_REGION_CODE, TSA_NUMBER, HARVEST_LICENSE_NUMBER, REVISION_COUNT, ENTRY_USERID)
  VALUES (8810, 2021, 582, '8', 'SC1', 'R1', 'TSA5', 'L582', 0, 'SEED');
INSERT INTO THE.TREE_TO_TRUCK_DETAIL_REPORT (TREE_TO_TRUCK_DETAIL_REPORT_ID, TREE_TO_TRUCK_REPORT_ID, CONTRACTOR_ID, GROUND_BASE_PCT, CONIFEROUS_VOLUME, ORIGINAL_TREE_TO_TRUCK_RATE, REVISION_COUNT, ENTRY_USERID)
  VALUES (8811, 8810, 'CDEL', 100, 400, 12.00, 0, 'SEED');
INSERT INTO THE.TREE_TO_TRUCK_RATE_DETAIL (TREE_TO_TRUCK_RATE_DETAIL_ID, TREE_TO_TRUCK_DETAIL_REPORT_ID, ILCR_RATE_COST_TYPE_CODE, ILCR_REPORT_COST_ITEM_ID, ITEM_DESCRIPTION, COSTING_RATE, REVISION_COUNT, ENTRY_USERID)
  VALUES (8812, 8811, 'CT1', 82, 'Add D', 3.00, 0, 'SEED');

-- ================================================================================================
-- 583 / 2021 — ACT, non-Draft (track 'S'): the write-gate 409 (requireDraft fires before persist).
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (583, 'Sch8 Submitted Milling', 583, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (583, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 583, 'S', 'SEED');

COMMIT;
