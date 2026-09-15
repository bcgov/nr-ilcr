package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The head-office indicator and contact selections to persist (S01).
 *
 * <p>Both contact ids are nullable and a null CLEARS the column — legacy's empty selection resolved
 * to no contact and stored null (MillDAO.java:229, :234), so null has to mean "cleared" rather than
 * "unchanged". A partial save is therefore not expressible by design: the screen sends all three
 * fields every time, matching legacy's save-the-whole-panel behaviour.
 *
 * <p>{@code revisionCount} is boxed and required rather than a primitive: a primitive would bind an
 * absent or misspelled JSON field to 0 and silently pass an optimistic-lock check against a
 * freshly-imported row, whose revision really is 0.
 *
 * @param headOfficeContactInd {@code Y} or {@code N}; the domain is a delivery CHECK constraint
 * @param headOfficeContactId the head-office contact, or null to clear
 * @param divisionContactId the division contact, or null to clear
 * @param revisionCount the revision last read for this mill
 */
public record SaveMillContactsRequest(
    @NotNull @Pattern(regexp = "[YN]") String headOfficeContactInd,
    Long headOfficeContactId,
    Long divisionContactId,
    @NotNull Integer revisionCount) {}
