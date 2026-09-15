package ca.bc.gov.nrs.ilcr.millmaintenance.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The optimistic-lock token for an activate or deactivate (S03/S04).
 *
 * <p>Boxed and required for the same reason as on the contact save: a primitive would let an absent
 * field pass as 0 and match a never-updated row by accident.
 *
 * @param revisionCount the revision last read for this mill
 */
public record ChangeMillStatusRequest(@NotNull Integer revisionCount) {}
