package ca.bc.gov.nrs.ilcr.schedule3.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Batch "Save" request for the Included Unacceptable Costs sub-page — the whole editable row set in one
 * call (legacy {@code Schedule3IncludedUnacceptableCostsMB.save()} persisted the entire collection).
 * The server reconciles by detail {@code id}: a row with no id is INSERTED, a row whose id matches an
 * existing item-38 row is UPDATED in place, and any existing row absent from the request is DELETED.
 * A row carrying an id that is not an item-38 row under this summary (stale / concurrently deleted) is
 * rejected with 404 rather than silently re-inserted. (Add/delete are also available as the dedicated
 * POST/DELETE endpoints, which persist immediately.) Validation mirrors {@link UnacceptableRequest} and
 * resolves the legacy bundle keys (AD-8).
 *
 * @param rows the rows to persist (each validated)
 */
public record UnacceptableSaveRequest(@Valid List<Row> rows) {

  /**
   * One row in a batch save.
   *
   * @param id the item-38 detail id (identifies the row to update)
   * @param description the cost description (required, &le; 30 chars)
   * @param total the Total $ (nullable; default range &plusmn;99,999,999)
   */
  public record Row(
      Integer id,
      @NotBlank(message = "{descriptionRequiredErrorMsg}")
      @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
      String description,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer total) {
  }
}
