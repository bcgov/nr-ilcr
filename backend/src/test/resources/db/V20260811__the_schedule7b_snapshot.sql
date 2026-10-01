-- =====================================================================================================
-- V20260811 — Schedule 7B (Culvert Costs, Epic 13 / UC-SCH7B-001) test-scope snapshot + read fixtures.
-- EXTENDS every earlier migration; never edits them.
--
-- A TIMESTAMP version, deliberately, per db/README.md: the next free integer was V35, and V35 was
-- explicitly left untaken because the integer convention has already collided four times. Flyway
-- orders 20260811 after V34 and after V20260807, so this still applies last, and no future merge can
-- force a renumber of it or of anyone else's. Registered ID ranges (verified unused elsewhere):
-- CULVERT_REPORT_ID 7801-7899, ILCR_COST_REPORT_DETAIL_ID 7901-7999, MILL_ID 680-681. NO runtime DDL exists (AD-2) — this is a test-scope snapshot
-- extension only; the delivery THE schema already carries CULVERT_REPORT, ILCR_CULVERT_TYPE_CODE, and
-- the shared sequences.
--
-- A culvert = one THE.CULVERT_REPORT row keyed (ILCR_MILL_ID, REPORT_YEAR, ILCR_CATEGORY_ID='7') — the
-- SAME category-'7' storage Schedule 7A's bridges use, which is why 7A/7B are twin capabilities over
-- one category. Its two costs = THE.ILCR_COST_REPORT_DETAIL rows keyed by CULVERT_REPORT_ID + cost item
-- 77 (material) or 78 (installation), ILCR_REPORT_SUMMARY_ID NULL (list schedule).
--
-- Reuses V2 context mills @ 2021: 514 = ACT + 1-10 track 'D' (Draft, editable); 517 = ACT + 'S'
-- (Submitted, editable:false). Culvert ids 7801-7899, cost-detail ids 7901-7999 (both below the
-- ILCR_REPORT_COMMON_SEQ start 9500 / ILCR_COST_REPORT_DETAIL_SEQ start 9000, so the write-path NEXTVAL
-- ids can never collide). Both sequences already exist (V21 / V4) — not re-created here.
--
-- Mills 680 (multi-year) and 681 are this migration's own, so the REPORT_YEAR predicate on every
-- read/UPDATE/DELETE is falsifiable without touching the shared V2 context mills.
-- =====================================================================================================

-- Per-report FK column on the shared cost-detail table. Delivery DOES carry a real FK constraint here
-- (ILCR_LCRD_CLV_RPT_FK, ENABLED, DELETE_RULE = 'NO ACTION'); it is declared in
-- R__90_cost_detail_bridge_culvert_fks.sql, which Flyway applies after every versioned migration.
-- Deleting a culvert therefore requires children-first ordering.
ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD CULVERT_REPORT_ID NUMBER(10);

-- The culvert parent table (legacy model/CulvertReport.java:36-99). LENGTH is NUMBER(7,1) here to carry
-- the BR-04 range 0.0-999,999.9 at the scale the app actually writes; DELIVERY IS NUMBER(8,2) (verified
-- 2026-08-13), i.e. one decimal WIDER than this snapshot. That is safe in this direction — a value this
-- test schema accepts, delivery accepts — and the app never emits scale 2 because legacy's
-- f:convertNumber mask was `###,##0.0` (messages.properties:206) and Schedule7bService rounds to scale
-- 1 to match. SPAN_SIZE/RISE_SIZE are NUMBER(7) for 0-9,999,999; CULVERT_PIECE_COUNT NUMBER(4) 1-9,999.
CREATE TABLE THE.CULVERT_REPORT (
  CULVERT_REPORT_ID      NUMBER(10)   PRIMARY KEY,
  REPORT_YEAR            NUMBER(10),
  ILCR_MILL_ID           NUMBER(10),
  ILCR_CATEGORY_ID       VARCHAR2(3),
  ILCR_CULVERT_TYPE_CODE VARCHAR2(10),
  SPAN_SIZE              NUMBER(7),
  RISE_SIZE              NUMBER(7),
  LENGTH                 NUMBER(7,1),
  CULVERT_PIECE_COUNT    NUMBER(4),
  COMMENTS               VARCHAR2(4000),
  REVISION_COUNT         NUMBER(10)   DEFAULT 0,
  ENTRY_USERID           VARCHAR2(30),
  ENTRY_TIMESTAMP        TIMESTAMP    DEFAULT SYSTIMESTAMP,
  UPDATE_USERID          VARCHAR2(30),
  UPDATE_TIMESTAMP       TIMESTAMP
);

