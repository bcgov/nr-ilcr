-- ============================================================================
-- UC-MILL-001 (Maintain Mills) — the dedicated mills every mutating mills
-- scenario except S01 runs on. One mill per concern, so parallel scenarios never
-- share a row:
--   26050  9181 E2E-DEACTIVATE-TEST      ACT, no users                 -> S03
--   26051  9182 E2E-ACTIVATE-TEST        CLS, no users                 -> S04
--   26052  9183 E2E-BLOCKED-TEST         ACT, one ACTIVE licensee      -> S12
--   26053  9184 E2E-USERS-TEST           ACT, one ACTIVE licensee      -> S08
--   26054  9185 E2E-CLOSED-USERS-TEST    CLS, one ENDED licensee       -> S13
--   26055  9186 E2E-READONLY-USERS-TEST  ACT, one ACTIVE licensee      -> S07, S10 (read-only)
--   26056  9187 E2E-ADD-USER-TEST        ACT, no users                 -> S05
--   26057  9188 E2E-IMPORT-TEST          THE.MILL row ONLY (importable) -> S02, S14
--   26058  9189 E2E-ACTIVATE-USER-TEST   ACT, one ENDED licensee       -> S09
--   26059  9190 E2E-CONTACTS-TEST        ACT, both contacts set        -> GAP-1 (clear a contact)
--   26060  9191 E2E-STATUS-SEARCH-TEST   CLS, never written            -> GAP-4 (read-only)
--   26061  9192 E2E-ENROL-TEST           CLS, NO current-year records  -> GAP-6 (activate enrols)
--   26062  9193 E2E-PARTIAL-TEST         CLS, PARTIAL current year     -> GAP-6 (activate refused)
--   26063  9194 E2E-ADD-NEW-USER-TEST    ACT, no users                 -> GAP-7 (add, no account yet)
--
-- WHY REAL DATA FELL SHORT
-- A status change cannot land on any mill another domain reads. A closed mill
-- answers 409 on every schedule (BR-06), so deactivating a schedule anchor
-- mid-run fails the sch1..sch6 scenarios on it. Of the extract's 21 tracked
-- mills every ACT one is a schedule anchor, and all of them carry active user
-- associations (S03 needs none). The one unpinned mill, 14050, is CLS with NO
-- current-year report records — activating it would ENROL it (twelve new rows
-- no endpoint can remove), so S04 on it could never be undone. And the extract
-- has NO importable mill at all: every THE.MILL row is already tracked, so the
-- importable search is empty (S02). Surveyed 2026-09-28.
--
-- WHY EACH TRACKED MILL CARRIES A COMPLETE CURRENT-YEAR RECORD SET
-- Activate enrols a mill whose current-year set is ABSENT and refuses one whose
-- set is PARTIAL (MillMaintenanceService.activate). With the whole set present
-- it writes the status alone, which is what makes every status scenario's
-- cleanup an exact API round trip. "Current" is MAX(ILCR_REPORTING_PERIOD
-- .REPORT_YEAR) (ReportingYearService.currentReportingYear) — derived below.
-- 26057 carries NOTHING but its THE.MILL row: importing it is what creates its
-- xref and records, and the S02/S14 cleanup deletes exactly those
-- (scripts/mill_db_restore.py forget-import).
--
-- THE USERS are synthetic GUIDs (never real directory identifiers), all role
-- LICENSEE. An ENDED association is ACTIVE_DATE set + INACTIVE_DATE set, the
-- shape the app's own deactivate leaves. ...0006 is an ILCR_USER with NO
-- association: S05 adds it to 26056, and because its account row already exists
-- the add writes one association row only, which the S05 cleanup deletes
-- (mill_db_restore.py drop-association). Every other user write is reversible
-- through the API.
--
-- The client location is 25050's (00001500/00): THE.MILL requires one here
-- (NOT NULL + MILL_CL_FK), and borrowing an existing location adds no row.
--
-- SENTINEL: ENTRY_USERID = 'E2E_SEED_MILLSTAT' on every row — deliberately NOT
-- 'E2E_SEED_MOCKUSER'. common/mock-submitter-associations.sql associates the
-- mock submitter with EVERY xref row and skips exactly this sentinel; without
-- that, a re-apply would give S03's mill an active user and S03 would be
-- refused. The CI seed makes the same exclusion (see R__80).
--
-- FOLDED INTO THE CI SEED in the same change: db-e2e/R__80_e2e_anchor_seed.sql,
-- "Mill administration — the dedicated mills". Pinned in
-- fixtures/mill/mills-test-data.ts.
--
-- IDEMPOTENT: every insert is guarded on its own primary key.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MILLSTAT';
  c_guid CONSTANT VARCHAR2(31) := 'E2E0000000000000000000000000000';
  l_year NUMBER;
  l_n    NUMBER;

  PROCEDURE mill_row(p_id NUMBER, p_number NUMBER, p_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.MILL WHERE MILL_ID = p_id;
    IF l_n = 0 THEN
      INSERT INTO THE.MILL
          (MILL_ID, MILL_NUMBER, MILL_NAME, CLIENT_NUMBER, CLIENT_LOCN_CODE, EFFECTIVE_DATE,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, p_number, p_name, '00001500', '00', DATE '2026-01-01',
              0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END mill_row;

  -- p_records: 'C' = the COMPLETE current-year set (status row + 11 categories), 'P' = PARTIAL (the
  -- status row alone), 'N' = NONE. p_ind cannot be NULL here: delivery's audit trigger IMSXA_B_I_U
  -- copies it into ILCR_MILL_STATUS_XREF_AUDIT, whose column is NOT NULL (defects.md VER-5).
  PROCEDURE mill(p_id NUMBER, p_number NUMBER, p_name VARCHAR2, p_status VARCHAR2,
                 p_ind VARCHAR2 DEFAULT 'Y', p_ho NUMBER DEFAULT NULL, p_div NUMBER DEFAULT NULL,
                 p_records VARCHAR2 DEFAULT 'C') IS
  BEGIN
    mill_row(p_id, p_number, p_name);

    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = p_id;
    IF l_n = 0 THEN
      -- The audit pair is a FIXED value, not SYSDATE: the page prints it as "Last Edited by / on
      -- date", and the CI seed carries the identical literal.
      INSERT INTO THE.ILCR_MILL_STATUS_XREF
          (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND,
           HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, p_status, p_ind, p_ho, p_div, 0,
              c_user, TIMESTAMP '2026-09-28 09:00:00', c_user, TIMESTAMP '2026-09-28 09:00:00');
    END IF;

    IF p_records = 'N' THEN
      RETURN;
    END IF;

    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_REPORT_STATUS
     WHERE REPORT_YEAR = l_year AND ILCR_MILL_ID = p_id;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_MILL_REPORT_STATUS
          (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE,
           REPORT_COMPLETED_IND, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID,
           UPDATE_TIMESTAMP)
      VALUES (l_year, p_id, 'D', 'D', 'N', 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    IF p_records = 'P' THEN
      RETURN;
    END IF;

    -- The eleven category rows, exactly as ReportingYearService.enrolMillInYear writes them.
    FOR cat IN 1 .. 11 LOOP
      SELECT COUNT(*) INTO l_n FROM THE.ILCR_REPORT_CATEGORY
       WHERE REPORT_YEAR = l_year AND ILCR_MILL_ID = p_id AND ILCR_CATEGORY_ID = TO_CHAR(cat);
      IF l_n = 0 THEN
        INSERT INTO THE.ILCR_REPORT_CATEGORY
            (REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CATEGORY_STATE_CODE, REPORTABLE_DETAIL_IND,
             REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
        VALUES (l_year, p_id, TO_CHAR(cat), 'D', 'Y', 0, c_user, SYSDATE, c_user, SYSDATE);
      END IF;
    END LOOP;
  END mill;

  PROCEDURE usr(p_n NUMBER) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_USER WHERE USER_GUID = c_guid || p_n;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_USER
          (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (c_guid || p_n, 'LICENSEE', 'Y', 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END usr;

  -- p_ended: 'N' = ACTIVE (ACTIVE_DATE set, INACTIVE_DATE null); 'Y' = ENDED (both set).
  PROCEDURE assoc(p_mill NUMBER, p_n NUMBER, p_ended VARCHAR2) IS
  BEGIN
    usr(p_n);
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_USER_XREF
     WHERE ILCR_MILL_ID = p_mill AND USER_GUID = c_guid || p_n;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_MILL_USER_XREF
          (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_mill, c_guid || p_n, DATE '2026-09-01',
              CASE WHEN p_ended = 'Y' THEN DATE '2026-09-15' END,
              0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END assoc;
BEGIN
  SELECT MAX(REPORT_YEAR) INTO l_year FROM THE.ILCR_REPORTING_PERIOD;

  mill(26050, 9181, 'E2E-DEACTIVATE-TEST',     'ACT');
  mill(26051, 9182, 'E2E-ACTIVATE-TEST',       'CLS');
  mill(26052, 9183, 'E2E-BLOCKED-TEST',        'ACT');
  mill(26053, 9184, 'E2E-USERS-TEST',          'ACT');
  mill(26054, 9185, 'E2E-CLOSED-USERS-TEST',   'CLS');
  mill(26055, 9186, 'E2E-READONLY-USERS-TEST', 'ACT');
  mill(26056, 9187, 'E2E-ADD-USER-TEST',       'ACT');
  mill(26058, 9189, 'E2E-ACTIVATE-USER-TEST',  'ACT');
  mill(26059, 9190, 'E2E-CONTACTS-TEST',       'ACT', 'Y', 2609, 2617);
  mill(26060, 9191, 'E2E-STATUS-SEARCH-TEST',  'CLS');
  mill(26061, 9192, 'E2E-ENROL-TEST',          'CLS', p_records => 'N');
  mill(26062, 9193, 'E2E-PARTIAL-TEST',        'CLS', p_records => 'P');
  mill(26063, 9194, 'E2E-ADD-NEW-USER-TEST',   'ACT');
  -- Importable: a ministry mill row and nothing else.
  mill_row(26057, 9188, 'E2E-IMPORT-TEST');

  assoc(26052, 1, 'N');  -- S12
  assoc(26053, 2, 'N');  -- S08 deactivates
  assoc(26058, 3, 'Y');  -- S09 activates
  assoc(26054, 4, 'Y');  -- S13 refused, then activated once the mill is
  assoc(26055, 5, 'N');  -- S07 duplicate / S10 view
  usr(6);                -- S05 adds it to 26056
  -- ...0007 is deliberately NOT created: GAP-7 adds a user with no account yet, so the add
  -- provisions its ILCR_USER row. The cleanup deletes both (mill_db_restore.py drop-association).

  COMMIT;
END;
/

-- RE-VERIFY QUERIES (after a re-extract, or if preflight/mill-anchors.setup.ts goes red):
--   The ids and numbers must still be free of real rows:
--     SELECT MILL_ID, MILL_NUMBER, ENTRY_USERID FROM THE.MILL
--      WHERE MILL_ID BETWEEN 26050 AND 26063 OR MILL_NUMBER BETWEEN 9181 AND 9194;
--   Each TRACKED mill must hold 1 status row + 11 category rows for the current year, except 26061
--   (none) and 26062 (the status row only); 26057 must hold no xref and no report row at all:
--     SELECT m.MILL_ID,
--            (SELECT COUNT(*) FROM THE.ILCR_MILL_STATUS_XREF x WHERE x.ILCR_MILL_STATUS_XREF_ID = m.MILL_ID) xref,
--            (SELECT COUNT(*) FROM THE.ILCR_MILL_REPORT_STATUS s
--              WHERE s.ILCR_MILL_ID = m.MILL_ID AND s.REPORT_YEAR = y.yr) rs,
--            (SELECT COUNT(*) FROM THE.ILCR_REPORT_CATEGORY c
--              WHERE c.ILCR_MILL_ID = m.MILL_ID AND c.REPORT_YEAR = y.yr) cats
--       FROM THE.MILL m, (SELECT MAX(REPORT_YEAR) yr FROM THE.ILCR_REPORTING_PERIOD) y
--      WHERE m.MILL_ID BETWEEN 26050 AND 26063 ORDER BY 1;
--   A NEW reporting year opened after this patch leaves these mills un-enrolled for it:
--   re-apply (the guards make that add only the new year's rows).
