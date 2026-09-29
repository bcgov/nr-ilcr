# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-MILL-001/gherkin/UC-MILL-001-S11.feature
# and -S15.feature (legacy JSF/PrimeFaces). The "Find and select Mill" guards: a search that matches no
# mill, and a non-numeric mill number.
#
# WHAT RE-GROUNDING CHANGED
#  * `form:messagesId` -> the dialog's own notification. The page banner sits behind the Carbon
#    overlay, so both outcomes render inside the dialog, which stays open for the retry.
#  * S11: the ERR-001 sentence is verbatim, but it arrives on a 200 beside an empty list and renders as
#    an INFO notice titled "No matches" — legacy showed it as an error. The text is asserted; the
#    severity is recorded as defects.md DIV-1 for a ruling, not asserted either way.
#  * S15: legacy's "JSF blocks the postback on a type-conversion failure" -> the backend refuses the
#    search with 400 and legacy's own converter message (validation.properties:52, ported verbatim:
#    `Number: 'abc' must be replaced with …`). The Gherkin marked the text [UNKNOWN]; it is now pinned.
#    "the table is not updated" -> no result table is shown.
#  * Each legacy file's two scenarios (the guard, then the corrected retry) are ONE here.
#  * S11 searches by NAME, which is also the first coverage of that criterion (defects.md GAP-4).
#  * The retry finds 9186 (E2E-READONLY-USERS-TEST): a seeded mill no scenario writes, so its row is
#    stable under parallel runs.

@mill @UC-MILL-001 @search
Feature: Maintain Mills — search guards on "Find and select Mill"
  As a Ministry Administrator
  I want a search that finds nothing, or that I typed wrongly, to tell me so and let me retry
  So that I can always get to the mill I am looking for

  @p1 @S11
  Scenario: A search that matches no mill says so, and a revised search finds the mill
    Given I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open the "Find and select Mill" dialog
    And I search the mill dialog by name "E2E-NO-SUCH-MILL"
    Then the mill dialog shows "No mill matching this criteria has been found. Please ensure the ilcr_mill_status_xref ID matches with mill ID or try importing the mill."
    And the "Find and select Mill" dialog is still open
    And the mill search lists no mill
    When I search the mill dialog by number "9186"
    Then the mill search lists mill "9186"

  @p1 @S15
  Scenario: A non-numeric mill number is refused, and a corrected number searches
    Given I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open the "Find and select Mill" dialog
    And I search the mill dialog by number "abc"
    Then the mill dialog shows "Number: 'abc' must be replaced with a number consisting of one or more digits - decimal values are not accepted."
    And the "Find and select Mill" dialog is still open
    And the mill search lists no mill
    When I search the mill dialog by number "9186"
    Then the mill search lists mill "9186"

  # GAP-4. The Status criterion. 26060 (9191) is Closed and written by no scenario; 26055 (9186) is
  # Active and written by none either, so both halves of the filter are stable under parallel runs.
  # Every other "E2E-" mill may change status mid-run, which is why the assertion is "every listed row
  # reads Close" rather than an exact list.
  @p2 @GAP-4
  Scenario: A search by name and status lists only mills in that status
    Given I am acting as the Ministry Administrator
    When I open Mills from the Administration menu
    And I open the "Find and select Mill" dialog
    And I search the mill dialog by name "E2E-" and status "Close"
    Then the mill search lists mill "9191"
    And every listed mill reads "Close"
    And the mill search does not list mill "9186"
