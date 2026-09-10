-- Test-scope THE snapshot extension for mill administration (UC-MILL-001): the
-- ILCR_MILL_STATUS_XREF column the maintain-mills write path needs, the delivery CHECK constraint
-- that guards its head-office indicator, and the ILCR_MILL_STATUS_CODE code table the mill search
-- inner-joins. TEST-SCOPE ONLY (the app executes no runtime DDL, AD-2).
--
-- None of this is new schema. Every type, precision, nullability, constraint name and CHECK
-- condition below was read from ALL_TAB_COLUMNS / ALL_CONSTRAINTS on the seeded real-data image
-- (ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded) on 2026-09-09, not inferred from the
-- legacy Hibernate mappings -- a snapshot that drifts from delivery makes a green IT prove less than
-- it appears to. The legacy mappings could not have supplied these anyway: they carry column names
-- and Java types only, no widths and no constraints.

-- COMMENTS was missing from this snapshot entirely, yet the import path writes it: legacy stamps the
-- literal 'Imported from ISP Mill table' on every imported cross-reference (MillDAO.java:189), and
-- the delivery audit trigger IMSXA_B_I_U copies the column into ILCR_MILL_STATUS_XREF_AUDIT, so it
-- is part of the audited row rather than an incidental note. Delivery width is VARCHAR2(4000).
ALTER TABLE THE.ILCR_MILL_STATUS_XREF ADD (COMMENTS VARCHAR2(4000));

-- A REAL delivery CHECK, mirrored under its delivery name so an ORA-02290 in a test names the same
-- constraint it would name in production -- the same reason V20260825 mirrored ILCR_USER's
-- AVCON_1440773538_ACTIV_000. Note the shared AVCON_1440773538_* prefix: these are siblings from the
-- same generated batch. Verified present on delivery 2026-09-09; this snapshot had no CHECK at all,
-- so the Y/N domain was previously convention here and constraint there.
--
-- NULL passes an Oracle CHECK, so every existing fixture that omits the column still loads --
-- including R__40's deliberate NULLs on mills 731/732/733, which drive the report's "-" fallback.
ALTER TABLE THE.ILCR_MILL_STATUS_XREF
  ADD CONSTRAINT AVCON_1440773538_HEAD__000 CHECK (HEAD_OFFICE_CONTACT_IND IN ('N', 'Y'));

-- DELIBERATE DEVIATION, and the reason matters. Delivery declares HEAD_OFFICE_CONTACT_IND DEFAULT
-- 'Y' and makes ILCR_MILL_STATUS_CODE, REVISION_COUNT and the audit quartet NOT NULL (verified
-- 2026-09-09; this snapshot has them nullable, and its timestamps are TIMESTAMP where delivery has
-- DATE). None of that is mirrored here. Dozens of grandfathered seed files already INSERT INTO
-- THE.ILCR_MILL_STATUS_XREF supplying only the id, the status code and ENTRY_USERID; mirroring the
-- NOT NULLs would fail every one of them, and adding the DEFAULT would silently flip those rows'
-- indicator from NULL to 'Y' and change what the Mill Information report renders for them. This is
-- the same trade V20260828 made for MILL.CLIENT_NUMBER / CLIENT_LOCN_CODE.
--
-- The consequence is load-bearing for the write path and cannot be left implicit: on THIS snapshot a
-- write that omits an audit column or REVISION_COUNT SUCCEEDS, while on delivery it fails as
-- ORA-01400. No tripwire can exist for that gap -- this container's own data dictionary shows the
-- snapshot's loosened shapes, not delivery's, so a nullability assertion here would pin the wrong
-- thing. The protection is behavioural instead: the application stamps all five explicitly (AD-11)
-- and MillMaintenanceIT asserts the stamped values on the insert and both update paths.

-- The status code table the mill search joins. Legacy's non-import search inner-joins it twice --
-- 'from Mill m join fetch m.millStatusXref x join fetch x.ilcrMillStatusCode u' (MillDAO.java:41) --
-- and filters on u.ilcr_mill_status_code, so the join is part of the search contract, not decoration:
-- a cross-reference whose code has no code-table row is invisible to the screen. It also holds the
-- only source of the human-readable status text, which no source file carries.
--
-- Shape read from delivery 2026-09-09: 5 columns, all NOT NULL, PK ILCRMSC_PK. Safe to mirror in
-- full because nothing seeds this table today, so there are no grandfathered inserts to break.
CREATE TABLE THE.ILCR_MILL_STATUS_CODE (
  ILCR_MILL_STATUS_CODE VARCHAR2(3)   NOT NULL,
  DESCRIPTION           VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE        DATE          NOT NULL,
  EXPIRY_DATE           DATE          NOT NULL,
  UPDATE_TIMESTAMP      DATE          NOT NULL,
  CONSTRAINT ILCRMSC_PK PRIMARY KEY (ILCR_MILL_STATUS_CODE)
);
