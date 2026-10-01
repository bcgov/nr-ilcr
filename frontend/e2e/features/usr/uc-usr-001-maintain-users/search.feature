# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-001/gherkin/UC-USR-001-S04 and
# -S07.feature (second scenario). The journey covers S07's failure and S13; this file carries S07's
# recovery and switching to a different user.
#
# WHAT RE-GROUNDING CHANGED
#  * S04 "Change User re-opens the search dialog with cleared search state" -> there is no Change User
#    button and no dialog: the picker stays on the page above the selected user, and picking another
#    candidate replaces the selection (and its Associated Mills) in place. What S04 protects — that the
#    page never mixes two users' data — is what is asserted.
#  * S07's "Clear" is the ComboBox's own clear; typing a new term replaces the old one, and the note
#    goes as soon as a lookup answers with a candidate.
#  * Read-only: ...15 (ACTIVE on 26068) and ...16 (no assignment). Nothing is written.

@usr @UC-USR-001 @search
Feature: Maintain Users — finding and switching users
  As a Ministry Administrator
  I want to retry a search that found no one, and move from one user to another
  So that I always work on the account I mean to

  Background:
    Given the directory can find the seeded licensees
    And I am acting as the Ministry Administrator

  @p1 @S07 @ERR-001
  Scenario: A search that finds no one can be retried with a user ID that matches
    Given the read-only user is at rest
    When I open Users from the Administration menu
    And I search the BCeID directory for user ID "e2e-nobody"
    Then the picker reports that no user matches
    When I search the BCeID directory for user ID "e2euser15"
    Then the picker offers the read-only user

  @p1 @S04
  Scenario: Picking a different user replaces the selected user and their mills
    Given the read-only user is at rest
    And the no-mills user is at rest
    When I open Users from the Administration menu
    And I find and select the read-only user
    Then the read-only user is selected
    And the selected user's associated mills are exactly "9199"
    When I find and select the no-mills user
    Then the no-mills user is selected
    And the selected user has no associated mills
