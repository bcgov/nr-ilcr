-- Story 24.3 test-schema extension. The legacy ILCRReportCostItem mapping allocates new
-- contractual-item identifiers from this delivery sequence; keep generated ids away from the
-- hand-seeded cost items and shared report ids used by the other integration fixtures.
CREATE SEQUENCE THE.ILCR_REPORT_COST_ITEM_SEQ START WITH 1000 INCREMENT BY 1 NOCACHE;
