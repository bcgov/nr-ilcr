-- =================================================================================================
-- Schedule 10 WRITE fixtures and the schema this schedule's write path needs.
--
-- Claimed identifiers -- these ranges are the repo's only collision-avoidance registry and MUST
-- match db/README.md exactly. An understated range invites the next story to claim an id that is
-- already taken, which is an ORA-00001 at migrate() time that reds every IT in the repo.
--
--   Flyway version   V20260818            (V20260817 was the high-water mark on disk)
--   Mills            717-723
--   Pages            8950-8959            ROAD_CONSTRUCTION_REPRT_ID
--   Road details     8960-8979            ROAD_CONSTRUCTION_REPRT_DTL_ID
--   Cost lines       8980-8999            ILCR_COST_REPORT_DETAIL_ID
--   Moisture xrefs   9001-9004            SOIL_MOISTURE_XREF_ID
--   BEC-moisture links 8803-8805          ILCR_BEC_SOIL_MOISTUR_XREF_ID
--
-- All fixture ids sit BELOW the sequence starts, so nothing seeded here can collide with a row the
-- application writes during a test (ILCR_REPORT_COMMON_SEQ 9500, ILCR_COST_REPORT_DETAIL_SEQ 9000,
-- and ROAD_CONSTRUCTION_REPORT_SEQ 9600 created below).
--
-- ISOLATION MODEL. A context is (mill, YEAR), not a mill. Mills that a destructive test writes into
-- carry several Draft years so each test method can claim its own and the suite stays
-- order-independent. Story 11.1's mills 710-716 are READ-ONLY here and are never mutated.
-- =================================================================================================

-- -------------------------------------------------------------------------------------------------
-- 1. SCHEMA THE WRITE PATH NEEDS
--
-- Story 11.1 built the read, which never touches the moisture cross-reference chain, so two pieces
-- of it were never created. The write path derives the two removed moisture codes through exactly
-- that chain, so the test schema has to carry it before any insert can succeed.
-- -------------------------------------------------------------------------------------------------

-- The triple the cross-reference resolves: RSMR class -> (ASM code, soil moisture code). Delivery
-- holds 38 of these rows; the fixtures below seed the shapes the derivation has to handle.
CREATE TABLE THE.ILCR_SOIL_MOISTURE_XREF (
  SOIL_MOISTURE_XREF_ID          NUMBER(10) PRIMARY KEY,
  REL_SOIL_MOIST_RGM_CLS_CODE    VARCHAR2(2)  NOT NULL,
  RELATIVE_SOIL_MOISTUR_RGM_CODE VARCHAR2(10) NOT NULL,
  ILCR_SOIL_MOISTURE_CODE        VARCHAR2(10) NOT NULL,
  ACTIVE_IND                     VARCHAR2(1)  NOT NULL,
  ENTRY_USERID                   VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP                DATE DEFAULT SYSDATE NOT NULL,
  CONSTRAINT ISMX_IRSMRCC_FK FOREIGN KEY (REL_SOIL_MOIST_RGM_CLS_CODE)
    REFERENCES THE.ILCR_RL_SOIL_MOIS_RGM_CLS_CODE (REL_SOIL_MOIST_RGM_CLS_CODE),
  CONSTRAINT ISMX_RSMRC_FK FOREIGN KEY (RELATIVE_SOIL_MOISTUR_RGM_CODE)
    REFERENCES THE.RELATIVE_SOIL_MOISTUR_RGM_CODE (RELATIVE_SOIL_MOISTUR_RGM_CODE),
  CONSTRAINT ISMX_ILCR_SMC_FK FOREIGN KEY (ILCR_SOIL_MOISTURE_CODE)
    REFERENCES THE.ILCR_SOIL_MOISTURE_CODE (ILCR_SOIL_MOISTURE_CODE)
);

-- The BEC gate carries the join key and an active flag in delivery; V20260817 created only the two
-- columns the read needed. Added guarded and nullable, backfilled, then tightened -- an existing row
-- cannot satisfy a NOT NULL column that is added in one step.
DECLARE
  e_column_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF ADD (SOIL_MOISTURE_XREF_ID NUMBER(10))';
EXCEPTION
  WHEN e_column_exists THEN NULL;
END;
/

DECLARE
  e_column_exists EXCEPTION;
  PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF ADD (ACTIVE_IND VARCHAR2(1))';
EXCEPTION
  WHEN e_column_exists THEN NULL;
END;
/

