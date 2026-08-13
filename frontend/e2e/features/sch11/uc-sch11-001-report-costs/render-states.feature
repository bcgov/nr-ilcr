# Re-grounded from UC-SCH11-001-S11/S12/S13/S20.feature — the four guard states, each rendering its
# message VERBATIM (the frontend prints the server's ProblemDetail `detail` / its own client literal;
# AD-8 means no message is ever re-typed in the UI).
#
# All four anchors are READ-ONLY: the Home Save is a resolve GET that writes no schedule data, so these
# scenarios need no cleanup and are parallel-safe by construction.
#
# DIVERGENCE on S20 (defects.md DIV-2): legacy DISABLED the Add panel's six fields, both Save
# buttons, both Check Status buttons, and every per-row control. The React app instead OMITS the Add
# panel and the per-row Actions entirely when `editable` is false, and disables the single Check Status
# button. Absence is the modern read-only contract; asserted as the app behaves.

@sch11 @UC-SCH11-001 @render-states
Feature: Report Basic Silviculture Costs (Schedule 11) — guard and read-only states
  As a mill reporter
  I want to be told clearly why Schedule 11 content is unavailable or not editable
  So that I know how to proceed

  @S11 @p1
  Scenario: No mill and reporting year selected
    # Client-side guard: the page renders the block WITHOUT issuing any request, so the literal shown is
    # the frontend's ERR_MILL_YEAR_NOT_SELECTED (no trailing space) rather than the server bundle's
    # trailing-space form. Matched as a substring, which holds for either.
    When I open Schedule 11 with no working context
    Then I should see the error "Please Select Mill and Reporting Year in the Home Page."
    And the Schedule 11 content is suppressed

  @S12 @p1
  Scenario: The selected mill is not active for the reporting year
    Given the Schedule 11 guard anchor "closed-mill"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11 expecting a guard message
    # ERR-002, verbatim from the 409 detail.
    Then I should see the error "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the Schedule 11 content is suppressed

  @S13 @p1
  Scenario: No Schedule 11 report exists for the selected mill and reporting year
    Given the Schedule 11 guard anchor "no-schedule"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11 expecting a guard message
    # ERR-003, verbatim from the 404 detail.
    Then I should see the error "Schedule not found."
    And the Schedule 11 content is suppressed

  # Both non-Draft codes are exercised: legacy's guard fires for Submitted ("S") AND Verified ("V"), and
  # covering only one would leave half of the STA-001 condition unproven.
  @S20 @p1
  Scenario Outline: Schedule 11 renders read-only when the silviculture track is <state>
    Given the Schedule 11 guard anchor "<anchor>"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    Then the Add New Location panel is not rendered
    And the Schedule 11 row actions are not rendered
    And the Check Status button is disabled
    # POSITIVE assertion — without it an empty-table regression would satisfy every check above
    # vacuously. Read-only must still DISPLAY the data.
    And the Schedule 11 read-only table still shows the seeded row for "<anchor>"

    Examples:
      | anchor    | state     |
      | submitted | Submitted |
      | verified  | Verified  |
