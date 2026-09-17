# Re-grounded from _bmad-output/implementation-artifacts/tests/UC-SCH5-001/gherkin/UC-SCH5-001-S07.feature
# (legacy JSF/PrimeFaces). S07 deletes a saved camp after confirming.
#
# WHAT RE-GROUNDING CHANGED
#  * `/ILCR/schedule5.xhtml` -> route `/schedule-5`; Delete is scoped to the camp's row in the
#    "Existing Camps" table rather than addressed through `schedule5Form:existingCampDT`.
#  * CFM-001 is a Carbon `Modal` headed "Delete camp" with Yes/No, not a PrimeFaces confirmDialog
#    (`.ui-confirmdialog-yes`). Its TEXT is unchanged from legacy (index.tsx:74), so only the control
#    type moved — worth noting because Schedule 1 and Schedule 3 both carry OPEN divergences for a
#    row delete that has NO confirm at all (their defects.md DIV-3 / DIV-5, issue #362). Schedule 5
#    does confirm, so it is not a fourth instance.
#  * The legacy scenario stops at "no longer appears in the table". This also reads the anchor back
#    through the API: a row can disappear from a client-side list without anything being persisted, so
#    the UI assertion alone would pass against a purely optimistic removal.
#  * ANCHOR: 22050/2022 (`DELETE_ANCHOR`).

@sch5 @UC-SCH5-001 @delete
Feature: Report Camp and Access Expenses (Schedule 5) — delete a camp
  As a mill reporter
  I want to delete a camp record I no longer need
  So that the schedule reflects only the mill's active camps

  @p1 @S07 @CFM-001
  Scenario: Delete an existing camp after confirming
    Given the Schedule 5 anchor "delete" is an editable Draft with no camps
    And a camp named "North Camp" already exists with stored descriptor and expense values
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 5
    And I delete the "North Camp" camp
    Then I should see the confirm text "This will delete the current record. Do you want to continue?"
    When I confirm the "Delete camp" dialog
    Then I should see the message "Data deleted successfully"
    And "North Camp" is no longer listed in the Existing Camps table
    And no camps are stored on the anchor
