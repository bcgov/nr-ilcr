# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S14..S18.feature
# (legacy JSF/PrimeFaces). Check Status is BR-07 readiness validation: POST /api/v1/schedule1/check-status
# (Story 2.6/2.7), read-only — it mutates nothing and renders the outcome on the MAIN Schedule 1 page as
# success / error / warning notifications. Messages are composed server-side and echoed verbatim (AD-8);
# the text below is the new app's message-bundle wording (messages.properties), NOT the legacy strings.
#
# S14/S15/S16 run against stable, populated REAL anchors (discovered 2026-07-30) that no scenario mutates,
# so they are parallel-safe and need no cleanup. S17/S18 need an itemized Other-Costs row and a shared
# volume of 0: the row is added through the real API and removed via the app's DELETE on teardown. Every
# scenario also asserts the schedule is unchanged (revisionCount stable) to prove Check Status is read-only.

@sch1 @UC-SCH1-001 @check-status
Feature: Report Average Cost of Logging (Schedule 1) — Check Status
  As a mill reporter
  I want Check Status to tell me whether Schedule 1 meets its completeness requirements
  So that I know the schedule is ready, or what to fix, before the track is submitted

  @S14 @p0
  Scenario: Check Status confirms all requirements are met
    Given the Schedule 1 anchor "requirements-met" is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the message "All requirements for this schedule have been met"
    And the Schedule 1 data should be unchanged

  @S15 @p1
  Scenario Outline: Check Status reports a missing mandatory field — <case>
    Given the Schedule 1 anchor "<anchor>" is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the error "<message>"
    And the Schedule 1 data should be unchanged

    Examples:
      | case                        | anchor                      | message                                                |
      | missing line-item volume    | missing-line-item-volume    | Standing Tree to Loaded Truck - Volume: Value Required |
      | missing Other Costs volume  | missing-other-costs-volume  | Subtotal Other Costs (0) - Volume: Value Required      |

  @S16 @p1
  Scenario: Check Status flags a Subtotal Other Costs volume present without a cost
    Given the Schedule 1 anchor "other-costs-volume-without-cost" is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the error "Subtotal Other Costs (0): Cost: must be greater than 0 when Volume is greater than 0"
    And the Schedule 1 data should be unchanged

  @S17 @p1
  Scenario: Check Status flags an Other Costs cost present without a shared volume
    Given the Schedule 1 anchor "other-costs-cost-without-volume" has a seeded Other Cost row
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the error "Subtotal Other Costs (1): Volume: must be greater than 0 when Cost is greater than 0"
    And the Schedule 1 data should be unchanged

  @S18 @p2
  Scenario: Check Status warns about an Other Cost row with an empty cost
    Given the Schedule 1 anchor "empty-cost-row" has a seeded Other Cost row
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I check Schedule 1 status
    Then I should see the warning "Subtotal Other Costs (1) - Cost: One or more entries contain an empty Cost value. Please verify there are no Other Costs to be entered."
    And the Schedule 1 data should be unchanged
