package ca.bc.gov.nrs.ilcr.schedule3.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Add/edit request for one Included Unacceptable Cost (item 38) row (AD-12). {@code description}
 * required &le; 30 chars; {@code total} optional in the default range. Range messages resolve the legacy
 * bundle keys (AD-8).
 *
 * @param description the cost description (required, &le; 30 chars)
 * @param total the Total $ (nullable; default range &plusmn;99,999,999)
 */
public record UnacceptableRequest(
    @NotBlank(message = "{descriptionRequiredErrorMsg}")
    @Size(max = 30, message = "{descriptionMaxLengthErrorMsg}")
    String description,
    @Min(value = -99999999, message = "{costValidatorErrorMsg}")
    @Max(value = 99999999, message = "{costValidatorErrorMsg}")
    Integer total) {
}
