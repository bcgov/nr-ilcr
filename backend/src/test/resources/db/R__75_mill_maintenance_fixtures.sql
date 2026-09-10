-- Fixtures for mill administration (UC-MILL-001): search, import, activate/deactivate and the
-- head-office/contact save. Mill id block 750-756, claimed here and recorded in db/README.md.
--
-- BAND 75 IS LOAD-BEARING, not cosmetic. R__70 gives the canonical test submitter an ACTIVE mill
-- assignment for every ILCR_MILL_STATUS_XREF row that exists AT THE MOMENT IT RUNS, set-based. A
-- fixture in a band below 70 therefore inherits an active assignment it never asked for, and the
-- deactivation guard (BR-01: a mill with active users cannot be closed) would refuse every mill in
-- this file -- making the guard's SUCCESS path unreachable and its failure path a false pass.
-- Sitting at 75 means these mills start with no assignments, and mill 752 gets the one assignment
-- this area needs, explicitly, below.
--
-- NO ROW HERE TOUCHES REPORT YEAR 2021. That is deliberate and easy to undo by accident. The 2021
-- ILCR_MILL_REPORT_STATUS rows are exactly five (514 plus R__40's 730-733) and MillReportStatusIT
-- asserts that count directly, so a sixth row seeded here would redden a passing test in another
-- area. The activate path needs a mill that already HAS a current-year status row; that row is
-- created by the test that needs it and removed in its @AfterEach, which is the pattern
-- V20260819 established for mills 990-992.
--
-- Consequence of carrying no report-status row: these mills are invisible to GET /api/v1/mills, which
-- inner-joins report statuses to build the Home selection list (mill 540 is the existing worked
-- example). That is correct -- the maintain-mills surface is admin-scoped and addresses mills by id,
-- not through the Home context (AD-4).

-- 750 and 756: ministry mills with NO cross-reference row, the only shape the import anti-join can
-- return (BR-04 offers only untracked mills; MillDAO.java:58-65). Every other mill in this snapshot
-- is 1:1 with a cross-reference, so without these the importable list is always empty and the import
-- path has nothing to act on. Two of them, so the list can prove its mill-number ordering.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (750, 'UNTRACKED MINISTRY MILL', 7500, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (756, 'UNTRACKED MINISTRY MILL TWO', 7560, 'SEED');

-- 751: ACTIVE with no assignments -- the deactivation SUCCESS path.
-- Its name is deliberately MIXED CASE, and it is the only mill in this snapshot that is. Legacy
-- upper-cased the search parameter but not the column (MillDAO.java:48 vs :73), so a name search was
-- effectively case-sensitive and matched nothing unless the stored name happened to be upper-case --
-- which, in this snapshot, every other name is. Searching "cariboo" against this row is what tells
-- the two behaviours apart.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (751, 'Cariboo Maintain Mill', 7510, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF
    (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (751, 'ACT', 'SEED');

-- 752: ACTIVE with one ACTIVE assignment -- the deactivation BLOCKED path (BR-01/S12).
-- The assignment is inserted here rather than inherited from R__70 because this file runs after it.
-- The canonical submitter's ILCR_USER row already exists (R__70), which the FK to ILCR_USER requires.
-- Active means INACTIVE_DATE IS NULL, the convention MillUserXrefEntity.isActive() owns.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (752, 'MAINTAIN MILL WITH ACTIVE USER', 7520, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF
    (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (752, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_USER_XREF
    (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
     ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (752, 'CANONSUBMITTERBBBBCCCCDDDD000001', SYSDATE, NULL, 0,
          'SEED', SYSDATE, 'SEED', SYSDATE);

-- 753 and 754: CLOSED mills, the activation path. Neither carries a current-year status row, so both
-- start on the "records must be created" branch of BR-07. Two of them so the two branches of that
-- rule -- create-when-missing and leave-alone-when-present -- never share a row, and neither test
-- depends on the other's cleanup having run.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (753, 'CLOSED MILL AWAITING RECORDS', 7530, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF
    (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (753, 'CLS', 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID)
  VALUES (754, 'CLOSED MILL WITH RECORDS', 7540, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF
    (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID)
  VALUES (754, 'CLS', 'SEED');

-- 755: ACTIVE, linked to a client location that has two contacts -- the head-office/contact save.
-- BR-09 limits both selections to the contacts of the mill's own client location, reached in legacy
-- through mill.clientLocation.clientContact (mills.xhtml:74-78). Contact 7561 below belongs to a
-- DIFFERENT client location and is the negative case: it exists, so it cannot be rejected as unknown,
-- and only the membership rule keeps it out.
-- HEAD_OFFICE_CONTACT_IND starts NULL and both contact columns start empty, which is the common
-- shape in real data (only 4 of 17 real mills carry contacts) and makes the first save a transition
-- from nothing rather than an overwrite.
INSERT INTO THE.MILL
    (MILL_ID, MILL_NAME, MILL_NUMBER, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (755, 'MAINTAIN MILL WITH CONTACTS', 7550, '00075501', '00', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF
    (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, ENTRY_USERID)
  VALUES (755, 'ACT', NULL, 'SEED');

INSERT INTO THE.CLIENT_LOCATION
    (CLIENT_NUMBER, CLIENT_LOCN_CODE, CLIENT_LOCN_NAME, ADDRESS_1, CITY)
  VALUES ('00075501', '00', 'MAINTAIN MILL HOLDINGS LTD', '300 THIRD AVENUE', 'KAMLOOPS');
INSERT INTO THE.CLIENT_LOCATION
    (CLIENT_NUMBER, CLIENT_LOCN_CODE, CLIENT_LOCN_NAME, ADDRESS_1, CITY)
  VALUES ('00075601', '00', 'UNRELATED HOLDINGS LTD', '400 FOURTH AVENUE', 'VERNON');

-- Two contacts on the mill's own location. CONTACT_NAME is the option label and CLIENT_CONTACT_ID
-- the option value (mills.xhtml:75-78, :90-93). The names sort in the reverse of their ids so an
-- ordering assertion cannot pass by accident on insert order.
INSERT INTO THE.CLIENT_CONTACT
    (CLIENT_CONTACT_ID, CLIENT_NUMBER, CLIENT_LOCN_CODE, BUS_CONTACT_CODE, CONTACT_NAME)
  VALUES (7551, '00075501', '00', 'BL', 'ZAMORA HEAD OFFICE');
INSERT INTO THE.CLIENT_CONTACT
    (CLIENT_CONTACT_ID, CLIENT_NUMBER, CLIENT_LOCN_CODE, BUS_CONTACT_CODE, CONTACT_NAME)
  VALUES (7552, '00075501', '00', 'DV', 'ABBOTT DIVISION');
-- A real contact on another client's location: the BR-09 negative case.
INSERT INTO THE.CLIENT_CONTACT
    (CLIENT_CONTACT_ID, CLIENT_NUMBER, CLIENT_LOCN_CODE, BUS_CONTACT_CODE, CONTACT_NAME)
  VALUES (7561, '00075601', '00', 'BL', 'FOREIGN CONTACT');
