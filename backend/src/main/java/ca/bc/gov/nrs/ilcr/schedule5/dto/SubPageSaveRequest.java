package ca.bc.gov.nrs.ilcr.schedule5.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The whole sub-page list, submitted as one batch (AD-5 — this endpoint is the sole writer of items
 * 62/68).
 *
 * <p><strong>Why the whole list rather than per-row endpoints.</strong> Legacy's Save persists the
 * entire list in one transaction ({@code Schedule5DAO.saveOtherCampExpenses}, {@code :438-486}) and
 * its Add is literally "append to the list, then save the list" ({@code
 * Schedule5CampExpensesMB.addOtherCampExpense()}, {@code :147-156}), so a single batch call
 * reproduces both operations atomically. A per-row API cannot express "the user cleared one
 * description and edited two costs, then hit Save" as one write.
 *
 * <p>An <strong>empty</strong> list is legal and clears every row for that camp and item — it is
 * the reconcile's delete-all case, not a validation error. An <strong>omitted or null</strong> list
 * is a 400: because absence means delete-everything here, a malformed body (a typoed key, a
 * truncated payload, a serializer bug) must fail loudly rather than silently destroy data. The
 * intentional clear is always spelled {@code "rows": []} — which is also the only form legacy could
 * produce, since its Save posted the rendered list every time. Null <em>elements</em> are rejected
 * for the same reason: {@code {"rows":[null]}} deserializes cleanly and would otherwise NPE inside
 * the transaction as a 500.
 *
 * @param rows the complete row set the camp should hold after this call
 */
public record SubPageSaveRequest(
    @NotNull(message = "rows is required") List<@NotNull(message = "rows must not contain null entries") @Valid SubPageRowRequest>
            rows) {}
