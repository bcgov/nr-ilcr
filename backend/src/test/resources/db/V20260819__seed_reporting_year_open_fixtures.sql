-- Story 24.1 (UC-RY-001) Open Reporting Year fixtures. Adds two RESERVED mills to the shared
-- THE.ILCR_MILL_STATUS_XREF so the open-year IT can prove an ACTIVE mill receives a report-status row
-- for the newly opened year and a CLOSED mill does not, independent of other seeds' mills. IDs 990/991
-- are used by no other seed; neither has an ILCR_MILL_REPORT_STATUS row, so they never appear in the
-- Home mill list until the open-year IT creates one (and its @AfterEach removes it). Runs last
-- (date version > V20260817).
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (990, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (991, 'CLS', 'SEED');

-- THE.ILCR_REPORT_CATEGORY is not modelled by any other slice, so it is created here (last-running
-- fixture) with the exact delivery shape (verified against the DEV database): composite key
-- (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID), a NOT NULL CATEGORY_STATE_CODE and REPORTABLE_DETAIL_IND,
-- and the NOT NULL audit quartet + REVISION_COUNT — so Story 24.1's open-year write is proven against
-- the real constraints. Opening a year pre-seeds one row per active mill per schedule category (Draft,
-- reportable-detail Y).
CREATE TABLE THE.ILCR_REPORT_CATEGORY (
  REPORT_YEAR           NUMBER(10)   NOT NULL,
  ILCR_MILL_ID          NUMBER(10)   NOT NULL,
  ILCR_CATEGORY_ID      VARCHAR2(5)  NOT NULL,
  CATEGORY_STATE_CODE   VARCHAR2(1)  NOT NULL,
  REPORTABLE_DETAIL_IND VARCHAR2(1)  NOT NULL,
  COMMENTS              VARCHAR2(400),
  REVISION_COUNT        NUMBER(10)   NOT NULL,
  ENTRY_USERID          VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP       DATE         NOT NULL,
  UPDATE_USERID         VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP      DATE         NOT NULL,
  CONSTRAINT PK_ILCR_REPORT_CATEGORY PRIMARY KEY (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID)
);
