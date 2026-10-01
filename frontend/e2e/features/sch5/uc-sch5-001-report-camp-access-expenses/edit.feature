# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S02.feature
# (legacy JSF/PrimeFaces). S02 reopens an already-saved camp, confirms it comes back pre-filled, changes
# a descriptor and a category cost, and saves.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; `schedule5Form:campReportPnl` and every
#    `schedule5Form:*` field id is gone. The EDIT panel and the ADD panel are the same React component
#    over one `GRID_ROWS` table, so they cannot drift apart the way the legacy pair of fragments could.
#  * The legacy scenario clicks Edit "on the North Camp row of schedule5Form:existingCampDT". Here the
#    Edit control is scoped to that camp's row in the "Existing Camps" table — every row carries its own
#    Edit/Copy/Delete trio, so an unscoped lookup would be ambiguous the moment a mill-year has two camps.
#  * The precondition ("a camp already exists ... with stored values") is created by the scenario itself
#    through the app's own POST rather than seeded as SQL, so the starting state is exactly what a user's
#    first save produces — and the camp is removed again by the cleanup registry.
#  * The legacy scenario stops at "the camp panel redisplays with the recalculated sub-totals". This also
#    reads the record back and asserts `revisionCount` advanced 0 -> 1, which is what distinguishes a
#    genuine UPDATE from the page re-rendering values it already held.
#  * ANCHOR: 9050/2022 (`EDIT_ANCHOR`), one of the cells opened by the sch5 fan-out.
#
# The legacy text also asserts all 11 category Volume/Cost/$/m3 fields are populated. The panel renders
# every category from ONE component and one table, so they populate together or not at all; this asserts
# the five descriptors plus both halves of a representative category rather than restating the grid.

@sch5 @UC-SCH5-001 @edit
Feature: Report Camp and Access Expenses (Schedule 5) — edit an existing camp
  As a mill reporter
  I want to change the descriptors or expense amounts of an already-saved camp
  So that the camp's reported figures reflect corrected or updated data

  @p1 @S02
  Scenario: Edit an existing camp's descriptors and cost amounts and save successfully
    Given the Schedule 5 anchor "edit" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I edit the "North Camp" camp
    Then the "North Camp" panel is populated with the stored values
    When I change the road distance and the Catering and Food cost
    And I save the camp
    Then I should see the message "Data saved successfully"
    And the edited camp carries the recalculated totals
