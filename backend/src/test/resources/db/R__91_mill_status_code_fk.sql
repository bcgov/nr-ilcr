-- The foreign key from THE.ILCR_MILL_STATUS_XREF to the status code table it joins.
--
-- Band 90+ for the same reason R__90 uses it: this constraint must land after every fixture that
-- inserts a cross-reference row, and that set grows every time a schedule adds a snapshot. Flyway
-- applies repeatables after all versioned migrations and orders them lexicographically by
-- description, so 91 is last-but-one by construction rather than by luck. A versioned file would
-- have to claim the highest V number on the tree and would lose that race at merge time.
--
-- Delivery name included so an ORA-02291 in a test names the same constraint it would name in
-- production. Verified ENABLED on delivery 2026-09-09, alongside ILCR_MSXRF_PK; there is
-- deliberately NO foreign key from this table to THE.MILL on delivery either, even though
-- ILCR_MILL_STATUS_XREF_ID is by convention equal to MILL.MILL_ID -- that 1:1 is held by
-- application code, not by the database, so this file does not invent one.
--
-- Safe to add: all 137 cross-reference inserts across db/ and db-e2e/ use 'ACT' or 'CLS' and nothing
-- else, so no existing fixture violates it.
ALTER TABLE THE.ILCR_MILL_STATUS_XREF
  ADD CONSTRAINT ILCR_MSXRF_ILCRMSC_FK FOREIGN KEY (ILCR_MILL_STATUS_CODE)
      REFERENCES THE.ILCR_MILL_STATUS_CODE (ILCR_MILL_STATUS_CODE);
