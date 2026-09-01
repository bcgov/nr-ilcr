# Re-grounded from UC-SCH3-001-S04.feature (AF1) — itemize grouped "other acceptable" costs on the
# Subtotal Other Costs sub-page, and see the Schedule 3 count and subtotal follow on return.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule3SubtotalOtherCosts.xhtml` -> route `/schedule-3/other-acceptable-costs`, reached from the
#    count link on Schedule 3 ("Subtotal Other Costs (n):"). The Add panel's `addCostForm:*` ids are now
#    `#add-description` / `#add-total` / `#add-pop`, and every listed row is an inline input.
#  * Legacy needed Add THEN Save. In the rewrite Add persists the whole row set immediately (the server
#    reconciles insert/update/delete), and Save is for in-place edits — so this scenario exercises both:
#    Add, then an in-place edit persisted by Save, then Remove.
#  * The per-row Crown $ is derived live from the row's own Total and PO&P (`Total - PO&P`, PO&P treated
#    as 0 when blank), which is the legacy `DescriptionCostType.getCrownCost` rule.
#  * Legacy's per-row delete asked for confirmation; the rewrite's icon-only "Remove" persists at once.
#    This scenario asserts the app as it behaves (so the removal itself stays covered); the missing
#    confirmation is DIV-5, tracked by its own deliberately-RED `row-delete-confirm.feature`.

@sch3 @UC-SCH3-001 @other-costs
Feature: Report Forest Management Administration Costs (Schedule 3) — itemize other-acceptable costs
  As a mill reporter
  I want to record grouped acceptable overhead costs beyond the eleven fixed lines
  So that they are included in the Subtotal Other Costs the schedule carries

  @p1 @S04
  Scenario: Add, edit and remove an other-acceptable cost row
    Given the Schedule 3 anchor "other-acceptable"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    Then the Other Costs count is 0
    When I open the Schedule 3 Other Costs sub-page
    Then the sub-page shows no records
    When I add an other-acceptable cost row
    Then I should see the message "Data saved successfully"
    And the sub-page lists the added row
    And the added row shows a total of "9000" and a Crown of "7500"
    And the sub-page Totals row shows "9000 / 1500 / 7500"
    And the stored other-acceptable rows are the added row
    # The count and the derived subtotal on the parent page follow the sub-page (CNT-001).
    When I go back to Schedule 3
    Then the Other Costs count is 1
    And the "Subtotal Other Costs" row shows "9000", "1500" and "7500"
    # An in-place edit is persisted by the sub-page's own Save (the legacy edit-everything-inline model).
    When I open the Schedule 3 Other Costs sub-page
    And I change the added row total to the edited value
    And I save the sub-page
    Then I should see the message "Data saved successfully"
    And the stored other-acceptable row carries the edited total
    When I remove the added row
    Then I should see the message "Data deleted successfully"
    And the sub-page no longer lists the added row
    And no other-acceptable rows are stored
    When I go back to Schedule 3
    Then the Other Costs count is 0
