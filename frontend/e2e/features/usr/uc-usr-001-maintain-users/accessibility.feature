# Story 23.4: "WCAG violations are zero or triaged" (and Story 23.3 AC11: the Users page's axe evidence
# rides this story). One scan per distinct state of the page: nothing selected; the picker's no-match
# note; a user selected with an assignment; and the two notifications that need no write — the
# duplicate-assignment WARNING (that add changes nothing) and the blocked-deactivation ERROR (refused,
# so nothing changes). A SUCCESS notification only exists after a write, so it stays out — defects.md
# GAP-1.
#
# Read-only throughout: ...15 (ACTIVE on 26068). The two write attempts are refused by the server and
# the scenario proves it.

@usr @UC-USR-001 @a11y
Feature: Maintain Users — accessibility (WCAG 2.1 AA)
  As a Ministry Administrator using assistive technology
  I want every state of the Users page to meet WCAG 2.1 AA
  So that I can maintain accounts without a barrier

  Background:
    Given the directory can find the seeded licensees
    And I am acting as the Ministry Administrator

  @p1
  Scenario: The Users page with nothing selected, and its no-match note, have no WCAG 2.1 AA violations
    When I open Users from the Administration menu
    Then the "Users (nothing selected)" view has no WCAG 2.1 AA accessibility violations
    When I search the BCeID directory for user ID "e2e-nobody"
    Then the picker reports that no user matches
    And the "Users (no-match note)" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The Users page with a user and an assignment selected has no WCAG 2.1 AA violations
    Given the read-only user is at rest
    When I open Users from the Administration menu
    And I find and select the read-only user
    Then the selected user's associated mills are exactly "9199"
    And the "Users (user selected)" view has no WCAG 2.1 AA accessibility violations

  @p1 @S09 @WRN-001
  Scenario: The duplicate-assignment warning has no WCAG 2.1 AA violations
    Given the read-only user is at rest
    When I open Users from the Administration menu
    And I find and select the read-only user
    And I add mill "9199" to the selected user
    Then I should see the warning "User E2E00000000000000000000000000015 is already associated to mill E2E-USR-READONLY. Please verify."
    And the selected user's associated mills are exactly "9199"
    And the "Users (duplicate-assignment warning)" view has no WCAG 2.1 AA accessibility violations

  @p1 @S08 @ERR-002
  Scenario: The blocked-deactivation error has no WCAG 2.1 AA violations
    Given the read-only user is at rest
    When I open Users from the Administration menu
    And I find and select the read-only user
    And I deactivate the selected user's account
    Then I should see the error "User E2E00000000000000000000000000015 -   has an 'Active' 'User To Mill Status' on one or more 'Associated Mills' listed below. All 'User To Mill Status' must be set to 'Inactive' before deactivation of this user is permitted."
    And the selected user's account is persisted as "Y"
    And the "Users (blocked-deactivation error)" view has no WCAG 2.1 AA accessibility violations
