# Re-grounded from UC-SCH11-001-S02.feature. Two locations added in ONE session, each confirmed, with
# the footer Totals accumulating across both (BR-08 / CNT-001).
#
# The two adds run in one scenario on one dedicated key by design: the point of S02 is that the SECOND
# add sees the first one's contribution in the totals, which two independent scenarios could not prove.

@sch11 @UC-SCH11-001
Feature: Report Basic Silviculture Costs (Schedule 11) — report additional locations in one session
  As a mill reporter with more than one silviculture location
  I want to add several locations in the same session
  So that all of the mill's locations are recorded together with up-to-date totals

  @S02 @p1
  Scenario: Add two locations sequentially and see the totals accumulate
    Given the Schedule 11 anchor "multi-add" is a pristine editable Draft
    And the Schedule 11 location "E2E S02 second" will also be cleaned up
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field        | value         |
      | Location     | E2E S02 first |
      | Enhanced     | Yes           |
      | Biogeo       | primary       |
      | NAR(ha)      | 100.5         |
      | Actual Cost  | 3000          |
      | Planned Cost | 2000          |
    And I click Add
    Then I should see the message "Data saved successfully"
    And the Schedule 11 table lists 1 locations
    And the Schedule 11 footer total "NAR(ha)" shows "100.5"
    And the Schedule 11 footer total "Total Act Plus Plan Cost ($)" shows "5,000"
    # Second location — a different Enhanced value and a different catalogue entry, so the row really is
    # independent rather than a copy of the first.
    When I fill the Add New Location panel:
      | field        | value          |
      | Location     | E2E S02 second |
      | Enhanced     | No             |
      | Biogeo       | secondary      |
      | NAR(ha)      | 50.5           |
      | Actual Cost  | 1000           |
      | Planned Cost | 500            |
    And I click Add
    Then I should see the message "Data saved successfully"
    And the Schedule 11 table lists 2 locations
    And the Schedule 11 location "E2E S02 first" is listed
    And the Schedule 11 location "E2E S02 second" is listed
    # Both rows persisted independently, with their own Enhanced flag and catalogue entry.
    And the Schedule 11 location "E2E S02 first" is persisted as:
      | field        | value   |
      | Enhanced     | Yes     |
      | Biogeo       | ESSFdc1 |
      | NAR(ha)      | 100.5   |
      | Total Cost   | 5000    |
    And the Schedule 11 location "E2E S02 second" is persisted as:
      | field        | value  |
      | Enhanced     | No     |
      | Biogeo       | SBSdh  |
      | NAR(ha)      | 50.5   |
      | Total Cost   | 1500   |
    # Totals now span both locations (100.5 + 50.5 = 151.0 ha; 4,000 actual; 2,500 planned; 6,500 total).
    And the Schedule 11 footer total "NAR(ha)" shows "151.0"
    And the Schedule 11 footer total "Actual Cost ($)" shows "4,000"
    And the Schedule 11 footer total "Planned Cost ($)" shows "2,500"
    And the Schedule 11 footer total "Total Act Plus Plan Cost ($)" shows "6,500"
