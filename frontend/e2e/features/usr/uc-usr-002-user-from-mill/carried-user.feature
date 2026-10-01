# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-USR-002/gherkin/UC-USR-002-S01, -S08
# and -S09.feature: arriving at the Users page from a mill with the user pre-selected, switching away,
# and opening the page with no carried user — plus the two carry guards Story 23.3 ruled on.
#
# WHAT RE-GROUNDING CHANGED
#  * THE CARRY RIDES THE URL, not the session: the Mills page's View navigates to
#    `/mill-associations?userGuid=<guid>`, and the route CONSUMES the parameter on arrival — a
#    replace-navigation to the bare URL. That is BR-02 ("the hand-off value ... is cleared so a later
#    page open does not reuse it"), now UI-observable: the URL, and a later open of the page with no one
#    selected. BR-03 (the extra DB session released) has no rebuild analogue — there is no second session.
#  * S01's "Select User hidden, Change User shown" -> neither button exists; the picker is always on the
#    page and shows the carried user as its selection. "Activate or Deactivate according to the active
#    flag" -> BOTH, deviation (O) (no account read). defects.md VER-3.
#  * S09 second scenario ("Select User reopens the search dialog") -> the search-first default IS the
#    picker, already on the page; asserted with the first scenario.
#  * The carried user is resolved by ONE exact directory lookup of its GUID, stubbed (defects.md SPEC-1).
#  * CARRY GUARDS (Story 23.3 AC10, ruling (N) — the destination opens with no selection and says why):
#    a malformed `userGuid` is dropped by the route before any lookup; a well-formed one the directory
#    cannot resolve (a departed user — Story 2.5 AC4) shows the page's error and selects no one.
#  * Read-only: ...15 (ACTIVE on 26068, 9199) and ...16 (no assignment).

@usr @UC-USR-002 @carried-user
Feature: Maintain User from a Mill Record — arriving with a user
  As a Ministry Administrator
  I want the Users page to open on the user I chose from a mill
  So that I can maintain that user without searching for them again

  Background:
    Given the directory can find the seeded licensees
    And I am acting as the Ministry Administrator

  @p0 @S01
  Scenario: View on a mill's associated user opens the Users page with that user pre-selected
    Given the read-only user is at rest
    When I view the read-only user from mill "9199" on the Mills page
    Then the carry has been consumed from the URL
    And the read-only user is selected
    And the selected user's associated mills are exactly "9199"
    And the selected user's role and active flag are not shown, and both account actions are offered
    When I open the Users page again from the Administration menu
    Then no user is selected

  @p1 @S08
  Scenario: After arriving from a mill, picking a different user replaces the carried one
    Given the read-only user is at rest
    And the no-mills user is at rest
    When I view the read-only user from mill "9199" on the Mills page
    Then the read-only user is selected
    When I find and select the no-mills user
    Then the no-mills user is selected
    And the selected user has no associated mills

  @p1 @S09
  Scenario: Opened with no carried user, the page starts search-first with no one selected
    When I open Users from the Administration menu
    Then no user is selected
    And no directory lookup was sent

  @p1 @S09 @GUARD-MALFORMED
  Scenario: A malformed carried user is dropped before any lookup
    When I open the Users page directly with "?userGuid=not-a-guid"
    Then the carry has been consumed from the URL
    And no user is selected
    And no directory lookup was sent

  @p1 @GUARD-UNRESOLVED
  Scenario: A carried user the directory cannot resolve selects no one, and says so
    When I open the Users page directly with "?userGuid=E2E00000000000000000000000000099"
    Then the page reports that the carried user could not be looked up
    And the carry has been consumed from the URL
    And no user is selected
