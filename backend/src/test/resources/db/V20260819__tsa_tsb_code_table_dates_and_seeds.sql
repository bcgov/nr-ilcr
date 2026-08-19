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
-- '99'/'99A' and '16Z' are deliberately NOT seeded: they are the unmapped-TSA and no-TSB-branch
-- fixtures, and leaving them out of the code tables keeps them representative of the legacy rows
-- that motivated the referenced-union leg on every list.
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
