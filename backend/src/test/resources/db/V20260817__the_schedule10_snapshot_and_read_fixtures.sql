-- Story 11.1 -- Schedule 10 (Report New Road Construction Costs) snapshot + read fixtures.
--
-- Shape is DELIVERY-FAITHFUL: every column type, precision and nullability below was read from
-- ALL_TAB_COLUMNS on the real-data image (ghcr.io/cgi-bc/nr-mof-oracle-ilcr-real-test-data-seeded,
-- 2026-08-14, Story 11.1 Task 1 gate (i)). NO DDL for these two tables existed anywhere in the repo
-- before this migration -- R__cost_detail_bridge_culvert_fks.sql:55 said "S10 is not built".
--
-- Audit columns are DATE and NOT NULL with NO column defaults, so an insert that skips
-- REVISION_COUNT or the audit quartet fails HERE exactly as it would in delivery (the Schedule 1
-- lax-snapshot lesson; deferred-work.md:15,204,211,217). Delivery carries the trigger pair
-- ILCR_RCRA_B_I_U / ILCR_RCRDA_B_I_U (gate (iv)) which only feeds the _AUD shadow tables -- app-side
-- stamping is still required.
--
-- Claims: Flyway V20260817 (V20260816 was the high-water mark on disk 2026-08-14); mills 710-716;
-- page ids 8900-8909; road-detail ids 8910-8919 plus 8940; cost-line ids 8920-8932. All below the sequence
-- starts (ILCR_REPORT_COMMON_SEQ 9500, ILCR_COST_REPORT_DETAIL_SEQ 9000).
-- These ranges are the repo's only collision-avoidance registry and MUST match the inserts below
-- and db/README.md exactly -- an understated range invites the next story to claim an id that is
-- already taken, which is an ORA-00001 at migrate() time that reds every IT in the repo. (An
-- earlier revision of this header understated all three; corrected at code review 2026-08-17.)

-- ---------------------------------------------------------------------------------------------
-- Code tables. Delivery shape carries DESCRIPTION VARCHAR2(120) NOT NULL plus EFFECTIVE_DATE /
-- EXPIRY_DATE / UPDATE_TIMESTAMP, all DATE NOT NULL (gate (vii)) -- legacy year-filters every code
-- cache on EFFECTIVE_DATE <= 1-Jan-{year} <= EXPIRY_DATE (LookupCache.java:77-98), so the dates are
-- load-bearing, not decoration.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE THE.ILCR_ROAD_LIFETIME_CODE (
  ILCR_ROAD_LIFETIME_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION             VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE          DATE NOT NULL,
  EXPIRY_DATE             DATE NOT NULL,
  UPDATE_TIMESTAMP        DATE NOT NULL
);

CREATE TABLE THE.ILCR_ROAD_BALLAST_METHOD_CODE (
  ILCR_ROAD_BALLAST_METHOD_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION                   VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE                DATE NOT NULL,
  EXPIRY_DATE                   DATE NOT NULL,
  UPDATE_TIMESTAMP              DATE NOT NULL
);

CREATE TABLE THE.ILCR_ROAD_BALLAST_MATERL_CODE (
  ILCR_ROAD_BALLAST_MATERL_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION                   VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE                DATE NOT NULL,
  EXPIRY_DATE                   DATE NOT NULL,
  UPDATE_TIMESTAMP              DATE NOT NULL
);

-- The two LD-removed classification tables. Schedule 10 never READS these (LD-1 removes ASM Code,
-- LD-2 removes Soil Moisture Code), but both detail columns are NOT NULL in delivery and both carry
-- ENABLED foreign keys, so the seed must mirror them or a Story 11.2 insert test would pass here
-- and raise ORA-01400 against the real database. Created with a single sentinel row each purely so
-- the read fixtures can satisfy the constraint (code review 2026-08-17).
CREATE TABLE THE.ILCR_SOIL_MOISTURE_CODE (
  ILCR_SOIL_MOISTURE_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION             VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE          DATE NOT NULL,
  EXPIRY_DATE             DATE NOT NULL,
  UPDATE_TIMESTAMP        DATE NOT NULL
);