-- The master sequence legacy declares for this table. It does NOT exist in the seeded delivery image
-- either -- it sits un-advanced at 1 against real ids of 90-184, because Schedule 10's rows were
-- bulk-loaded rather than written through the application. That is an environment defect to be fixed
-- by advancing the sequence, NOT a reason to repoint the code at a shared sequence; creating it here
-- is what lets the ITs exercise the real id path instead of hand-picked ids.
CREATE SEQUENCE THE.ROAD_CONSTRUCTION_REPORT_SEQ START WITH 9600 INCREMENT BY 1 NOCACHE;

-- -------------------------------------------------------------------------------------------------
-- 1b. REPAIR A SHARED-SEQUENCE LANDMINE  (cross-schedule; found by this story, not caused by it)
--
-- THE.ILCR_COST_REPORT_DETAIL_SEQ starts at 9000 (V4) and EVERY schedule's cost-line writes draw
-- from it. But V33 SEEDS ILCR_COST_REPORT_DETAIL ids 9500-9506 -- inside that sequence's path. So
-- once the application has created ~500 cost rows in one container, NEXTVAL reaches 9500 and every
-- subsequent insert raises ORA-00001 against a seeded row.
--
-- Nothing hit it before because no schedule wrote many cost rows per operation: Schedule 6 writes one
-- item-69 row, 7A and 7B about two. SCHEDULE 10 WRITES TWELVE PER ROAD DETAIL -- all four
-- subcategories, blank costs included, because legacy maintains every line -- which pushed cumulative
-- consumption past 9500 for the first time. The failures then landed on whichever schedules happened
-- to run after Schedule 10 in the suite (observed: Schedule 6 and 7A writes, seven tests, all HTTP
-- 500 from DuplicateKeyException) which is why it reads as a Schedule 6/7A fault and is not one.
--
-- The fix belongs here rather than in a Schedule 10 workaround: a shared sequence must start above
-- EVERY seeded id in its table, and 9506 is the current maximum. Restarting it removes the ceiling
-- for all schedules instead of merely moving Schedule 10 below it. DROP + CREATE follows the V21
-- precedent, which did exactly this to ILCR_REPORT_COMMON_SEQ for the same class of reason.
--
-- No test pins a generated cost-detail id in the 9000-9599 band, so raising the floor breaks nothing.
-- Verified before changing it.
DROP SEQUENCE THE.ILCR_COST_REPORT_DETAIL_SEQ;
CREATE SEQUENCE THE.ILCR_COST_REPORT_DETAIL_SEQ START WITH 10000 INCREMENT BY 1;

-- -------------------------------------------------------------------------------------------------
-- 2. THE REAL MOISTURE CODES
--
-- Delivery holds three soil-moisture codes and eight ASM codes, and every one of the 66 real road
-- details carries a genuine pair. V20260817 seeded a single 'SM1'/'ASM1' placeholder each, purely to
-- satisfy the NOT NULL columns for a read that never looks at them -- neither code exists in
-- delivery, so an insert test written against them would pass here and raise ORA-02291 there.
--
-- The placeholders are RETAINED rather than replaced: Story 11.1's seeded detail rows reference them
-- by foreign key, and another story's fixtures are never repurposed.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_SOIL_MOISTURE_CODE VALUES ('Dry',   'Dry',   DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_SOIL_MOISTURE_CODE VALUES ('Moist', 'Moist', DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.ILCR_SOIL_MOISTURE_CODE VALUES ('Wet',   'Wet',   DATE '2013-01-01', DATE '2099-12-31', SYSDATE);

