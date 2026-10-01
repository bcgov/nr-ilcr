# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-002/gherkin/UC-USR-002-S02 to -S07,
# -S10 and -S11.feature: everything the administrator does to the user after arriving from the mill —
# the account, adding a mill (and the duplicate warning), the per-row activate/deactivate, and the
# blocked deactivation that succeeds once every assignment is cleared.
#
# WHAT RE-GROUNDING CHANGED
#  * Every scenario ARRIVES the way the UC says — Mills page, select the mill, View the user — so the
#    carried selection is what the action lands on.
#  * S02/S03's account actions, S04's add and S06/S07's row actions are the same controls UC-USR-001
#    drives (the Users page is one screen); the steps are shared, not duplicated.
#  * S04 "the newly added mill appears as an Active assignment" -> the Users-page add creates it ACTIVE
#    (AssignmentService.assign), unlike the Mills page's add (INACTIVE, UC-MILL-001 BR-08). VER-4.
#  * S11 "for each remaining Active mill assignment row in turn" -> two rows, 26072 and 26073.
#  * ANCHORS, one user per writer: ...17 ('N') and ...18 ('Y') both ENDED on 26069 (9200); ...19 ACTIVE on
#    26070 (9201), adding 26067 (9198); ...20 ENDED and ...21 ACTIVE on 26071 (9202); ...22 ACTIVE on
#    26072/26073 (9203/9204); S05 is read-only on ...15. Every write is restored through the API except
#    S04's new assignment, which is deleted at the DB (scripts/usr_db_restore.py).

@usr @UC-USR-002 @after-arrival
Feature: Maintain User from a Mill Record — maintaining the carried user
  As a Ministry Administrator
  I want to maintain the user I arrived with from a mill
  So that I can fix their account and assignments in the same visit

  Background:
    Given the directory can find the seeded licensees
    And I am acting as the Ministry Administrator

  @p0 @S02 @SUC-001
  Scenario: Activate the carried user's inactive account
    Given the account-inactive user is at rest
    When I view the account-inactive user from mill "9200" on the Mills page
    And I activate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000017 -   has been activated."
    And the selected user's account is persisted as "Y"
    And the selected user's account shows Active "Y", role "LICENSEE", and offers only "Deactivate"

  @p0 @S03 @SUC-002
  Scenario: Deactivate the carried user's account when no assignment is active
    Given the account-active user is at rest
    When I view the account-active user from mill "9200" on the Mills page
    Then no row of the selected user is active
    When I deactivate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000018 -   has been deactivated."
    And the selected user's account is persisted as "N"
    And the selected user's account shows Active "N", role "LICENSEE", and offers only "Activate"

  @p0 @S04
  Scenario: Add a mill the carried user is not yet assigned to
    Given the add-from-mill user is at rest
    When I view the add-from-mill user from mill "9201" on the Mills page
    And I add mill "9198" to the selected user
    Then I should see the message "Mill 9198 - E2E-USR-ADD-TARGET has been activated for user E2E00000000000000000000000000019 -  ."
    And the selected user's assignment to mill "9198" is persisted as "ACTIVE"
    And the row of mill "9198" shows "Active" and offers "Deactivate"
    And the selected user's associated mills are exactly "9198, 9201"

  @p0 @S05 @WRN-001
  Scenario: Adding the mill the carried user is already assigned to warns and changes nothing
    Given the read-only user is at rest
    When I view the read-only user from mill "9199" on the Mills page
    And I add mill "9199" to the selected user
    Then I should see the warning "User E2E00000000000000000000000000015 is already associated to mill E2E-USR-READONLY. Please verify."
    And the selected user's assignment to mill "9199" is persisted as "ACTIVE"
    And the selected user's associated mills are exactly "9199"

  @p0 @S06
  Scenario: Activate an inactive assignment row of the carried user
    Given the row-ended user is at rest
    When I view the row-ended user from mill "9202" on the Mills page
    Then the row of mill "9202" shows "Inactive" and offers "Activate"
    When I activate the assignment to mill "9202"
    Then I should see the message "Mill 9202 - E2E-USR-ROWS has been activated for user E2E00000000000000000000000000020 -  ."
    And the selected user's assignment to mill "9202" is persisted as "ACTIVE"
    And the row of mill "9202" shows "Active" and offers "Deactivate"

  @p0 @S07
  Scenario: Deactivate an active assignment row of the carried user
    Given the row-active user is at rest
    When I view the row-active user from mill "9202" on the Mills page
    Then the row of mill "9202" shows "Active" and offers "Deactivate"
    When I deactivate the assignment to mill "9202"
    Then I should see the message "Mill 9202 - E2E-USR-ROWS has been deactivated for user E2E00000000000000000000000000021 -  ."
    And the selected user's assignment to mill "9202" is persisted as "ENDED"
    And the row of mill "9202" shows "Inactive" and offers "Activate"

  @p0 @S10 @S11 @ERR-002 @SUC-002
  Scenario: Deactivation is refused while assignments are active, and succeeds once each is deactivated
    Given the blocked user is at rest
    When I view the blocked user from mill "9203" on the Mills page
    And I deactivate the selected user's account
    Then I should see the error "User E2E00000000000000000000000000022 -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted."
    And the selected user's account is persisted as "Y"
    When I deactivate the assignment to mill "9203"
    Then I should see the message "Mill 9203 - E2E-USR-BLOCK-A has been deactivated for user E2E00000000000000000000000000022 -  ."
    When I deactivate the assignment to mill "9204"
    Then I should see the message "Mill 9204 - E2E-USR-BLOCK-B has been deactivated for user E2E00000000000000000000000000022 -  ."
    And no row of the selected user is active
    When I deactivate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000022 -   has been deactivated."
    And the selected user's account is persisted as "N"