CREATE TABLE THE.RELATIVE_SOIL_MOISTUR_RGM_CODE (
  RELATIVE_SOIL_MOISTUR_RGM_CODE VARCHAR2(10) PRIMARY KEY,
  DESCRIPTION                    VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE                 DATE NOT NULL,
  EXPIRY_DATE                    DATE NOT NULL,
  UPDATE_TIMESTAMP               DATE NOT NULL
);

INSERT INTO THE.ILCR_SOIL_MOISTURE_CODE VALUES ('SM1', 'Soil Moisture One (LD-2 removed; retained for the NOT NULL constraint)', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('ASM1', 'ASM One (LD-1 removed; retained for the NOT NULL constraint)', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);

-- RSMR Class. NOTE the PK column name differs from the table name (delivery-confirmed), and the
-- detail column REL_SOIL_MOIST_RGM_CLS_CODE is VARCHAR2(2) -- matched here so the FK is legal.
CREATE TABLE THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE (
  REL_SOIL_MOIST_RGM_CLS_CODE VARCHAR2(2) PRIMARY KEY,
  DESCRIPTION                 VARCHAR2(120) NOT NULL,
  EFFECTIVE_DATE              DATE NOT NULL,
  EXPIRY_DATE                 DATE NOT NULL,
  UPDATE_TIMESTAMP            DATE NOT NULL
);

-- The BEC gate. LD-1/LD-2 remove ASM Code and Soil Moisture Code, which kills BR-06's runtime
-- FILTERING of those two lists -- but ILCR_BEC_SOIL_MOISTUR_XREF is ALSO the join that decides which
-- catalogue rows the BEC control may offer at all (BiogeoclimaticCatalogue.java:28,
-- filterBiogeoClimaticDetailsByXrefLookup). That second leg SURVIVES the removals (Story 11.1
-- deviation (e)), so the table is seeded and the read joins through it.
CREATE TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF (
  ILCR_BEC_SOIL_MOISTUR_XREF_ID NUMBER(10) PRIMARY KEY,
  BIOGEOCLIMATIC_CATALOGUE_ID   NUMBER(10) NOT NULL,
  CONSTRAINT IBSMX_BEC_BC_FK FOREIGN KEY (BIOGEOCLIMATIC_CATALOGUE_ID)
    REFERENCES THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID)
);

-- ILCR_FOREST_REGION_CODE already exists from V22:90 in the bare (code, description) shape. Legacy
-- year-filters it like every other code cache, so add the date columns rather than recreating the
-- table (V22's row and Schedule 8's use of it must keep working). Guarded: a rebase that reorders
-- this against another migration adding the same columns would otherwise raise ORA-01430 inside
-- AbstractOracleIT's static block and red EVERY integration test in the repo.
DECLARE
  e_column_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE THE.ILCR_FOREST_REGION_CODE ADD (EFFECTIVE_DATE DATE, EXPIRY_DATE DATE)';
EXCEPTION
  WHEN e_column_exists THEN NULL;
END;
/

-- V22's pre-existing forest-region row predates the date columns; widen it so it survives the
-- year filter instead of silently vanishing from every code list.
UPDATE THE.ILCR_FOREST_REGION_CODE
   SET EFFECTIVE_DATE = DATE '2000-01-01', EXPIRY_DATE = DATE '2099-12-31'
 WHERE EFFECTIVE_DATE IS NULL;

