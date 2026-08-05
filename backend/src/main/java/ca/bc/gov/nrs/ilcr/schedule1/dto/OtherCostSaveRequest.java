package ca.bc.gov.nrs.ilcr.schedule1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Batch "Save" request for the Subtotal Other Costs sub-page — the whole editable row set in one call
 * (legacy {@code Schedule1OtherCostsMB.save()} persisted the entire collection). Each row carries its
 * detail {@code id}. The server reconciles by that id: a row with no id is INSERTED, a row whose id
 * matches an existing item-19 row is UPDATED in place, and any existing row absent from the request is
 * DELETED. A row carrying an id that is not an itemized item-19 row under this summary (stale /
 * concurrently deleted) is rejected with 404 rather than silently re-inserted. (Add/delete are also
 * available as the dedicated POST/DELETE endpoints, which persist immediately.) Validation mirrors
 * {@link OtherCostRequest} and resolves the legacy bundle keys (AD-8).
 *
 * @param rows the rows to persist (each validated)
 */
public record OtherCostSaveRequest(@Valid List<Row> rows) {

  /**
   * One row in a batch save.
   *
   * @param id the item-19 detail id (identifies the row to update)
   * @param description the itemized cost description (required, &le; 30 chars)
   * @param cost the entered cost (nullable; default range &plusmn;99,999,999)
   */
  public record Row(
      Integer id,
      @NotBlank(message = "{descriptionRequiredErrorMsg}")
      @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
      String description,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}")
      @Max(value = 99999999, message = "{costValidatorErrorMsg}")
      Integer cost) {
  }
}
