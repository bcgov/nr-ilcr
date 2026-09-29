-- ============================================================================
-- UC-MILL-001 (Maintain Mills) — three dedicated mills for the STATUS slices:
-- S03 deactivate, S04 activate, and S12 deactivation blocked by an active user.
--
-- WHY REAL DATA FELL SHORT
-- A status change cannot land on any mill another domain reads. A closed mill
-- answers 409 on every schedule (BR-06), so deactivating a schedule anchor
-- mid-run fails the sch1..sch6 scenarios on it. Of the extract's 21 tracked
-- mills every ACT one is a schedule anchor, and all of them carry active user
-- associations (S03 needs none). The one unpinned mill, 14050, is CLS with NO
-- current-year report records — activating it would ENROL it (twelve new rows
-- no endpoint can remove), so S04 on it could never be undone. Surveyed
-- 2026-09-28.
--
-- WHAT EACH MILL IS, and why each carries a COMPLETE current-year record set
--   26050  9181 E2E-DEACTIVATE-TEST  ACT, no users            -> S03
--   26051  9182 E2E-ACTIVATE-TEST    CLS, no users            -> S04
--   26052  9183 E2E-BLOCKED-TEST     ACT, one ACTIVE licensee -> S12
-- Activate enrols a mill whose current-year set is ABSENT and refuses one whose
-- set is PARTIAL (MillMaintenanceService.activate). With the whole set present
-- it writes the status alone, which is what makes every scenario's cleanup an
-- exact API round trip (deactivate <-> activate, user deactivate <-> activate).
-- "Current" is MAX(ILCR_REPORTING_PERIOD.REPORT_YEAR) (ReportingYearService
-- .currentReportingYear) — derived below, not hard-coded.
--
-- The client location is 25050's (00001500/00): THE.MILL requires one here
-- (NOT NULL + MILL_CL_FK), and borrowing an existing location adds no row.
-- These mills are therefore offered 25050's two contacts, which nothing asserts
-- against them beyond the preflight.
--
-- SENTINEL: ENTRY_USERID = 'E2E_SEED_MILLSTAT' on every row — deliberately NOT
-- 'E2E_SEED_MOCKUSER'. common/mock-submitter-associations.sql associates the
-- mock submitter with EVERY xref row and skips exactly this sentinel; without
-- that, a re-apply would give S03's mill an active user and S03 would be
-- refused. The CI seed makes the same exclusion (see R__80).
--
-- FOLDED INTO THE CI SEED in the same change: db-e2e/R__80_e2e_anchor_seed.sql,
-- "Mill administration — the status anchors". Pinned in
-- fixtures/mill/mills-test-data.ts (S03_MILL, S04_MILL, S12_MILL).
--
-- IDEMPOTENT: every insert is guarded on its own primary key.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MILLSTAT';
  c_guid CONSTANT VARCHAR2(32) := 'E2EMILLSTATUSLICENSEE00000000001';
  l_year NUMBER;
  l_n    NUMBER;

  PROCEDURE mill(p_id NUMBER, p_number NUMBER, p_name VARCHAR2, p_status VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.MILL WHERE MILL_ID = p_id;
    IF l_n = 0 THEN
      INSERT INTO THE.MILL
          (MILL_ID, MILL_NUMBER, MILL_NAME, CLIENT_NUMBER, CLIENT_LOCN_CODE, EFFECTIVE_DATE,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, p_number, p_name, '00001500', '00', DATE '2026-01-01',
              0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = p_id;
    IF l_n = 0 THEN
      -- The audit pair is a FIXED value, not SYSDATE: the page prints it as "Last Edited by / on
      -- date", and the CI seed carries the identical literal.
      INSERT INTO THE.ILCR_MILL_STATUS_XREF
          (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, p_status, 'Y', 0,
              c_user, TIMESTAMP '2026-09-28 09:00:00', c_user, TIMESTAMP '2026-09-28 09:00:00');
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
BEGIN
  SELECT MAX(REPORT_YEAR) INTO l_year FROM THE.ILCR_REPORTING_PERIOD;

  mill(26050, 9181, 'E2E-DEACTIVATE-TEST', 'ACT');
  mill(26051, 9182, 'E2E-ACTIVATE-TEST',   'CLS');
  mill(26052, 9183, 'E2E-BLOCKED-TEST',    'ACT');

  -- S12's one active licensee: a synthetic identity (never a real directory GUID) and its
  -- association, ACTIVE_DATE set / INACTIVE_DATE null — the app's "active" convention.
  SELECT COUNT(*) INTO l_n FROM THE.ILCR_USER WHERE USER_GUID = c_guid;
  IF l_n = 0 THEN
    INSERT INTO THE.ILCR_USER
        (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES (c_guid, 'LICENSEE', 'Y', 0, c_user, SYSDATE, c_user, SYSDATE);
  END IF;

  SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_USER_XREF
   WHERE ILCR_MILL_ID = 26052 AND USER_GUID = c_guid;
  IF l_n = 0 THEN
    INSERT INTO THE.ILCR_MILL_USER_XREF
        (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES (26052, c_guid, DATE '2026-09-28', NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
  END IF;

  COMMIT;
END;
/

-- RE-VERIFY QUERIES (after a re-extract, or if preflight/mill-anchors.setup.ts goes red):
--   The ids and numbers must still be free of real rows:
--     SELECT MILL_ID, MILL_NUMBER, ENTRY_USERID FROM THE.MILL
--      WHERE MILL_ID IN (26050, 26051, 26052) OR MILL_NUMBER IN (9181, 9182, 9183);
--   Each mill must hold 1 status row + 11 category rows for the current year:
--     SELECT m.id, (SELECT COUNT(*) FROM THE.ILCR_MILL_REPORT_STATUS s
--                    WHERE s.ILCR_MILL_ID = m.id AND s.REPORT_YEAR = y.yr) rs,
--                  (SELECT COUNT(*) FROM THE.ILCR_REPORT_CATEGORY c
--                    WHERE c.ILCR_MILL_ID = m.id AND c.REPORT_YEAR = y.yr) cats
--       FROM (SELECT 26050 id FROM DUAL UNION ALL SELECT 26051 FROM DUAL UNION ALL SELECT 26052 FROM DUAL) m,
--            (SELECT MAX(REPORT_YEAR) yr FROM THE.ILCR_REPORTING_PERIOD) y;
--   A NEW reporting year opened after this patch leaves these mills un-enrolled for it:
--   re-apply (the guards make that add only the new year's rows).
