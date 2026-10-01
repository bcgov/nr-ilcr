package ca.bc.gov.nrs.ilcr.schedule3.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Batch "Save" request for the Other Acceptable Costs sub-page — the whole editable group set in
 * one call (legacy {@code Schedule3SubtotalOtherCostsMB.save()} persisted the entire collection).
 * Each row carries the group's TOT detail {@code id}. The server reconciles by that id: a group
 * with no id is INSERTED as a fresh TOT + PO&amp;P pair, a group whose id matches an existing TOT
 * row is UPDATED in place, and any existing group absent from the request is DELETED. A group
 * carrying an id that is not a TOT row under this summary (stale / concurrently deleted) is
 * rejected with 404 rather than silently re-inserted. (Add/delete are also available as the
 * dedicated POST/DELETE endpoints, which persist immediately.) Validation mirrors {@link
 * OtherAcceptableRequest} and resolves the legacy bundle keys (AD-8).
 *
 * @param rows the groups to persist (each validated)
 */
public record OtherAcceptableSaveRequest(@Valid List<Row> rows) {

  /**
   * One group in a batch save.
   *
   * @param id the group's TOT detail id (identifies the group to update)
   * @param description the cost description (required, &le; 30 chars)
   * @param total the Harvest Total $ (nullable; default range &plusmn;99,999,999)
   * @param pop the PO&amp;P $ (nullable; default range &plusmn;99,999,999)
   */
  public record Row(
      Integer id,
      @NotBlank(message = "{descriptionRequiredErrorMsg}") @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}") String description,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer total,
      @Min(value = -99999999, message = "{costValidatorErrorMsg}") @Max(value = 99999999, message = "{costValidatorErrorMsg}") Integer pop) {}
}
