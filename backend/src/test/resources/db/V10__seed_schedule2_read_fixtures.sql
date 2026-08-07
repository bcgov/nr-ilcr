-- Story 3.1 (Schedule 2 read) seed EXTENSION (never edit earlier migrations). Adds the Schedule 2
-- read fixtures and the cross-schedule sources they carry, pinned to hand-checkable values that
-- match the *IT assertions. Numbered V10: V1-V9 were claimed by other tracks (schedule 3, other
-- costs, home) once main merged in — see README.md. Originally authored as V5.
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
-- item 135 (Subtotal Actual Costs / PO&P cost) is shared master data already defined by V5
-- (schedule 3 crown+admin) with an identical row — referenced below but NOT re-inserted here to avoid
-- an ORA-00001 PK collision (see README.md).
-- (Item 119 Crown Timber is the CROWN_VOLUME column, not a detail row; item 144 already seeded in V2.)

-- ================================================================================================
-- Mill 621 / 2021 — Draft main READ fixture (AC1/AC2). FULLY SELF-CONTAINED: its own cat-1/2/3
-- summaries so Schedule 2's cross-schedule read never collides with schedule 3, which piles its own
-- item-135 row onto the SHARED mill-514 cat-3 summary (1003). Numbered in Schedule 2's 62x mill /
-- 12xx summary block (see README.md). Category-"2" summary + items 25/26; cross-schedule sources
-- (Sch3 118/135 on the cat-3 summary + CROWN_VOLUME 12345; Sch1 144 on the cat-1 summary). Values
-- pinned for clean, hand-checkable derived figures.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (621, 'Sch2 Read Milling', 621, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (621, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 621, 'D', 'SEED');
-- cat-1 (Sch1 source) and cat-3 (Sch3 source + CROWN_VOLUME 12345) summaries owned by this fixture.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ENTRY_USERID) VALUES (1201, 2021, 621, '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CROWN_VOLUME, ENTRY_USERID) VALUES (1203, 2021, 621, '3', 12345, 'SEED');
-- cat-2 summary (Schedule 2's own) + items 25/26.
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1202, 2021, 621, '2', 'Seed Schedule 2 comment for 621/2021', 0, 'SEED');
-- item 25: Purchased/Private Log Costs — COST only (volume carried from Sch3 118).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6030, 1202, 25, NULL, 500000, NULL, 'SEED');
-- item 26: (less) Log Sales — VOLUME 2000 / COST 100000 -> perUnit 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6031, 1202, 26, 2000, 100000, NULL, 'SEED');
-- Sch3 cat-3 summary 1203 (CROWN_VOLUME 12345): PO&P volume + PO&P cost.
-- item 118 PO&P Timber volume = 10000  -> purchasedLogCost.perUnit = 500000/10000 = 50.0
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6032, 1203, 118, 10000, NULL, NULL, 'SEED');
-- item 135 (legacy inert): PO&P actual cost is now the Sch3 Subtotal Actual Costs PO&P column, seeded
-- as real actual-cost lines (items 27/125/37) in V33 — NOT this row. Kept to prove 135 is ignored.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6033, 1203, 135, NULL, 20000, NULL, 'SEED');
-- Sch1 cat-1 summary 1201: item 144 Subtotal Company Logging cost = 617250 (silv items 1/2 in V33).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6034, 1201, 144, NULL, 617250, NULL, 'SEED');

-- Expected derived figures for 621/2021 (asserted by Schedule2DocumentIT; Sch3 actual-cost + Sch1
-- silviculture rows added in V33 — PO&P actual cost 20000, actual-costs crown 100000, silvAdmin
-- crown 5000, Sch1 silvActual 20000 / silvAccrued 8450):
--   purchasedLogCost:      volume 10000, cost 500000, perUnit 50.0
--   purchasedWoodOverhead: volume 10000, cost 20000,  perUnit 2.0    (Sch3 Subtotal Actual Costs PO&P)
--   subtotal:              volume 10000, cost 520000,  perUnit 52.0
--   lessLogSales:          volume 2000,  cost 100000,  perUnit 50.0
--   netPurchased:          volume 8000,  cost 420000,  perUnit 52.5   (10000-2000 ; 520000-100000)
--   totalCompanyLogging:   volume 12345, cost 740700,  perUnit 60.0   (617250+100000+((20000-5000)+8450))
--   totalAverage:          volume 20345, cost 1160700           (8000+12345 ; 420000+740700)

-- ================================================================================================
-- Mill 517 / 2021 — non-Draft (track "S") fixture (AC5) AND missing-Sch3-data fixture (AC "absent
-- Sch3 -> null derived"). Category-"2" summary + stored items 25/26, but NO category-"3" summary and
-- NO Sch1 item-144 row, so every carried/derived figure is null (omitted). editable must be false.
-- ================================================================================================
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1228, 2021, 517, '2', 'Seed Schedule 2 comment for 517/2021', 3, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6010, 1228, 25, NULL, 333000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6011, 1228, 26, 500, 25000, NULL, 'SEED');

COMMIT;
