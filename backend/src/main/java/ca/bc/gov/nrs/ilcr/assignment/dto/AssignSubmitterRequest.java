package ca.bc.gov.nrs.ilcr.assignment.dto;

/**
 * Wire shape for assigning a submitter to a mill (AD-12 pin, Story 2.1). The mill is a path
 * variable and the acting admin is the JWT principal, so the body carries only the submitter's FAM
 * user GUID ({@code custom:idp_user_id}). Consumed by Story 2.2's {@code POST
 * /api/v1/mills/{millId}/submitters}.
 *
 * @param userGuid the FAM user GUID of the submitter to assign
 */
public record AssignSubmitterRequest(String userGuid) {}
