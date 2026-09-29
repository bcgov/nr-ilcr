-- ============================================================================
-- UC-USR-001 / UC-USR-002 (Maintain Users, from the Users page and from a mill)
-- — the dedicated users and mills every Users-page scenario runs on. One user
-- per writer, so parallel scenarios never share an account or an assignment:
--
--   MILLS (all ACT, a current-year report-status row, no category rows)
--   26064  9195 E2E-USR-JOURNEY-A    no users     -> journey adds it (twice)
--   26065  9196 E2E-USR-JOURNEY-B    no users     -> journey adds it
--   26066  9197 E2E-USR-REACTIVATE   ...12 ENDED  -> S03 reactivates
--   26067  9198 E2E-USR-ADD-TARGET   no users     -> S12 and UC-USR-002 S04 add it
--   26068  9199 E2E-USR-READONLY     ...15 ACTIVE -> read-only (S04, S05', 002-S01/S05/S08, a11y)
--   26069  9200 E2E-USR-ACCOUNT      ...17 + ...18 ENDED -> 002-S02 / 002-S03 arrive from it
--   26070  9201 E2E-USR-ADD-FROM     ...19 ACTIVE -> 002-S04 arrives from it
--   26071  9202 E2E-USR-ROWS         ...20 ENDED, ...21 ACTIVE -> 002-S06 / 002-S07
--   26072  9203 E2E-USR-BLOCK-A      ...22 ACTIVE -> 002-S10/S11
--   26073  9204 E2E-USR-BLOCK-B      ...22 ACTIVE -> 002-S10/S11
--
--   USERS (E2E000000000000000000000000000<nn>, role LICENSEE)
--   ...11  ACTIVE_IND 'N', no assignment      -> the 23.4 journey
--   ...12  'Y', ENDED on 26066                -> S03
--   ...13  NO ACCOUNT                         -> S11 (activate creates it)
--   ...14  NO ACCOUNT                         -> S12 (add-mill creates it)
--   ...15  'Y', ACTIVE on 26068               -> read-only
--   ...16  'Y', no assignment                 -> read-only (the "other" user of a switch)
--   ...17  'N', ENDED on 26069                -> UC-USR-002 S02 (activate)
--   ...18  'Y', ENDED on 26069                -> UC-USR-002 S03 (deactivate)
--   ...19  'Y', ACTIVE on 26070               -> UC-USR-002 S04 (adds 26067)
--   ...20  'Y', ENDED on 26071                -> UC-USR-002 S06
--   ...21  'Y', ACTIVE on 26071               -> UC-USR-002 S07
--   ...22  'Y', ACTIVE on 26072 and 26073     -> UC-USR-002 S10/S11
--
-- WHY REAL DATA FELL SHORT
-- The extract's only ILCR_USER rows are the mock submitter and 22.4's mill-admin
-- licensees, and every one of them is some other scenario's fixture: an account
-- write here (activate/deactivate) or an assignment write would move a mills
-- scenario's at-rest state. The account flag has no read endpoint, so a shared
-- account is a race no read-back can see. Surveyed 2026-09-29.
--
-- WHY THE ENDED ROWS HAVE NO ACTIVE_DATE
-- The app's own end (AssignmentService.end) sets INACTIVE_DATE and CLEARS
-- ACTIVE_DATE; the Users page renders a row "Active" whenever ACTIVE_DATE is
-- set (components/millAssociations/index.tsx), so a row with both dates reads
-- Active, offers both buttons, and a Deactivate click 409s. The mill-admin
-- anchors (mill/mill-status-anchors.sql) seed both dates — harmless on the
-- Mills page, which keys off INACTIVE_DATE — so these rows deliberately differ.
--
-- WHY A REPORT-STATUS ROW, AND ONLY THAT
-- The Users page's Add-mill dropdown is GET /v1/mills, which for an admin lists
-- a tracked mill only if it has an ILCR_MILL_REPORT_STATUS row (any year:
-- MillContextRepository.findAllMillEntities). Nothing here activates a mill, so
-- the category rows the mill-admin anchors need are not needed.
--
-- WHY ...13 AND ...14 HAVE NO ACCOUNT
-- They are the first-time-import cases: activate provisions the account ACTIVE
-- (S11), add-mill provisions it INACTIVE (S12, the recorded legacy asymmetry).
-- No endpoint deletes an account or an assignment, so those cleanups — and the
-- journey's and UC-USR-002 S04's new assignments — go through
-- scripts/usr_db_restore.py, which is allow-listed to exactly these rows.
--
-- The client location is 25050's (00001500/00): THE.MILL requires one (NOT NULL
-- + MILL_CL_FK), and borrowing an existing location adds no row.
--
-- SENTINEL: ENTRY_USERID = 'E2E_SEED_USRADM' on every row — NOT 'E2E_SEED' and
-- NOT 'E2E_SEED_MOCKUSER'. common/mock-submitter-associations.sql associates the
-- mock submitter with every tracked mill and skips exactly this sentinel (and the
-- mill-admin one); without that, every journey mill would already have an active
-- user and the mills' association lists would carry a stranger. The CI seed's
-- association INSERT is keyed on 'E2E_SEED', so it excludes these by construction.
--
-- FOLDED INTO THE CI SEED in the same change: db-e2e/R__80_e2e_anchor_seed.sql,
-- "User administration — the dedicated users and mills". Pinned in
-- fixtures/usr/users-test-data.ts.
--
-- IDEMPOTENT: every insert is guarded on its own primary key.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_USRADM';
  c_guid CONSTANT VARCHAR2(30) := 'E2E000000000000000000000000000';
  l_year NUMBER;
  l_n    NUMBER;

  PROCEDURE mill(p_id NUMBER, p_number NUMBER, p_name VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.MILL WHERE MILL_ID = p_id;
    IF l_n = 0 THEN
      INSERT INTO THE.MILL
          (MILL_ID, MILL_NUMBER, MILL_NAME, CLIENT_NUMBER, CLIENT_LOCN_CODE, EFFECTIVE_DATE,
           REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, p_number, p_name, '00001500', '00', DATE '2026-01-01',
              0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;

    -- p_ind 'Y', never NULL: delivery's audit trigger IMSXA_B_I_U copies it into a NOT NULL column
    -- (mill defects.md VER-5).
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID = p_id;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_MILL_STATUS_XREF
          (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_id, 'ACT', 'Y', 0, c_user, SYSDATE, c_user, SYSDATE);
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
  END mill;

  PROCEDURE usr(p_n NUMBER, p_active VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_USER WHERE USER_GUID = c_guid || p_n;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_USER
          (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (c_guid || p_n, 'LICENSEE', p_active, 0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END usr;

  -- p_ended: 'N' = ACTIVE (ACTIVE_DATE set, INACTIVE_DATE null); 'Y' = ENDED the way the app ends
  -- one (ACTIVE_DATE null, INACTIVE_DATE set).
  PROCEDURE assoc(p_mill NUMBER, p_n NUMBER, p_ended VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO l_n FROM THE.ILCR_MILL_USER_XREF
     WHERE ILCR_MILL_ID = p_mill AND USER_GUID = c_guid || p_n;
    IF l_n = 0 THEN
      INSERT INTO THE.ILCR_MILL_USER_XREF
          (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
           ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
      VALUES (p_mill, c_guid || p_n,
              CASE WHEN p_ended = 'N' THEN DATE '2026-09-01' END,
              CASE WHEN p_ended = 'Y' THEN DATE '2026-09-15' END,
              0, c_user, SYSDATE, c_user, SYSDATE);
    END IF;
  END assoc;
BEGIN
  SELECT MAX(REPORT_YEAR) INTO l_year FROM THE.ILCR_REPORTING_PERIOD;

  mill(26064, 9195, 'E2E-USR-JOURNEY-A');
  mill(26065, 9196, 'E2E-USR-JOURNEY-B');
  mill(26066, 9197, 'E2E-USR-REACTIVATE');
  mill(26067, 9198, 'E2E-USR-ADD-TARGET');
  mill(26068, 9199, 'E2E-USR-READONLY');
  mill(26069, 9200, 'E2E-USR-ACCOUNT');
  mill(26070, 9201, 'E2E-USR-ADD-FROM');
  mill(26071, 9202, 'E2E-USR-ROWS');
  mill(26072, 9203, 'E2E-USR-BLOCK-A');
  mill(26073, 9204, 'E2E-USR-BLOCK-B');

  usr(11, 'N');
  usr(12, 'Y');
  -- ...13 and ...14 deliberately have NO account (S11 / S12 provision them).
  usr(15, 'Y');
  usr(16, 'Y');
  usr(17, 'N');
  usr(18, 'Y');
  usr(19, 'Y');
  usr(20, 'Y');
  usr(21, 'Y');
  usr(22, 'Y');

  assoc(26066, 12, 'Y');
  assoc(26068, 15, 'N');
  assoc(26069, 17, 'Y');
  assoc(26069, 18, 'Y');
  assoc(26070, 19, 'N');
  assoc(26071, 20, 'Y');
  assoc(26071, 21, 'N');
  assoc(26072, 22, 'N');
  assoc(26073, 22, 'N');

  COMMIT;
END;
/

-- RE-VERIFY QUERIES (after a re-extract, or if preflight/usr-anchors.setup.ts goes red):
--   The ids and numbers must still be free of real rows:
--     SELECT MILL_ID, MILL_NUMBER, ENTRY_USERID FROM THE.MILL
--      WHERE MILL_ID BETWEEN 26064 AND 26073 OR MILL_NUMBER BETWEEN 9195 AND 9204;
--   The users and their at-rest state:
--     SELECT u.USER_GUID, u.ACTIVE_IND,
--            (SELECT LISTAGG(x.ILCR_MILL_ID || NVL2(x.INACTIVE_DATE, ':E', ':A'), ',')
--               FROM THE.ILCR_MILL_USER_XREF x WHERE x.USER_GUID = u.USER_GUID) assignments
--       FROM THE.ILCR_USER u WHERE u.USER_GUID LIKE 'E2E0000000000000000000000000001_'
--          OR u.USER_GUID LIKE 'E2E0000000000000000000000000002_' ORDER BY 1;
