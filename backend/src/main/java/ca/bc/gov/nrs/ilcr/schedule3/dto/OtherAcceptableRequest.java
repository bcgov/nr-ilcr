package ca.bc.gov.nrs.ilcr.schedule3.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add/edit request for one Other Acceptable Cost (item 124) group (AD-12). {@code description} is
 * required (legacy JSF {@code required}) and capped at 30 chars (legacy {@code maxlength="30"});
 * {@code total} and {@code pop} are OPTIONAL costs in the default range (legacy permits blank at Save —
 * Check Status catches missing). Crown is derived server-side and never client-supplied. Range messages
 * resolve the LEGACY bundle keys (AD-8) via the wired {@code MessageSource}.
 *
 * @param description the cost description (required, &le; 30 chars)
 * @param total the Harvest Total $ (nullable; default range &plusmn;99,999,999)
 * @param pop the PO&amp;P $ (nullable; default range &plusmn;99,999,999)
 */
public record OtherAcceptableRequest(
    @NotBlank(message = "{descriptionRequiredErrorMsg}")
    @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
    String description,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer total,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer pop) {
}
