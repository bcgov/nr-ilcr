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

COMMIT;
