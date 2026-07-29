-- Story 3.2 (Schedule 2 write) seed EXTENSION (never edit earlier migrations). Adds the summary
-- sequence the create-on-absent save path needs and DEDICATED write/delete/concurrency/non-Draft
-- fixtures so the mutating acceptance tests never clobber the read-only Schedule 2 fixtures (514/517)
-- the read *IT classes assert against (the Testcontainers container + data are shared across the
-- whole *IT run). Numbered V11 (paired with the V10 read fixtures) — see README.md. Originally V6.
--
-- ID namespace (see README.md): Schedule 2 owns mills 622-625 and summary ids 12xx so its seed rows
-- never collide with schedule 3 (mill 522) or other costs (mills 523-525, summary 1025) which share
-- the *IT Testcontainers database.
--
-- Schedule 2 divergence: SAVE creates the category-"2" summary when none exists (Schedule 2 never
-- 404s). V4 created ILCR_COST_REPORT_DETAIL_SEQ but intentionally NOT a summary sequence (the guarded
-- Schedule 1 PUT never creates a summary). Schedule 2's create-on-save needs one, added here.

-- Summary sequence for the create-on-first-save path. Must match the sequence the repository draws
-- from: THE.ILCR_REPORT_COMMON_SEQ (Schedule2Repository.java:230 — the real THE sequence legacy
-- ILCRReportSummary uses). Test-scope here; present in the delivery DB.
CREATE SEQUENCE THE.ILCR_REPORT_COMMON_SEQ START WITH 9000 INCREMENT BY 1;

-- ================================================================================================
-- Mill 622 / 2021 — ACT, Draft, EXISTING category-"2" summary (REVISION_COUNT 0) with items 25/26.
-- Dedicated write fixture: update (persist + bump), stale-revision (AC "Save - stale"), clear-to-null
-- (item 25 cost null), and DELETE (summary + 25/26 removed). Cross-schedule sources deliberately
-- absent, so the recomputed echo carries only the stored line items (carried/derived figures null).
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (622, 'Sch2 Write Milling', 622, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (622, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 622, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1222, 2021, 622, '2', 'Seed Schedule 2 write fixture 622/2021', 0, 'SEED');
-- item 25: Purchased/Private Log Costs — COST only.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6022, 1222, 25, NULL, 400000, NULL, 'SEED');
-- item 26: (less) Log Sales — VOLUME 1000 / COST 50000.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6023, 1222, 26, 1000, 50000, NULL, 'SEED');

-- ================================================================================================
-- Mill 623 / 2021 — ACT, non-Draft (track "S") with an existing category-"2" summary. Dedicated
-- non-Draft write-gate fixture: PUT and DELETE -> 409 scheduleNotEditableErrorMsg, no data change.
-- (Kept separate from the read-only 517 fixture so the 409 assertions never race a read assertion.)
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (623, 'Sch2 Submitted Milling', 623, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (623, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 623, 'S', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1223, 2021, 623, '2', 'Seed non-Draft Schedule 2 623/2021', 2, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6024, 1223, 25, NULL, 111000, NULL, 'SEED');

-- ================================================================================================
-- Mill 624 / 2021 — ACT, Draft, EXISTING category-"2" summary. Dedicated fixture for the security-ON
-- authorization happy path (Schedule2WriteAuthorizationIT) so its PUT never collides with mill 622.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (624, 'Sch2 Authz Milling', 624, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (624, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 624, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1224, 2021, 624, '2', 'Seed Schedule 2 authz fixture 624/2021', 0, 'SEED');

-- ================================================================================================
-- Mill 625 / 2021 — ACT, Draft, EXISTING category-"2" summary + items 25/26. Dedicated DELETE fixture
-- so the destructive delete never races the mill-622 update/stale/clear assertions.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (625, 'Sch2 Delete Milling', 625, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (625, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 625, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1225, 2021, 625, '2', 'Seed Schedule 2 delete fixture 625/2021', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6025, 1225, 25, NULL, 90000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6026, 1225, 26, 300, 15000, NULL, 'SEED');

-- NOTE: mill 515/2021 (ACT + Draft, NO category summary; seeded in V2) is reused for the
-- create-on-save path — a PUT there inserts a brand-new category-"2" summary (revision 0 -> 1).

COMMIT;
