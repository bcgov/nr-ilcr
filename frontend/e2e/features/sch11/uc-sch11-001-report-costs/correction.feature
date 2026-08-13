# The correct-and-retry recovery arm that UC-SCH11-001-S14..S19 each carry as a second scenario
# ("Licensee corrects the missing X and successfully adds the entry").
#
# WHY ONCE, NOT SIX TIMES. The recovery mechanism is identical for every field — the panel stays open
# with the entered values retained, the offending input is fixed, Add is clicked again, and the row is
# accepted. It is driven by one code path (`handleAdd` clears `addErrors` and re-runs `validateLocation`
# over the whole form), so six copies would exercise the same branch six times while each needing its own
# dedicated write anchor. It is covered once here, on its own key, and the equivalence is recorded in
# coverage.md rather than left implicit. The per-field REJECTIONS — which is what the 25.4 AC actually
# enumerates — are all covered individually in validation.feature.
#
# This scenario deliberately starts from a rejection (so "the panel remains open with the entered values
# retained" is genuinely exercised, not assumed) and then completes the add.

@sch11 @UC-SCH11-001 @validation
Feature: Report Basic Silviculture Costs (Schedule 11) — correct a rejected entry and add it
  As a mill reporter whose entry was rejected
  I want to fix just the offending field and add the location
  So that I do not have to re-enter everything

  @S14 @S17 @p2
  Scenario: A rejected entry keeps its values, and fixing the missing field completes the add
    Given the Schedule 11 anchor "correction" is a pristine editable Draft
    And a spy is watching the Schedule 11 location requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    # Reject first: NAR is left empty.
    And I fill the Add New Location panel:
      | field        | value         |
      | Location     | E2E corrected |
      | Enhanced     | Yes           |
      | Biogeo       | primary       |
      | Actual Cost  | 2500          |
      | Planned Cost | 1500          |
    And I click Add
    Then I should see the error "NAR(ha): Value is required."
    And no Schedule 11 location mutation should have been sent
    And the Schedule 11 location "E2E corrected" is not persisted
    # The panel retained everything already typed — only the missing field needs supplying.
    When I fill the Add New Location panel:
      | field   | value |
      | NAR(ha) | 75.5  |
    And I click Add
    Then I should see the message "Data saved successfully"
    # The values retained through the rejection are the ones that got stored.
    And the Schedule 11 location "E2E corrected" is persisted as:
      | field        | value   |
      | Enhanced     | Yes     |
      | Biogeo       | ESSFdc1 |
      | NAR(ha)      | 75.5    |
      | Actual Cost  | 2500    |
      | Planned Cost | 1500    |
      | Total Cost   | 4000    |
