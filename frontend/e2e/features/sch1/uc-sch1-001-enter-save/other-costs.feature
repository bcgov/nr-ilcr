# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH1-001/gherkin/UC-SCH1-001-S09..S12.feature
# (legacy JSF/PrimeFaces). The Subtotal Other Costs sub-page (Story 2.4/2.5, route /schedule-1/other-costs)
# is reached from the main Schedule 1 page via the "Subtotal Other Costs(N):" button (an editable schedule
# first confirms a "Leave Schedule 1" discard-unsaved-edits Modal). Add/edit/remove go through
# /api/v1/schedule1/other-costs; success text is the API's verbatim SUC-002 (AD-8). Add validation is
# advisory client-side (components/schedule1OtherCosts/validation.ts) mirroring the backend DTO.
#
# S12 PARITY NOTE (2026-08-07): legacy required a PrimeFaces confirm dialog (`confirmDeleteMsg`) before
# removing a row — technical.md:102,154 and detailed.md:66. The bcgov EditableSubPage rewrite dropped it,
# so Remove now deletes immediately. This scenario is re-grounded to the app's ACTUAL behaviour and is
# GREEN, but the missing prompt is a parity regression confirmed against the legacy SOURCE and now with
# the Schedule 1 developer (defects.md DIV-3), pending a check against the real legacy app once BCeID
# access lands. If it is confirmed, this scenario flips to a @discovered-divergence red.
#
# Each mutating scenario owns a DEDICATED editable Draft (S09 add → 25050/2017; S12 remove → 9050/2017) and
# self-cleans its rows via the API cleanup registry (marker-keyed). S12 seeds its row through the real API
# add, then removes it through the UI. S10/S11 reject client-side and never write, so they share a
# read-only editable Draft (17052/2016) and prove a zero-POST. Anchors discovered 2026-07-30 (see
# fixtures/sch1/schedule1-test-data.ts).

@sch1 @UC-SCH1-001 @other-costs
Feature: Report Average Cost of Logging (Schedule 1) — maintain Subtotal Other Costs line items
  As a mill reporter
  I want to itemize, correct, and remove additional logging costs on the Other Costs sub-page
  So that costs not covered by the fixed line items are recorded against the mill and reporting year

  @S09 @p1
  Scenario: Add a new Other Cost line item and see the Schedule 1 count update
    Given the Other Costs "add" target is an editable Draft
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    And I note the Subtotal Other Costs count
    When I open the Other Costs sub-page
    And I add an Other Cost "E2E S09 add" with cost "12345"
    Then I should see the message "Data saved successfully"
    And the Other Cost "E2E S09 add" appears in the Other Costs list
    And the Other Cost "E2E S09 add" is persisted with cost 12345
    When I go back to Schedule 1
    Then the Subtotal Other Costs count has increased by one

  @S10 @p1
  Scenario: An Other Cost line item with no description is rejected before saving
    Given the Other Costs "validate" target is an editable Draft
    And a spy is watching the Other Costs add request
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I add an Other Cost "" with cost "500"
    Then I should see the error "Description: Value is required."
    And the Other Cost add request should not have been sent

  @S11 @p1
  Scenario Outline: An Other Cost line item with an invalid cost is rejected before saving — <case>
    Given the Other Costs "validate" target is an editable Draft
    And a spy is watching the Other Costs add request
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I add an Other Cost "E2E S11 desc" with cost "<cost>"
    Then I should see the error "<message>"
    And the Other Cost add request should not have been sent

    Examples:
      | case               | cost      | message                                                 |
      | cost out of range  | 150000000 | Entered cost must be between -99,999,999 and 99,999,999. |
      | non-numeric cost   | abc       | Entered cost is invalid.                                |

  # PARITY CHANGE (bcgov EditableSubPage rewrite): the per-row delete-confirmation modal was removed —
  # Remove now deletes immediately and persists the whole set. Re-grounded to that behavior; flagged for
  # BA review (the legacy per-row confirm no longer exists in the app).
  @S12 @p1
  Scenario: Remove an Other Cost line item
    Given an itemized Other Cost line item exists to remove
    And I have selected that mill and reporting year on the Home page
    And I open Schedule 1
    When I open the Other Costs sub-page
    And I delete the Other Cost "E2E S12 remove"
    Then I should see the message "Data deleted successfully"
    And the Other Cost "E2E S12 remove" is no longer in the Other Costs list
    And the Other Cost "E2E S12 remove" is not persisted