-- ---------------------------------------------------------------------------------------------
-- Master: THE.ROAD_CONSTRUCTION_REPRT. 16 columns, delivery-verified.
-- There is deliberately NO RMG / ROAD_GROUP column: Road Group is DERIVED on every read from the
-- TSA/TSB or TFL tables (RoadGroupUtil.setRG10By*), never stored (Story 11.1 deviation (h)).
-- CONSTRUCTION_DIVISION_NAME is VARCHAR2(20) -- schedule10.xhtml:140 sets maxlength="30", which is
-- a real defect (10 chars wider than the column, ORA-12899 on save). Recorded for Story 11.2; the
-- column is reproduced at its TRUE width so 11.2's tests fail here exactly as delivery would.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE THE.ROAD_CONSTRUCTION_REPRT (
  ROAD_CONSTRUCTION_REPRT_ID NUMBER(10) PRIMARY KEY,
  REPORT_YEAR                NUMBER(4) NOT NULL,
  ILCR_MILL_ID               NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID           VARCHAR2(5) NOT NULL,
  CONSTRUCTION_DATE          DATE,
  CONSTRUCTION_PERIOD        VARCHAR2(20),
  CONSTRUCTION_DIVISION_NAME VARCHAR2(20),
  ILCR_FOREST_REGION_CODE    VARCHAR2(10) NOT NULL,
  TSB_NUMBER_CODE            VARCHAR2(3),
  TSA_NUMBER                 VARCHAR2(2),
  TFL_NUMBER_CODE            VARCHAR2(2),
  REVISION_COUNT             NUMBER(5) NOT NULL,
  ENTRY_USERID               VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP            DATE NOT NULL,
  UPDATE_USERID              VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP           DATE NOT NULL,
  CONSTRAINT RC_RPT_ILCR_FRC_FK FOREIGN KEY (ILCR_FOREST_REGION_CODE)
    REFERENCES THE.ILCR_FOREST_REGION_CODE (ILCR_FOREST_REGION_CODE)
);

