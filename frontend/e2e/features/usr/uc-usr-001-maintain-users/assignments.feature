# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-001/gherkin/UC-USR-001-S03, -S11
# and -S12.feature: re-activating an ended assignment, and the two first-time imports of a directory user
# who has never held an ILCR account.
#
# WHAT RE-GROUNDING CHANGED
#  * The Activate on an ended row re-POSTs the assign, which REVIVES the row in place (ACTIVE_DATE set,
#    INACTIVE_DATE cleared) — the same single row, never a second one (BR-07).
#  * S11: activating a user with no account CREATES it ACTIVE, role LICENSEE (deviation (L) — legacy only
#    ever acted on listed users). S12: adding a mill for one PROVISIONS it INACTIVE ('N') with the
#    assignment ACTIVE — the legacy asymmetry Story 23.1 says to replicate, not fix (DEC-2026-08-11
#    §4.4: ACTIVE_IND gates nothing).
#  * S12's "the selected-user row shows Activate (not Deactivate)" -> deviation (O): the add's response
#    carries no account, so the row still shows both actions and "—". The provisioned flag is proved at
#    the DB instead. defects.md VER-3.
#  * ANCHORS: ...12 ENDED on 26066; ...13 and ...14 with NO account (the cleanup deletes what they gain
#    at the DB — scripts/usr_db_restore.py drop-assignment / drop-account); 26067 is S12's target.

@usr @UC-USR-001 @assignments
Feature: Maintain Users — re-activating an assignment, and first-time accounts
  As a Ministry Administrator
  I want to revive a licensee's ended mill assignment, and set up licensees who have never used ILCR
  So that their access follows the mills they report on

  Background:
    Given the directory can find the seeded licensees
    And I am acting as the Ministry Administrator

  @p0 @S03
  Scenario: Re-activate a previously deactivated mill assignment
    Given the reactivate user is at rest
    When I open Users from the Administration menu
    And I find and select the reactivate user
    Then the row of mill "9197" shows "Inactive" and offers "Activate"
    When I activate the assignment to mill "9197"
    Then I should see the message "Mill 9197 - E2E-USR-REACTIVATE has been activated for user E2E00000000000000000000000000012 -  ."
    And the selected user's assignment to mill "9197" is persisted as "ACTIVE"
    And the row of mill "9197" shows "Active" and offers "Deactivate"

  @p0 @S11 @SUC-001
  Scenario: Activating a licensee who has never had an ILCR account creates the account, active
    Given the first-activate user is at rest
    When I open Users from the Administration menu
    And I find and select the first-activate user
    Then the selected user has no associated mills
    When I activate the selected user's account
    Then I should see the message "User E2E00000000000000000000000000013 -   has been activated."
    And the selected user's account is persisted as "Y"
    And the selected user's account shows Active "Y", role "LICENSEE", and offers only "Deactivate"

  @p0 @S12
  Scenario: Adding a mill for a licensee who has never had an ILCR account creates the account inactive, and the assignment active
    Given the first-add user is at rest
    When I open Users from the Administration menu
    And I find and select the first-add user
    And I add mill "9198" to the selected user
    Then I should see the message "Mill 9198 - E2E-USR-ADD-TARGET has been activated for user E2E00000000000000000000000000014 -  ."
    And the selected user's assignment to mill "9198" is persisted as "ACTIVE"
    And the row of mill "9198" shows "Active" and offers "Deactivate"
    And the selected user's account is persisted as "N"
    And the selected user's role and active flag are not shown, and both account actions are offered
