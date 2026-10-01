-- THE.*_AUD / *_AUDIT audit-shadow tables and the fourteen THE.*_S_VW "submitted snapshot" views
-- that Story 16.2's original-value indicators read. TEST-SCOPE ONLY (the app executes no runtime DDL,
-- AD-2), and DDL ONLY -- no seed rows here, per the 2026-08-20 Flyway fixture decision enforced by
-- FlywayMigrationConventionTest.newVersionedMigrationsCarryNoSeedData. The 'S'-stamped snapshot rows
-- live in R__60_original_value_snapshots.sql.
--
-- None of this is new. All 28 objects already exist in managed THE. Every column type, precision and
-- nullability below, and every view body, was read from the live 19c dev database (fortmp1.nrs.bcgov,
-- schema THE) on 2026-09-09 via ALL_TAB_COLUMNS and ALL_VIEWS.TEXT rather than inferred from the
-- legacy Hibernate mappings -- the *Ov entity classes are @Immutable projections and deliberately
-- expose only the columns their screens needed, so they are NOT a reliable source for the real shape
-- (BASIC_SILVICULTURE_REPORT_S_VW is the proof: the AUD table stores ENHANCED_IND and COMMENTS, the
-- view selects neither, which is the root cause of the two Schedule 11 indicators that can never fire).
--
-- Story 25.1 deliberately said "do not create the _S_VW views in the seed" (25-1-...:117) while the
-- feature was deferred. That deferral ends with Story 16.2, which is why they arrive now.
--
-- WHY THE AUDIT TRIGGERS ARE NOT REPRODUCED. In delivery, RECORD_STATE_CODE is written by a
-- per-table BEFORE INSERT OR UPDATE row trigger (ILCR_CRDA_B_I_U, CAMP_RAUD_B_I_U, BSRAUD_B_I_U, ...
-- one for each of the fourteen base tables, all present and verified) which resolves the report
-- year/mill/category from whichever parent FK is non-null, reads
--   CASE category_id WHEN '11' THEN mill_silvicultur_status_code ELSE ilcr_mill_report_status_code END
-- together with ILCR_REPORT_CATEGORY.CATEGORY_STATE_CODE, and maps the pair:
--   (D,S)->S   (D,D)->D   (A,S)->A   (A,D)->D   (S,V)->V   (A,V)->V   (V,S)->A   (V,V)->V
-- so ONLY (category_state='D', mill_status='S') produces the 'S' snapshot this feature reads. The
-- application never inserts _AUD rows (PRD DL-18: "audit is database-trigger-owned"), so replicating
-- the triggers here would test the database's behaviour rather than ours. The fixtures instead seed
-- 'S' rows directly, which is what the trigger would have left behind.
--
-- The snapshot is therefore inherently STABLE across successive ministry corrections -- the first
-- write after submission is stamped 'S', the category then flips to 'A', and every later correction is
-- stamped 'A' -- which is exactly what UC-CHK-011 S07 requires and why no application code preserves it.
--
-- NOT NULL and precision are kept as delivery has them so a bad fixture fails here rather than in a
-- reader. Views are created after every table they read.

