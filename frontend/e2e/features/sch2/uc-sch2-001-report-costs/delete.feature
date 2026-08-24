# UC-SCH2-001-S05 — Delete Schedule 2 (AF1 / BR-08)
#
# RE-GROUNDING NOTE — the confirmation is a Carbon Modal ("Delete" / "Cancel"), not a PrimeFaces
# `p:confirmDialog` (`.ui-confirmdialog-yes`) and not a native browser dialog.
#
# After a successful delete the rewrite re-GETs the document, which returns the 200 empty EDITABLE
# document (Schedule 2 never 404s) — so the reporter lands back on a blank, immediately re-enterable
# form, which is exactly the legacy AF1 outcome.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — delete a saved schedule

  As a Licensee
  I want to remove a Schedule 2 I saved in error
  So that the cost report does not carry figures that should not be there

  @p0 @S05
  Scenario: Deleting a saved schedule empties the form and confirms verbatim
    Given the Schedule 2 anchor "delete" has a saved schedule
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    Then the Schedule 2 Delete action is available
    When I delete Schedule 2
    Then the Schedule 2 delete confirmation asks "This will delete the current record. Do you want to continue?"
    When I confirm the Schedule 2 delete
    Then I should see the message "Data deleted successfully"
    # Back to the empty editable document: the fields are blank and immediately re-enterable.
    And the Schedule 2 "Purchased Log Cost cost" field shows ""
    And the Schedule 2 "Less Log Sales volume" field shows ""
    And the Schedule 2 "Less Log Sales cost" field shows ""
    # BR-08 at the source of truth: the summary AND its detail records are gone.
    And no Schedule 2 record is stored
    # Delete closes behind itself: with the record gone there is nothing left to delete, so the button
    # greys out and the hint returns. This assertion USED to be declined here — BUG-1's second face
    # meant the button stayed enabled after a successful delete, and one red per defect was the
    # tracking signal. Both faces are fixed (nr-ilcr #292), so the P0 journey now pins the whole cycle
    # instead of stopping at the delete. See defects.md BUG-1 (CLOSED).
    And the Schedule 2 Delete action is unavailable
    And the Schedule 2 delete-unavailable reason is announced

  # Dismissing the confirmation must be a genuine no-op, not a delayed delete.
  @p1 @S05
  Scenario: Cancelling the confirmation leaves the saved schedule untouched
    Given the Schedule 2 anchor "cancel-delete" has a saved schedule
    And a spy is watching the Schedule 2 save requests
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I note the Schedule 2 mutation count
    And I delete Schedule 2
    And I cancel the Schedule 2 delete
    Then the Schedule 2 delete confirmation is dismissed
    And I should not see the message "Data deleted successfully"
    # The values are still on screen and still stored, and no request was sent at all.
    And the Schedule 2 "Purchased Log Cost cost" field shows "12,345"
    And no further Schedule 2 mutation should have been sent
    And the stored Schedule 2 revision is unchanged
    And the stored Schedule 2 record is:
      | field                | value |
      | purchasedLogCostCost | 12345 |
      | lessLogSalesVolume   | 20    |
      | lessLogSalesCost     | 2000  |
