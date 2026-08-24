-- Schedule 6 corrections fixtures. Follows V32's conventions: 82xx PKs would collide here (this
-- task adds no road-record rows, only code-table rows), so no id block is claimed.
--
-- Code-table rows for the code-list endpoints (Task 1). Y9/Y9A are in-window for 2021; X9/X9A are
-- EXPIRED before 2021 and exist to prove the year filter actually excludes something. The IT
-- asserts containment, never exact list equality: the seeded image carries real TSA/TSB rows too.
INSERT INTO THE.TSA_NUMBER_CODE
    (TSA_NUMBER, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
VALUES ('Y9', 'Fixture Timber Supply Area', DATE '1990-01-01', DATE '9999-12-31', SYSTIMESTAMP);

INSERT INTO THE.TSA_NUMBER_CODE
    (TSA_NUMBER, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
VALUES ('X9', 'Fixture Expired TSA', DATE '1990-01-01', DATE '2010-12-31', SYSTIMESTAMP);

INSERT INTO THE.TSB_NUMBER_CODE
    (TSB_NUMBER_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
VALUES ('Y9A', 'Fixture Supply Block A', DATE '1990-01-01', DATE '9999-12-31', SYSTIMESTAMP);

INSERT INTO THE.TSB_NUMBER_CODE
    (TSB_NUMBER_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
VALUES ('X9A', 'Fixture Expired Block', DATE '1990-01-01', DATE '2010-12-31', SYSTIMESTAMP);

-- Task 3 (DELETE) fixtures. Mills 667-669 claimed fresh (661-666 are V32's; no cat-6 rows are added
-- to any existing fixture mill/year, since the check-status and Schedule6DocumentIT suites pin exact
-- record counts there). ROAD_MAINTENANCE_REPORT_ID 8370-8373, ILCR_COST_REPORT_DETAIL_ID 8380-8382
-- (free: V31/V32 claim up to 8362/8362 respectively). Mirrors the V32 "make a mill active" shape:
-- MILL + ILCR_MILL_STATUS_XREF ('ACT') + ILCR_MILL_REPORT_STATUS ('D' for 2021).

-- ============ Mill 667 / 2021 — SOLE record, non-blank COMMENTS ============
-- Deleting 8370 must re-insert a bare placeholder carrying its comment, or the schedule-level
-- general comment is destroyed with it (Schedule6DAO.java:297-309 — the BR-09 delete-side guard).
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (667, 'Sch6 Delete Sole Comment Milling', 667, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (667, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 667, 'D', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8370, 2021, 667, '6', '01', '01B', NULL, 'Comment that must survive the delete', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8380, 8370, 69, 1000, 40000, 'row comment', 'SEED');

-- ============ Mill 668 / 2021 — SOLE record, NULL COMMENTS ============
-- Deleting 8371 must leave NOTHING behind: legacy re-inserts only when the deleted row's comment
-- was non-EMPTY (Schedule6DAO.java:297 via CoreUtil.isNullOrEmptyString, which is empty-aware and
-- NOT trim-aware); a placeholder here would resurrect an empty comment. COMMENTS is NULL here, so
-- this fixture pins the re-insert's OFF branch under either predicate — the whitespace-only case
-- that actually separates them is unit-pinned in Schedule6WriteServiceTest.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (668, 'Sch6 Delete Sole Blank Milling', 668, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (668, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 668, 'D', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8371, 2021, 668, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8381, 8371, 69, 500, 20000, 'no general comment row', 'SEED');

-- ============ Mill 669 / 2021 — TWO records sharing the replicated comment ============
-- Deleting one (8372, WITH an item-69 detail -> proves the child delete precedes the master delete,
-- ORA-02292 otherwise) must leave the other (8373, delivery-real shape: no detail) standing, still
-- carrying the comment, and NO placeholder inserted -- deleting a NON-sole record never touches the
-- BR-09 re-insert branch.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (669, 'Sch6 Delete Non-Sole Milling', 669, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (669, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 669, 'D', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8372, 2021, 669, '6', '01', '01B', NULL, 'Replicated comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8382, 8372, 69, 300, 15000, 'Replication row 1', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8373, 2021, 669, '6', NULL, NULL, '18', 'Replicated comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Task 5 (whole-document PUT) fixtures. Mill 671 (the brief's original choice) is ALREADY TAKEN —
-- Schedule 5's V20260807 write fixtures claim the entire 670-676 mill block — so this task uses
-- fresh mills 724/725 (next free after Schedule 10's 723) and ROAD_MAINTENANCE_REPORT_ID 8390-8398
-- (free: this file's own Task 3 block ends at 8373/8382, and V31/V32 together only reach 8362; all
-- comfortably inside Schedule 6's reserved 8301-8399 id block, see V31's header comment). No
-- ILCR_COST_REPORT_DETAIL rows are seeded here — every row below is delivery-real (no item-69
-- detail), so the save path's upsert always exercises its INSERT branch, and no detail PK is
-- needed at all.
--
-- Order-independence (the Task 3 "one destructive test per (mill, year) context" contract, applied
-- to a single PUT that can mutate rows AND the comment together): every MUTATING save gets its own
-- dedicated year, mirroring mill 661's per-year layout in V32. A REJECTED save (400/404/409) never
-- writes anything, so several read-only probes are deliberately allowed to share one context.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (724, 'Sch6 Save Document Milling', 724, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (724, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2018, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2019, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2022, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2023, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2024, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2025, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2026, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2027, 724, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2028, 724, 'D', 'SEED');

-- 2018: SOLE real record -> the success test (rows written, THEN the general comment, one PUT).
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8390, 2018, 724, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2019: TWO real records -> submitting only one is a 400 (OmittedRoadRecordsException), never a
-- silent skip of the other.
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8391, 2019, 724, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8392, 2019, 724, '6', '03', '03B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2020: ONE real record -> a stale revisionCount on this row must roll back the WHOLE save,
-- including the general comment written after the rows -- the whole point of one transaction.
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8393, 2020, 724, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2021: ZERO records -> an empty list plus a non-blank comment inserts the general-comment
-- placeholder (no fixture row needed; the context itself, Draft with nothing stored, is the seed).

-- 2022: SOLE placeholder record (all-NULL classification, non-blank COMMENTS) -> an empty list
-- plus a blank comment must delete it (BR-09 third branch, chosen from the submitted list here
-- rather than a second DB read, per saveGeneralComments' precedent).
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8394, 2022, 724, '6', NULL, NULL, NULL, 'Lone comment to clear', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2023: ONE real record -> the shared REJECTION-ONLY context (unknown id, null revisionCount,
-- field validation, and this row addressed as "foreign" from the 2024 context below). No test
-- mutates here, so several probes may safely share it.
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8395, 2023, 724, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2024: SOLE placeholder record -> addressing it BY ID in the records[] payload is a 404 (a
-- placeholder is not a served record, so a client can never legitimately target it); also the
-- "foreign id" probe target for 8395 above (a request scoped to 2024 can never touch a 2023 row).
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8396, 2024, 724, '6', NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2025: SOLE placeholder record -> code review 2026-08-21 (C1): an empty records[] plus a NEW
-- comment must be written ONTO this existing placeholder, never inserted as a second row alongside
-- it (legacy Schedule6DAO.java:263,286's `onlyGeneralCommentsExist` guard). The BR-09 branch must
-- key on the STORED rows, not the submitted (always-empty-here) records list.
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8398, 2025, 724, '6', NULL, NULL, NULL, 'original comment', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2026: SOLE real record, delivery-real shape (no item-69 detail) -> Task 8 ports
-- Schedule6WriteIT's retired switchAreaTypeTsaToTfl through the whole-document PUT: TSA->TFL (S19),
-- Supply Block cleared, RMG re-derived, revision bumped, and the detail upsert's INSERT branch
-- (real cat-6 rows have none -- Story 8.1 Task 1).
-- ROAD_MAINTENANCE_REPORT_ID 8374 (final-review correction: was 8401, which sat OUTSIDE Schedule
-- 6's declared 8301-8399 block and inside Schedule 5 read's registered 8401-8438 -- README.md:109.
-- 8374 is free: this file's own Task 3 block ends at 8373/8382, and Task 5's block above tops out
-- at 8399, so 8374-8379 sits unclaimed between the two).
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8374, 2026, 724, '6', '01', '01B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- 2027: SOLE real record WITH an existing item-69 detail -> Task 8 ports Schedule6WriteIT's retired
-- editWithExistingDetail_updatesInPlace: editing a record that already has a detail row must UPDATE
-- it in place (same ILCR_COST_REPORT_DETAIL_ID), never insert a second one alongside it.
-- ROAD_MAINTENANCE_REPORT_ID 8375 / ILCR_COST_REPORT_DETAIL_ID 8383 (same final-review correction:
-- were 8402/8403, both outside their declared blocks. 8383 is free in the detail-id namespace: this
-- file's own Task 3 details end at 8382, so 8383-8389 sits unclaimed before Task 6's 8400 below).
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8375, 2027, 724, '6', '05', '05B', NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8383, 8375, 69, 1000, 50000, 'Seeded 2027 record', 'SEED');

-- 2028: ZERO records -> ports Schedule6GeneralCommentsIT's retired raw-untrimmed-comment proof
-- (no fixture row needed, same as 2021 above: the context itself, Draft with nothing stored, is
-- the seed).

-- ============ Mill 725 / 2021 — non-Draft ('S') track -> the whole-document PUT write-gate 409 ==
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (725, 'Sch6 Save Document Locked Milling', 725, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (725, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 725, 'S', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8397, 2021, 725, '6', '01', '01B', NULL, 'Locked comment.', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Task 6 (Check Status over submitted values) fixtures. Originally placed on mill 671 — which
-- belongs to Schedule 5 (V20260807 write fixtures claim the whole 670-676 block for category '5')
-- — moved in Task 8 onto a fresh mill of its own (726, next free after Task 5's 724/725) rather than
-- borrowing another schedule's fixture mill: no key collision exists today (Schedule 5 owns
-- 671/2021, this used 671/2020), but sharing a mill id across schedules breaks the
-- one-task-owns-its-fixtures discipline every other block in this file follows, and would collide
-- the moment Schedule 5 ever seeds a 671/2020 row. ROAD_MAINTENANCE_REPORT_ID 8399, ILCR_COST_
-- REPORT_DETAIL_ID 8384 (final-review correction: the detail id was 8400, which sat OUTSIDE
-- Schedule 6's declared 8301-8399 block and inside Schedule 5 read's registered 8401-8438 --
-- README.md:109. 8384 is free: it falls in the same 8383-8389 unclaimed span the 2027 block above
-- draws 8383 from). Read-only endpoint (checkStatus mutates nothing), so both IT tests below safely
-- share this one context — no per-test isolation is needed the way a mutating write path would
-- require.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (726, 'Sch6 Check Status Milling', 726, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (726, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 726, 'D', 'SEED');
INSERT INTO THE.ROAD_MAINTENANCE_REPORT (ROAD_MAINTENANCE_REPORT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, TSA_NUMBER, TSB_NUMBER_CODE, TFL_NUMBER_CODE, COMMENTS, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8399, 2020, 726, '6', 'Y9', 'Y9A', NULL, 'stored comment, never touched by check-status', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ROAD_MAINTENANCE_REPORT_ID, ILCR_REPORT_COST_ITEM_ID, VOLUME, COST, COMMENTS, ENTRY_USERID)
  VALUES (8384, 8399, 69, 10, 15000, 'stored detail comment', 'SEED');

COMMIT;
