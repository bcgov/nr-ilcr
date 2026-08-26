package ca.bc.gov.nrs.ilcr.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire shape for assigning a submitter to a mill (AD-12 pin, Story 2.1). The mill is a path
 * variable and the acting administrator comes from the token, so the body carries only the
 * submitter's directory GUID.
 *
 * <p>The GUID is constrained to exactly 32 characters because the whole design rests on exact
 * equality between this value, {@code ILCR_USER.USER_GUID VARCHAR2(32)}, and the {@code
 * custom:idp_user_id} claim — a blank or wrong-length value could only ever die later as a raw
 * database error, so it is rejected at the boundary instead.
 *
 * <p>The same payload also reactivates a previously ended assignment, because one user↔mill pair
 * has at most one row and is toggled in place rather than re-created.
 *
 * @param userGuid directory GUID ({@code custom:idp_user_id}) of the submitter to assign
 */
public record AssignSubmitterRequest(@NotBlank @Size(min = 32, max = 32) String userGuid) {}
