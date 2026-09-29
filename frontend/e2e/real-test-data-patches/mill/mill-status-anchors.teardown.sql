-- Undoes mill-status-anchors.sql exactly: every row it adds carries ENTRY_USERID
-- 'E2E_SEED_MILLSTAT', and nothing else in the extract does. Children first, in FK order.
--
-- Rows the SCENARIOS write are keyed on the same three mill ids, not on the sentinel: an app
-- write re-stamps UPDATE_USERID but never ENTRY_USERID, so a row the patch created still reads
-- as the patch's. The mill-id clauses are belt-and-braces for a report row an app path enrolled.

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MILLSTAT';
BEGIN
  DELETE FROM THE.ILCR_MILL_USER_XREF
   WHERE ENTRY_USERID = c_user OR ILCR_MILL_ID IN (26050, 26051, 26052);

  DELETE FROM THE.ILCR_REPORT_CATEGORY
   WHERE ENTRY_USERID = c_user OR ILCR_MILL_ID IN (26050, 26051, 26052);

  DELETE FROM THE.ILCR_MILL_REPORT_STATUS
   WHERE ENTRY_USERID = c_user OR ILCR_MILL_ID IN (26050, 26051, 26052);

  DELETE FROM THE.ILCR_MILL_STATUS_XREF
   WHERE ENTRY_USERID = c_user AND ILCR_MILL_STATUS_XREF_ID IN (26050, 26051, 26052);

  DELETE FROM THE.MILL
   WHERE ENTRY_USERID = c_user AND MILL_ID IN (26050, 26051, 26052);

  DELETE FROM THE.ILCR_USER
   WHERE ENTRY_USERID = c_user;

  COMMIT;
END;
/
