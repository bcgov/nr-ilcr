-- Story 1.1 (Home page) test fixtures. TEST-SCOPE ONLY (AD-2: no runtime DDL).
-- EXTENDS the shared snapshot (V1) and Schedule 1 seed (V2); never forks V1-V4.
--
-- V2 already seeds mills 514 (ACT), 515 (ACT), 516 (CLS), 517 (ACT) and the single opened
-- reporting year 2021. This migration adds ONE more opened reporting year (2020) so the
-- GET /api/v1/reporting-years descending-order assertion (2021 before 2020, BR-03) is meaningful.
-- Mills are NOT re-seeded here (they already exist from V2).

INSERT INTO THE.ILCR_REPORTING_PERIOD (REPORT_YEAR, REPORT_OFFICIAL_START_DATE, REPORT_OFFICIAL_END_DATE, ENTRY_USERID)
  VALUES (2020, DATE '2020-01-01', DATE '2020-12-31', 'SEED');

COMMIT;
