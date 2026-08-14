# Re-grounded from UC-SCH11-001-S09.feature — "add-is-save".
#
# The legacy note flagged this as "a non-obvious, code-grounded behavior; confirm against a live
# instance since no reference doc or recording exists to independently corroborate it". This scenario IS
# that confirmation, and it is now stronger than legacy's: legacy inferred persistence from
# `addLocation()` calling `save(true)` internally, whereas the React app's Add is literally a POST that
# writes the row — so the reopen below reads it back from the database.
#
# Legacy's "navigate away without clicking either Save button" is expressed as a full page RELOAD, which
# is the stronger form: it discards all in-memory React state (and the TanStack Router cache), so a row
# that survives it can only have come from the server.

@sch11 @UC-SCH11-001
Feature: Report Basic Silviculture Costs (Schedule 11) — Add persists without a separate Save
  As a mill reporter
  I want the location I added to survive even though there is no separate Save step
  So that I do not lose data by navigating away right after adding

  @S09 @p1
  Scenario: A location added with Add alone survives a full page reopen
    Given the Schedule 11 anchor "persist" is a pristine editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 11
    And I fill the Add New Location panel:
      | field    | value           |
      | Location | E2E S09 persist |
      | Enhanced | Yes             |
      | Biogeo   | primary         |
      | NAR(ha)  | 60.5            |
    And I click Add
    Then I should see the message "Data saved successfully"
    # No Save click of any kind happens in this scenario — the app has no page-level Save to click.
    When I reopen Schedule 11
    Then the Schedule 11 location "E2E S09 persist" is listed
    # Costs were never entered, so both stay NULL (they are optional at entry) and the derived total is
    # null too — null renders BLANK, never "0", which is a meaningful distinction the app preserves.
    And the Schedule 11 location "E2E S09 persist" is persisted as:
      | field        | value   |
      | Enhanced     | Yes     |
      | Biogeo       | ESSFdc1 |
      | NAR(ha)      | 60.5    |
      | Actual Cost  |         |
      | Planned Cost |         |
      | Total Cost   |         |
    And the Schedule 11 row "E2E S09 persist" shows "" in "Actual Cost ($)"
    And the Schedule 11 row "E2E S09 persist" shows "" in "Total Act Plus Plan Cost ($)"
