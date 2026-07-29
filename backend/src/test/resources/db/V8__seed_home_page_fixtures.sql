-- Story 1.1 (Home page) test fixtures. TEST-SCOPE ONLY (AD-2: no runtime DDL).
-- EXTENDS the shared snapshot (V1) and Schedule 1 seed (V2); never forks V1-V4.
--
-- RENUMBERED V5 -> V8 (Story 1.3): the Schedule 3 / Other-Costs backend work merged onto this branch
-- introduced its own V5-V7 seeds, colliding with the Home V5/V6. Home's migrations move AFTER the
-- schedule seeds (V8/V9) so both coexist; only Home-owned files change (branch-ownership split).
--
-- Home's never-enrolled mill is REASSIGNED 522 -> 540: the merged schedule V5 seed already owns mill
-- 522 ("Prefill Milling", enrolled with a 2021 status row), which contradicts Home's need for an
-- xref-but-never-enrolled mill. 540 is a fresh id (seeded range is 514-532) that preserves the
-- exclusion semantics without touching the schedule-owned fixture.
--
-- V2 already seeds mills 514 (ACT), 515 (ACT), 516 (CLS), 517 (ACT) and the single opened
-- reporting year 2021. This migration adds ONE more opened reporting year (2020) so the
-- GET /api/v1/reporting-years descending-order assertion (2021 before 2020, BR-03) is meaningful.
-- Mills are NOT re-seeded here (they already exist from V2).

INSERT INTO THE.ILCR_REPORTING_PERIOD (REPORT_YEAR, REPORT_OFFICIAL_START_DATE, REPORT_OFFICIAL_END_DATE, ENTRY_USERID)
  VALUES (2020, DATE '2020-01-01', DATE '2020-12-31', 'SEED');

-- Mill 540: has its status xref (ACT) but NO ILCR_MILL_REPORT_STATUS row for any year — never
-- enrolled in reporting. Legacy getMills() inner-joins x.millReportStatuses, so such mills are
-- EXCLUDED from the Home selection list (2026-07-21 review decision: match legacy exactly).
-- Exists solely so the mills endpoint test can prove the exclusion.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (540, 'Never Enrolled Milling', 540, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (540, 'ACT', 'SEED');

COMMIT;
