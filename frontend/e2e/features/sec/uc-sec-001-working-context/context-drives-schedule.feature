# HOME-1.5 headline outcome (AC2) + the S06 closed-mill consequence (= UC-SCH1-001 S20).
# The working context saved on Home must actually DRIVE the schedule pages: Schedule 1 operates on the
# SELECTED mill/year (not the scaffold default), and a closed mill is blocked from viewing its schedule.
# Cross-cuts SEC (Home) → SCH1 (Schedule 1); read-only, so no teardown. The block message is the verbatim
# 409 detail (ERR-002) confirmed live: "This Mill is not active for the current Reporting Year. Please
# select another mill from the Home Page."

@sec @UC-SEC-001 @HOME-1.5
Feature: A saved working context drives the schedule pages
  As a mill reporter
  I want the mill and year I saved on Home to govern the schedule pages
  So that Schedule 1 shows my selected mill/year and a closed mill is blocked from its schedule

  Background:
    Given I am on the Home page

  @S01 @drives-schedule @p0
  Scenario: A saved context drives Schedule 1 (the selected mill/year, not the default)
    When I select the working context "open with status"
    And I save the working context
    # Wait for SUC-001 before navigating: setContext fires in the SAME success callback as this message,
    # so its visibility guarantees the new context is committed and nav won't race the stale default.
    Then I should see the message "Data saved successfully"
    When I open Schedule 1 for the current context
    Then the Schedule 1 request used the "open with status" context

  @S06 @UC-SCH1-001 @S20 @p1
  Scenario: A closed mill saves on Home but is blocked from viewing its Schedule 1
    When I select the working context "closed"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I try to open Schedule 1 for the current context
    Then the schedule page is blocked with "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
