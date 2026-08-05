-- Story 2.1 — the submitter↔mill assignment table (FAM-keyed), extending the shared THE test snapshot.
-- FAM is the source of truth for identity: USER_GUID is the FAM custom:idp_user_id (32-char IDIR/BCeID
-- GUID, Story 1.0), with no FK to any user table. USER_DISPLAY_NAME / IDP_USERNAME are snapshotted from
-- FAM at assign time (Q5) so an ENDED assignment whose user has left FAM still renders a name.
--
-- AUDIT-COLUMN DISCIPLINE (deliberate deviation from the older snapshot tables): REVISION_COUNT and the
-- four audit columns are declared NOT NULL with NO DEFAULT — unlike the pre-existing THE.* snapshot
-- tables which give them DEFAULTs. That leniency is exactly what let the Schedule 2/4/8 create-path
-- audit-column omissions pass ITs and 500 in prod; here the strictness makes the Story 2.2 write ITs
-- FAIL if an INSERT omits them. Matches the real THE schema (cf. nr-mof-db THE.ILCR_MILL_USER_XREF).

CREATE SEQUENCE THE.ILCR_MILL_USER_PROFILE_XREF_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE THE.ILCR_MILL_USER_PROFILE_XREF (
  ILCR_MILL_USER_PROFILE_XREF_ID NUMBER(10)    NOT NULL,
  USER_GUID                      VARCHAR2(32)  NOT NULL,   -- FAM custom:idp_user_id (Story 1.0)
  ILCR_MILL_ID                   NUMBER(10)    NOT NULL,   -- shared THE.MILL id (index only; delivery FK is a DBA-ticket call)
  USER_DISPLAY_NAME              VARCHAR2(255),            -- Q5: FAM custom:idp_display_name snapshot
  IDP_USERNAME                   VARCHAR2(30),             -- Q5: FAM custom:idp_username snapshot (e.g. GRPASCUC)
  START_DATE                     DATE,                     -- assignment active from (null = never activated)
  END_DATE                       DATE,                     -- assignment ended (null = active)
  REVISION_COUNT                 NUMBER(5)     NOT NULL,   -- optimistic lock (AD-9)
  ENTRY_USERID                   VARCHAR2(30)  NOT NULL,   -- acting admin custom:idp_username (fits 30)
  ENTRY_TIMESTAMP                DATE          NOT NULL,
  UPDATE_USERID                  VARCHAR2(30)  NOT NULL,
  UPDATE_TIMESTAMP               DATE          NOT NULL,
  CONSTRAINT ILCR_IMUPX_PK PRIMARY KEY (ILCR_MILL_USER_PROFILE_XREF_ID)
);

-- D4 — at most one ACTIVE assignment per (user, mill). The keys are NULLed for ENDED rows (END_DATE not
-- null), so Oracle does not index them: unlimited ENDED history coexists while only one ACTIVE row is
-- allowed. (NOT `CASE WHEN END_DATE IS NULL THEN 1 END`, which would collide two ENDED rows -> ORA-00001.)
CREATE UNIQUE INDEX THE.ILCR_IMUPX_ACTIVE_UQ ON THE.ILCR_MILL_USER_PROFILE_XREF (
  CASE WHEN END_DATE IS NULL THEN USER_GUID    END,
  CASE WHEN END_DATE IS NULL THEN ILCR_MILL_ID END);

CREATE INDEX THE.ILCR_IMUPX_MILL_I ON THE.ILCR_MILL_USER_PROFILE_XREF (ILCR_MILL_ID);
