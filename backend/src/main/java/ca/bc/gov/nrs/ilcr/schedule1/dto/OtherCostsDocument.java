package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Subtotal Other Costs sub-resource document (AD-5/AD-12) — the itemized item-19 rows plus the
 * shared volume and server-computed totals. Returned by GET and echoed (with a {@code message}) by
 * the POST/PUT/DELETE mutations so the sub-page redisplays without a second GET.
 *
 * @param volume the shared Other-Costs volume (the null-description item-19 row)
 * @param costSubtotal sum of the itemized row costs (null-safe), whole dollars
 * @param perUnit {@code costSubtotal / volume}, server-computed (null when volume null/zero)
 * @param count number of itemized rows
 * @param rows the itemized rows (description non-null), ordered by detail id
 * @param editable whether the caller may edit (EDIT_SCHEDULE + Draft track)
 * @param message success message on a mutation echo (AD-8); null on GET
 */
public record OtherCostsDocument(
    BigDecimal volume,
    Long costSubtotal,
    BigDecimal perUnit,
    int count,
    List<OtherCostRow> rows,
    boolean editable,
    MessageInfo message) {

  /** A copy carrying the given success message (for the mutation echo, AD-8). */
  public OtherCostsDocument withMessage(MessageInfo message) {
    return new OtherCostsDocument(volume, costSubtotal, perUnit, count, rows, editable, message);
  }
}
