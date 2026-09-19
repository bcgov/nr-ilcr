package ca.bc.gov.nrs.ilcr.security;

/**
 * Named authorization actions (AD-7). Permission checks reference an action, never a role literal
 * and never a per-page boolean flag. This is WebADE's action-based model remapped, CSP {@code
 * PermissionConstants} style.
 */
public enum Action {
  /** View a schedule document (this story's guard: GET /api/v1/schedule1). */
  VIEW_SCHEDULE,
  /** Edit/save/delete a schedule (used by Story 2.1 writes; declared here for the central map). */
  EDIT_SCHEDULE,
  /**
   * Maintain the lookup/reference code tables (Story 24.3, UC-CODE-001) — the Administration ▸
   * Table Maintenance surface. ADMIN-only: unlike VIEW/EDIT_SCHEDULE (held by both production
   * roles), this is granted solely to {@link Role#ADMIN}, so a SUBMITTER hitting the code-table
   * APIs is denied 403.
   */
  MAINTAIN_CODE_TABLES,
  /**
   * Open a new reporting year (UC-RY-001) — the Administration ▸ Open Reporting Year surface.
   * ADMIN-only, like {@link #MAINTAIN_CODE_TABLES}: a SUBMITTER hitting the open-year API is denied
   * 403.
   */
  OPEN_REPORTING_YEAR,
  /**
   * Edit the role-keyed Home welcome messages (Story 24.2, UC-CNT-001) — the Administration ▸ Home
   * Content surface. ADMIN-only: a SUBMITTER hitting the save API is denied 403. The read for Home
   * rendering is a separate, authenticated (non-admin) endpoint.
   */
  EDIT_HOME_CONTENT,
  /**
   * Maintain licensee accounts and their mill assignments (UC-USR-001/002) — the Administration ▸
   * Users surface. ADMIN-only: a SUBMITTER hitting the assignment or account APIs is denied 403,
   * because these endpoints decide which mills a submitter may report on.
   */
  MAINTAIN_USERS,
  /**
   * Generate the ministry mill reports (the Generate Reports area). ADMIN-only: legacy required
   * BOTH {@code generateReports} and {@code millReport} to render the menu item, and neither was
   * held by a Licensee, so a SUBMITTER hitting the report API is denied 403. Deliberately NOT
   * {@link #VIEW_SCHEDULE} — that is the print/schedule read gate, which both production roles
   * hold.
   */
  GENERATE_MILL_REPORTS,
  /**
   * Maintain the mill inventory and its lifecycle (UC-MILL-001) — the Administration ▸ Mills
   * surface: search, import a ministry mill, activate/deactivate, and save the head-office and
   * contact details. ADMIN-only: a SUBMITTER hitting these APIs is denied 403.
   *
   * <p>Deliberately one action for the whole surface. Legacy derived a separate WebADE action from
   * each button's English label ({@code mills/Save}, {@code mills/Deactivate}, …), which coupled
   * the permission set to UI copy and left the icon-only import button gated by the degenerate name
   * {@code mills/}. Authorization here names the capability, not the control (AD-7).
   */
  MAINTAIN_MILLS,
  /**
   * Move a report track's status on the Check Status page — verify a submitted Schedules 1&ndash;10
   * track, and (when those stories land) reverse it back to Submitted or Draft. ADMIN-only: a
   * SUBMITTER hitting these APIs is denied 403.
   *
   * <p>Deliberately one action for all three admin-driven transitions rather than one per button,
   * for the same reason as {@link #MAINTAIN_MILLS}: legacy derived a WebADE action from each
   * button's label, coupling the permission set to UI copy. Legacy's own gates were also
   * inconsistent here &mdash; {@code canUserSetToDraft}/{@code canUserSetToSubmit} tested for
   * Administrator exactly, while {@code canUserVerifyReport} merely tested "not a Licensee", so an
   * unrecognised role passed. Both collapse onto this one action under the two-group model.
   *
   * <p>Deliberately NOT {@link #EDIT_SCHEDULE}: a SUBMITTER holds that, so reusing it would answer
   * a licensee's verify attempt with the status matrix's 409 rather than an authorization 403.
   * Submitting is the licensee's own transition and is not covered by this action.
   */
  SET_REPORT_STATUS,
  /**
   * Submit the Schedules 1–10 track for ministry review (UC-CHK-002, FR5) — the Check Status page's
   * Submit button. SUBMITTER-only: legacy {@code UserSessionMB.canUserSubmitReport():502-521}
   * enabled the button for {@code ILCR_LICENSEE} alone, and PRD FR5 keeps "ministry users cannot
   * submit on a Licensee's behalf" as a role rule. ADMIN alone therefore receives 403. Epic 16
   * unions capabilities for a dual-role ADMIN+SUBMITTER, but this action retains SUBMITTER mill
   * scope: the caller must be actively assigned to the mill. Holding the action says only that
   * Submit may be OFFERED; whether it succeeds is the track status and the ten-schedule validation
   * gate, decided in the domain service.
   */
  SUBMIT_REPORT,
  /**
   * Extract reported cost data to CSV (UC-EXT-001) — the Generate Reports ▸ Data Extract surface.
   * ADMIN-only: a SUBMITTER hitting the extract API is denied 403.
   *
   * <p>Deliberately NOT {@link #GENERATE_MILL_REPORTS}, even though both live in the Generate
   * Reports area. Legacy derived each page's WebADE action from its view id ({@code
   * AuthorizationPhaseListener.getAuthKey}, so on submit the key was {@code extractData/Generate
   * Report}), separate from the {@code generateReports} action that rendered the submenu around it
   * — a distinction the rebuild keeps rather than collapses. The menu item itself ({@code
   * menu.xhtml:39}) carried no per-item {@code rendered} action of its own, unlike its two report
   * siblings; the page's own action rests on the view-id derivation alone (D2, ratified). It also
   * matters on its own terms: this action releases every selected mill's cost and volume data in
   * one file, which is a wider capability than viewing one ministry report.
   */
  GENERATE_DATA_EXTRACT
}
