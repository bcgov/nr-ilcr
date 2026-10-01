-- ============================================================================
-- Teardown for `common/mock-submitter-associations.sql`.
--
-- Removes the synthetic mock-submitter identity and every association it added,
-- keyed on the `E2E_SEED_MOCKUSER` sentinel. No extract row carries that
-- sentinel (the real ones read 'IDIR\<username>'), so a real association can
-- never be deleted here even though the patch's inserts are set-based.
--
-- Associations go BEFORE the ILCR_USER row: the xref's USER_GUID FK points at
-- it, so the reverse order would fail.
--
-- After running this, Home offers a mock SUBMITTER no mill again — the state
-- the extract shipped, and the state in which no mock user can enter schedule
-- data (see the patch header for why). The mock ADMIN is unaffected either way;
-- it is tied to no mill and reads `findAllMills`.
-- ============================================================================

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MOCKUSER';
BEGIN
  -- 1. the associations (child of the identity below)
  DELETE FROM THE.ILCR_MILL_USER_XREF
   WHERE ENTRY_USERID = c_user;

  -- 2. the synthetic identity itself
  DELETE FROM THE.ILCR_USER
   WHERE ENTRY_USERID = c_user;

  COMMIT;
END;
/
