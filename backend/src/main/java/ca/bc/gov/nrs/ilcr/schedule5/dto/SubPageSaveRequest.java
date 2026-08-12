package ca.bc.gov.nrs.ilcr.schedule5.dto;

import jakarta.validation.Valid;
import java.util.List;

/**
 * The whole sub-page list, submitted as one batch (AD-5 — this endpoint is the sole writer of items
 * 62/68).
 *
 * <p><strong>Why the whole list rather than per-row endpoints.</strong> Legacy's Save persists the
 * entire list in one transaction ({@code Schedule5DAO.saveOtherCampExpenses}, {@code :438-486}) and
 * its Add is literally "append to the list, then save the list"
 * ({@code Schedule5CampExpensesMB.addOtherCampExpense()}, {@code :147-156}), so a single batch call
 * reproduces both operations atomically. A per-row API cannot express "the user cleared one
 * description and edited two costs, then hit Save" as one write.
 *
 * <p>An <strong>empty or null</strong> list is legal and clears every row for that camp and item —
 * it is the reconcile's delete-all case, not a validation error.
 *
 * @param rows the complete row set the camp should hold after this call
 */
public record SubPageSaveRequest(@Valid List<SubPageRowRequest> rows) {

  /** The submitted rows, never null — an omitted array is the same as an empty one. */
  public List<SubPageRowRequest> rowsOrEmpty() {
    return rows == null ? List.of() : rows;
  }
}
