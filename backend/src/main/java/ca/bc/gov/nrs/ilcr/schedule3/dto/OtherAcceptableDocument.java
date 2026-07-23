package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.util.List;

/**
 * The Other Acceptable Costs sub-resource document (AD-5/AD-12) — the itemized item-124 groups plus the
 * server-computed three-column subtotal. Returned by GET and echoed (with a {@code message}) by the
 * POST/PUT/DELETE mutations so the sub-page redisplays without a second GET.
 *
 * @param editable whether the caller may edit (EDIT_SCHEDULE + Draft track)
 * @param count number of groups (CNT-001)
 * @param subtotal the derived three-column subtotal (TOT→harvest, PO&amp;P→pop, crown derived)
 * @param rows the itemized groups, ordered by TOT detail id
 * @param message success message on a mutation echo (AD-8); null on GET
 */
public record OtherAcceptableDocument(
    boolean editable,
    int count,
    ThreeColumnTotal subtotal,
    List<OtherAcceptableRow> rows,
    MessageInfo message) {

  /** A copy carrying the given success message (for the mutation echo, AD-8). */
  public OtherAcceptableDocument withMessage(MessageInfo message) {
    return new OtherAcceptableDocument(editable, count, subtotal, rows, message);
  }
}
