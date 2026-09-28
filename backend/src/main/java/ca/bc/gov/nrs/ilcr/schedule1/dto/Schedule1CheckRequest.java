package ca.bc.gov.nrs.ilcr.schedule1.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: every value Schedule 1's check reads that is ON THIS SCREEN
 * (bcgov/nr-ilcr#359).
 *
 * <p>Legacy's Check Status ({@code schedule1.xhtml:38,798}, {@code Schedule1MB.checkStatus()})
 * described the screen, including unsaved edits, and persisted nothing — the observed behaviour
 * #359 records. No mechanism is claimed for it: the same button markup on legacy Schedule 5 judged
 * the saved record (#476). The shipped implementation re-read the database instead, so an unsaved
 * edit was invisible to the verdict. This DTO restores the legacy behaviour.
 *
 * <p><strong>What it does NOT carry.</strong> The itemized Other Costs rows — and with them the row
 * count and cost subtotal — are edited on the Other Costs sub-page and are never inputs on this
 * screen, so both the payload path and the stored path read them from the database. Only the shared
 * Other Costs VOLUME is an input here, so only it travels. Sending the rows would let a stale
 * client copy override the database.
 *
 * <p>Deliberately NOT reusing {@link Schedule1Request}: its {@code revisionCount} is required and
 * exists for writes, and its fields are range-validated. Check Status addresses no stored row,
 * takes no optimistic lock, and must ACCEPT incomplete input — reporting incomplete input is its
 * entire purpose. The members are therefore unvalidated; only the body's presence is enforced (an
 * absent body is a clean 400 from {@code @RequestBody}'s own required-ness, never a 500). A null or
 * partial {@code lineItems} is not an error either: a line that is not carried is simply missing,
 * and is reported as such.
 *
 * <p><strong>Nulls must arrive as nulls.</strong> Every check is a pure null test (a stored or
 * typed {@code 0} PASSES), so coercing a blank field to zero anywhere on the way here would turn
 * every missing value into a pass.
 *
 * <p>Read-only: this type reaches no write path.
 *
 * @param lineItems the on-screen volume/cost per cost-item code — 12–18, 1 and 2 carry both; 143,
 *     144, 139 and 140 carry a volume only (their costs are pulled or derived, never checked)
 * @param otherCostsVolume the shared Subtotal Other Costs volume as entered
 */
public record Schedule1CheckRequest(List<LineEntry> lineItems, BigDecimal otherCostsVolume) {

  /**
   * One on-screen line. Unvalidated by design (see the type's javadoc).
   *
   * @param costItemCode the line's cost-item code
   * @param volume the volume as entered, or null when blank
   * @param cost the cost as entered, or null when blank (or not an input on this line)
   */
  public record LineEntry(Integer costItemCode, BigDecimal volume, Integer cost) {}
}
