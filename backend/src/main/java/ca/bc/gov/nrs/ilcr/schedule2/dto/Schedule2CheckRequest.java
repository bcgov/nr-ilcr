package ca.bc.gov.nrs.ilcr.schedule2.dto;

/**
 * The Check Status body: the value Schedule 2's check reads, as it is currently ON SCREEN
 * (bcgov/nr-ilcr#359).
 *
 * <p>Legacy's Check Status was an {@code ajax="false"} full postback into a {@code @ViewScoped}
 * bean ({@code schedule2.xhtml:36,173}), so JSF applied the on-screen inputs to the model BEFORE
 * {@code Schedule2MB.checkStatus()} evaluated it — the verdict described the screen, and nothing
 * was persisted. The shipped implementation re-read the database instead, so an unsaved edit was
 * invisible to the verdict. This DTO restores the legacy behaviour.
 *
 * <p>Deliberately NOT reusing {@link Schedule2Request}: its {@code revisionCount} is required and
 * exists for writes, and its fields are range-validated. Check Status addresses no stored row,
 * takes no optimistic lock, and must ACCEPT incomplete input — reporting incomplete input is its
 * entire purpose. The member is therefore unvalidated; only the body's presence is enforced (an
 * absent body is a clean 400 from {@code @RequestBody}'s own required-ness, never a 500).
 *
 * <p><strong>Nulls must arrive as nulls.</strong> The check is a pure null test (a stored or typed
 * {@code 0} PASSES), so coercing a blank field to zero anywhere on the way here would turn the
 * missing-value finding into a pass.
 *
 * <p>Read-only: this type reaches no write path.
 *
 * @param purchasedLogCostCost item 25 (Purchased/Private Log Costs) cost as entered, or null
 */
public record Schedule2CheckRequest(Integer purchasedLogCostCost) {}
