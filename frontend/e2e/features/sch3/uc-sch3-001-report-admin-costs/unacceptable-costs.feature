# Re-grounded from UC-SCH3-001-S05.feature (AF2) — itemize "included unacceptable" costs on their own
# sub-page, and see the Schedule 3 count follow on return.
#
# WHAT RE-GROUNDING CHANGED
#  * `schedule3IncludedUnacceptableCosts.xhtml` -> route `/schedule-3/included-unacceptable-costs`. The
#    Add panel is `#add-description` / `#add-total` (this page has a single money column, BR-07).
#  * The read-only Annual Rents (Forest Act, S111) figure — `[UNKNOWN — no explicit id]` in the legacy
#    sidecar — is now `#annualRentsS111`, so BR-04's "Annual Rent is carried here as an unacceptable
#    cost" is directly assertable: it shows the main page's Annual Rents Harvest amount and is disabled.
#  * The Included Unacceptable count on Schedule 3 is NOT simply the row count: the backend adds 1 when
#    Annual Rents carries a non-zero Harvest amount, so a schedule with one itemized row and an Annual
#    Rents amount shows (2). That is legacy-faithful, confirmed at the source —
#    `Schedule3DO.getNumberOfUnacceptableCosts()` carries the comment "add 1 to unacceptable costs total
#    if there is a value for annual rent" and the same non-zero test. (Raised as DIV-4, then RETRACTED.)
#  * As on the other sub-page, Add persists immediately and the Totals footer refreshes from the save
#    echo (this page has no live footer mirror — faithful to its legacy handlers).

@sch3 @UC-SCH3-001 @unacceptable-costs
Feature: Report Forest Management Administration Costs (Schedule 3) — itemize included-unacceptable costs
  As a mill reporter
  I want to record unacceptable overhead costs separately from the acceptable ones
  So that they are excluded from the mill's acceptable administration costs

  @p1 @S05
  Scenario: Add an included-unacceptable cost row and see the count follow
    Given the Schedule 3 anchor "unacceptable"
    And Schedule 3 has been saved with every fixed line and both timber volumes
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    # Annual Rents alone already counts as one included-unacceptable entry (BR-04).
    Then the Included Unacceptable Costs count is 1
    And the "Included Unacceptable Costs" row shows "5000", "—" and "5000"
    When I open the Schedule 3 Included Unacceptable Costs sub-page
    Then the sub-page shows no records
    And the Annual Rents (Forest Act, S111) figure shows "5000"
    When I add an included-unacceptable cost row
    Then I should see the message "Data saved successfully"
    And the sub-page lists the added row
    And the sub-page Totals row shows "11500"
    And the stored included-unacceptable rows are the added row
    When I go back to Schedule 3
    Then the Included Unacceptable Costs count is 2
    And the "Included Unacceptable Costs" row shows "11500", "—" and "11500"
    # The itemized row raises Included Unacceptable Costs, which is subtracted from Subtotal (Actual
    # Costs) to give Total Costs: 285,000 - 11,500 = 273,500.
    And the "Total Costs" row shows "273500", "29850" and "243650"
