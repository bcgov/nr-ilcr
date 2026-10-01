# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-001/gherkin/UC-USR-001-S06.feature
# ("View button on a mill row redirects to that mill's record", <<extend>> UC-MILL-002 S01) — the user ->
# mill half of Story 23.4's round-trip criterion, and Story 23.3's AC10 ("the Mills page opens with that
# mill pre-selected and the search dialog skipped").
#
# ONE ROUND TRIP, Users -> Mills -> Users. The user -> mill leg shipped in bcgov/nr-ilcr#527 (defects.md
# DIV-1, resolved); the way back is Story 22.3's mill -> user leg (UC-USR-002 S01, whose own carry guards
# live in uc-usr-002-user-from-mill). Driving both legs in one scenario is what proves the round trip
# through the real router and API, not each hand-off in isolation; and the Mills page is axe-scanned in
# the one state only this hand-off produces — a mill selected without the search dialog ever opening.
#
# The carry rides the URL (`/mills?millId=<id>`) and the route consumes it with a replace-navigation, so
# the bare `/mills` afterwards is BR-02 made observable. The open control is looked for as a link OR a
# button named for the mill, so the test does not dictate the control.
#
# Read-only: ...15, ACTIVE on 26068 (9199).

@usr @UC-USR-001 @cross-navigation
Feature: Maintain Users — jump from an assigned mill to the Mills page and back
  As a Ministry Administrator
  I want to open one of a user's assigned mills straight from the Users page
  So that I can move between a user and their mills without searching again

  @p0 @S06 @a11y
  Scenario: An assigned mill opens on the Mills page pre-selected, and its user opens back on the Users page
    Given the read-only user is at rest
    And the directory can find the seeded licensees
    And I am acting as the Ministry Administrator
    When I open Users from the Administration menu
    And I find and select the read-only user
    And I open mill "9199" from the selected user's associated mills
    Then I am on the Mills page with mill "9199" selected
    And the carried mill has been consumed from the URL
    And the "Mills (carried mill)" view has no WCAG 2.1 AA accessibility violations
    When I view the read-only user from the selected mill
    Then the carry has been consumed from the URL
    And the read-only user is selected
    And the selected user's associated mills are exactly "9199"
