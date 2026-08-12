-- Story 24.3 (UC-CODE-001) code-table maintenance fixtures. Creates ONE registry table with the full
-- AbstractILCRCode shape (code PK, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP) so the
-- generic CodeTableRepository read + upsert have a real table to exercise. ILCR_UNIT_CODE is created
-- by no other seed and read/written by no other IT, so this repository IT's writes stay isolated in
-- the shared (JVM-wide) Testcontainer. Runs last (date version > V20260807).
CREATE TABLE THE.ILCR_UNIT_CODE (
  ILCR_UNIT_CODE   VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION      VARCHAR2(120),
  EFFECTIVE_DATE   DATE,
  EXPIRY_DATE      DATE,
  UPDATE_TIMESTAMP TIMESTAMP
);

INSERT INTO THE.ILCR_UNIT_CODE (ILCR_UNIT_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('M3', 'Cubic Metres', DATE '2000-01-01', NULL, SYSTIMESTAMP);
INSERT INTO THE.ILCR_UNIT_CODE (ILCR_UNIT_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('TON', 'Tonnes', DATE '2000-01-01', DATE '2020-12-31', SYSTIMESTAMP);
