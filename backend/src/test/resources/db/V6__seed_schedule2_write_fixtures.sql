-- Story 3.2 (Schedule 2 write) seed EXTENSION (never edit V1-V5). Adds the summary sequence the
-- create-on-absent save path needs and DEDICATED write/delete/concurrency/non-Draft fixtures so the
-- mutating acceptance tests never clobber the read-only Schedule 2 fixtures (514/517) the read *IT
-- classes assert against (the Testcontainers container + data are shared across the whole *IT run).
-- V6 is the next free migration number (V1-V5 present).
--
-- Schedule 2 divergence: SAVE creates the category-"2" summary when none exists (Schedule 2 never
-- 404s). V4 created ILCR_COST_REPORT_DETAIL_SEQ but intentionally NOT a summary sequence (the guarded
-- Schedule 1 PUT never creates a summary). Schedule 2's create-on-save needs one, added here.

-- Summary sequence for the create-on-first-save path (present in the delivery DB; test-scope here).
CREATE SEQUENCE THE.ILCR_REPORT_SUMMARY_SEQ START WITH 9000 INCREMENT BY 1;

-- ================================================================================================
-- Mill 522 / 2021 — ACT, Draft, EXISTING category-"2" summary (REVISION_COUNT 0) with items 25/26.
-- Dedicated write fixture: update (persist + bump), stale-revision (AC "Save - stale"), clear-to-null
-- (item 25 cost null), and DELETE (summary + 25/26 removed). Cross-schedule sources deliberately
-- absent, so the recomputed echo carries only the stored line items (carried/derived figures null).
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (522, 'Sch2 Write Milling', 522, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (522, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 522, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1022, 2021, 522, '2', 'Seed Schedule 2 write fixture 522/2021', 0, 'SEED');
-- item 25: Purchased/Private Log Costs — COST only.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6022, 1022, 25, NULL, 400000, NULL, 'SEED');
-- item 26: (less) Log Sales — VOLUME 1000 / COST 50000.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6023, 1022, 26, 1000, 50000, NULL, 'SEED');

-- ================================================================================================
-- Mill 523 / 2021 — ACT, non-Draft (track "S") with an existing category-"2" summary. Dedicated
-- non-Draft write-gate fixture: PUT and DELETE -> 409 scheduleNotEditableErrorMsg, no data change.
-- (Kept separate from the read-only 517 fixture so the 409 assertions never race a read assertion.)
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (523, 'Sch2 Submitted Milling', 523, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (523, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 523, 'S', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1023, 2021, 523, '2', 'Seed non-Draft Schedule 2 523/2021', 2, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6024, 1023, 25, NULL, 111000, NULL, 'SEED');

-- ================================================================================================
-- Mill 524 / 2021 — ACT, Draft, EXISTING category-"2" summary. Dedicated fixture for the security-ON
-- authorization happy path (Schedule2WriteAuthorizationIT) so its PUT never collides with mill 522.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (524, 'Sch2 Authz Milling', 524, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (524, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 524, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1024, 2021, 524, '2', 'Seed Schedule 2 authz fixture 524/2021', 0, 'SEED');

-- ================================================================================================
-- Mill 525 / 2021 — ACT, Draft, EXISTING category-"2" summary + items 25/26. Dedicated DELETE fixture
-- so the destructive delete never races the mill-522 update/stale/clear assertions.
-- ================================================================================================
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (525, 'Sch2 Delete Milling', 525, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (525, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 525, 'D', 'SEED');
INSERT INTO THE.ILCR_REPORT_SUMMARY (ILCR_REPORT_SUMMARY_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, COMMENTS, REVISION_COUNT, ENTRY_USERID)
  VALUES (1025, 2021, 525, '2', 'Seed Schedule 2 delete fixture 525/2021', 0, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6025, 1025, 25, NULL, 90000, NULL, 'SEED');
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_SUMMARY_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, ITEM_DESCRIPTION, ENTRY_USERID) VALUES (6026, 1025, 26, 300, 15000, NULL, 'SEED');

-- NOTE: mill 515/2021 (ACT + Draft, NO category summary; seeded in V2) is reused for the
-- create-on-save path — a PUT there inserts a brand-new category-"2" summary (revision 0 -> 1).

COMMIT;