-- ===================================================================================
-- The fourteen audit-shadow tables.
-- ===================================================================================
CREATE TABLE THE.BASIC_SILVICULTURE_RPRT_AUD (
  BASIC_SILVICULTURE_RPRT_AUD_ID NUMBER(10) NOT NULL,
  BASIC_SILVICULTURE_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  LOCATION VARCHAR2(30) NOT NULL,
  BECBIOGEOCLIMATIC_CATALOGUE_ID NUMBER(10) NOT NULL,
  REFORESTED_NET_AREA NUMBER(10,2) NOT NULL,
  RECORD_STATE_CODE VARCHAR2(1),
  ENHANCED_IND VARCHAR2(1) NOT NULL,
  COMMENTS VARCHAR2(3500),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.BRIDGE_REPORT_AUDIT (
  BRIDGE_REPORT_AUDIT_ID NUMBER(10) NOT NULL,
  BRIDGE_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  LOCATION_NAME VARCHAR2(30) NOT NULL,
  BUILT_DATE DATE NOT NULL,
  LENGTH NUMBER(5,1) NOT NULL,
  DECK_WIDTH NUMBER(5,1),
  HEIGHT NUMBER(5,1) NOT NULL,
  DISTANCE_FROM_STORAGE NUMBER(4) NOT NULL,
  ILCR_DECK_CODE VARCHAR2(10) NOT NULL,
  ILCR_BRIDGE_ABUTMENT_TYPE_CODE VARCHAR2(2) NOT NULL,
  ILCR_BRIDGE_SUPERSTRUCTR_CODE VARCHAR2(1) NOT NULL,
  EXPECTED_BRIDGE_LIFE_SPAN NUMBER(3) NOT NULL,
  ILCR_BRIDGE_LOAD_RATING_CODE VARCHAR2(10) NOT NULL,
  ILCR_BRIDGE_CNSTRCTN_TYPE_CODE VARCHAR2(10) NOT NULL,
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.CAMP_REPORT_AUDIT (
  CAMP_REPORT_AUDIT_ID NUMBER(10) NOT NULL,
  CAMP_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  CAMP_NAME VARCHAR2(30) NOT NULL,
  DISTANCE_TO_OPERATING_AREA NUMBER(8,2),
  CAMP_SIZE_CAPACITY NUMBER(3),
  ASSOCIATED_CAMP_VOLUME NUMBER(7),
  ISOLATED_CAMP_IND VARCHAR2(1),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.CONTRACTUAL_WORK_REPORT_AUD (
  CONTRACTUAL_WORK_REPORT_AUD_ID NUMBER(10) NOT NULL,
  CONTRACTUAL_WORK_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  CONTRACTOR_ID VARCHAR2(50) NOT NULL,
  SIDE_SLOPE_PCT NUMBER(3),
  PERFORMED_UNIT NUMBER(6,1),
  ILCR_UNIT_CODE VARCHAR2(10) NOT NULL,
  UNIT_DESCRIPTION VARCHAR2(50),
  ILCR_CONTRACTUAL_SOURCE_CODE VARCHAR2(20) NOT NULL,
  SOURCE_DESCRIPTION VARCHAR2(120),
  BEC_ZONE_CODE VARCHAR2(4) NOT NULL,
  COMMENTS VARCHAR2(3500),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.CULVERT_REPORT_AUDIT (
  CULVERT_REPORT_AUDIT_ID NUMBER(10) NOT NULL,
  CULVERT_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  CULVERT_PIECE_COUNT NUMBER(4) NOT NULL,
  SPAN_SIZE NUMBER(7),
  RISE_SIZE NUMBER(7),
  LENGTH NUMBER(8,2),
  ILCR_CULVERT_TYPE_CODE VARCHAR2(20) NOT NULL,
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.ILCR_COST_REPORT_DETAIL_AUD (
  ILCR_COST_REPORT_DETAIL_AUD_ID NUMBER(10) NOT NULL,
  ILCR_COST_REPORT_DETAIL_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  ILCR_REPORT_SUMMARY_ID NUMBER(10),
  TRANSPORTATION_REPORT_ID NUMBER(10),
  CAMP_REPORT_ID NUMBER(10),
  ROAD_MAINTENANCE_REPORT_ID NUMBER(10),
  BRIDGE_REPORT_ID NUMBER(10),
  CULVERT_REPORT_ID NUMBER(10),
  CONTRACTUAL_WORK_REPORT_ID NUMBER(10),
  ROAD_CONSTRUCTION_REPRT_DTL_ID NUMBER(10),
  BASIC_SILVICULTURE_REPORT_ID NUMBER(10),
  ILCR_REPORT_COST_ITEM_ID NUMBER(10) NOT NULL,
  VOLUME NUMBER(10,2),
  COST NUMBER(8),
  ITEM_DESCRIPTION VARCHAR2(120),
  COMMENTS VARCHAR2(400),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.ILCR_REPORT_SUMMARY_AUDIT (
  ILCR_REPORT_SUMMARY_AUDIT_ID NUMBER(10) NOT NULL,
  ILCR_REPORT_SUMMARY_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  LOCATION VARCHAR2(100),
  CROWN_VOLUME NUMBER(8),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.ROAD_CONSTRUCTION_REPRT_AUD (
  ROAD_CONSTRUCTION_REPRT_AUD_ID NUMBER(10) NOT NULL,
  ROAD_CONSTRUCTION_REPRT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  CONSTRUCTION_DATE DATE,
  CONSTRUCTION_PERIOD VARCHAR2(20),
  CONSTRUCTION_DIVISION_NAME VARCHAR2(20),
  ILCR_FOREST_REGION_CODE VARCHAR2(10) NOT NULL,
  TSB_NUMBER_CODE VARCHAR2(3),
  TSA_NUMBER VARCHAR2(2),
  TFL_NUMBER_CODE VARCHAR2(10),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.ROAD_CONSTRUCTN_RPT_DTL_AUD (
  ROAD_CONSTRUCTN_RPT_DTL_AUD_ID NUMBER(10) NOT NULL,
  ROAD_CONSTRUCTION_REPRT_DTL_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  ROAD_CONSTRUCTION_REPRT_ID NUMBER(10) NOT NULL,
  ROAD_NAME VARCHAR2(30) NOT NULL,
  SIDE_SLOPE_PCT NUMBER(3),
  ILCR_ROAD_LIFETIME_CODE VARCHAR2(10) NOT NULL,
  BOULDER_AREA_PCT NUMBER(3),
  ILCR_SOIL_MOISTURE_CODE VARCHAR2(10) NOT NULL,
  RIPPABLE_ROCK_PCT NUMBER(3),
  RELATIVE_SOIL_MOISTUR_RGM_CODE VARCHAR2(10) NOT NULL,
  SOLID_ROCK_PCT NUMBER(3),
  COARSE_MATERIAL_PCT NUMBER(3),
  FINE_MATERIAL_PCT NUMBER(3),
  BECBIOGEO_CATALOGUE_ID NUMBER(10) NOT NULL,
  ORGANIC_MATERIAL_PCT NUMBER(3),
  SUB_GRADE_LENGTH NUMBER(6,3),
  DETAIL_ENGINEERING_COST_IND VARCHAR2(1) NOT NULL,
  END_HAUL_DISTANCE NUMBER(5,1),
  END_HAUL_VOLUME NUMBER(7),
  OVERLAND_DISTANCE NUMBER(5,1),
  OVERLAND_VOLUME NUMBER(7),
  ILCR_ROAD_BALLAST_METHOD_CODE VARCHAR2(10) NOT NULL,
  SUB_GRADE_SURFACE_WIDTH NUMBER(4,1),
  ILCR_ROAD_BALLAST_MATERL_CODE VARCHAR2(10) NOT NULL,
  STABILIZING_LENGTH NUMBER(6,3),
  STABILIZING_SURFACE_WIDTH NUMBER(4,1),
  STABILIZING_DEPTH NUMBER(3,1),
  STABILIZING_DISTANCE_TO_SOURCE NUMBER(4,1),
  REL_SOIL_MOIST_RGM_CLS_CODE VARCHAR2(2),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.ROAD_MAINTENANCE_REPORT_AUD (
  ROAD_MAINTENANCE_REPORT_AUD_ID NUMBER(10) NOT NULL,
  ROAD_MAINTENANCE_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  TSA_NUMBER VARCHAR2(2),
  TSB_NUMBER_CODE VARCHAR2(3),
  TFL_NUMBER_CODE VARCHAR2(2),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.TRANSPORTATION_REPORT_AUD (
  TRANSPORTATION_REPORT_AUD_ID NUMBER(10) NOT NULL,
  TRANSPORTATION_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  LOCATION_DESCRIPTION VARCHAR2(30) NOT NULL,
  DISTANCE NUMBER(8,2),
  TRANSPORTATION_CYCLE_TIME NUMBER(8),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.TREE_TO_TRUCK_DTL_RPRT_AUD (
  TREE_TO_TRUCK_DTL_RPRT_AUD_ID NUMBER(10) NOT NULL,
  TREE_TO_TRUCK_DETAIL_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  TREE_TO_TRUCK_REPORT_ID NUMBER(10) NOT NULL,
  CONTRACTOR_ID VARCHAR2(50) NOT NULL,
  CUT_BLOCK VARCHAR2(12),
  GROUND_BASE_PCT NUMBER(3),
  GRAPPLE_PCT NUMBER(3),
  SKYLINE_PCT NUMBER(3),
  SKYLINE_SLOPE_DISTANCE NUMBER(5),
  SKYLINE_SUPPORT_NUMBER NUMBER(4),
  SUPPORT_AVERAGE_DISTANCE NUMBER(7,2),
  HIGHLEAD_PCT NUMBER(3),
  HELICOPTER_PCT NUMBER(3),
  DISTANCE NUMBER(6,1),
  CYCLE_TIME NUMBER(6,1),
  WATER_DUMP_DESTINATION_IND VARCHAR2(1),
  UPHILL_DIRECTION_IND VARCHAR2(1),
  OTHER_SKIDDING_PCT NUMBER(3),
  ILCR_SKID_TYPE_CODE VARCHAR2(3) NOT NULL,
  SKID_TYPE_DESCRIPTION VARCHAR2(120),
  CONIFEROUS_VOLUME NUMBER(7),
  DECIDUOUS_VOLUME NUMBER(7),
  ORIGINAL_TREE_TO_TRUCK_RATE NUMBER(8,2),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.TREE_TO_TRUCK_RATE_DTL_AUD (
  TREE_TO_TRUCK_RATE_DTL_AUD_ID NUMBER(10) NOT NULL,
  TREE_TO_TRUCK_RATE_DETAIL_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  TREE_TO_TRUCK_DETAIL_REPORT_ID NUMBER(10) NOT NULL,
  ILCR_REPORT_COST_ITEM_ID NUMBER(10) NOT NULL,
  ITEM_DESCRIPTION VARCHAR2(120),
  ILCR_RATE_COST_TYPE_CODE VARCHAR2(1) NOT NULL,
  RATE_COST_TYPE_DESCRIPTION VARCHAR2(120),
  COSTING_RATE NUMBER(9,2) NOT NULL,
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

CREATE TABLE THE.TREE_TO_TRUCK_REPORT_AUD (
  TREE_TO_TRUCK_REPORT_AUD_ID NUMBER(10) NOT NULL,
  TREE_TO_TRUCK_REPORT_ID NUMBER(10) NOT NULL,
  AUDIT_ACTION_CODE VARCHAR2(10) NOT NULL,
  REPORT_YEAR NUMBER(4) NOT NULL,
  ILCR_MILL_ID NUMBER(10) NOT NULL,
  ILCR_CATEGORY_ID VARCHAR2(5) NOT NULL,
  CUTTING_PERMIT_NUMBER VARCHAR2(10),
  CONTACT_NAME VARCHAR2(50),
  CONTACT_PHONE_NUMBER VARCHAR2(10),
  TSA_NUMBER VARCHAR2(2),
  TSB_NUMBER_CODE VARCHAR2(3),
  TFL_NUMBER_CODE VARCHAR2(2),
  BEC_ZONE_CODE VARCHAR2(4) NOT NULL,
  ILCR_FOREST_REGION_CODE VARCHAR2(10) NOT NULL,
  ILCR_SUPPORT_CENTRE_CODE VARCHAR2(10) NOT NULL,
  HARVEST_LICENSE_NUMBER VARCHAR2(8) NOT NULL,
  DIVISION_LOCATION VARCHAR2(30),
  COMMENTS VARCHAR2(4000),
  RECORD_STATE_CODE VARCHAR2(1),
  REVISION_COUNT NUMBER(5) NOT NULL,
  ENTRY_USERID VARCHAR2(30) NOT NULL,
  ENTRY_TIMESTAMP DATE NOT NULL,
  UPDATE_USERID VARCHAR2(30) NOT NULL,
  UPDATE_TIMESTAMP DATE NOT NULL
);

-- ===================================================================================
-- The fourteen submitted-snapshot views, bodies verbatim from ALL_VIEWS.TEXT.
-- Each is: the latest audit row per business key whose RECORD_STATE_CODE = 'S'.
-- ===================================================================================

CREATE OR REPLACE VIEW THE.BASIC_SILVICULTURE_REPORT_S_VW AS
SELECT basic_silviculture_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location
	, becbiogeoclimatic_catalogue_id
	, reforested_net_area
 	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
       ,recordnumber
       FROM
       (SELECT basic_silviculture_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location
	, becbiogeoclimatic_catalogue_id
	, reforested_net_area
 	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY basic_silviculture_report_id ORDER BY basic_silviculture_rprt_aud_id  DESC) recordnumber
     FROM basic_silviculture_rprt_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.BRIDGE_REPORT_S_VW AS
SELECT bridge_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location_name
	, built_date
	, length
	, deck_width
	, height
	, distance_from_storage
	, ilcr_deck_code
	, ilcr_bridge_abutment_type_code
	, ilcr_bridge_superstructr_code
	, expected_bridge_life_span
	, ilcr_bridge_load_rating_code
	, ilcr_bridge_cnstrctn_type_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
       FROM (SELECT
          bridge_report_id
 	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location_name
	, built_date
	, length
	, deck_width
	, height
	, distance_from_storage
	, ilcr_deck_code
	, ilcr_bridge_abutment_type_code
	, ilcr_bridge_superstructr_code
	, expected_bridge_life_span
	, ilcr_bridge_load_rating_code
	, ilcr_bridge_cnstrctn_type_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY bridge_report_id ORDER BY bridge_report_audit_id  DESC) recordnumber
      FROM bridge_report_audit WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.CAMP_REPORT_S_VW AS
SELECT camp_report_id
       , report_year
       , ilcr_mill_id
       , ilcr_category_id
       , camp_name
       , distance_to_operating_area
       , camp_size_capacity
       , associated_camp_volume
       , isolated_camp_ind
       , comments
       , revision_count
       , entry_userid
       , entry_timestamp
       , update_userid
       , update_timestamp
       ,recordnumber
       FROM
       (SELECT camp_report_id
       , report_year
       , ilcr_mill_id
       , ilcr_category_id
       , camp_name
       , distance_to_operating_area
       , camp_size_capacity
       , associated_camp_volume
       , isolated_camp_ind
       , comments
       , revision_count
       , entry_userid
       , entry_timestamp
       , update_userid
       , update_timestamp
       , ROW_NUMBER() OVER (PARTITION BY camp_report_id ORDER BY camp_report_audit_id  DESC) recordnumber
     FROM camp_report_audit WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.CONTRACTUAL_WORK_REPORT_S_VW AS
SELECT contractual_work_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, contractor_id
	, side_slope_pct
	, performed_unit
	, ilcr_unit_code
	, unit_description
	, ilcr_contractual_source_code
	, source_description
	, bec_zone_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
       FROM (SELECT
          contractual_work_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, contractor_id
	, side_slope_pct
	, performed_unit
	, ilcr_unit_code
	, unit_description
	, ilcr_contractual_source_code
	, source_description
	, bec_zone_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY contractual_work_report_id ORDER BY contractual_work_report_aud_id  DESC) recordnumber
     FROM contractual_work_report_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.CULVERT_REPORT_S_VW AS
SELECT culvert_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
        -- 2016, Jan 26
	, culvert_piece_count
	, span_size
	, rise_size
	, length
	, ilcr_culvert_type_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
       FROM (SELECT
          culvert_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
        -- 2016, Jan 26
	, culvert_piece_count
	, span_size
	, rise_size
	, length
	, ilcr_culvert_type_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY culvert_report_id ORDER BY culvert_report_audit_id  DESC) recordnumber
     FROM culvert_report_audit WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.ILCR_COST_REPORT_DETAIL_S_VW AS
SELECT ilcr_cost_report_detail_id
    , ilcr_report_summary_id
    , transportation_report_id
    , camp_report_id
    , road_maintenance_report_id
    , bridge_report_id
    , culvert_report_id
    , contractual_work_report_id
    , road_construction_reprt_dtl_id
    , basic_silviculture_report_id
    , ilcr_report_cost_item_id
    , volume
    , cost
    , item_description
    , comments
    , revision_count
    , entry_userid
    , entry_timestamp
    , update_userid
    , update_timestamp
    , recordnumber
    FROM (SELECT
      ilcr_cost_report_detail_id
    , ilcr_report_summary_id
    , transportation_report_id
    , camp_report_id
    , road_maintenance_report_id
    , bridge_report_id
    , culvert_report_id
    , contractual_work_report_id
    , road_construction_reprt_dtl_id
    , basic_silviculture_report_id
    , ilcr_report_cost_item_id
    , volume
    , cost
    , item_description
    , comments
    , revision_count
    , entry_userid
    , entry_timestamp
    , update_userid
    , update_timestamp
    , ROW_NUMBER() OVER (PARTITION BY ilcr_cost_report_detail_id ORDER BY ilcr_cost_report_detail_aud_id  DESC) recordnumber
     FROM ilcr_cost_report_detail_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.ILCR_REPORT_SUMMARY_S_VW AS
SELECT ilcr_report_summary_id
      , report_year
      , ilcr_mill_id
      , ilcr_category_id
      , location
      , crown_volume
      , comments
      , revision_count
      , entry_userid
      , entry_timestamp
      , update_userid
      , update_timestamp
      , recordnumber
      FROM (SELECT
        ilcr_report_summary_id
      , report_year
      , ilcr_mill_id
      , ilcr_category_id
      , location
      , crown_volume
      , comments
      , revision_count
      , entry_userid
      , entry_timestamp
      , update_userid
      , update_timestamp
      , ROW_NUMBER() OVER (PARTITION BY ilcr_report_summary_id ORDER BY ilcr_report_summary_audit_id  DESC) recordnumber
      FROM ilcr_report_summary_audit WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.ROAD_CONSTRUCTION_REPRT_S_VW AS
SELECT road_construction_reprt_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, construction_date
	, construction_period
	, construction_division_name
	, ilcr_forest_region_code
	, tsb_number_code
	, tsa_number
	, tfl_number_code
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
      FROM (SELECT
	  road_construction_reprt_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, construction_date
	, construction_period
	, construction_division_name
	, ilcr_forest_region_code
	, tsb_number_code
	, tsa_number
	, tfl_number_code
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY road_construction_reprt_id ORDER BY road_construction_reprt_aud_id  DESC) recordnumber
        FROM road_construction_reprt_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.ROAD_CONSTRUCTN_RPT_DTL_S_VW AS
SELECT road_construction_reprt_dtl_id
	, road_construction_reprt_id
	, road_name
	, side_slope_pct
	, ilcr_road_lifetime_code
	, boulder_area_pct
	, ilcr_soil_moisture_code
	, rippable_rock_pct
	, relative_soil_moistur_rgm_code
	, solid_rock_pct
	, coarse_material_pct
	, fine_material_pct
	, becbiogeo_catalogue_id
	, organic_material_pct
	, sub_grade_length
	, detail_engineering_cost_ind
	, end_haul_distance
	, end_haul_volume
	, overland_distance
	, overland_volume
	, ilcr_road_ballast_method_code
	, sub_grade_surface_width
	, ilcr_road_ballast_materl_code
	, stabilizing_length
	, stabilizing_surface_width
	, stabilizing_depth
	, stabilizing_distance_to_source
    ,rel_soil_moist_rgm_cls_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
    , recordnumber
      FROM (SELECT
  	  road_construction_reprt_dtl_id
	, road_construction_reprt_id
	, road_name
	, side_slope_pct
	, ilcr_road_lifetime_code
	, boulder_area_pct
	, ilcr_soil_moisture_code
	, rippable_rock_pct
	, relative_soil_moistur_rgm_code
	, solid_rock_pct
	, coarse_material_pct
	, fine_material_pct
	, becbiogeo_catalogue_id
	, organic_material_pct
	, sub_grade_length
	, detail_engineering_cost_ind
	, end_haul_distance
	, end_haul_volume
	, overland_distance
	, overland_volume
	, ilcr_road_ballast_method_code
	, sub_grade_surface_width
	, ilcr_road_ballast_materl_code
	, stabilizing_length
	, stabilizing_surface_width
	, stabilizing_depth
	, stabilizing_distance_to_source
    ,rel_soil_moist_rgm_cls_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY road_construction_reprt_dtl_id ORDER BY              road_constructn_rpt_dtl_aud_id  DESC) recordnumber
        FROM road_constructn_rpt_dtl_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.ROAD_MAINTENANCE_REPORT_S_VW AS
SELECT road_maintenance_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, tsa_number
	, tsb_number_code
	, tfl_number_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
                  , recordnumber
      FROM (SELECT
          road_maintenance_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, tsa_number
	, tsb_number_code
	, tfl_number_code
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY road_maintenance_report_id ORDER BY road_maintenance_report_aud_id  DESC) recordnumber
        FROM road_maintenance_report_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.TRANSPORTATION_REPORT_S_VW AS
SELECT transportation_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location_description
	, distance
	, transportation_cycle_time
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
      FROM (SELECT
	  transportation_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, location_description
	, distance
	, transportation_cycle_time
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY transportation_report_id ORDER BY transportation_report_aud_id  DESC) recordnumber
        FROM transportation_report_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.TREE_TO_TRUCK_DTL_RPRT_S_VW AS
SELECT tree_to_truck_detail_report_id
	, tree_to_truck_report_id
	, contractor_id
                   , cut_block
	, ground_base_pct
	, grapple_pct
	, skyline_pct
	, skyline_slope_distance
	, skyline_support_number
	, support_average_distance
	, highlead_pct
	, helicopter_pct
	, distance
	, cycle_time
	, water_dump_destination_ind
	, uphill_direction_ind
	, other_skidding_pct
	, ilcr_skid_type_code
	, skid_type_description
	, coniferous_volume
	, deciduous_volume
	, original_tree_to_truck_rate
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
      FROM (SELECT
 	  tree_to_truck_detail_report_id
	, tree_to_truck_report_id
	, contractor_id
                   , cut_block
	, ground_base_pct
	, grapple_pct
	, skyline_pct
	, skyline_slope_distance
	, skyline_support_number
	, support_average_distance
	, highlead_pct
	, helicopter_pct
	, distance
	, cycle_time
	, water_dump_destination_ind
	, uphill_direction_ind
	, other_skidding_pct
	, ilcr_skid_type_code
	, skid_type_description
	, coniferous_volume
	, deciduous_volume
	, original_tree_to_truck_rate
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY tree_to_truck_detail_report_id ORDER BY tree_to_truck_dtl_rprt_aud_id  DESC)
recordnumber
        FROM tree_to_truck_dtl_rprt_aud  WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.TREE_TO_TRUCK_RATE_DTL_S_VW AS
SELECT tree_to_truck_rate_detail_id
	, tree_to_truck_detail_report_id
	, ilcr_report_cost_item_id
	, item_description
	, ilcr_rate_cost_type_code
                  , rate_cost_type_description
	, costing_rate
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
      FROM (SELECT
 	  tree_to_truck_rate_detail_id
	, tree_to_truck_detail_report_id
	, ilcr_report_cost_item_id
	, item_description
	, ilcr_rate_cost_type_code
                  , rate_cost_type_description
	, costing_rate
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY tree_to_truck_rate_detail_id ORDER BY tree_to_truck_rate_dtl_aud_id  DESC) recordnumber
        FROM tree_to_truck_rate_dtl_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;

CREATE OR REPLACE VIEW THE.TREE_TO_TRUCK_REPORT_S_VW AS
SELECT tree_to_truck_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, cutting_permit_number
	, contact_name
	, contact_phone_number
	, tsa_number
	, tsb_number_code
	, tfl_number_code
	, bec_zone_code
	, ilcr_forest_region_code
	, ilcr_support_centre_code
                   , harvest_license_number
                   , division_location
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , recordnumber
      FROM (SELECT
 	  tree_to_truck_report_id
	, report_year
	, ilcr_mill_id
	, ilcr_category_id
	, cutting_permit_number
	, contact_name
	, contact_phone_number
	, tsa_number
	, tsb_number_code
	, tfl_number_code
	, bec_zone_code
	, ilcr_forest_region_code
	, ilcr_support_centre_code
                   , harvest_license_number
                   , division_location
	, comments
	, revision_count
	, entry_userid
	, entry_timestamp
	, update_userid
	, update_timestamp
        , ROW_NUMBER() OVER (PARTITION BY tree_to_truck_report_id ORDER BY tree_to_truck_report_aud_id  DESC) recordnumber
        FROM tree_to_truck_report_aud WHERE record_state_code = 'S')
  WHERE recordnumber = 1;
