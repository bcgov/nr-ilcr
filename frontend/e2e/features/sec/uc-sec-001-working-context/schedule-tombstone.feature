# UC-SEC-001 (banner → tombstone, bcgov #227): the working context a Home Save establishes now DISPLAYS
# on the schedule pages' shared ScheduleTombstone header (the global ContextBanner moved there). These
# port the former Home-banner display arm (S01 display / S03 switch / S06 closed / S07 no-status) to
# where the lines now render — Schedule 2, a ScheduleTombstone page. The tombstone reuses the SAME
# `region[name="Working context"]` landmark + WorkingContextLines as the Home banner, so the expected
# text comes from the same fixture builders (fixtures/sec/working-context-test-data.ts). Navigation is
# client-side so the in-memory MillYearContext survives. Read-only (Home Save is a resolve GET) → no
# teardown; parallel-safe by construction.

@sec @UC-SEC-001 @HOME-1.5 @tombstone
Feature: The saved working context displays on the schedule tombstone
  As a mill reporter
  I want the mill and year I saved on Home to appear on every schedule page's header
  So that I always know which mill and reporting year I am working in

  Background:
    Given I am on the Home page

  @S01 @a11y @p1
  Scenario: An established context renders the mill line and both track statuses on the tombstone
    When I select the working context "open with status"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I open "Schedule 2" from the side-nav
    Then the working-context tombstone shows the "open with status" context
    And the "Schedule 2 tombstone" view has no WCAG 2.1 AA accessibility violations

  @S03 @p1
  Scenario: Switching the working context replaces the tombstone lines
    When I select the working context "open with status"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I open "Schedule 2" from the side-nav
    Then the working-context tombstone shows the "open with status" context
    Given I am on the Home page
    And I select the working context "open alternate"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I open "Schedule 2" from the side-nav
    Then the working-context tombstone shows the "open alternate" context
    And the schedule page no longer shows the "open with status" context

  @S06 @p1
  Scenario: A closed mill renders its tombstone exactly like an open mill
    When I select the working context "closed"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I open "Schedule 2" from the side-nav
    Then the working-context tombstone shows no closed-mill wording for "closed"

  @S07 @p1
  Scenario: A mill and year with no report-status row renders the mill line only
    When I select the working context "no status"
    And I save the working context
    Then I should see the message "Data saved successfully"
    When I open "Schedule 2" from the side-nav
    Then the working-context tombstone shows only the mill line for "no status"
