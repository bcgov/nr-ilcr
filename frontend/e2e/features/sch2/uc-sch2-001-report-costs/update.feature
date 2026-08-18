# UC-SCH2-001-S02 — Update Previously Saved Schedule 2 Values
#
# The slice's point is two-fold: previously saved amounts are SHOWN pre-filled on reopen (Basic Flow
# step 2, "any previously saved amounts are shown"), and saving again OVERWRITES the existing detail
# records rather than inserting a second pair (BR-02).
#
# The overwrite is proven by the optimistic-lock token moving 1 -> 2 while the record still holds
# exactly one set of values — an insert would leave the original values reachable.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — update previously saved values

  As a Licensee
  I want to correct purchased-log and log-sales figures I saved earlier
  So that the cost report carries the corrected amounts

  @p1 @S02
  Scenario: Previously saved amounts are pre-filled, and saving again overwrites them
    Given the Schedule 2 anchor "update" has a saved schedule
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # The prior save is what the reporter sees on reopen — grouped for display ("12,345"), not raw.
    Then the Schedule 2 "Purchased Log Cost cost" field shows "12,345"
    And the Schedule 2 "Less Log Sales volume" field shows "20"
    And the Schedule 2 "Less Log Sales cost" field shows "2,000"
    And the Schedule 2 "comments" field shows "seeded by e2e"
    And the Schedule 2 Delete action is available
    When I enter the following Schedule 2 values:
      | field                   | value          |
      | Purchased Log Cost cost | 99999          |
      | Less Log Sales volume   | 10             |
      | Less Log Sales cost     | 1000           |
      | comments                | updated by e2e |
    And I save Schedule 2
    Then I should see the message "Data saved successfully"
    # BR-06: the derived blocks are recomputed from the NEW values, not the seeded ones.
    And the Schedule 2 document shows:
      | row                             | volume | cost   | perUnit   |
      | Purchased/Private Log Costs:    | 1      | 99,999 | 99,999.00 |
      | Subtotal:                       | 1      | 99,999 | 99,999.00 |
      | (less) Log Sales:               | 10     | 1,000  | 100.00    |
      | Net Purchased/Private Log Cost: | -9     | 98,999 | -10,999.89 |
    # One set of values, overwritten in place — the record is the new state, with no trace of the old.
    And the stored Schedule 2 record is:
      | field                   | value          |
      | purchasedLogCostCost    | 99999          |
      | lessLogSalesVolume      | 10             |
      | lessLogSalesCost        | 1000           |
      | comments                | updated by e2e |