-- The culvert type code table: code PK + DESCRIPTION, plus the EFFECTIVE_DATE/EXPIRY_DATE pair every
-- ILCR code table carries (delivery: legacy `AbstractILCRCode` @MappedSuperclass maps both columns onto
-- ILCRCulvertTypeCode). The service filters the list to the codes effective on Jan 1 of the reporting
-- year, mirroring legacy `LookUpCaches.getILCRCulvertTypeCodeCache()`.
CREATE TABLE THE.ILCR_CULVERT_TYPE_CODE (
  ILCR_CULVERT_TYPE_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION            VARCHAR2(120),
  EFFECTIVE_DATE         DATE,
  EXPIRY_DATE            DATE
);

-- The legacy code set (Constant.java:600-601 — A, ABL, HE, O, PA, R, VE, WBL). Descriptions for A/ABL/
-- HE/PA/VE/WBL are [UNKNOWN] in the legacy source (seeded in a delivery reference table not vendored),
-- so these test labels are illustrative; only the CODES matter to behaviour, and only 'R' (Round) and
-- 'O' (Others) carry Check Status meaning (BR-07).
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('A',    'Arch',              DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('ABL',  'Arch Bottomless',   DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('HE',   'Horizontal Ellipse', DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('O',    'Others',            DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('PA',   'Pipe Arch',         DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('R',    'Round',             DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('VE',   'Vertical Ellipse',  DATE '1900-01-01', DATE '2999-12-31');
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('WBL',  'Weir Bottomless',   DATE '1900-01-01', DATE '2999-12-31');
-- RETIRED before the 2021 fixture year: must never be offered in the 2021 list, and a write naming it
-- must be rejected (force-selection enforcement).
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('XOLD', 'Retired culvert type', DATE '1900-01-01', DATE '2015-12-31');
-- Effective mid-2021: legacy evaluated the list at JANUARY 1 of the reporting year
-- (CoreUtil.getDate(int)), so a code that only comes into force in June is NOT offered for 2021 and
-- must be rejected on write. This row is what makes effectiveOn() falsifiable — move the as-of instant
-- to any later day in 2021 and MIDYR starts appearing.
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('MIDYR', 'Effective mid-2021', DATE '2021-06-01', DATE '2999-12-31');
-- Expired mid-2021: in force on Jan 1 2021, retired that June. Legacy's Jan-1 evaluation OFFERS it for
-- reporting year 2021 (it was effective at the instant checked), which pins that the filter uses Jan 1
-- rather than "today" or year-end.
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('EXPJUN', 'Expired mid-2021', DATE '1900-01-01', DATE '2021-06-30');
-- NULL bounds encode "no bound" and MUST still be offered: a bare comparison against NULL is false in
-- SQL, so an unguarded filter would drop this row from the dropdown AND make validateCulvertType reject
-- a value already stored on an existing culvert. The query NVLs both bounds; this row pins that.
INSERT INTO THE.ILCR_CULVERT_TYPE_CODE VALUES ('OPEN', 'Never-expiring type', NULL, NULL);

-- Register the two Schedule 7B cost items (category '7'). Items 70-76/79-81 are already registered by
-- V27 (Schedule 7A); 77 and 78 are the 7B pair and are defined once, here.
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (77, 'Culvert Material cost', '7', '4', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (78, 'Culvert Installation cost', '7', '4', 'SEED');

-- ---------------------------------------------------------------------------------------------------
-- 514 / 2021 (Draft) — three culverts, one per Check Status shape.
-- INSERTED OUT OF ID ORDER (7803, 7801, 7802) on purpose: the served list and the 1-based rowCounter
-- that Check Status quotes back to the reporter both depend on ORDER BY CULVERT_REPORT_ID, and with
-- ascending inserts an unordered heap read returns the same sequence — the clause would be
-- unfalsifiable. Drop the ORDER BY and Schedule7bDocumentIT's id/rowCounter assertions now fail.
--   7801 Round, COMPLETE (span present): totalCost 5500 (4000 material + 1500 install). Passes.
--   7802 Others, COMPLETE (comments present, no span — which is FINE for a non-Round type, S26):
--        totalCost 3200 (2500 + 700). Passes.
--   7803 Round, INCOMPLETE — span NULL (S15), length NULL (S17) and install cost NULL (S20), and rise
--        NULL too (which must NOT be flagged for any type, S28). totalCost 900 (material only, install
--        NULL) — pins the null-tolerant total.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7803, 2021, 514, '7', 'R', NULL, NULL, NULL, 1, NULL, 0, 'SEED');
INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7801, 2021, 514, '7', 'R', 1200, 900, 12.5, 3, 'Main haul road', 0, 'SEED');
INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7802, 2021, 514, '7', 'O', NULL, NULL, 8.0, 2, 'Custom box culvert, fabricated on site', 0, 'SEED');

-- 7801 costs (both present).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7901, NULL, 7801, 77, NULL, 4000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7902, NULL, 7801, 78, NULL, 1500, NULL, 'SEED');

-- 7802 costs (both present).
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7903, NULL, 7802, 77, NULL, 2500, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7904, NULL, 7802, 78, NULL, 700, NULL, 'SEED');

-- 7803 costs: material present, install row present but COST NULL — the legacy storage shape for a
-- cleared cost (a NULL row, never a missing row). Check Status must still flag Install Cost (S20), and
-- the total must be 900, not 900+0.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7905, NULL, 7803, 77, NULL, 900, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7906, NULL, 7803, 78, NULL, NULL, NULL, 'SEED');

-- ---------------------------------------------------------------------------------------------------
-- 517 / 2021 (Submitted 'S') — one complete culvert. Read serves editable:false; every write is
-- Draft-gated to 409; Check Status (not Draft-gated) runs and reports all-met.
--   7851 Pipe Arch, COMPLETE: totalCost 2100 (1800 + 300). Type 'PA' is neither R nor O, so neither
--        the span nor the comments check applies — it passes with both blank (S26 + S27 together).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7851, 2021, 517, '7', 'PA', NULL, NULL, 6.5, 4, NULL, 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7951, NULL, 7851, 77, NULL, 1800, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7952, NULL, 7851, 78, NULL, 300, NULL, 'SEED');

