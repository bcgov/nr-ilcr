-- THE.CLIENT_LOCATION, THE.CLIENT_CONTACT and THE.ISP_SELL_PRICE_ZONE_CODE, plus the MILL and
-- ILCR_MILL_STATUS_XREF columns that reach them. TEST-SCOPE ONLY (the app executes no runtime DDL, AD-2).
--
-- None of this is new. All three tables already exist in managed THE and are owned by THE itself --
-- verified 2026-08-28 against ALL_OBJECTS and ALL_TAB_COLUMNS on the seeded real-data image
-- (ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded). Every column type, precision and
-- nullability below was read from that image rather than inferred from the legacy Hibernate mappings,
-- because a snapshot that drifts from delivery makes a green IT prove less than it appears to.
--
-- Only the columns the Mill Information report actually reads are mirrored, plus the keys needed to
-- join them. CLIENT_LOCATION has 27 columns in delivery and CLIENT_CONTACT 16; carrying the audit
-- quartet, the org-unit columns and the unrelated contact/address fields would add nothing a test can
-- assert and would force every fixture to supply them.

CREATE TABLE THE.CLIENT_LOCATION (
  CLIENT_NUMBER    VARCHAR2(8)  NOT NULL,
  CLIENT_LOCN_CODE VARCHAR2(2)  NOT NULL,
  CLIENT_LOCN_NAME VARCHAR2(40),
  ADDRESS_1        VARCHAR2(40) NOT NULL,
  ADDRESS_2        VARCHAR2(40),
  ADDRESS_3        VARCHAR2(40),
  CITY             VARCHAR2(30) NOT NULL,
  POSTAL_CODE      VARCHAR2(10),
  CONSTRAINT CLIENT_LOCATION_PK PRIMARY KEY (CLIENT_NUMBER, CLIENT_LOCN_CODE)
);

-- BUS_CONTACT_CODE is NOT NULL in delivery and is kept even though the report never displays it:
-- omitting a NOT NULL column would let a fixture insert succeed here and fail in production.
CREATE TABLE THE.CLIENT_CONTACT (
  CLIENT_CONTACT_ID NUMBER(12)    NOT NULL,
  CLIENT_NUMBER     VARCHAR2(8)   NOT NULL,
  CLIENT_LOCN_CODE  VARCHAR2(2)   NOT NULL,
  BUS_CONTACT_CODE  VARCHAR2(3)   NOT NULL,
  CONTACT_NAME      VARCHAR2(120) NOT NULL,
  BUSINESS_PHONE    VARCHAR2(10),
  EMAIL_ADDRESS     VARCHAR2(128),
  CONSTRAINT CLIENT_CONTACT_PK PRIMARY KEY (CLIENT_CONTACT_ID)
);

CREATE TABLE THE.ISP_SELL_PRICE_ZONE_CODE (
  ISP_SELL_PRICE_ZONE_CODE VARCHAR2(2)   NOT NULL,
  DESCRIPTION              VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE           DATE          NOT NULL,
  EXPIRY_DATE              DATE          NOT NULL,
  CONSTRAINT ISP_SELL_PRICE_ZONE_CODE_PK PRIMARY KEY (ISP_SELL_PRICE_ZONE_CODE)
);

-- The MILL columns the report joins on. DELIBERATE DEVIATION, and the reason matters: delivery has
-- CLIENT_NUMBER and CLIENT_LOCN_CODE NOT NULL, but 45 grandfathered seed files already INSERT INTO
-- THE.MILL without them. Mirroring the NOT NULL would fail every one of those inserts and force a
-- snapshot-wide fixture rewrite. They are nullable here so a mill with no client linkage still loads,
-- which is also the shape the report's LEFT JOIN and "-" fallbacks are written for.
-- Note the column spelling: the FK column is ISP_SELL_PRICE_ZONE_CODE ("Price"), even though the
-- legacy Java getter reads getIsp_Sell_Prize_Zone_Code ("Prize"). The column wins.
ALTER TABLE THE.MILL ADD (
  ISP_SELL_PRICE_ZONE_CODE VARCHAR2(2),
  CLIENT_NUMBER            VARCHAR2(8),
  CLIENT_LOCN_CODE         VARCHAR2(2)
);

-- All three are nullable in delivery too, so these mirror exactly. HEAD_OFFICE_CONTACT_IND is the
-- Y/N flag the report prints beside the head-office block; the two ids point at CLIENT_CONTACT.
ALTER TABLE THE.ILCR_MILL_STATUS_XREF ADD (
  HEAD_OFFICE_CONTACT_IND VARCHAR2(1),
  HEAD_OFFICE_CONTACT_ID  NUMBER(12),
  DIVISION_CONTACT_ID     NUMBER(12)
);
