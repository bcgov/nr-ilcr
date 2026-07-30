-- Story 25.3 (UC-SCH11-001) seed: extra THE.BIOGEOCLIMATIC_CATALOGUE rows for the BEC type-ahead
-- search endpoint (BR-09/S16). TEST-SCOPE ONLY. V22 claimed 2026-07-29 (V1-V9, V20, V21 on this
-- tree; next free). NO runtime DDL (AD-2) - the catalogue table + ids 8801-8803 exist from V20.
--
-- V20's three rows all carry distinct zone prefixes (ICH/CWH/ESSF), so they cannot prove a prefix
-- search that returns MULTIPLE label-ordered matches while excluding non-matches. These four share
-- the 'SBS' zone prefix; becLabel concat (zone+subzone+variant+phase, nulls -> ''):
--   8804 -> 'SBSdk' | 8805 -> 'SBSmc2' | 8806 -> 'SBSwk1a' | 8807 -> 'SBPSxc'
-- 'SBS' (case-insensitive) matches 8804/8805/8806 in that label order; 'SBSm' narrows to 8805;
-- 8807 ('SBPS...') is the near-miss that a prefix (not contains) match must EXCLUDE for 'SBS'.
INSERT INTO THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID, BEC_ZONE_CODE, SUBZONE, VARIANT, PHASE) VALUES (8804, 'SBS', 'dk', NULL, NULL);
INSERT INTO THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID, BEC_ZONE_CODE, SUBZONE, VARIANT, PHASE) VALUES (8805, 'SBS', 'mc', '2', NULL);
INSERT INTO THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID, BEC_ZONE_CODE, SUBZONE, VARIANT, PHASE) VALUES (8806, 'SBS', 'wk', '1', 'a');
INSERT INTO THE.BIOGEOCLIMATIC_CATALOGUE (BIOGEOCLIMATIC_CATALOGUE_ID, BEC_ZONE_CODE, SUBZONE, VARIANT, PHASE) VALUES (8807, 'SBPS', 'xc', NULL, NULL);

COMMIT;
