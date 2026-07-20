-- Story 3.1 (Schedule 2 read) seed EXTENSION (never edit V1-V4). Adds the Schedule 2 read fixtures
-- and the cross-schedule sources they carry, pinned to hand-checkable values that match the *IT
-- assertions. V5 is the next free migration number (V1-V4 present).
--
-- Cost-item ids (legacy Constant.REPORT_COST_ITEMS): Sch2 25 (Purchased/Private Log Costs, cost),
-- 26 (less Log Sales, volume+cost); Sch3 118 (PO&P Timber volume), 135 (PO&P actual cost); Sch1 144
-- (Subtotal Company Logging). Sch3 Crown Timber volume (119) is the CROWN_VOLUME summary column.
--
-- STORAGE-SHAPE RESOLUTION (Ask-First #1): the legacy Schedule 3 model computes PO&P volume/cost and
-- Crown cost from a graph of category-3 cost types (Schedule3DO.getPopTimber()/getSubtotalActualCosts()),
-- and getReportSummaryID() never returns null. No Schedule 3 backend exists yet. Per Story 3.1's
-- cross-schedule-reads-not-features decision, the carried figures are sourced from the pinned
-- persisted figures the schema already supports: item 118 (PO&P volume) and item 135 (PO&P cost) as
-- ILCR_COST_REPORT_DETAIL rows on the category-"3" summary; item 119 (Crown volume) as the
-- ILCR_REPORT_SUMMARY.CROWN_VOLUME column on the category-"3" summary (the same pattern Story 1.2 used
-- for Crown); Sch1 item 144 as a detail row on the category-"1" summary.
--
-- NO-SUMMARY FIXTURE RESOLUTION (Ask-First #2): mill 515/2021 (seeded in V2 as ACT + 1-10 track "D"
-- with a report-status row but no category summary) is reused for the AC6 empty-editable-document
-- path. It has no category-"2" summary and no Schedule 3 data, so all blocks come back empty.

-- New cost items used by Schedule 2 + its cross-schedule sources.
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (25,  'Purchased/Private Log Costs', '2', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (26,  'Less Log Sales', '2', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (118, 'Privately Owned/Purchased PO&P Timber', '3', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (135, 'Subtotal Actual Costs (PO&P)', '3', '1', 'SEED');
-- (Item 119 Crown Timber is the CROWN_VOLUME column, not a detail row; item 144 already seeded in V2.)

-- ================================================================================================
-- Mill 514 / 2021 — Draft main fixture (AC1/AC2). Category-"2" summary + items 25/26; cross-schedule
-- sources (Sch3 118/135 detail rows on summary 1003, CROWN_VOLUME 12345 already on 1003; Sch1 144 on
-- summary 1001). Values pinned for clean, hand-checkable derived figures.
-- ================================================================================================
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1002, 2021, 514, '2', 'Seed Schedule 2 comment for 514/2021', 0, 'SEED');
-- item 25: Purchased/Private Log Costs — COST only (volume carried from Sch3 118).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6001, 1002, 25, NULL, 500000, NULL, 'SEED');
-- item 26: (less) Log Sales — VOLUME 2000 / COST 100000 -> perUnit 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6002, 1002, 26, 2000, 100000, NULL, 'SEED');

-- Sch3 (category-"3" summary 1003, seeded in V2 with CROWN_VOLUME 12345): PO&P volume + PO&P cost.
-- item 118 PO&P Timber volume = 10000  -> purchasedLogCost.perUnit = 500000/10000 = 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6003, 1003, 118, 10000, NULL, NULL, 'SEED');
-- item 135 PO&P actual cost = 20000 -> purchasedWoodOverhead.perUnit = 20000/10000 = 2.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6004, 1003, 135, NULL, 20000, NULL, 'SEED');

-- Sch1 (category-"1" summary 1001): item 144 Subtotal Company Logging cost = 617250
--   -> totalCompanyLogging.perUnit = 617250 / 12345 (Crown vol) = 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6005, 1001, 144, NULL, 617250, NULL, 'SEED');

-- Expected derived figures for 514/2021 (asserted by Schedule2DocumentIT):
--   purchasedLogCost:      volume 10000, cost 500000, perUnit 50.0
--   purchasedWoodOverhead: volume 10000, cost 20000,  perUnit 2.0
--   subtotal:              volume 10000, cost 520000,  perUnit 52.0
--   lessLogSales:          volume 2000,  cost 100000,  perUnit 50.0
--   netPurchased:          volume 8000,  cost 420000,  perUnit 52.5   (10000-2000 ; 520000-100000)
--   totalCompanyLogging:   volume 12345, cost 617250,  perUnit 50.0
--   totalAverage:          volume 20345, cost 1037250            (8000+12345 ; 420000+617250)

-- ================================================================================================
-- Mill 517 / 2021 — non-Draft (track "S") fixture (AC5) AND missing-Sch3-data fixture (AC "absent
-- Sch3 -> null derived"). Category-"2" summary + stored items 25/26, but NO category-"3" summary and
-- NO Sch1 item-144 row, so every carried/derived figure is null (omitted). editable must be false.
-- ================================================================================================
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1028, 2021, 517, '2', 'Seed Schedule 2 comment for 517/2021', 3, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6010, 1028, 25, NULL, 333000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6011, 1028, 26, 500, 25000, NULL, 'SEED');

COMMIT;
