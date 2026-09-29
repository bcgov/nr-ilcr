# Story 22.4's accessibility criterion: WCAG 2.1 AA violations on the Mills page are zero, or triaged in
# defects.md. Not a legacy slice — an axe sweep (pages/common/axe.ts, wcag2a/aa + wcag21a/aa) over each
# state the page has: nothing selected, a mill selected (details, editable panel, licensee table), and
# each of its dialogs open. (The import dialog's scan lives in import.feature: it lists the one
# importable mill, which S02/S14 import, so it must run serially with them.)
#
# ANCHOR: 26055 (9186 E2E-READONLY-USERS-TEST) — a seeded mill no scenario writes, with one licensee
# row, so the table state is scanned populated. Nothing here writes: the search and the dialogs are
# opened and scanned, never used. The directory is stubbed only so the Add dialog renders its enabled
# picker rather than the "directory not available" note.

@mill @UC-MILL-001 @a11y
Feature: Maintain Mills — accessibility (WCAG 2.1 AA)
  As a Ministry Administrator using assistive technology
  I want every state of the Mills page to meet WCAG 2.1 AA
  So that I can maintain mills without a barrier

  @p1
  Scenario: The Mills page with nothing selected has no WCAG 2.1 AA violations
    Given I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    Then the "Mills (nothing selected)" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The Mills page with a mill selected, and its search dialog, have no WCAG 2.1 AA violations
    Given the S10 mill is at rest with its status and active users
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open the "Find and select Mill" dialog
    And I search the mill dialog by number "9186"
    Then the "Find and select Mill (with results)" view has no WCAG 2.1 AA accessibility violations
    When I choose mill "9186" from the search results
    Then the "Mills (mill selected)" view has no WCAG 2.1 AA accessibility violations

  @p1
  Scenario: The Find and Add User dialog has no WCAG 2.1 AA violations
    Given the S10 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000005"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I open the Find and Add User dialog
    Then the "Find and Add User" view has no WCAG 2.1 AA accessibility violations

  # GAP-5, the notifications that need no write: the duplicate-user WARNING (that add writes nothing)
  # and the search dialog's ERROR. A SUCCESS notification only exists after a write, so it stays out.
  @p1 @GAP-5
  Scenario: The duplicate-user warning has no WCAG 2.1 AA violations
    Given the S10 mill is at rest with its status and active users
    And the directory can find user "E2E00000000000000000000000000005"
    And I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I search for the mill by its number and select it
    And I add user "E2E00000000000000000000000000005" from the Find and Add User dialog
    Then I should see the warning "User E2E00000000000000000000000000005 is already associated to mill E2E-READONLY-USERS-TEST. Please verify."
    And the "Mills (duplicate-user warning)" view has no WCAG 2.1 AA accessibility violations

  @p1 @GAP-5
  Scenario: The search dialog's error has no WCAG 2.1 AA violations
    Given I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open the "Find and select Mill" dialog
    And I search the mill dialog by number "abc"
    Then the mill dialog shows "Number: 'abc' must be replaced with a number consisting of one or more digits - decimal values are not accepted."
    And the "Find and select Mill (error)" view has no WCAG 2.1 AA accessibility violations
