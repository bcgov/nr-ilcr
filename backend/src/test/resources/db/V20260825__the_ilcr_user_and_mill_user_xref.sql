-- THE.ILCR_USER + THE.ILCR_MILL_USER_XREF — the two EXISTING legacy tables that carry a licensee's
-- ILCR account and their dated mill assignments. Extends the shared THE test snapshot; TEST-SCOPE ONLY
-- (the app executes no runtime DDL, AD-2).
--
-- These tables are NOT new. They already exist in managed THE, so this file is a mirror, not a design:
-- every column type, precision, nullability and constraint name below was read from ALL_TAB_COLUMNS and
-- ALL_CONSTRAINTS on the seeded real-data image (ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded).
-- A snapshot that drifts from delivery makes a green IT prove less than it appears to.
--
-- It REPLACES the retired V30__ilcr_mill_user_profile_xref.sql. That file modelled a net-new
-- ILCR_MILL_USER_PROFILE_XREF with a surrogate PK, FAM name-snapshot columns and a function-based
-- active-unique index. The business-ratified model reuses these two legacy tables unchanged instead —
-- no new table, no surrogate key, no name snapshot, no DDL ticket.
--
-- Ordering: this must run after ILCR_ROLE (created by V20260820) and after ILCR_MILL_STATUS_XREF
-- (created by V1), because both are foreign-key targets below. A timestamp version is used rather than
-- the next free integer for exactly that reason — Flyway compares version parts numerically, so an
-- integer version (V35) would sort BEFORE every V2026* migration and the FK targets would not yet exist.

-- Delivery declares no DEFAULT on any column here, and that strictness is load-bearing: REVISION_COUNT
-- and the audit quartet are NOT NULL with no DEFAULT, so a write that omits one fails loudly in tests
-- instead of silently defaulting and then 500-ing in production.
CREATE TABLE THE.ILCR_USER (
  USER_GUID        VARCHAR2(32) NOT NULL,   -- the 32-char IDIR/BCeID directory GUID (custom:idp_user_id)
  ILCR_ROLE_NAME   VARCHAR2(10) NOT NULL,
  ACTIVE_IND       VARCHAR2(1)  NOT NULL,   -- VARCHAR2(1) in delivery, not CHAR(1)
  REVISION_COUNT   NUMBER(5)    NOT NULL,
  ENTRY_USERID     VARCHAR2(30) NOT NULL,   -- acting admin's custom:idp_username, which fits 30
  ENTRY_TIMESTAMP  DATE         NOT NULL,
  UPDATE_USERID    VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE         NOT NULL,
  CONSTRAINT ILCR_USR_PK PRIMARY KEY (USER_GUID),
  CONSTRAINT ILCR_USR_ILCR_ROLE_FK FOREIGN KEY (ILCR_ROLE_NAME)
    REFERENCES THE.ILCR_ROLE (ILCR_ROLE_NAME),
  -- Delivery's ONE check constraint beyond NOT NULLs (verified 2026-08-25 against ALL_CONSTRAINTS on
  -- the seeded image; ILCR_MILL_USER_XREF has none). The generated-looking name is delivery's real
  -- constraint name, mirrored so the tripwire IT can assert it verbatim.
  CONSTRAINT AVCON_1440773538_ACTIV_000 CHECK (ACTIVE_IND IN ('N', 'Y'))
);

-- Column order mirrors delivery (ILCR_MILL_ID first), as does the PK column order — the PK is
-- (ILCR_MILL_ID, USER_GUID), not (USER_GUID, ILCR_MILL_ID). The composite PK is what structurally
-- enforces one row per user↔mill pair, so an assignment is toggled in place rather than re-inserted:
-- active = INACTIVE_DATE IS NULL. Verified against the 64 real rows, where the two dates are strictly
-- mutually exclusive (48 active rows all carry ACTIVE_DATE; all 16 ended rows carry a NULL ACTIVE_DATE;
-- none carries both) — so no reactivation history is kept, by design.
--
-- ILCR_MILL_ID is the subtle one. The value written is numerically the mill id, but the ENABLED foreign
-- key points at ILCR_MILL_STATUS_XREF, not MILL. Both are true at once because ILCR_MILL_STATUS_XREF
-- shares its primary key with MILL: ILCR_MILL_STATUS_XREF_ID = MILL.MILL_ID, 1:1 across all 21 real
-- rows with no orphan in either direction. The practical consequence for the write path is that a mill
-- with no ILCR_MILL_STATUS_XREF row cannot be assigned — the FK, not the mill table, is the gate.
CREATE TABLE THE.ILCR_MILL_USER_XREF (
  ILCR_MILL_ID     NUMBER(10)   NOT NULL,
  USER_GUID        VARCHAR2(32) NOT NULL,
  ACTIVE_DATE      DATE,                    -- set on activate, cleared on deactivate
  INACTIVE_DATE    DATE,                    -- null ⇒ the assignment is ACTIVE
  REVISION_COUNT   NUMBER(5)    NOT NULL,
  ENTRY_USERID     VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP  DATE         NOT NULL,
  UPDATE_USERID    VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE         NOT NULL,
  CONSTRAINT IUMX_PK PRIMARY KEY (ILCR_MILL_ID, USER_GUID),
  CONSTRAINT ILCR_IUMX_USER_FK FOREIGN KEY (USER_GUID)
    REFERENCES THE.ILCR_USER (USER_GUID),
  CONSTRAINT ILCR_IUMX_MSXRF_FK FOREIGN KEY (ILCR_MILL_ID)
    REFERENCES THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID)
);
