# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S05, -S07,
# -S08, -S09, -S10 and -S13.feature (legacy JSF/PrimeFaces). The Associated Licensee User panel: add a
# user, the duplicate warning, deactivate and activate an association, the closed-mill block on
# activation, and View.
#
# WHAT RE-GROUNDING CHANGED
#  * ONE panel, "Associated Licensee User". The Associated Auditors panel is retired (DL-23), so every
#    legacy "form:licenseeList or form:auditorList" step is the licensee table, and S06 (add an auditor)
#    is not written.
#  * Each row carries its single applicable action as a button named for the row's GUID
#    (`Deactivate user <guid>` / `Activate user <guid>` / `View user <guid>`), fired without
#    confirmation, as in legacy.
#  * THE DIRECTORY IS STUBBED — the one request here that does not reach the real backend. "Find and Add
#    User" and View's carried user both ask the NR User Lookup directory, which is switched off in every
#    environment (`ilcr.user-lookup.enabled: false`, so the endpoint 404s and the picker disables
#    itself). "the directory can find user" answers that lookup with a record in the backend's own
#    DirectoryUser shape; the add, the lists and every message are real. defects.md SPEC-1.
#  * The messages name the user by GUID with both name positions EMPTY ("for user <guid> -  ."): the
#    association row knows only the id, and the backend deliberately renders the names blank rather
#    than guess them (MillAssociationController NAME_UNRESOLVED). defects.md VER-3.
#  * S05's "the Find and Add User dialog closes" and S07's "no duplicate" are read back through the API:
#    the add answers 200 in BOTH branches, and only the stored rows tell a new association from a
#    warning.
#  * S13's two legacy scenarios are ONE here — the second's Given is the first's end state.
#  * S10's "carried into the working session and pre-selected on the Users page" -> the carry rides the
#    URL (`/mill-associations?userGuid=`), and the page resolves it to the user's details and mills.
#  * ANCHORS: dedicated seeded mills 26053-26056 and 26058 (real-test-data-patches/mill/
#    mill-status-anchors.sql, folded into R__80), one per writer; S07/S10 share 26055 and write nothing. S05's new association has no delete endpoint, so its cleanup deletes the one row
#    at the DB (scripts/mill_db_restore.py).

@mill @UC-MILL-001 @associations
Feature: Maintain Mills — the mill's user associations
  As a Ministry Administrator
  I want to manage which licensees are associated with a mill
  So that only the right users can report for it

  @p0 @S05
  Scenario: Add a new user to the mill as a Licensee
    Given the S05 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000006"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I add user "E2E00000000000000000000000000006" from the Find and Add User dialog
    Then I should see the message "Mill 9187 - E2E-ADD-USER-TEST has been activated for user E2E00000000000000000000000000006 -  ."
    And the "Find and Add User" dialog closes
    And user "E2E00000000000000000000000000006" is associated with the mill, created inactive
    And the row of user "E2E00000000000000000000000000006" shows "Inactive" and offers "Activate"

  @p0 @S07
  Scenario: Attempt to add a user who is already associated with the mill
    Given the S07 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000005"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I add user "E2E00000000000000000000000000005" from the Find and Add User dialog
    Then I should see the warning "User E2E00000000000000000000000000005 is already associated to mill E2E-READONLY-USERS-TEST. Please verify."
    And the "Find and Add User" dialog closes
    And the mill's user associations are unchanged

  @p0 @S08
  Scenario: Deactivate an active associated user's assignment to the mill
    Given the S08 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then the row of user "E2E00000000000000000000000000002" shows "Active" and offers "Deactivate"
    When I deactivate the association of user "E2E00000000000000000000000000002"
    Then I should see the message "Mill 9184 - E2E-USERS-TEST has been deactivated for user E2E00000000000000000000000000002 -  ."
    And the association of user "E2E00000000000000000000000000002" is persisted as "ENDED"
    And the row of user "E2E00000000000000000000000000002" shows "Inactive" and offers "Activate"

  @p0 @S09
  Scenario: Activate an inactive associated user's assignment while the mill is Active
    Given the S09 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then the row of user "E2E00000000000000000000000000003" shows "Inactive" and offers "Activate"
    When I activate the association of user "E2E00000000000000000000000000003"
    Then I should see the message "Mill 9189 - E2E-ACTIVATE-USER-TEST has been activated for user E2E00000000000000000000000000003 -  ."
    And the association of user "E2E00000000000000000000000000003" is persisted as "ACTIVE"
    And the row of user "E2E00000000000000000000000000003" shows "Active" and offers "Deactivate"

  @p0 @S10
  Scenario: View an associated user's record from the mill's Licensee panel
    Given the S10 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000005"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I view user "E2E00000000000000000000000000005"
    Then I am on the Users page with user "E2E00000000000000000000000000005" selected
    And the selected user's associated mills include mill "9186"

  @p0 @S13
  Scenario: Activating a user on a Closed mill is refused, and succeeds once the mill is activated
    Given the S13 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    Then the Mill Details panel shows the status "Close"
    When I activate the association of user "E2E00000000000000000000000000004"
    Then I should see the error "You must activate the mill before activating any users."
    And the association of user "E2E00000000000000000000000000004" is persisted as "ENDED"
    When I activate the mill
    Then I should see the message "Mill 9185 - E2E-CLOSED-USERS-TEST has been activated."
    When I activate the association of user "E2E00000000000000000000000000004"
    Then I should see the message "Mill 9185 - E2E-CLOSED-USERS-TEST has been activated for user E2E00000000000000000000000000004 -  ."
    And the association of user "E2E00000000000000000000000000004" is persisted as "ACTIVE"

  # GAP-7. S05's user already has an account, so only the association is written. This user has NONE,
  # so the add provisions the ILCR_USER row first. The association row is the evidence: its user FK
  # would refuse it without the account. The cleanup deletes both (mill_db_restore.py).
  @p1 @GAP-7
  Scenario: Adding a user who has no ILCR account yet creates the account and the association
    Given the GAP-7 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000007"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I add user "E2E00000000000000000000000000007" from the Find and Add User dialog
    Then I should see the message "Mill 9194 - E2E-ADD-NEW-USER-TEST has been activated for user E2E00000000000000000000000000007 -  ."
    And user "E2E00000000000000000000000000007" is associated with the mill, created inactive
