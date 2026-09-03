-- Mill Information report fixtures (UC-MRPT-003 / UC-MRPT-001 content slices).
--
-- REPEATABLE (R__) seed per the 2026-08-20 Flyway fixture convention: data never rides a versioned
-- migration. Prefix 40 puts this in the 10-80 data band and, deliberately, BEFORE R__70 -- that file
-- associates the canonical submitter to every ILCR_MILL_STATUS_XREF row with a set-based insert, so
-- the three mills below must already exist when it runs.
--
-- MILL_ID band 730-733. The README's range table stops at 716 but is stale: schedule 10 write took
-- 717-723 and schedule 6 correction took 724-726, so 727+ is the real high-water mark. Clear of the
-- db-e2e anchor seed's mills (13, 9050-25054) as well.
--
-- Report year 2021, which THE.ILCR_REPORTING_PERIOD already carries. Only V9 seeds
-- ILCR_MILL_REPORT_STATUS_RPT_VW today (514/2021 and 514/2020), so the 2021 report renders exactly
-- five sections -- 514 plus the four below -- which keeps the multi-mill assertions deterministic.
--
-- EXTENDED 2026-09-01 (Story 19.2, Mill Status Report table). The same four mills now also carry the
-- Schedule 11 (silviculture) track: MILL_SILVICULTUR_STATUS_CODE on ILCR_MILL_REPORT_STATUS and the
-- three SILVI_STATUS_* strings on the report view. Without them every Schedule 11 column on the
-- status table renders blank and nothing distinguishes the two track column groups from each other.
-- Extended IN PLACE rather than added as a new file: no new MILL_ID band is introduced, so the
-- README's range table is unchanged, and the 2021 row count stays five (514 plus these four).
--
-- The three mills are chosen to exercise every branch the report has:
--   730  fully populated  -- location, zone description, both contacts, all four milestone dates
--   731  partially null   -- no zone, no contacts, null postal code and address 2, and milestone
--                            dates that are prefix-only (the blank-after-strip case)
--   732  no client at all -- null CLIENT_NUMBER/CLIENT_LOCN_CODE, so every address and the ownership
--                            client name fall back to "-"
--   733  ACT in the year, CLS today -- the mill whose two status codes DISAGREE. The report must
--                            print Active: Yes (the view's per-year code), because the xref's code
--                            is the mill's status now, not its status in 2021. Without this mill no
--                            test can tell the two sources apart.
--
-- On the Schedule 11 track the same four mills spread the same way: 730 fully dated (with dates
-- DIFFERENT from its Schedules 1-10 dates, so swapping the two column groups cannot pass), 731
-- prefix-only, 732 and 733 NULL. There is deliberately NO SILVI_STATUS_OPEN_DATE column: the view has
-- none and the Schedule 11 track has no independent opened date, so both column groups on the status
-- table render the SAME MILL_STATUS_OPEN_DATE (legacy millReportStatus.xhtml:103).
--
-- Date strings carry the legacy 3-character prefix. Delivery uses "D: " / "O: " (letter, colon,
-- space) -- verified 2026-08-28 against ILCR_MILL_REPORT_STATUS_RPT_VW on the seeded real-data image,
-- where all 118 rows match that shape. V9's existing rows use a "01 " digit prefix instead; both are
-- three characters so both survive the substring(3) strip, but new fixtures follow delivery.

INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (730, 'MILL INFO FULL', 7300, 'Z1', '00073001', '00', 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (731, 'MILL INFO SPARSE', 7310, NULL, '00073101', '00', 'SEED');
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (732, 'MILL INFO NO CLIENT', 7320, NULL, NULL, NULL, 'SEED');

-- Zone descriptions come from APPRAISAL_SELL_PRICE_ZONE_CODE, not from the table the MILL column is
-- named after -- see the V20260828 snapshot header for the legacy mapping that settles it. Story
-- 1.5's recorded "MILL -> ISP_SELL_PRICE_ZONE_CODE closure gap" on the seeded image was that wrong
-- table: on the FTA database all 140 mills' codes resolve against APPRAISAL_SELL_PRICE_ZONE_CODE.
-- Seeding one zone here is what makes mill 730 prove the populated path while 731 proves the "-"
-- fallback from a NULL code -- the shape that used to NPE the degrade (deferred-work, 19.1).
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (733, 'MILL INFO CLOSED SINCE', 7330, NULL, NULL, NULL, 'SEED');

INSERT INTO THE.APPRAISAL_SELL_PRICE_ZONE_CODE (APPRAISAL_SELL_PRICE_ZONE_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE, UPDATE_TIMESTAMP)
  VALUES ('Z1', 'Kootenay Selling Price Zone', DATE '2015-01-01', DATE '9999-12-31', DATE '2015-01-01');

INSERT INTO THE.CLIENT_LOCATION (CLIENT_NUMBER, CLIENT_LOCN_CODE, CLIENT_LOCN_NAME, ADDRESS_1, ADDRESS_2, CITY, POSTAL_CODE)
  VALUES ('00073001', '00', 'FULL OWNERSHIP HOLDINGS LTD', '100 MAIN STREET', 'SUITE 400', 'CRANBROOK', 'V1C1A1');
-- Null ADDRESS_2 and POSTAL_CODE: both are nullable in delivery and both drive a "-" substitution.
INSERT INTO THE.CLIENT_LOCATION (CLIENT_NUMBER, CLIENT_LOCN_CODE, CLIENT_LOCN_NAME, ADDRESS_1, ADDRESS_2, CITY, POSTAL_CODE)
  VALUES ('00073101', '00', 'SPARSE HOLDINGS LTD', '200 SECOND AVENUE', NULL, 'REVELSTOKE', NULL);

-- Only 4 of 17 real mills carry contacts, so an absent contact is the common shape, not the edge.
-- Mill 730 has both; 731 and 732 have neither.
INSERT INTO THE.CLIENT_CONTACT (CLIENT_CONTACT_ID, CLIENT_NUMBER, CLIENT_LOCN_CODE, BUS_CONTACT_CODE, CONTACT_NAME, BUSINESS_PHONE, EMAIL_ADDRESS)
  VALUES (7301, '00073001', '00', 'BL', 'HEAD OFFICE CONTACT', '2505551212', 'head.office@example.test');
-- Null BUSINESS_PHONE on the division contact: the phone formatter must yield "-" rather than throw.
INSERT INTO THE.CLIENT_CONTACT (CLIENT_CONTACT_ID, CLIENT_NUMBER, CLIENT_LOCN_CODE, BUS_CONTACT_CODE, CONTACT_NAME, BUSINESS_PHONE, EMAIL_ADDRESS)
  VALUES (7302, '00073001', '00', 'DV', 'DIVISION CONTACT', NULL, 'division@example.test');

INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (730, 'ACT', 'Y', 7301, 7302, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (731, 'ACT', NULL, NULL, NULL, 'SEED');
-- CLS so the report's Active flag renders "No" for at least one section (legacy maps ACT -> Yes).
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (732, 'CLS', NULL, NULL, NULL, 'SEED');
INSERT INTO THE.ILCR_MILL_STATUS_XREF (ILCR_MILL_STATUS_XREF_ID, ILCR_MILL_STATUS_CODE, HEAD_OFFICE_CONTACT_IND, HEAD_OFFICE_CONTACT_ID, DIVISION_CONTACT_ID, ENTRY_USERID)
  VALUES (733, 'CLS', NULL, NULL, NULL, 'SEED');

-- The two tracks are INDEPENDENT (PRD): MILL_SILVICULTUR_STATUS_CODE is set apart from
-- ILCR_MILL_REPORT_STATUS_CODE and deliberately disagrees with it on 731, so nothing can read one
-- track's status and pass for the other. NULL on 732/733 is the never-started Schedule 11 case, which
-- V9's (2021, 514) row also carries.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 730, 'V', 'V', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 731, 'D', 'S', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 732, 'D', NULL, 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, MILL_SILVICULTUR_STATUS_CODE, ENTRY_USERID) VALUES (2021, 733, 'D', NULL, 'SEED');

-- 730: all four milestones dated on BOTH tracks, with six DISTINCT dates -- the Schedules 1-10 dates
-- are Mar/May/Jul and the Schedule 11 dates are Apr/Jun/Aug, so a status-table cell that renders the
-- wrong track's value is visible rather than plausible. 731: opened only -- every other milestone on
-- both tracks is prefix-with-no-date, which is what 80 of the 118 real rows look like.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE,
     SILVI_STATUS_DRAFT_DATE, SILVI_STATUS_SUBMIT_DATE, SILVI_STATUS_VERIFY_DATE)
  VALUES (2021, 730, 'ACT', 'O: 2021-01-05', 'D: 2021-03-10', 'S: 2021-05-20', 'V: 2021-07-01',
          'D: 2021-04-12', 'S: 2021-06-15', 'V: 2021-08-20');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE,
     SILVI_STATUS_DRAFT_DATE, SILVI_STATUS_SUBMIT_DATE, SILVI_STATUS_VERIFY_DATE)
  VALUES (2021, 731, 'ACT', 'O: 2021-01-05', 'D: ', 'S: ', 'V: ',
          'D: ', 'S: ', 'V: ');
-- 732 carries NULL date columns outright -- the null-guard case legacy's unguarded substring(2)
-- would have thrown on. The three SILVI_STATUS_* columns are omitted here and on 733, so both are
-- NULL on the Schedule 11 track too: on the status table those lines must render EMPTY, never the
-- text "null".
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE)
  VALUES (2021, 732, 'CLS', NULL, NULL, NULL, NULL);
-- Mill 733: ACT here (the reporting year) but CLS on the xref (today). The two disagree ON PURPOSE.
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE)
  VALUES (2021, 733, 'ACT', 'O: 2021-01-05', 'D: ', 'S: ', 'V: ');

-- NO ILCR_USER / ILCR_MILL_USER_XREF rows are seeded here. The associated-user tables are descoped
-- from this story (the app cannot resolve a display name from a USER_GUID: ILCR_USER stores no name
-- by DL-7 design, and the directory offers GUID lookup for BCeID only), so user fixtures no test
-- asserts would be dead weight. R__70 still associates the canonical submitter to these mills along
-- with every other seeded mill; that is incidental and nothing here depends on it.