-- ---------------------------------------------------------------------------------------------
-- Detail: THE.ROAD_CONSTRUCTION_REPRT_DTL. 34 columns, delivery-verified.
--
-- ALL FIVE classification columns carry ENABLED foreign keys in delivery (gate (iii)) -- the
-- OPPOSITE of what Story 8.2 found for Schedule 6, whose "(f) no-FK branch" deviation must NOT be
-- inherited here. They are mirrored below so an unknown code fails in tests as it would in delivery.
--
-- ILCR_SOIL_MOISTURE_CODE and RELATIVE_SOIL_MOISTUR_RGM_CODE are NOT NULL in delivery, yet LD-2 and
-- LD-1 remove exactly those two fields from the UI/API. Story 11.1 is read-only and simply never
-- reads them, so they are nullable HERE to let the read fixtures exist. STORY 11.2 CANNOT INSERT A
-- DETAIL ROW WITHOUT THEM in delivery -- a sentinel value or a DDL change must be decided before
-- 11.2 is drafted. Their FKs are therefore omitted rather than mirrored (nothing references them).
--
-- REL_SOIL_MOIST_RGM_CLS_CODE (RSMR Class) is NULLABLE in delivery despite the view marking it
-- required, and is populated in only 18 of 66 real rows -- the read must not assume it.
-- COMMENTS is VARCHAR2(4000); the view's maxlength="3500" is NARROWER, which is the safe direction.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE THE.ROAD_CONSTRUCTION_REPRT_DTL (
  ROAD_CONSTRUCTION_REPRT_DTL_ID NUMBER(10) PRIMARY KEY,
  ROAD_CONSTRUCTION_REPRT_ID     NUMBER(10) NOT NULL,
  ROAD_NAME                      VARCHAR2(30) NOT NULL,
  SIDE_SLOPE_PCT                 NUMBER(3),
  BOULDER_AREA_PCT               NUMBER(3),
  ILCR_ROAD_LIFETIME_CODE        VARCHAR2(10) NOT NULL,
  ILCR_SOIL_MOISTURE_CODE        VARCHAR2(10) NOT NULL,
  RIPPABLE_ROCK_PCT              NUMBER(3),
  RELATIVE_SOIL_MOISTUR_RGM_CODE VARCHAR2(10) NOT NULL,
  SOLID_ROCK_PCT                 NUMBER(3),
  COARSE_MATERIAL_PCT            NUMBER(3),
  BECBIOGEO_CATALOGUE_ID         NUMBER(10) NOT NULL,
  FINE_MATERIAL_PCT              NUMBER(3),
  ORGANIC_MATERIAL_PCT           NUMBER(3),
  SUB_GRADE_LENGTH               NUMBER(6,3),
  DETAIL_ENGINEERING_COST_IND    VARCHAR2(1) NOT NULL,
  END_HAUL_DISTANCE              NUMBER(5,1),
  END_HAUL_VOLUME                NUMBER(7),
  OVERLAND_DISTANCE              NUMBER(5,1),
  OVERLAND_VOLUME                NUMBER(7),
  ILCR_ROAD_BALLAST_METHOD_CODE  VARCHAR2(10) NOT NULL,
  SUB_GRADE_SURFACE_WIDTH        NUMBER(4,1),
  ILCR_ROAD_BALLAST_MATERL_CODE  VARCHAR2(10) NOT NULL,
  STABILIZING_LENGTH             NUMBER(6,3),
  STABILIZING_SURFACE_WIDTH      NUMBER(4,1),
  STABILIZING_DEPTH              NUMBER(3,1),
  STABILIZING_DISTANCE_TO_SOURCE NUMBER(4,1),
  REL_SOIL_MOIST_RGM_CLS_CODE    VARCHAR2(2),
  COMMENTS                       VARCHAR2(4000),
  REVISION_COUNT                 NUMBER(5) NOT NULL,
  ENTRY_USERID                   VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP                DATE NOT NULL,
  UPDATE_USERID                  VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP               DATE NOT NULL,
  CONSTRAINT RCR_DTL_RC_RPT_FK FOREIGN KEY (ROAD_CONSTRUCTION_REPRT_ID)
    REFERENCES THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID),
  CONSTRAINT RCR_DTL_ILCR_RLTC_FK FOREIGN KEY (ILCR_ROAD_LIFETIME_CODE)
    REFERENCES THE.ILCR_ROAD_LIFETIME_CODE (ILCR_ROAD_LIFETIME_CODE),
  CONSTRAINT RCR_DTL_ILCR_RBMC_FK FOREIGN KEY (ILCR_ROAD_BALLAST_METHOD_CODE)
    REFERENCES THE.ILCR_ROAD_BALLAST_METHOD_CODE (ILCR_ROAD_BALLAST_METHOD_CODE),
  CONSTRAINT RCR_DTL_IRBMC_FK FOREIGN KEY (ILCR_ROAD_BALLAST_MATERL_CODE)
    REFERENCES THE.ILCR_ROAD_BALLAST_MATERL_CODE (ILCR_ROAD_BALLAST_MATERL_CODE),
  CONSTRAINT RCR_DTL_ILCRRSMRCC_FK FOREIGN KEY (REL_SOIL_MOIST_RGM_CLS_CODE)
    REFERENCES THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE (REL_SOIL_MOIST_RGM_CLS_CODE),
  CONSTRAINT RCR_DTL_ILCR_SMC_FK FOREIGN KEY (ILCR_SOIL_MOISTURE_CODE)
    REFERENCES THE.ILCR_SOIL_MOISTURE_CODE (ILCR_SOIL_MOISTURE_CODE),
  CONSTRAINT RCR_DTL_RSMRC_FK FOREIGN KEY (RELATIVE_SOIL_MOISTUR_RGM_CODE)
    REFERENCES THE.RELATIVE_SOIL_MOISTUR_RGM_CODE (RELATIVE_SOIL_MOISTUR_RGM_CODE),
  CONSTRAINT RCR_DTL_BEC_BC_FK FOREIGN KEY (BECBIOGEO_CATALOGUE_ID)
    REFERENCES THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID)
);

