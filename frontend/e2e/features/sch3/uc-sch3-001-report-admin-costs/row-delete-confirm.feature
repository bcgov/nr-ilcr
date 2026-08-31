# DIVERGENCE — this scenario is DELIBERATELY RED. It reproduces defects.md DIV-5, tracked upstream as
# bcgov/nr-ilcr#362, and stays failing until the confirmation is restored. Do not weaken it, skip it, or
# "fix" it by asserting the current behaviour: the failing state IS the tracking signal. Filter it out of
# a fresh-failures run with `npm run test:gate`.
#
# WHAT IT REPRODUCES
# Each row on a Schedule 3 cost sub-page carries a small trash-can button. Clicking it deletes that row
# and persists the change immediately — one mis-click destroys a recorded cost with no prompt and no
# undo. Legacy asked first: `webapp/schedule3SubtotalOtherCosts.xhtml:94-96` puts
# `<p:confirm header="Confirmation" message="#{msg.confirmDeleteMsg}" icon="ui-icon-alert" />` on the
# per-row Delete. Confirmed at the legacy SOURCE (docs/nr-ilcr-2.0.4), not merely from the sidecar.
#
# The new app is also internally inconsistent about it: the whole-schedule Delete kept its "Delete
# schedule" confirm modal, so the app confirms the large destructive action and not the small one.
#
# SHARED, NOT SCHEDULE-3-SPECIFIC. The behaviour lives in `useEditableCostRows.removeRow` ->
# `persist(next, 'delete')`, inside the shared EditableSubPage rewrite — so Schedule 1 has the same
# defect and logged it first (`features/sch1/uc-sch1-001-enter-save/defects.md` DIV-3). #362 covers all
# three pages, and Schedule 1's `other-costs.feature` `@S12` now tracks it from that side — one fix in
# the shared hook turns both suites' reds green.
#
# WHAT THE ASSERTION PINS. That *a* confirmation is shown — not any particular heading or body text.
# The chrome a fix would use is the developer's choice, and the repo already has
# `components/core/ConfirmDeleteModal`. What the legacy guarantee requires is that something asks before
# the row is destroyed.

@sch3 @UC-SCH3-001 @row-delete-confirm
Feature: Report Forest Management Administration Costs (Schedule 3) — removing an itemized cost row
  As a mill reporter
  I want to be asked before an itemized cost row is deleted
  So that a single mis-click cannot destroy a recorded cost with no way back

  @discovered-divergence @p1 @S04
  Scenario: Removing an other-acceptable cost row asks for confirmation before deleting it [DISCOVERED DIVERGENCE — the row delete has no confirmation; defects.md DIV-5 / issue #362]
    Given the Schedule 3 anchor "row-delete-confirm"
    And an other-acceptable cost row has already been saved
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page
    Then the sub-page lists the added row
    When I remove the added row
    Then the sub-page asks me to confirm the removal
    And the removed row is still stored
