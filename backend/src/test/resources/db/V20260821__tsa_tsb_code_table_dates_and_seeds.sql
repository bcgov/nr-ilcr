-- TSA / TSB code tables: bring the test snapshot up to the delivery shape and seed the Schedule 10
-- location options.
--
-- THE.TSA_NUMBER_CODE and THE.TSB_NUMBER_CODE were created by the Schedule 8 snapshot (V22:92-93) as
-- code + DESCRIPTION only. Delivery carries the standard code-table quartet -- DESCRIPTION plus
-- EFFECTIVE_DATE / EXPIRY_DATE / UPDATE_TIMESTAMP -- because both entities extend AbstractILCRCode
-- and legacy year-filters every code cache on EFFECTIVE_DATE <= 1-Jan-{year} <= EXPIRY_DATE
-- (LookupCache.java:77-98). Serving these two as Schedule 10 dropdown lists is the first consumer
-- that reads the dates, so the understatement surfaces here.
--
-- Columns are added with DEFAULTs so the rows V22 already seeded are backfilled in place; NOT NULL
-- then matches delivery, and an insert that skips a date fails here exactly as it would there.

ALTER TABLE THE.TSA_NUMBER_CODE ADD (
  EFFECTIVE_DATE   DATE DEFAULT DATE '1900-01-01' NOT NULL,
  EXPIRY_DATE      DATE DEFAULT DATE '9999-12-31' NOT NULL,
  UPDATE_TIMESTAMP DATE DEFAULT SYSDATE           NOT NULL
);

ALTER TABLE THE.TSB_NUMBER_CODE ADD (
  EFFECTIVE_DATE   DATE DEFAULT DATE '1900-01-01' NOT NULL,
  EXPIRY_DATE      DATE DEFAULT DATE '9999-12-31' NOT NULL,
  UPDATE_TIMESTAMP DATE DEFAULT SYSDATE           NOT NULL
);

-- The TSA numbers and supply blocks the Schedule 10 read fixtures store on their pages, so each
-- stored location resolves to an option in its own dropdown.
--
-- '99'/'99A' are deliberately NOT seeded: they are the unmapped-TSA fixtures, and a code with no row
-- at all is a real delivery state the dropdown has to survive.
--
-- CORRECTED 2026-08-20 (code review H5): this block previously left '16Z' unseeded too, on the stated
-- grounds that doing so kept it "representative of the legacy rows that motivated the referenced-union
-- leg". That is backwards. The union leg reads
--   SELECT ... FROM THE.TSB_NUMBER_CODE WHERE <date window> OR TSB_NUMBER_CODE IN (<referenced>)
-- so it can only rescue a code that HAS a row and fell outside the date window. A code absent from the
-- table can never be selected by it, however many pages reference it — so the unseeded fixtures
-- exercised nothing, and the leg had no coverage on either list. '16Z' is now seeded EXPIRED and is
-- referenced by page 8904 (mill 712), which is the one shape that does exercise it.
--
-- A code that is absent ENTIRELY still cannot be served, which is why the FRONTEND synthesises the
-- stored code as its own option (review H2) rather than relying on the backend for it.
INSERT INTO THE.TSA_NUMBER_CODE (TSA_NUMBER, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('01', 'Arrow TSA', DATE '1900-01-01', DATE '9999-12-31', SYSDATE);
INSERT INTO THE.TSA_NUMBER_CODE (TSA_NUMBER, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('16', 'Lakes TSA', DATE '1900-01-01', DATE '9999-12-31', SYSDATE);

-- An expired TSA, to pin that the year filter drops a code no stored page references.
INSERT INTO THE.TSA_NUMBER_CODE (TSA_NUMBER, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('90', 'Retired TSA', DATE '1900-01-01', DATE '2010-12-31', SYSDATE);

INSERT INTO THE.TSB_NUMBER_CODE (TSB_NUMBER_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('01A', 'Arrow TSA Block A', DATE '1900-01-01', DATE '9999-12-31', SYSDATE);
INSERT INTO THE.TSB_NUMBER_CODE (TSB_NUMBER_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('16G', 'Lakes TSA Block G', DATE '1900-01-01', DATE '9999-12-31', SYSDATE);

-- An EXPIRED block that page 8904 (mill 712) still references: the only fixture that makes the
-- referenced-union leg falsifiable. Out of the date window, so the date predicate alone drops it; in
-- the referenced set for mill 712, so the union leg brings it back for that mill and no other.
INSERT INTO THE.TSB_NUMBER_CODE (TSB_NUMBER_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('16Z', 'Lakes TSA Block Z (retired)', DATE '1900-01-01', DATE '2010-12-31', SYSDATE);