-- ILCR_COST_REPORT_DETAIL carries one FK column per report family. Guarded, per V34:61-69.
DECLARE
  e_column_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE THE.ILCR_COST_REPORT_DETAIL ADD (ROAD_CONSTRUCTION_REPRT_DTL_ID NUMBER(10))';
EXCEPTION
  WHEN e_column_exists THEN NULL;
END;
/

-- ---------------------------------------------------------------------------------------------
-- The twelve category-'10' cost items (legacy Constant.REPORT_COST_ITEMS :371-376). Every id, name,
-- category and subcategory was verified against the delivery ILCR_REPORT_COST_ITEM rows (gate
-- (viii)) -- the UC catalog had recorded this mapping as an unconfirmed ASSUMPTION
-- (UC-SCH10-001-detailed.md:238); it is now confirmed against both source and delivery.
-- All twelve ids were verified ABSENT from every existing migration before inserting: cost items are
-- shared master data, and a blind re-insert is ORA-00001 at migrate() time which reds every IT in
-- the repo (the Story 7.1 near-miss on item 68).
-- Item 21's name carries the legacy typo "Hauk" -- reproduced verbatim, not corrected.
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (3,  'Transfer Costs',              'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (6,  'Less Culvert Cost',           'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (7,  'Less Bridge',                 'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (8,  'Less Landing Cost',           'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (11, 'Less Overland Cost',          'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (20, 'Actual cost',                 'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (21, 'Less End Hauk Cost',          'Y', '10', '1', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (10, 'Transfer Costs',              'Y', '10', '2', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (22, 'Actual cost',                 'Y', '10', '2', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (4,  'Less Other Engineering Cost', 'Y', '10', '3', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (5,  'Other TtT Transfer',          'Y', '10', '3', 'SEED');
INSERT INTO THE.ILCR_REPORT_COST_ITEM (ILCR_REPORT_COST_ITEM_ID, ITEM_NAME, LUMP_SUM_IND, ILCR_CATEGORY_ID, ILCR_SUBCATEGORY_ID, ENTRY_USERID) VALUES (9,  'Other Transfer',              'Y', '10', '4', 'SEED');

-- ---------------------------------------------------------------------------------------------
-- Code-list rows. Effective 2000-2099 so the 2021 fixtures pass the legacy year filter.
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_FOREST_REGION_CODE (ILCR_FOREST_REGION_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE) VALUES ('RNI', 'Northern Interior', DATE '2000-01-01', DATE '2099-12-31');
INSERT INTO THE.ILCR_FOREST_REGION_CODE (ILCR_FOREST_REGION_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE) VALUES ('RNO', 'Northern Region',    DATE '2000-01-01', DATE '2099-12-31');
-- An EXPIRED region: proves the year filter actually excludes rows rather than passing everything.
INSERT INTO THE.ILCR_FOREST_REGION_CODE (ILCR_FOREST_REGION_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE) VALUES ('ROLD', 'Retired Region',    DATE '2000-01-01', DATE '2010-12-31');

INSERT INTO THE.ILCR_ROAD_LIFETIME_CODE VALUES ('P', 'Permanent', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_LIFETIME_CODE VALUES ('T', 'Temporary', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);

-- Legacy ILCR_ROAD_BALLAST_METHOD_CODES enum is {N, C, D} (Constant.java:667-668).
INSERT INTO THE.ILCR_ROAD_BALLAST_METHOD_CODE VALUES ('N', 'None',      DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_METHOD_CODE VALUES ('C', 'Crushed',   DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_METHOD_CODE VALUES ('D', 'Pit Run',   DATE '2000-01-01', DATE '2099-12-31', SYSDATE);

INSERT INTO THE.ILCR_ROAD_BALLAST_MATERL_CODE VALUES ('GR', 'Gravel',        DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_MATERL_CODE VALUES ('NA', 'Not Applicable', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);

-- RSMR Class renders as "{code} - {description}" -- the ONLY code list that does (schedule10.xhtml:762).
INSERT INTO THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE VALUES ('1', 'Very Dry', DATE '2000-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE VALUES ('2', 'Moist',    DATE '2000-01-01', DATE '2099-12-31', SYSDATE);

-- BEC gate rows. 8801 (ICHdw1) and 8802 (CWHvm) are offered; 8803 (ESSFwc4a) exists in
-- BIOGEOCLIMATIC_CATALOGUE from V20 but is deliberately NOT in the xref, so a read that skips the
-- xref join would wrongly offer it -- that is the assertion that pins deviation (e).
INSERT INTO THE.ILCR_BEC_SOIL_MOISTUR_XREF VALUES (8801, 8801);
INSERT INTO THE.ILCR_BEC_SOIL_MOISTUR_XREF VALUES (8802, 8802);

-- Year-filter decoys. Each list gets one EXPIRED and one NOT-YET-EFFECTIVE row so the
-- EFFECTIVE_DATE <= 1-Jan-{year} <= EXPIRY_DATE predicate is falsifiable: delete either leg and
-- the code-list assertions fail. Without these the seeded rows all sit inside the window, so the
-- filter could be removed entirely and nothing would notice (code review 2026-08-17).
INSERT INTO THE.ILCR_ROAD_LIFETIME_CODE VALUES ('XP', 'Expired Lifetime', DATE '2000-01-01', DATE '2010-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_LIFETIME_CODE VALUES ('FU', 'Future Lifetime', DATE '2030-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_METHOD_CODE VALUES ('XP', 'Expired Method', DATE '2000-01-01', DATE '2010-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_METHOD_CODE VALUES ('FU', 'Future Method', DATE '2030-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_MATERL_CODE VALUES ('XP', 'Expired Material', DATE '2000-01-01', DATE '2010-12-31', SYSDATE);
INSERT INTO THE.ILCR_ROAD_BALLAST_MATERL_CODE VALUES ('FU', 'Future Material', DATE '2030-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE VALUES ('XP', 'Expired Class', DATE '2000-01-01', DATE '2010-12-31', SYSDATE);
INSERT INTO THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE VALUES ('FU', 'Future Class', DATE '2030-01-01', DATE '2099-12-31', SYSDATE);
-- A never-expiring code: NULL EXPIRY_DATE must still appear (NULL >= date is UNKNOWN, which the
-- original predicate silently excluded).
INSERT INTO THE.ILCR_FOREST_REGION_CODE (ILCR_FOREST_REGION_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE) VALUES ('RNUL', 'Never Expires', DATE '2000-01-01', NULL);

-- ---------------------------------------------------------------------------------------------
-- Mill fixtures 710-716, reporting year 2021 (the canonical seeded ILCR_REPORTING_PERIOD, V2:10).
--   710 Draft, rich: 2 pages / 2 road details / full cost lines  -> AC1, AC2, AC4, AC5
--   711 TFL-located page                                          -> AC2 (TFL branch)
--   712 unmapped TSA/TSB  -> blank Road Group, legacy path 2      -> AC3
--   713 unmapped TFL      -> blank Road Group, legacy path 3      -> AC3
--   714 page with ZERO road details (CNT-001 = 0)                 -> AC2
--   715 valid active context, ZERO pages                          -> AC8
--   716 track 'S' (non-Draft)                                     -> AC7
-- Reused read-only from V2: mill 516 (CLS -> 409), unseeded 999999 (-> 404).
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (710, 'Sch10 Rich Construction', 710, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (711, 'Sch10 TFL Located',       711, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (712, 'Sch10 Unmapped TSA',      712, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (713, 'Sch10 Unmapped TFL',      713, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (714, 'Sch10 Page No Details',   714, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (715, 'Sch10 No Pages',          715, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (716, 'Sch10 Submitted Track',   716, 'SEED');

INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (710, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (711, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (712, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (713, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (714, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (715, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (716, 'ACT', 'SEED');

INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 710, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 711, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 712, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 713, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 714, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 715, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 716, 'S', 'SEED');

-- NOTE: page 8901 is inserted BEFORE 8900, and detail 8911 before 8910, deliberately. Insert
-- order must differ from id order or the explicit ORDER BY in findPages/findRoadDetails would be
-- unpinned -- Oracle returns small-table rows in insert order, so the assertions would hold even
-- with the ORDER BY deleted (code review 2026-08-17).
-- ---------------------------------------------------------------------------------------------
-- Pages. Road Group is NOT stored -- these TSA/TSB and TFL values are chosen so the DERIVED value is
-- pinned by the legacy tables (RoadGroupUtil.java:285-520):
--   8900: TSA '01' + TSB '01A' -> startsWith("01") -> "11"
--   8901: TSA '16' + TSB '16G' -> matches .*[G-Pg-p] -> "6"     (regex branch, not startsWith)
--   8902: TFL '08'             -> "10"
--   8903: TSA '99' (not in switch)      -> default -> null       (unmapped path 3)
--   8904: TSA '16' + TSB '16Z' (no branch matches) -> ""         (unmapped path 2 -> normalized null)
--   8905: TFL '77' (not in switch)      -> default -> null       (unmapped path 3)
--   8906: page with no road details (CNT-001 = 0)
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8901, 2021, 710, '10', DATE '2021-07-20', '2021-07', 'South Division', 'RNI', '16G', '16', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8900, 2021, 710, '10', DATE '2021-06-15', '2021-06', 'North Division', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8902, 2021, 711, '10', DATE '2021-05-01', '2021-05', 'TFL Division',   'RNO', NULL,  NULL, '08', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8903, 2021, 712, '10', DATE '2021-04-01', '2021-04', 'Unmapped TSA',   'RNO', '99A', '99', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8904, 2021, 712, '10', DATE '2021-04-02', '2021-04', 'No TSB Branch',  'RNO', '16Z', '16', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8905, 2021, 713, '10', DATE '2021-03-01', '2021-03', 'Unmapped TFL',   'RNO', NULL,  NULL, '77', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8906, 2021, 714, '10', DATE '2021-02-01', '2021-02', 'Empty Page',     'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
-- Mill 716 (non-Draft track 'S') still owns data -- AC7 asserts it LISTS with editable:false.
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8907, 2021, 716, '10', DATE '2021-01-01', '2021-01', 'Submitted Div',  'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- ---------------------------------------------------------------------------------------------
-- Road details.
--   8910: page 8900, fully populated -- every substructure, RSMR class present, ballast method 'C'
--   8911: page 8900, SPARSE -- RSMR class NULL, all dimensions/percentages NULL, ballast 'N'/'NA'.
--         This is the shape REAL delivery data actually has (RSMR populated in only 18 of 66 rows),
--         and it is what pins the null-propagation rule: sumBigDecimalValues over all-null returns
--         NULL, not zero, so its subGrade totals must be ABSENT from the response, not 0.
--   8912: page 8901
--   8913: page 8902 (TFL page)
--   8914: page 8903, 8915: page 8904, 8916: page 8905  (unmapped Road Group pages)
--   8917: page 8907 (non-Draft mill)
-- Page 8906 deliberately has NO detail rows.
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8911, 8900, 'Spur B',     NULL, NULL, 'T', 'SM1', NULL, 'ASM1', NULL, NULL, 8802, NULL, NULL, NULL, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8910, 8900, 'Mainline A', 25, NULL, 'P', 'SM1', 20, 'ASM1', 10, 30, 8801, 25, 15, 12.500, 'N', 2.5, 1200, 1.5, 800, 'C', 6.5, 'GR', 3.000, 5.5, 0.3, 12.4, '1', 'Fully populated detail', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8912, 8901, 'Regex Road', 10, NULL, 'P', 'SM1', 5,  'ASM1', 5,  60, 8801, 20, 10, 4.000,  'Y', 1.0, 500,  2.0, 600, 'D', 5.0, 'NA', 1.000, 5.0, 0.2, 8.0,  '2', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8913, 8902, 'TFL Road',   15, NULL, 'P', 'SM1', 10, 'ASM1', 20, 50, 8802, 10, 10, 8.250,  'N', NULL, NULL, NULL, NULL, 'C', 7.0, 'GR', 2.500, 7.0, 0.4, 15.0, '1', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8914, 8903, 'Unmapped A', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 1.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8915, 8904, 'Unmapped B', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 1.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8916, 8905, 'Unmapped C', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 1.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8917, 8907, 'Submitted Rd', 5, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 2.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Mill/year/category decoys. Every page above is 2021 + category '10', so the REPORT_YEAR and
-- ILCR_CATEGORY_ID predicates in findPages/findRoadDetails/findCostLines could each be deleted
-- with the whole suite still green -- only the mill filter was decoyed. These two rows make both
-- falsifiable: if either predicate is dropped, mill 710's page count changes (code review
-- 2026-08-17).
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 710, 'D', 'SEED');
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8908, 2020, 710, '10', DATE '2020-06-15', '2020-06', 'Wrong Year', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT VALUES (8909, 2021, 710, '99', DATE '2021-06-15', '2021-06', 'Wrong Category', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8918, 8908, 'Wrong Year Rd', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 1.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8919, 8909, 'Wrong Cat Rd', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8801, NULL, NULL, 1.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8932, 20, 999999, 8918, 0, 'SEED', 'SEED', SYSDATE);

-- A detail on mill 711's TFL page referencing catalogue row 8803, which is deliberately NOT in
-- ILCR_BEC_SOIL_MOISTUR_XREF. This is the ONLY fixture that exercises the referenced-BEC fallback:
-- the row must still render its stored classification (ESSFwc4a) while 8803 stays OUT of the
-- offerable dropdown. Without it the whole fallback query and merge could be deleted undetected.
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL VALUES (8940, 8902, 'De-listed BEC Rd', NULL, NULL, 'P', 'SM1', NULL, 'ASM1', NULL, NULL, 8803, NULL, NULL, 2.000, 'N', NULL, NULL, NULL, NULL, 'N', NULL, 'NA', NULL, NULL, NULL, NULL, NULL, NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- ---------------------------------------------------------------------------------------------
-- Cost lines for detail 8910 ONLY. Delivery has ZERO Schedule 10 cost lines (Task 1 data probe:
-- 52 pages, 66 details, 0 cost rows), so AC5 can ONLY be proven against a deliberately constructed
-- fixture -- this is it. Detail 8911 has none, which is the real-world shape.
--
-- Pinned arithmetic for 8910 (legacy CoreUtil semantics, DERIVED FORMULAS in the story):
--   subGradeTotalCosts      = 150000 + (-5000) + 2000                = 147000
--   subGradeTotalDeductions = 1000 + 2000 + 3000 + 6000 + 4000 + 5000 = 21000
--   subGradeTotal           = 147000 - 21000                          = 126000
--   subGradeCostPerLength   = 126000 / 12.500                         = 10080.00
--   stabilizingTotal        = 40000 + 0 + 0                           = 40000
--   stabilizingCostPerLength= 40000 / 3.000                           = 13333.33
--   materialTypeTotal       = 10 + 20 + 40 + 20 + 10                  = 100
-- NOTE lessOtherEng (item 4) and otherTtTTransfer (item 5) live under SUBCATEGORY 3, and
-- otherTransfer (item 9) under subcategory 4 -- the six "Less" fields deliberately span three
-- subcategories, so a read that only scans subcategory 1 would silently under-count the deductions.
-- ---------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8920, 20, 150000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8921,  3,  -5000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8922,  5,   2000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8923,  7,   1000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8924,  6,   2000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8925,  8,   3000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8926, 11,   4000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8927,  4,   5000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8928, 21,   6000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8929, 22,  40000, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8930, 10, 2500, 8910, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8931,  9, -1500, 8910, 0, 'SEED', 'SEED', SYSDATE);
