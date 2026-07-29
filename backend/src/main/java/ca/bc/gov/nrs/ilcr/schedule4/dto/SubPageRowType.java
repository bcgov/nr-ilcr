package ca.bc.gov.nrs.ilcr.schedule4.dto;

/**
 * The three Schedule 4 sub-page list types and their legacy cost-item codes (Story 4.3, §Decision 1).
 * Code 54 ({@code Schedule4_3_OtherTowing}) is dead — the Other sub-page persists only 55 — so it is
 * intentionally absent.
 */
public enum SubPageRowType {
  /** Towing Total — cost-item 43, no cycle. */
  TOWING(43),
  /** Truck Rehaul (Dewater/Transfer) — cost-item 46, the only type carrying a transportation cycle. */
  TRUCK_REHAUL(46),
  /** Other Transportation — cost-item 55. */
  OTHER(55);

  private final int code;

  SubPageRowType(int code) {
    this.code = code;
  }

  /** The legacy {@code ILCR_REPORT_COST_ITEM_ID} for this sub-page type. */
  public int code() {
    return code;
  }
}
