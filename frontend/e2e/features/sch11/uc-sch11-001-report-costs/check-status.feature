# Re-grounded from UC-SCH11-001-S04/S05/S06.feature.
#
# BR-07: a location passes iff BOTH its Actual and Planned cost are non-null. Missing means NULL — zero
# is present — so the S05/S06 preconditions seed a genuine NULL and assert it landed as null before
# proceeding (seeding 0 would make the scenario prove nothing).
#
# SUC-004 "Status has been checked" fires on EVERY invocation, pass or fail; SUC-003 "All requirements
# ... met" only on the pass branch. That asymmetry is legacy-faithful and is asserted BOTH ways below:
# the fail branches assert the flag message AND that the "met" message is absent.
#
# There is ONE Check Status button in the React app, not legacy's top+bottom pair (DIV-3).
#
# Each of the three scenarios owns its own anchor, so they can run in parallel without one scenario's
# seeded row appearing in another's check-status result.

@sch11 @UC-SCH11-001 @check-status
Feature: Report Basic Silviculture Costs (Schedule 11) — check completeness status
  As a mill reporter
  I want to check whether every location has its cost data
  So that I know what remains before the schedule can be considered complete

  @S04 @p1
  Scenario: Check Status reports all requirements met when every location has both costs
    Given the Schedule 11 anchor "check-met" has a seeded location "E2E S04 met"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I run Check Status
    Then I should see the message "Status has been checked"
    And I should see the message "All requirements for this schedule have been met"
    # AC7 names the Check-Status result as one of the states axe must cover — a freshly-rendered set of
    # notifications is DOM that no other sweep sees.
    And the "Schedule 11 (Check Status result)" view has no WCAG 2.1 AA accessibility violations

  @S05 @p1
  Scenario: Check Status flags a location missing its Actual Cost
    Given the Schedule 11 anchor "check-missing-actual" has a seeded location "E2E S05 noactual" with no "actual" cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I run Check Status
    # Verbatim FLD-004, INCLUDING the literal double space after "location" — composed server-side in
    # Schedule11Service.missingCost() and confirmed live. This is a legacy literal, not a typo to fix.
    # Asserted against the region's RAW textContent: Playwright's text matchers normalize whitespace, so
    # the generic "I should see the error" step would pass a single-space regression.
    Then the Schedule 11 check status shows verbatim "location  : E2E S05 noactual - Actual cost: Value Required"
    And I should see the message "Status has been checked"
    And I should not see the message "All requirements for this schedule have been met"

  @S06 @p1
  Scenario: Check Status flags a location missing its Planned Cost
    Given the Schedule 11 anchor "check-missing-planned" has a seeded location "E2E S06 noplanned" with no "planned" cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I run Check Status
    Then the Schedule 11 check status shows verbatim "location  : E2E S06 noplanned - Planned cost: Value Required"
    And I should see the message "Status has been checked"
    And I should not see the message "All requirements for this schedule have been met"
