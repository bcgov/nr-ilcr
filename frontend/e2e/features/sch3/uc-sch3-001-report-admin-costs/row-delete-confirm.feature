# FORMER DIVERGENCE — defects.md DIV-5, bcgov/nr-ilcr#362. FIXED: this scenario was a deliberate red
# until the row delete was put behind the shared confirmation, and went green with no assertion edited;
# only the `@discovered-divergence` tag and the title marker came off. It now guards the prompt.
#
# WHAT IT GUARDS
# Each row on a Schedule 3 cost sub-page carries a small trash-can button. Legacy asked before deleting
# the row: `webapp/schedule3SubtotalOtherCosts.xhtml:94-96` puts
# `<p:confirm header="Confirmation" message="#{msg.confirmDeleteMsg}" icon="ui-icon-alert" />` on the
# per-row Delete. Before #362 the new app deleted and persisted on the click, so one mis-click destroyed a
# recorded cost with no prompt and no undo.
#
# SHARED, NOT SCHEDULE-3-SPECIFIC. The prompt lives in `useEditableCostRows.requestRemove` and the shared
# `EditableSubPageLayout` (which mounts `components/core/ConfirmDeleteModal`), so Schedule 1's
# `other-costs.feature` `@S12` guards the same behaviour from that side (sch1 DIV-3).
#
# WHAT THE ASSERTION PINS. That *a* confirmation is shown and the row survives until it is answered —
# not any particular heading or body text (the unit tests pin the legacy wording).

@sch3 @UC-SCH3-001 @row-delete-confirm
Feature: Report Forest Management Administration Costs (Schedule 3) — removing an itemized cost row
  As a mill reporter
  I want to be asked before an itemized cost row is deleted
  So that a single mis-click cannot destroy a recorded cost with no way back

  @p1 @S04
  Scenario: Removing an other-acceptable cost row asks for confirmation before deleting it
    Given the Schedule 3 anchor "row-delete-confirm"
    And an other-acceptable cost row has already been saved
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page
    Then the sub-page lists the added row
    When I remove the added row
    Then the sub-page asks me to confirm the removal
    And the removed row is still stored