-- The ASM moisture gradient, driest to wettest. 'ED' reproduces delivery's own spelling of
-- "Exremely Dry".
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('ED', 'Exremely Dry',   DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('VD', 'Very Dry',       DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('MD', 'Moderately Dry', DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('SD', 'Slightly Dry',   DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('F',  'Fresh',          DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('M',  'Moist',          DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('VM', 'Very Moist',     DATE '2013-01-01', DATE '2099-12-31', SYSDATE);
INSERT INTO THE.RELATIVE_SOIL_MOISTUR_RGM_CODE VALUES ('W',  'Wet',            DATE '2013-01-01', DATE '2099-12-31', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 3. DERIVATION FIXTURES -- the three outcomes the write path must distinguish
--
-- Deliberately built so a defect FAILS rather than so the assertions pass:
--
--   BEC 8801 + RSMR '1'  -> exactly ONE candidate            (the ordinary auto-select path)
--   BEC 8802 + RSMR '2'  -> TWO candidates                   (forces the tie-break to be exercised)
--   BEC 8801 + RSMR '2'  -> ZERO, via an INACTIVE link       (pins ACTIVE_IND on the BEC gate)
--   BEC 8803 + anything  -> ZERO, absent from the gate       (pins the offerable check)
--
-- Row 9004 is an INACTIVE xref sharing BEC 8801 and RSMR '1' with row 9001. Dropping ACTIVE_IND from
-- the join would turn that single-candidate case into a two-candidate one and silently start
-- exercising the tie-break, so the flag is falsifiable rather than decorative.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ILCR_SOIL_MOISTURE_XREF VALUES (9001, '1', 'MD', 'Dry',   'Y', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_SOIL_MOISTURE_XREF VALUES (9002, '2', 'SD', 'Moist', 'Y', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_SOIL_MOISTURE_XREF VALUES (9003, '2', 'F',  'Moist', 'Y', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_SOIL_MOISTURE_XREF VALUES (9004, '1', 'VD', 'Dry',   'N', 'SEED', SYSDATE);

-- Backfill the two links V20260817 seeded, then add the rest.
UPDATE THE.ILCR_BEC_SOIL_MOISTUR_XREF SET SOIL_MOISTURE_XREF_ID = 9001, ACTIVE_IND = 'Y' WHERE ILCR_BEC_SOIL_MOISTUR_XREF_ID = 8801;
UPDATE THE.ILCR_BEC_SOIL_MOISTUR_XREF SET SOIL_MOISTURE_XREF_ID = 9002, ACTIVE_IND = 'Y' WHERE ILCR_BEC_SOIL_MOISTUR_XREF_ID = 8802;

-- BEC 8802 gains a second pair for RSMR '2' -- this is the multi-candidate case. The gradient orders
-- SD before F, so the tie-break must resolve to 'SD'/'Moist'.
INSERT INTO THE.ILCR_BEC_SOIL_MOISTUR_XREF (ILCR_BEC_SOIL_MOISTUR_XREF_ID, BIOGEOCLIMATIC_CATALOGUE_ID, SOIL_MOISTURE_XREF_ID, ACTIVE_IND) VALUES (8803, 8802, 9003, 'Y');
-- The inactive xref, linked ACTIVE so only the xref's own flag excludes it.
INSERT INTO THE.ILCR_BEC_SOIL_MOISTUR_XREF (ILCR_BEC_SOIL_MOISTUR_XREF_ID, BIOGEOCLIMATIC_CATALOGUE_ID, SOIL_MOISTURE_XREF_ID, ACTIVE_IND) VALUES (8804, 8801, 9004, 'Y');
-- An INACTIVE LINK to an active xref, so BEC 8801 + RSMR '2' resolves to nothing.
INSERT INTO THE.ILCR_BEC_SOIL_MOISTUR_XREF (ILCR_BEC_SOIL_MOISTUR_XREF_ID, BIOGEOCLIMATIC_CATALOGUE_ID, SOIL_MOISTURE_XREF_ID, ACTIVE_IND) VALUES (8805, 8801, 9002, 'N');

-- Now that every row carries values, tighten to delivery's shape.
ALTER TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF MODIFY (SOIL_MOISTURE_XREF_ID NUMBER(10) NOT NULL);
ALTER TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF MODIFY (ACTIVE_IND VARCHAR2(1) NOT NULL);

ALTER TABLE THE.ILCR_BEC_SOIL_MOISTUR_XREF
  ADD CONSTRAINT IBSMX_ISMX_FK FOREIGN KEY (SOIL_MOISTURE_XREF_ID)
  REFERENCES THE.ILCR_SOIL_MOISTURE_XREF (SOIL_MOISTURE_XREF_ID);

-- -------------------------------------------------------------------------------------------------
-- 4. WRITE-FIXTURE MILLS 717-723 -- one mill per destructive concern
--
--   717 write playground   Draft 2019-2024, one year per destructive test method
--   718 non-Draft ('S')    every write must answer 409
--   719 check-status MET   a complete page and road detail
--   720 check-status ISSUES the quirk fixtures (see section 6)
--   721 copy source        a page WITH road details, so a copy that carried them would be caught
--   722 delete cascade     a page with details and cost lines
--   723 IDOR neighbour     must survive every write aimed at another mill
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (717, 'Sch10 Write Playground', 717, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (718, 'Sch10 Write Non Draft',  718, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (719, 'Sch10 Check All Met',    719, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (720, 'Sch10 Check Issues',     720, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (721, 'Sch10 Copy Source',      721, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (722, 'Sch10 Delete Cascade',   722, 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ENTRY_USERID) VALUES (723, 'Sch10 Idor Neighbour',   723, 'SEED');

INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (717, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (718, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (719, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (720, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (721, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (722, 'ACT', 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, ENTRY_USERID) VALUES (723, 'ACT', 'SEED');

-- Mill 717 carries six Draft years so destructive tests never share a context.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2019, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2020, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2022, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2023, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2024, 717, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 718, 'S', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 719, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 720, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 721, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2022, 721, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 722, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2022, 722, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 723, 'D', 'SEED');

-- -------------------------------------------------------------------------------------------------
-- 5. CHECK STATUS -- the passing schedule (mill 719)
--
-- Ballast method 'N', so the six additional-stabilizing rules are not required and the road detail
-- passes on the strength of its own values rather than on a gate being skipped by accident.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8950, 2021, 719, '10', '2021-06', 'Complete Division', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, RIPPABLE_ROCK_PCT, SOLID_ROCK_PCT, COARSE_MATERIAL_PCT, BECBIOGEO_CATALOGUE_ID, FINE_MATERIAL_PCT, ORGANIC_MATERIAL_PCT, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, SUB_GRADE_SURFACE_WIDTH, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8960, 8950, 'Complete Road', 25, 'P', 20, 10, 40, 8801, 20, 10, 12.500, 'N', 'N', 6.5, 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 6. CHECK STATUS -- the outstanding schedule (mill 720), built so quirks are FALSIFIABLE
--
-- Page 8952 is inserted BEFORE 8951 deliberately: the explicit ORDER BY is what fixes the positional
-- page numbers quoted in the message text, and inserting in id order would let a dropped ORDER BY
-- inherit insert order and pass anyway.
--
--   8951  a page missing Division AND Period Surveyed          -> two page-level rules
--         detail 8961: every material percentage BLANK          -> the total still reports "must
--                      equal 100", because the legacy total coerces nulls to zero
--         detail 8962: a SECOND road on the same page           -> proves the Road Name / Sub Zone
--                      titles carry only the PAGE label, so the two are indistinguishable
--   8952  a page whose detail uses ballast 'C' with NO material -> the gated rules all fire
--         detail 8963: BEC 8803, which exists in the catalogue but is absent from the gate
--                      -> the not-offerable branch
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8952, 2021, 720, '10', '2021-07', 'Ballast Division', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8951, 2021, 720, '10', NULL, NULL, 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Detail 8962 inserted before 8961, same reasoning as the pages above.
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8962, 8951, 'Second Road', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8961, 8951, 'Blank Material Road', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- Ballast 'C' with the four dimensions and three costs left NULL, and a BEC the gate does not
-- offer. The material code CANNOT be left blank -- ILCR_ROAD_BALLAST_MATERL_CODE is NOT NULL -- so
-- the material-type rule inside that gate is unreachable from stored data and is pinned at the unit
-- seam instead.
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8963, 8952, 'Crushed Missing Dims', 25, 'P', 8803, 1.000, 'N', 'C', 'GR', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 7. COPY SOURCE (mill 721) -- a page WITH road details
--
-- The copy must carry the page header only. Seeding the source WITH children is what makes a copy
-- that duplicated them detectable; a childless source would pass either way.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8953, 2021, 721, '10', '2021-08', 'Copy Me', 'RNI', '01A', '01', NULL, 3, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8964, 8953, 'Copy Child One', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8965, 8953, 'Copy Child Two', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 7b. SURGICAL ROAD-DETAIL DELETE (mill 721, year 2022) -- its OWN context, not the copy source's
--
-- Added at code review 2026-08-18. Schedule10DeleteIT.roadDetailDeleteIsSurgical previously deleted
-- detail 8964 from page 8953, which Schedule10CopyIT asserts still has TWO road details -- so the two
-- classes shared (721, 2021) and CopyIT passed only because "Copy" sorts before "Delete". One Oracle
-- container per JVM with no per-test rollback means committed state carries across IT classes, which
-- is exactly why Task 8 mandates one (mill, YEAR) per destructive test. Year 2022 was already seeded
-- as a spare Draft context for mill 721 and went unused; this claims it.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8958, 2022, 721, '10', '2022-04', 'Surgical', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8971, 8958, 'Surgical Target', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8972, 8958, 'Surgical Sibling', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 8. DELETE CASCADE (mill 722) -- a page with details AND cost lines at both levels
--
-- Two details, each carrying cost lines, so the ordered cascade has grandchildren to remove. Getting
-- the order wrong raises ORA-02292 against the foreign key the repeatable migration declares.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8954, 2021, 722, '10', '2021-09', 'Delete Me', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8966, 8954, 'Cascade Road One', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8967, 8954, 'Cascade Road Two', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8980, 20, 111000, 8966, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8981,  3,  -2000, 8966, 0, 'SEED', 'SEED', SYSDATE);
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8982, 20, 222000, 8967, 0, 'SEED', 'SEED', SYSDATE);
-- A stored NULL cost, which is ordinary data: legacy writes NULL for a cost left blank, and the read
-- must treat it exactly as it treats an absent row.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8983, 22, NULL,   8967, 0, 'SEED', 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 9. IDOR NEIGHBOUR (mill 723)
--
-- Every scoped UPDATE and DELETE must leave this page and its road detail untouched. Asserting the
-- neighbour survives is what proves the mill/year predicate is doing the work, rather than the test
-- simply not having anything else to hit.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8955, 2021, 723, '10', '2021-10', 'Do Not Touch', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8968, 8955, 'Neighbour Road', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

-- -------------------------------------------------------------------------------------------------
-- 10. EDIT TARGET (mill 717, year 2019) -- an existing page and road detail to edit
--
-- Revision counts start non-zero so a test that hardcodes 0 fails rather than passing by luck.
-- -------------------------------------------------------------------------------------------------
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8956, 2019, 717, '10', '2019-05', 'Edit Me', 'RNI', '01A', '01', NULL, 2, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8969, 8956, 'Edit Road', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 5, 'SEED', SYSDATE, 'SEED', SYSDATE);
-- One existing cost line, so the upsert's UPDATE-in-place branch is exercised rather than only its
-- INSERT branch. Asserting COUNT(*) = 1 afterwards is what pins update-in-place over a second insert.
INSERT INTO THE.ILCR_COST_REPORT_DETAIL (ILCR_COST_REPORT_DETAIL_ID, ILCR_REPORT_COST_ITEM_ID, COST, ROAD_CONSTRUCTION_REPRT_DTL_ID, REVISION_COUNT, ENTRY_USERID, UPDATE_USERID, UPDATE_TIMESTAMP) VALUES (8984, 20, 50000, 8969, 0, 'SEED', 'SEED', SYSDATE);

-- Mill 718's non-Draft page, so a 409 test has something real to aim at.
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT (ROAD_CONSTRUCTION_REPRT_ID, REPORT_YEAR, ILCR_MILL_ID, ILCR_CATEGORY_ID, CONSTRUCTION_PERIOD, CONSTRUCTION_DIVISION_NAME, ILCR_FOREST_REGION_CODE, TSB_NUMBER_CODE, TSA_NUMBER, TFL_NUMBER_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8957, 2021, 718, '10', '2021-06', 'Submitted Division', 'RNI', '01A', '01', NULL, 0, 'SEED', SYSDATE, 'SEED', SYSDATE);
INSERT INTO THE.ROAD_CONSTRUCTION_REPRT_DTL (ROAD_CONSTRUCTION_REPRT_DTL_ID, ROAD_CONSTRUCTION_REPRT_ID, ROAD_NAME, SIDE_SLOPE_PCT, ILCR_ROAD_LIFETIME_CODE, BECBIOGEO_CATALOGUE_ID, SUB_GRADE_LENGTH, DETAIL_ENGINEERING_COST_IND, ILCR_ROAD_BALLAST_METHOD_CODE, ILCR_ROAD_BALLAST_MATERL_CODE, REL_SOIL_MOIST_RGM_CLS_CODE, ILCR_SOIL_MOISTURE_CODE, RELATIVE_SOIL_MOISTUR_RGM_CODE, REVISION_COUNT, ENTRY_USERID, ENTRY_TIMESTAMP, UPDATE_USERID, UPDATE_TIMESTAMP)
  VALUES (8970, 8957, 'Submitted Road', 25, 'P', 8801, 1.000, 'N', 'N', 'NA', '1', 'Dry', 'MD', 0, 'SEED', SYSDATE, 'SEED', SYSDATE);

COMMIT;
