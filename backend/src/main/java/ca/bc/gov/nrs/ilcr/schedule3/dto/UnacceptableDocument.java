package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.util.List;

/**
 * The Included Unacceptable Costs sub-resource document (AD-5/AD-12) — the itemized item-38 rows plus
 * the server-computed total and the read-only Annual Rents (S111) figure (the item-29 Harvest, shown
 * read-only on the sub-page). {@code count} is the number of item-38 rows (the main page's
 * {@code unacceptableCount} additionally adds Annual Rents — unchanged here).
 *
 * @param editable whether the caller may edit (EDIT_SCHEDULE + Draft track)
 * @param count number of item-38 rows
 * @param subtotalTotal sum of the item-38 row costs (whole dollars)
 * @param annualRentsTotal the Annual Rents (Forest Act, S111) figure = item-29 Harvest (read-only)
 * @param rows the itemized rows, ordered by detail id
 * @param message success message on a mutation echo (AD-8); null on GET
 */
public record UnacceptableDocument(
    boolean editable,
    int count,
    Long subtotalTotal,
    Integer annualRentsTotal,
    List<UnacceptableRow> rows,
    MessageInfo message) {

  /** A copy carrying the given success message (for the mutation echo, AD-8). */
  public UnacceptableDocument withMessage(MessageInfo message) {
    return new UnacceptableDocument(
        editable, count, subtotalTotal, annualRentsTotal, rows, message);
  }
}
