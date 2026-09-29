# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-001/gherkin/UC-USR-001-S06.feature
# ("View button on a mill row redirects to that mill's record", <<extend>> UC-MILL-002 S01) — the user ->
# mill half of Story 23.4's round-trip criterion, and Story 23.3's AC ("the Mills page opens with that
# mill pre-selected and the search dialog skipped").
#
# RED ON PURPOSE — @discovered-divergence, defects.md DIV-1. The Associated mills rows carry only
# Activate/Deactivate, and the Mills route reads no carried mill (routes/mills.tsx has no validateSearch),
# so there is nothing to click and nowhere for it to land. Story 23.3 marked this AC10 "BLOCKED — do not
# build" on the then-missing Mills page; Story 22.3 has since built that page and the mill -> user half
# (UC-USR-002 S01, green in uc-usr-002-user-from-mill), but not this half, and 23.3 closed as done.
# The test asserts the committed behaviour and stays red until it ships — never skipped, never inverted.
# It looks for a link OR a button named for the mill, so it does not dictate the control.
#
# Read-only: ...15, ACTIVE on 26068 (9199).

@usr @UC-USR-001 @cross-navigation
Feature: Maintain Users — jump from an assigned mill to the Mills page
  As a Ministry Administrator
  I want to open one of a user's assigned mills straight from the Users page
  So that I can move between a user and their mills without searching again

  @p0 @S06 @discovered-divergence @DIV-1
  Scenario: DIV-1 red until built — an assigned mill opens on the Mills page pre-selected (the row offers no way to open it)
    Given the read-only user is at rest
    And the directory can find the seeded licensees
    And I am acting as the Ministry Administrator
    When I open Users from the Administration menu
    And I find and select the read-only user
    And I open mill "9199" from the selected user's associated mills
    Then I am on the Mills page with mill "9199" selected
