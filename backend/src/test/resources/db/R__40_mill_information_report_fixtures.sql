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

-- The seeded real-data image has ZERO rows in ISP_SELL_PRICE_ZONE_CODE (all 21 of its mills orphan
-- the FK -- the closure gap Story 1.5 recorded), so the populated region path is untested there.
-- Seeding one zone here is what makes mill 730 prove it while 731 proves the "-" fallback.
INSERT INTO THE.MILL (MILL_ID, MILL_NAME, MILL_NUMBER, ISP_SELL_PRICE_ZONE_CODE, CLIENT_NUMBER, CLIENT_LOCN_CODE, ENTRY_USERID)
  VALUES (733, 'MILL INFO CLOSED SINCE', 7330, NULL, NULL, NULL, 'SEED');

INSERT INTO THE.ISP_SELL_PRICE_ZONE_CODE (ISP_SELL_PRICE_ZONE_CODE, DESCRIPTION, EFFECTIVE_DATE, EXPIRY_DATE)
  VALUES ('Z1', 'Kootenay Selling Price Zone', DATE '2015-01-01', DATE '9999-12-31');

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

INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 730, 'V', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 731, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 732, 'D', 'SEED');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_REPORT_STATUS_CODE, ENTRY_USERID) VALUES (2021, 733, 'D', 'SEED');

-- 730: all four milestones dated. 731: opened only -- the other three are prefix-with-no-date, which
-- is what 80 of the 118 real rows look like and must render blank, not "D: ".
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE)
  VALUES (2021, 730, 'ACT', 'O: 2021-01-05', 'D: 2021-03-10', 'S: 2021-05-20', 'V: 2021-07-01');
INSERT INTO THE.ILCR_MILL_REPORT_STATUS_RPT_VW
    (REPORT_YEAR, ILCR_MILL_ID, ILCR_MILL_STATUS_CODE, MILL_STATUS_OPEN_DATE, MILL_STATUS_DRAFT_DATE, MILL_STATUS_SUBMIT_DATE, MILL_STATUS_VERIFY_DATE)
  VALUES (2021, 731, 'ACT', 'O: 2021-01-05', 'D: ', 'S: ', 'V: ');
-- 732 carries NULL date columns outright -- the null-guard case legacy's unguarded substring(2)
-- would have thrown on.
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
