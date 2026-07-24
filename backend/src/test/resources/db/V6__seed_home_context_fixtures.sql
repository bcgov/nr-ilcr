-- Story 1.2 (Home page working context) test fixtures. TEST-SCOPE ONLY (AD-2: no runtime DDL).
-- EXTENDS the shared snapshot; V1-V5 untouched.
--
-- ILCR_MILL_REPORT_STATUS_RPT_VW is a VIEW on the delivery DB (legacy maps it via
-- ILCRMillReportStatusRptOv @Table(name="ILCR_MILL_REPORT_STATUS_RPT_VW")). The app only ever
-- SELECTs from it, so the test snapshot stands it in as a TABLE with the columns the legacy model
-- maps. Date columns are STRINGS carrying a 3-character prefix (legacy strips it with
-- substring(3), UserSessionMB.java:374); the delivery format must be confirmed against the real
-- view (story DoD) — these seeds encode the prefix contract the code implements.
CREATE TABLE THE.ILCR_MILL_REPORT_STATUS_RPT_VW (
  REPORT_YEAR              NUMBER(10),
  ILCR_MILL_ID             NUMBER(10),
  ILCR_MILL_STATUS_CODE    VARCHAR2(3),
  MILL_STATUS_OPEN_DATE    VARCHAR2(30),
  MILL_STATUS_DRAFT_DATE   VARCHAR2(30),
  MILL_STATUS_SUBMIT_DATE  VARCHAR2(30),
  MILL_STATUS_VERIFY_DATE  VARCHAR2(30),
  SILVI_STATUS_DRAFT_DATE  VARCHAR2(30),
  SILVI_STATUS_SUBMIT_DATE VARCHAR2(30),
  SILVI_STATUS_VERIFY_DATE VARCHAR2(30)
);

-- Both-tracks fixture: NEW (2020, 514) row — 1-10 Submitted, silviculture Draft. New PK; the V2
-- (2021, 514) row (1-10 'D', silvi NULL) is untouched and remains the null-silvi-code case.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID)
  VALUES (2020, 514, 'S', 'D', 'SEED');

-- Status dates. 514/2021: 1-10 Draft date. 514/2020: 1-10 Submitted date + silvi Draft date.
-- Deliberately NO row for (2021, 516): closed mill's status renders date null -> "Not Initiated" (1.4).
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_DRAFT_DATE)
  VALUES (2021, 514, 'ACT', '01 2021-03-15');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_SUBMIT_DATE, SILVI_STATUS_DRAFT_DATE)
  VALUES (2020, 514, 'ACT', '02 2020-11-30', '01 2020-08-01');

-- S07 pair (515, 2020) needs NO seed: mill 515 is selectable (enrolled via its 2021 row), year 2020
-- is opened (V5), and no ILCR_MILL_REPORT_STATUS row exists for the pair.

COMMIT;
