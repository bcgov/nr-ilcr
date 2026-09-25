package ca.bc.gov.nrs.ilcr.schedule3.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Check Status body: every value Schedule 3's check reads that is ON THIS SCREEN
 * (bcgov/nr-ilcr#359).
 *
 * <p>Legacy's Check Status ({@code schedule3.xhtml:38,421}, {@code Schedule3MB.checkStatus()})
 * described the screen, including unsaved edits, and persisted nothing — the observed behaviour
 * #359 records. No mechanism is claimed for it: the same button markup on legacy Schedule 5 judged
 * the saved record (#476). The shipped implementation re-read the database instead, so an unsaved
 * edit was invisible to the verdict. This DTO restores the legacy behaviour.
 *
 * <p><strong>The override drives every rule that reads it.</strong> The on-screen Override
 * Harvest/Total PO&amp;P suppresses the Harvest&lt;PO&amp;P check on the fixed lines AND on the
 * Other Acceptable (item 124) subtotal, exactly as the stored flag does on the stored path.
 *
 * <p><strong>What it does NOT carry.</strong> The Other Acceptable (item 124) and Included
 * Unacceptable (item 38) rows are edited on their own sub-pages and are never inputs on this
 * screen, so both the payload path and the stored path read them from the database. Sending them
 * would let a stale client copy override the database.
 *
 * <p>Deliberately NOT reusing {@link Schedule3Request}: its {@code revisionCount} is required and
 * exists for writes, and its fields are range-validated. Check Status addresses no stored row,
 * takes no optimistic lock, and must ACCEPT incomplete input — reporting incomplete input is its
 * entire purpose. The members are therefore unvalidated; only the body's presence is enforced (an
 * absent body is a clean 400 from {@code @RequestBody}'s own required-ness, never a 500). A null or
 * partial {@code lineItems} is not an error either: a line that is not carried is simply missing,
 * and is reported as such.
 *
 * <p><strong>Nulls must arrive as nulls.</strong> Every missing-value check is a pure null test (a
 * stored or typed {@code 0} PASSES), so coercing a blank field to zero anywhere on the way here
 * would turn every missing value into a pass.
 *
 * <p>Read-only: this type reaches no write path.
 *
 * @param overrideHarvestTotalPop the Override Harvest/Total PO&amp;P indicator as selected; only
 *     {@code "Y"} overrides, anything else (including null) does not
 * @param lineItems the eleven fixed lines' on-screen amounts, keyed by Harvest cost-item code
 * @param popTimberVolume PO&amp;P Timber (item 118) volume as entered
 * @param crownTimberVolume Crown Timber (item 119) volume as entered
 */
public record Schedule3CheckRequest(
    String overrideHarvestTotalPop,
    List<LineEntry> lineItems,
    BigDecimal popTimberVolume,
    BigDecimal crownTimberVolume) {

  /**
   * One fixed line as entered, keyed by its Harvest cost-item code (27–37). Unvalidated by design
   * (see the type's javadoc). {@code pop} is ignored for the Harvest-only lines (29, 33, 37), which
   * the check never tests for PO&amp;P.
   *
   * @param costItemCode the line's Harvest cost-item code
   * @param harvest the Harvest Total as entered, or null when blank
   * @param pop the PO&amp;P as entered, or null when blank
   */
  public record LineEntry(Integer costItemCode, Integer harvest, Integer pop) {}
}
