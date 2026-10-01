# Story 23.4's acceptance journey, re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-001/
# gherkin/UC-USR-001-S13, -S07, -S01, -S09, -S08 and -S02.feature (legacy JSF/PrimeFaces users.xhtml):
# find a directory user (the all-blank and no-match searches first), activate the account, assign two
# mills (a repeat add warns), try to deactivate while assignments are active (blocked), clear each
# assignment, then deactivate. ONE scenario on purpose — each step's Given is the previous step's end
# state, exactly as the story writes it. The legacy slices are also covered one by one elsewhere in this
# folder where they have an arm this journey does not reach.
#
# WHAT RE-GROUNDING CHANGED
#  * THE PICKER IS A TYPE-AHEAD, not a dialog. There is no "Select User" button, no Search/Clear button,
#    no results table and no Select row: an Identity provider dropdown, then a ComboBox whose options ARE
#    the candidates. Licensees are BCeID Business users, and a BCeID lookup is an exact user ID.
#  * THE DIRECTORY IS STUBBED — the one request here that does not reach the real backend. The NR User
#    Lookup API is off in every environment (DL-27: the endpoint 404s and the picker disables itself).
#    "the directory can find the seeded licensees" answers the lookup in the backend's own DirectoryUser
#    shape; the account, the assignments and every message are real. defects.md SPEC-1.
#  * S13 (all-blank lists everyone) is deviation (B): the directory refuses a blank search, so the picker
#    never sends one. Asserted as shipped — no request, no candidate, no message — see defects.md VER-1.
#  * S07's ERR-001 is recast (deviation (C)): the ADAM role wording names retired WebADE concepts, so the
#    note reads "...granted the ILCR Submitter role." VER-2.
#  * ROLE AND ACTIVE ARE UNKNOWN UNTIL A WRITE (deviation (O)): no endpoint reads the account, so the
#    page offers BOTH account actions and shows "—" until one answers. The account flag is therefore
#    read back at the DB (scripts/usr_db_restore.py read-account). VER-3.
#  * Adding a mill is the "Mill" dropdown + Add, not a Find and Add Mill dialog; the new assignment is
#    ACTIVE at once (AssignmentService.assign). The messages name the user by GUID with both name
#    positions EMPTY (deviation (H)).
#  * The mill-add is the Users page's own surface (AssignmentApi) — not the Mills page's, whose add
#    creates the row INACTIVE (UC-MILL-001 BR-08). Both are shipped; the asymmetry is recorded, VER-4.
#  * ANCHORS: the journey user (...11, account inactive, no assignment) and the two journey mills
#    26064/26065 (real-test-data-patches/usr/user-admin-anchors.sql, folded into R__80). Both new
#    assignments have no delete endpoint, so the cleanup deletes them at the DB and puts the account
#    back through the API.

@usr @UC-USR-001 @journey
Feature: Maintain Users — the account and mill-assignment journey
  As a Ministry Administrator
  I want to find a licensee, switch their account on, assign their mills, and switch it off again
  So that I control who may work in ILCR and which mills they may report on

  @p0 @S13 @S07 @S01 @S09 @S08 @S02 @ERR-001 @ERR-002 @WRN-001 @SUC-001 @SUC-002
  Scenario: Find a licensee, activate the account, assign two mills, and deactivate once every assignment is cleared
    Given the journey user is at rest
    And the directory can find the seeded licensees
    And I am acting as the Ministry Administrator
    When I open Users from the Administration menu
    And I search the BCeID directory with nothing entered
    Then no directory search is sent and no user is offered
    When I search the BCeID directory for user ID "e2e-nobody"
    Then the picker reports that no user matches
    When I find and select the journey user
    Then the journey user is selected
    And the selected user's role and active flag are not shown, and both account actions are offered
    And the selected user has no associated mills

    When I activate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000011 -   has been activated."
    And the selected user's account is persisted as "Y"
    And the selected user's account shows Active "Y", role "LICENSEE", and offers only "Deactivate"

    When I add mill "9195" to the selected user
    Then I should see the message "Mill 9195 - E2E-USR-JOURNEY-A has been activated for user E2E00000000000000000000000000011 -  ."
    And the selected user's assignment to mill "9195" is persisted as "ACTIVE"
    And the row of mill "9195" shows "Active" and offers "Deactivate"
    When I add mill "9195" to the selected user
    Then I should see the warning "User E2E00000000000000000000000000011 is already associated to mill E2E-USR-JOURNEY-A. Please verify."
    And the selected user's assignment to mill "9195" is persisted as "ACTIVE"
    When I add mill "9196" to the selected user
    Then I should see the message "Mill 9196 - E2E-USR-JOURNEY-B has been activated for user E2E00000000000000000000000000011 -  ."
    And the selected user's associated mills are exactly "9195, 9196"

    When I deactivate the selected user's account
    Then I should see the error "User E2E00000000000000000000000000011 -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted."
    And the selected user's account is persisted as "Y"

    When I deactivate the assignment to mill "9195"
    Then I should see the message "Mill 9195 - E2E-USR-JOURNEY-A has been deactivated for user E2E00000000000000000000000000011 -  ."
    And the selected user's assignment to mill "9195" is persisted as "ENDED"
    And the row of mill "9195" shows "Inactive" and offers "Activate"
    When I deactivate the assignment to mill "9196"
    Then the selected user's assignment to mill "9196" is persisted as "ENDED"
    And no row of the selected user is active

    When I deactivate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000011 -   has been deactivated."
    And the selected user's account is persisted as "N"
    And the selected user's account shows Active "N", role "LICENSEE", and offers only "Activate"
