-- Reference data for THE.ILCR_MILL_STATUS_CODE (created by V20260909).
--
-- REPEATABLE (R__), not versioned: seed data carries no version (db/README.md). Band 41 sits with
-- R__40, which seeds the other code table this area needs (APPRAISAL_SELL_PRICE_ZONE_CODE), and
-- comfortably before band 90+ where R__91 adds the foreign key that requires these rows to exist.
--
-- Both rows copied verbatim from delivery on 2026-09-09, descriptions included. Two things worth
-- knowing: the domain really is just these two codes -- legacy's Constant.MILL_STATUS_CODES enum
-- (ACT, CLS) matches the table exactly -- and CLS reads "Close", not "Closed". That text was an open
-- item in the recovered requirements (UC-MILL-001-technical.md records the rendered status label as
-- unconfirmable from source, because legacy rendered ilcrMillStatusCode.description straight from
-- this table and no source file or bundle carries it). It is now confirmed, and it is the label any
-- screen showing a mill's status should render.
INSERT INTO THE.ILCR_MILL_STATUS_CODE
    (ILCR_MILL_STATUS_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('ACT', 'Active', DATE '2016-03-16', DATE '2099-12-31', DATE '2016-03-16');
INSERT INTO THE.ILCR_MILL_STATUS_CODE
    (ILCR_MILL_STATUS_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('CLS', 'Close', DATE '2016-03-16', DATE '2099-12-31', DATE '2016-03-16');
