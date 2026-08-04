-- Story 25.3 code-review seed: 51 THE.BIOGEOCLIMATIC_CATALOGUE rows sharing the 'ZZQ' zone prefix
-- so the BEC search's FETCH FIRST 50 ROWS ONLY cap is observable (a 'ZZQ' search must return exactly
-- 50 of the 51, label-ordered, dropping 'ZZQz51'). TEST-SCOPE ONLY. Renumbered V23 -> V28 2026-07-30
-- alongside V27 after schedule 8 merged from main claimed V22-V26 (bump the newer, unmerged
-- migration per README convention). Ids 8901-8951 (V20 uses 8801-8803, V27 8804-8807). Labels are
-- 'ZZQz01'..'ZZQz51' (subzone 'z01'..'z51', VARCHAR2(3)); no real BEC zone starts with 'ZZQ', so
-- these rows are invisible to every other prefix assertion.
INSERT INTO THE.BIOGEOCLIMATIC_CATALOGUE
    (BIOGEOCLIMATIC_CATALOGUE_ID, BEC_ZONE_CODE, SUBZONE, VARIANT, PHASE)
SELECT 8900 + LEVEL, 'ZZQ', 'z' || TO_CHAR(LEVEL, 'FM00'), NULL, NULL
  FROM DUAL
CONNECT BY LEVEL <= 51;

COMMIT;
