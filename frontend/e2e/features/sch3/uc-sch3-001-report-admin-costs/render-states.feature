# Re-grounded from UC-SCH3-001-S13..S16.feature (EF1/EF2/EF3 + the "Schedule not found" slice) — the
# four states in which Schedule 3 renders something other than an editable form.
#
# WHAT RE-GROUNDING CHANGED
#  * There is no `p:messages` panel and no hand-built `div.ui-messages-error`: each guard renders a Carbon
#    error notification whose subtitle is the API's own `ProblemDetail.detail` (or, for the missing
#    working context, a client-side constant since no request is made at all). Every message string is
#    unchanged from legacy.
#  * S15's read-only state is asserted structurally rather than by the legacy mechanism: the rewrite
#    renders every figure as TEXT (no disabled inputs at all) and disables the three actions, so the
#    proof is "zero editable inputs" plus "all three actions disabled". The outline runs BOTH non-Draft
#    codes — Submitted and Verified — so one arm of the mirror cannot pass for the other.
#  * S15 also drops the legacy assertion about the `subtotalOtherCostsEditsEnabled` links not rendering:
#    the rewrite always renders the count links and simply opens the sub-pages read-only (no discard
#    prompt is needed there — a read-only schedule has no unsaved edits). Asserted as the app behaves;
#    recorded as DIV-3, which is CLOSED as an accepted re-grounding.
#  * S16 ("Schedule not found.") is reached the way the REWRITE reaches it — a mill/year carrying no
#    report-status row. The legacy trigger (no reporting-year context) has no equivalent, and the app has
#    a second, much broader path to the same message that legacy did not have at all: see DIV-1.

@sch3 @UC-SCH3-001 @render-states
Feature: Report Forest Management Administration Costs (Schedule 3) — guard and read-only renders
  As a mill reporter
  I want Schedule 3 to explain itself when it cannot be edited
  So that I know whether to fix my working context or to stop editing

  @p1 @S13
  Scenario: Opening Schedule 3 with no mill and reporting year selected
    Given no mill and reporting year are selected
    Then I should see the error "Please Select Mill and Reporting Year in the Home Page."
    And the Schedule 3 form is not displayed

  @p1 @S14
  Scenario: Opening Schedule 3 for a mill that is not active for the reporting year
    Given the Schedule 3 render-state anchor "closed-mill"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then I should see the error "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the Schedule 3 form is not displayed

  @p2 @S16
  Scenario: Opening Schedule 3 for a mill and year with no report-status row
    Given the Schedule 3 render-state anchor "not-found"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3 expecting a guard
    Then I should see the error "Schedule not found."
    And the Schedule 3 form is not displayed

  @p1 @S15
  Scenario Outline: Schedule 3 is read-only once the Schedules 1-10 track leaves Draft
    Given the Schedule 3 render-state anchor "<anchor>"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    Then the Schedule 3 amount fields are read-only
    And the Schedule 3 actions are disabled
    And both Schedule 3 sub-page links are shown

    Examples:
      | anchor              |
      | readonly-submitted  |
      | readonly-verified   |

  @p2 @S15
  Scenario: The cost sub-pages are read-only too, and open without a discard prompt
    Given the Schedule 3 render-state anchor "readonly-submitted"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page read-only
    Then the sub-page rows are read-only
