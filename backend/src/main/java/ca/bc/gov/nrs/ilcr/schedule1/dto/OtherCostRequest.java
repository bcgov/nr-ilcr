package ca.bc.gov.nrs.ilcr.schedule1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add/edit request for one itemized Subtotal Other Costs row (AD-12). {@code description} is required
 * (legacy JSF {@code required}) and capped at 30 chars (legacy {@code maxlength="30"}); {@code cost}
 * is OPTIONAL — legacy permits a null cost (that is how the WRN-002 empty-cost warning rows arise).
 * The row's volume is not client-supplied: it inherits the shared Other-Costs volume server-side
 * (BR-06). Range messages resolve the LEGACY bundle keys (AD-8) via the wired {@code MessageSource}.
 *
 * @param description the itemized cost description (required, &le; 30 chars)
 * @param cost the entered cost (nullable; default range &plusmn;99,999,999)
 */
public record OtherCostRequest(
    @NotBlank(message = "{descriptionRequiredErrorMsg}")
    @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
    String description,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer cost) {
}
