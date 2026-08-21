package ca.bc.gov.nrs.ilcr.schedule5.dto;

import ca.bc.gov.nrs.ilcr.schedule1.dto.MessageInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * One sub-page's document — the rows for a single camp and item, plus the camp context the page
 * renders around them (AD-12; extends the 7.1/7.2 camp contract rather than re-shaping it).
 *
 * <p>{@code campName} and {@code associatedCampVolume} are carried here so the sub-page renders its
 * heading and its always-disabled Volume input without a second call; legacy got them the same way,
 * by loading the whole Schedule 5 document and scanning it for the Flash-passed camp id ({@code
 * Schedule5CampExpensesMB.getCampDetails()}, {@code :125-133}).
 *
 * <p>{@code editable} is SERVER-authoritative (AD-9): {@code EDIT_SCHEDULE} AND a Draft 1–10 track,
 * never inferred from the role or the track status alone on the client.
 *
 * <p><strong>{@code totals} is NOT the camp panel's figure for the same category, and the two sides
 * compute it differently (deviation (C)).</strong> On the CAMP sub-page the footer volume is the
 * SUM of the row volumes — and because every row's volume was stamped with the same camp-level
 * amount, that sum is {@code n × campVolume} ({@code CoreUtil.sumDescriptionCostVolumeType}, {@code
 * :610-632}). On the ACCESS sub-page it is the SINGLE camp volume, because {@code
 * CampReportType.getOtherAccessExpensesTotal()} ({@code :460-464}) sums cost only and then
 * overwrites the total's volume outright. Each footer's {@code costPerVolume} inherits its own
 * denominator. The two look identical on screen and are not; both are ported verbatim and pinned by
 * a test each.
 *
 * <p>An empty list yields a {@code null} cost, never {@code 0} — except on the camp side in the one
 * case 7.1 deviation (h) describes, where at least one row exists with every cost null and the
 * item-141 volume is non-null. That path serves {@code 0}.
 *
 * @param campId the parent {@code CAMP_REPORT_ID}
 * @param campName the parent camp's name, for the page heading
 * @param associatedCampVolume the item-141/142 volume every row's volume is stamped from
 * @param editable whether this caller may write these rows
 * @param rows the itemized rows in {@code ILCR_COST_REPORT_DETAIL_ID} order (deviation (G))
 * @param totals the footer triple — see the two shapes above
 * @param message the AD-8 success echo; absent on a GET
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubPageDocument(
    int campId,
    String campName,
    BigDecimal associatedCampVolume,
    boolean editable,
    List<SubPageRow> rows,
    CategoryAmount totals,
    MessageInfo message) {

  /** A copy of this document carrying the given success message (the save/delete echo, AD-8). */
  public SubPageDocument withMessage(MessageInfo message) {
    return new SubPageDocument(
        campId, campName, associatedCampVolume, editable, rows, totals, message);
  }
}
