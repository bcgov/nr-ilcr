-- ============================================================================
-- CROSS-DOMAIN — the mock/dev submitter's mill associations, so Home offers a
-- mill at all (closes bcgov/nr-ilcr#385 for the extract-backed database).
--
-- WHY REAL DATA FELL SHORT
-- Nothing is missing from the extract: it carries real ILCR_USER rows and real
-- ILCR_MILL_USER_XREF associations. The problem is that they belong to real
-- IDIR identities, and a local run has none. Security is off locally
-- (`ilcr.security.enabled=false`), so the caller is MockPrincipalFilter's
-- stand-in principal, whose directory GUID is the SYNTHETIC
-- 'CANONSUBMITTERBBBBCCCCDDDD000001'. No extract row associates that GUID, and
-- since Story 5.5 `MillContextService.listMills` fail-closes a submitter whose
-- GUID resolves to nothing to an EMPTY list — deliberately, so a submitter can
-- never see mills that are not theirs. So Home offers the local submitter NO
-- mill and no schedule can be reached at all.
--
-- Pointing `ilcr.security.mock-user-guid` at one of the extract's real GUIDs
-- would also work, and is deliberately NOT done: those are real directory
-- identifiers of real people and must not be committed to source, and they do
-- not exist in CI — so the property would need a different value per database
-- and the two would drift. One synthetic GUID seeded into both is the version
-- that holds.
--
-- Story 16.1 is what made this fatal rather than cosmetic. Before the role x
-- status editability matrix, the ADMIN mock user (`MOCK_USERS[0]`, the
-- frontend's fallback) could edit a Draft, so local dev worked as the admin and
-- nobody needed the submitter to see anything. Now an admin is READ-ONLY at
-- Draft while a submitter is offered no mill, and NEITHER mock user can enter
-- schedule data.
--
-- WHAT IT ADDS (all NEW rows — no existing row is ever modified)
--   1. One THE.ILCR_USER row for the synthetic GUID, role 'LICENSEE' — the
--      legacy name for ILCR_SUBMITTER, and what the extract's own submitters
--      carry. Needed for the ILCR_MILL_USER_XREF FK, not by any query.
--   2. One THE.ILCR_MILL_USER_XREF row per mill in THE.ILCR_MILL_STATUS_XREF,
--      ACTIVE_DATE set / INACTIVE_DATE null. That is the app's "active"
--      convention and exactly what `findMillsForUser` requires (it checks
--      ACTIVE_DATE IS NOT NULL and INACTIVE_DATE IS NULL, with no date window).
--      Set-based over the xref table — the FK target of
--      ILCR_MILL_USER_XREF.ILCR_MILL_ID — so it covers whatever mills a given
--      extract happens to carry and can never violate the FK. That also makes
--      it survive a re-extract without a re-pick, unlike an anchor patch.
--
-- EVERY MILL, NOT A SUBSET, and that is the point. An ILCR_ADMIN sees every
-- listable mill (`findAllMills`); associating all of them makes the submitter's
-- scoped list (`findMillsForUser`) the same SET, so the Home dropdown is
-- unchanged from what every scenario has always asserted while the query behind
-- it becomes the real scoped one. A narrower subset would quietly change the
-- dropdown's contents and nothing needs it.
--
-- ALREADY FOLDED INTO THE CI SEED, per this folder's rule: the equivalent rows
-- are the final INSERT in `backend/src/test/resources/db-e2e/
-- R__80_e2e_anchor_seed.sql`. There the ILCR_USER row already exists — from
-- `db/R__70_test_scope_canonical_submitter.sql`, which sorts BEFORE R__80 and
-- is precisely why its own set-based association cannot reach the e2e mills.
-- The GUID is the same in both databases, which is what makes one property
-- default correct everywhere.
--
-- IDEMPOTENT: both inserts are guarded on their own absence, so a second apply
-- is a no-op. Sentinel-marked `E2E_SEED_MOCKUSER`, which no extract row
-- carries (the real ones read 'IDIR\<username>'), so the teardown removes
-- exactly these rows and can never touch a real association.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MOCKUSER';
  c_guid  CONSTANT VARCHAR2(32) := 'CANONSUBMITTERBBBBCCCCDDDD000001';
  l_n     NUMBER;
  l_other NUMBER;
BEGIN
  -- 1. The identity itself — FK parent of every association below.
  SELECT COUNT(*) INTO l_n FROM THE.ILCR_USER WHERE USER_GUID = c_guid;
  IF l_n = 0 THEN
    INSERT INTO THE.ILCR_USER
        (USER_GUID, ILCR_ROLE_NAME, ACTIVE_IND, REVISION_COUNT,
         ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
    VALUES (c_guid, 'LICENSEE', 'Y', 0, c_user, SYSDATE, c_user, SYSDATE);
  END IF;

  -- 2. One ACTIVE association per mill this extract carries.
  --
  -- The guard counts ACTIVE rows, not rows. Counting rows was a real hole: `findMillsForUser`
  -- requires ACTIVE_DATE IS NOT NULL AND INACTIVE_DATE IS NULL, and the app's own Mill
  -- Associations admin screen can END an association — so a row could exist while the mill was
  -- unselectable, and a re-apply would see "already there", do nothing, and leave the dropdown
  -- short. Reapply now REACTIVATES its own sentinel rows, so this is genuinely idempotent in the
  -- sense that matters: after it runs, every mill is selectable. Only rows this patch created are
  -- ever updated (sentinel-keyed) — a real association that someone deliberately ended is left
  -- exactly as it is.
  FOR m IN (SELECT x.ILCR_MILL_STATUS_XREF_ID mill FROM THE.ILCR_MILL_STATUS_XREF x) LOOP
    SELECT COUNT(*) INTO l_n
      FROM THE.ILCR_MILL_USER_XREF
     WHERE ILCR_MILL_ID = m.mill AND USER_GUID = c_guid
       AND ACTIVE_DATE IS NOT NULL AND INACTIVE_DATE IS NULL;
    IF l_n = 0 THEN
      -- Revive a sentinel row that was ended, rather than inserting a duplicate (the PK is
      -- (ILCR_MILL_ID, USER_GUID), so a second INSERT would raise ORA-00001).
      --
      -- The UPDATE stays sentinel-keyed ON PURPOSE. Dropping `ENTRY_USERID = c_user` would also
      -- prevent the ORA-00001 below and is simpler, but it would let this patch modify a row it
      -- did not create — reviving an association somebody deliberately ended, which breaks this
      -- folder's rule that a patch touches only its own rows and its teardown removes exactly
      -- those. So instead the INSERT is guarded on the absence of ANY row for the pair.
      UPDATE THE.ILCR_MILL_USER_XREF
         SET ACTIVE_DATE = SYSDATE, INACTIVE_DATE = NULL,
             UPDATE_USERID = c_user, UPDATE_TIMESTAMP = SYSDATE
       WHERE ILCR_MILL_ID = m.mill AND USER_GUID = c_guid
         AND ENTRY_USERID = c_user;

      IF SQL%ROWCOUNT = 0 THEN
        -- No sentinel row was revived. Either there is no row at all (insert one), or a row for
        -- this pair exists under a DIFFERENT ENTRY_USERID and is inactive — someone else's, ended
        -- on purpose. Inserting then would be ORA-00001 on the PK, and overwriting it would be
        -- worse than the error. Leave it: the mill simply stays unselectable, and
        -- `preflight/mill-scope.setup.ts` names it rather than letting a scenario time out.
        SELECT COUNT(*) INTO l_other
          FROM THE.ILCR_MILL_USER_XREF
         WHERE ILCR_MILL_ID = m.mill AND USER_GUID = c_guid;
        IF l_other = 0 THEN
          INSERT INTO THE.ILCR_MILL_USER_XREF
              (ILCR_MILL_ID, USER_GUID, ACTIVE_DATE, INACTIVE_DATE, REVISION_COUNT,
               ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
          VALUES (m.mill, c_guid, SYSDATE, NULL, 0, c_user, SYSDATE, c_user, SYSDATE);
        END IF;
      END IF;
    END IF;
  END LOOP;

  COMMIT;
END;
/

-- RE-VERIFY QUERIES (re-pick is never needed — the patch is set-based).
-- The first query must return 0; it proves every association for the mock is active:
--   SELECT COUNT(*) inactive_associations FROM THE.ILCR_MILL_USER_XREF
--    WHERE USER_GUID = 'CANONSUBMITTERBBBBCCCCDDDD000001'
--      AND (ACTIVE_DATE IS NULL OR INACTIVE_DATE IS NOT NULL);
-- Both MINUS queries must return no rows; together they prove the active scoped set matches the
-- full mill set in the extract:
--   SELECT x.ILCR_MILL_STATUS_XREF_ID FROM THE.ILCR_MILL_STATUS_XREF x
--    WHERE EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s
--                   WHERE s.ILCR_MILL_ID = x.ILCR_MILL_STATUS_XREF_ID)
--   MINUS
--   SELECT u.ILCR_MILL_ID FROM THE.ILCR_MILL_USER_XREF u
--    WHERE u.USER_GUID = 'CANONSUBMITTERBBBBCCCCDDDD000001'
--      AND u.ACTIVE_DATE IS NOT NULL AND u.INACTIVE_DATE IS NULL
--      AND EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s
--                   WHERE s.ILCR_MILL_ID = u.ILCR_MILL_ID);
--
--   SELECT u.ILCR_MILL_ID FROM THE.ILCR_MILL_USER_XREF u
--    WHERE u.USER_GUID = 'CANONSUBMITTERBBBBCCCCDDDD000001'
--      AND u.ACTIVE_DATE IS NOT NULL AND u.INACTIVE_DATE IS NULL
--      AND EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s
--                   WHERE s.ILCR_MILL_ID = u.ILCR_MILL_ID)
--   MINUS
--   SELECT x.ILCR_MILL_STATUS_XREF_ID FROM THE.ILCR_MILL_STATUS_XREF x
--    WHERE EXISTS (SELECT 1 FROM THE.ILCR_MILL_REPORT_STATUS s
--                   WHERE s.ILCR_MILL_ID = x.ILCR_MILL_STATUS_XREF_ID);