-- ---------------------------------------------------------------------------------------------------
-- 680 — MULTI-YEAR fixture, owned solely by Schedule 7B. Two opened Draft reporting years for one
-- mill, each with exactly one culvert. This exists to make the `AND REPORT_YEAR = :year` predicate on
-- findCulverts / countCulvert / updateCulvert / deleteCulvert FALSIFIABLE: with only 2021 seeded
-- anywhere in the snapshot, any other year 404s at the mill/year context guard before the SQL runs, so
-- the predicate could be deleted from all four statements with the whole suite green while a 2021
-- request served, corrected or deleted a mill's 2020 culverts.
--   7861 -> 680 / 2020 (Round, complete, total 300)
--   7862 -> 680 / 2021 (Round, complete, total 700)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (680, 'Culvert Multi-Year Milling', 680, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (680, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 680, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 680, 'D', 'SEED');

INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7861, 2020, 680, '7', 'R', 500, 400, 3.0, 1, 'Prior year culvert', 0, 'SEED');
INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7862, 2021, 680, '7', 'R', 600, 500, 4.0, 2, 'Current year culvert', 0, 'SEED');

INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7961, NULL, 7861, 77, NULL, 200, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7962, NULL, 7861, 78, NULL, 100, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7963, NULL, 7862, 77, NULL, 400, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7964, NULL, 7862, 78, NULL, 300, NULL, 'SEED');

-- ---------------------------------------------------------------------------------------------------
-- 681 — a Draft mill holding ONE culvert whose stored type has since EXPIRED (XOLD, retired 2015), so
-- the unchanged-type exemption in Schedule7bService.validateCulvertType is exercised: correcting some
-- other field on this row, or a page-level Save that resubmits it, must succeed rather than 400 the
-- whole page — while genuinely CHANGING the type to XOLD must still be rejected.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (681, 'Culvert Legacy-Code Milling', 681, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (681, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 681, 'D', 'SEED');

INSERT INTO THE.CULVERT_REPORT (CULVERT_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, ILCR_CULVERT_TYPE_CODE, SPAN_SIZE, RISE_SIZE, LENGTH, CULVERT_PIECE_COUNT, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (7871, 2021, 681, '7', 'XOLD', 700, 600, 5.0, 3, 'Stored with a since-retired type', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7971, NULL, 7871, 77, NULL, 50, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, CULVERT_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (7972, NULL, 7871, 78, NULL, 60, NULL, 'SEED');
