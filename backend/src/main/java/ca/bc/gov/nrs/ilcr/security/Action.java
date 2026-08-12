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
  EDIT_SCHEDULE,
  /**
   * Maintain the lookup/reference code tables (Story 24.3, UC-CODE-001) — the Administration ▸ Table
   * Maintenance surface. ADMIN-only: unlike VIEW/EDIT_SCHEDULE (held by both production roles), this
   * is granted solely to {@link Role#ADMIN}, so a SUBMITTER hitting the code-table APIs is denied 403.
   */
  MAINTAIN_CODE_TABLES
}
