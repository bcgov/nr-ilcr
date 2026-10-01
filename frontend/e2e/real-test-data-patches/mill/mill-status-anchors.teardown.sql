-- Undoes mill-status-anchors.sql exactly. Every row it adds carries ENTRY_USERID 'E2E_SEED_MILLSTAT'
-- and sits on mill ids 26050-26063, which nothing in the extract uses. Children first, in FK order.
--
-- Rows the SCENARIOS write are keyed on the mill ids, not on the sentinel: an app write stamps the
-- acting administrator. Importing 26057 creates its xref and report rows under that user, and S05
-- creates a 26056 association the same way. Deleting by mill id catches them even when a run died
-- before its own cleanup.

SET DEFINE OFF

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'E2E_SEED_MILLSTAT';
BEGIN
  DELETE FROM THE.ILCR_MILL_USER_XREF   WHERE ILCR_MILL_ID BETWEEN 26050 AND 26063;
  DELETE FROM THE.ILCR_REPORT_CATEGORY  WHERE ILCR_MILL_ID BETWEEN 26050 AND 26063;
  DELETE FROM THE.ILCR_MILL_REPORT_STATUS WHERE ILCR_MILL_ID BETWEEN 26050 AND 26063;
  DELETE FROM THE.ILCR_MILL_STATUS_XREF WHERE ILCR_MILL_STATUS_XREF_ID BETWEEN 26050 AND 26063;
  DELETE FROM THE.MILL
   WHERE ENTRY_USERID = c_user AND MILL_ID BETWEEN 26050 AND 26063;
  DELETE FROM THE.ILCR_USER
   WHERE ENTRY_USERID = c_user
      OR USER_GUID = 'E2E00000000000000000000000000007';  -- provisioned by GAP-7's add
  COMMIT;
END;
/
