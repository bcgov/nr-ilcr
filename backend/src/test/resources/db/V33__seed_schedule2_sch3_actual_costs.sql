-- Schedule 2 read fixture (621/2021) — proper Schedule 3 actual-cost + Schedule 1 silviculture rows.
--
-- Schedule 2 now sources its Schedule-3 figures from Schedule 3's COMPUTED document (Schedule3Service),
-- not ad-hoc stored item reads. So the old approximation in V10 (a persisted "item 135" for PO&P actual
-- cost) is inert — PO&P actual cost is the Schedule 3 Subtotal Actual Costs PO&P column. This migration
-- seeds a real category-'3' actual-cost line so that subtotal computes to the values the
-- Schedule2DocumentIT asserts. It runs after V17 (which seeds the item 27/125 catalog rows) so the
-- ILCR_REPORT_COST_ITEM foreign keys resolve; item 37 (V5) and items 1/2 (Schedule 1) already exist.
--
-- Summary 1203 = 621/2021 category-'3' (CROWN_VOLUME 12345; item 118 PO&P timber volume 10000, in V10).
-- Actual-cost lines:
--   item 27  (Licenses, Fees, Insurance) Harvest 115000, PO&P peer item 125 = 21000  -> crown 94000
--   item 37  (Silviculture Admin, Harvest-only, PO&P forced 0) Harvest 5000          -> crown 5000
-- => Subtotal Actual Costs: PO&P 21000, Crown 99000.  Silviculture Admin crown = 5000.
-- NB: item 125 = 21000 (NOT the inert V10 "item 135" stored 20000) and item 12 = 620000 (NOT the inert
-- V10 item-144 stored 617250) ON PURPOSE — the two must differ so the IT proves the service reads the
-- COMPUTED Schedule 3 / Schedule 1 documents, not the old stored rows (equal values are value-blind).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9500, 1203, 27, NULL, 115000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9501, 1203, 125, NULL, 21000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9502, 1203, 37, NULL, 5000, NULL, 'SEED');
-- Crown Timber volume: Schedule 3 reads item 119 as a DETAIL row (not the summary CROWN_VOLUME), so
-- seed it here (12345) — the V10 summary CROWN_VOLUME is now inert for the computed Schedule 3 read.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9506, 1203, 119, 12345, NULL, NULL, 'SEED');

-- Schedule 1 category-'1' summary 1201: Subtotal Company Logging is now Schedule 1's COMPUTED value
-- (Schedule1Service sums logging items 12-18, + FMA + Other Costs; Schedule 2 uses it minus FMA), NOT
-- the stored item 144. Seed one logging line (item 12) = 620000 so the no-FMA subtotal is 620000 (no
-- FMA / Other Costs seeded for 621). The old item-144 row (V10) is now display-only.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9505, 1201, 12, NULL, 620000, NULL, 'SEED');

-- The two silviculture $ terms of getTotalLoggingCost (both need NULL ITEM_DESCRIPTION — the
-- fixed-line rows). item 1 = Actual $ Spent, item 2 = Accrued less Actual.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9503, 1201, 1, NULL, 20000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL
    (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID)
  VALUES (9504, 1201, 2, NULL, 8450, NULL, 'SEED');
