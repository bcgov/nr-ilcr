package ca.bc.gov.nrs.ilcr.security;

/**
 * Named authorization actions (AD-7). Permission checks reference an action, never a role literal
 * and never a per-page boolean flag. This is WebADE's action-based model remapped, CSP
 * {@code PermissionConstants} style.
 */
public enum Action {
  /** View a schedule document (this story's guard: GET /api/v1/schedule1). */
  VIEW_SCHEDULE,
  /** Edit/save/delete a schedule (used by Story 2.1 writes; declared here for the central map). */
  EDIT_SCHEDULE
}
