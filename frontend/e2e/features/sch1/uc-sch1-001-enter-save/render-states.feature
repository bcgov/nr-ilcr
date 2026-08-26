# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S19..S22.feature
# (legacy JSF/PrimeFaces). These are the Schedule 1 page's context-guard RENDER states in the React/Carbon
# app (components/schedule1/index.tsx): the working context comes from the in-memory MillYearContext set on
# Home, and the schedule1 GET enforces the guards server-side. All read-only — the Home Save is a resolve
# GET (writes no report data), so no cleanup and parallel-safe. Anchors + verbatim guard text discovered
# 2026-07-30 (see fixtures/sch1/schedule1-test-data.ts). Guard messages are the new app's wording (ERR-001
# client chrome; 409/404 ProblemDetail.detail echoed verbatim), NOT the legacy strings.

@sch1 @UC-SCH1-001 @render-states
Feature: Report Average Cost of Logging (Schedule 1) — context-guard render states
  As a mill reporter
  I want Schedule 1 to tell me why the input form is unavailable, or to be read-only when it must be
  So that I understand the state of my schedule and cannot change data I should not

  @S19 @p1
  Scenario: No mill and reporting year selected suppresses the input form
    When I open Schedule 1 with no working context
    Then I should see the error "Please Select Mill and Reporting Year in the Home Page."
    And the Schedule 1 input form is not displayed

  @S20 @p1
  Scenario: A mill closed for the reporting year is blocked from viewing Schedule 1
    Given the Schedule 1 render-state anchor "closed-mill" is selected
    And I have selected that mill and reporting year on the Home page
    When I navigate to Schedule 1 expecting a guard
    Then I should see the error "This Mill is not active for the current Reporting Year. Please select another mill from the Home Page."
    And the Schedule 1 input form is not displayed

  # INVERTED by defect #296. This scenario used to assert the defect: a mill/year with no Schedule 1
  # summary showed an error and no form, which meant a Schedule 1 could never be started on a newly
  # opened reporting year (the PUT 404'd too). The summary is now created by the first save, as it
  # already was on Schedule 2, so the same anchor must render a blank, usable form.
  @S21 @p1
  Scenario: A mill and year with no Schedule 1 yet opens a blank, editable form
    Given the Schedule 1 render-state anchor "no-schedule" is selected
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 1
    Then the Schedule 1 input form is displayed
    And every Schedule 1 amount is blank
    And the Schedule 1 Delete action is not offered

  @S22 @p1
  Scenario: A schedule outside Draft is displayed read-only with the actions disabled
    Given the Schedule 1 render-state anchor "submitted" is selected
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    Then the Schedule 1 amount and comment fields are read-only
    And the Schedule 1 actions are disabled
