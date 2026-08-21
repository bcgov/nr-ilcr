# UC-SCH2-001-S01 — Enter and Save Schedule 2 Amounts (Happy Path)
#
# Re-grounded from the legacy slice onto the React app: route /schedule-2 (not schedule2.xhtml),
# Carbon ids (not schedule2Form:* naming-container ids), and the success text comes from the API's
# own `message.text` (AD-8) rather than a JSF `p:messages` panel.
#
# This scenario is the BR-06 proof. It asserts the carried figures BEFORE any entry, then the
# recomputed figures after the save, then reads the stored record back through the API — so a
# success banner alone can never make it pass. The numbers are pinned in
# fixtures/sch2/schedule2-test-data.ts from a real probe of this anchor, never predicted.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — enter and save purchased log costs and log sales

  As a Licensee
  I want to record the mill's purchased/private log costs and offsetting log sales on Schedule 2
  So that the reporting year's cost report reflects accurate purchased-log and log-sales figures

  @p0 @S01
  Scenario: Enter the cost and log-sales figures, save, and see every derived figure recomputed
    Given the Schedule 2 anchor "happy-path" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # At rest: the Purchased/Private volume is CARRIED from Schedule 3 and the Total Company Logging
    # line from Schedule 1 — neither is entered here (BR-03/BR-04). The two editable cost cells are
    # empty and their $/m³ cells show an em dash.
    Then the Schedule 2 document shows:
      | row                                 | volume | cost | perUnit |
      | Purchased/Private Log Costs:        | 10     |      | —       |
      | Purchased/Private Wood Overhead:    | 10     | 0    | 0.00    |
      | Subtotal:                           | 10     | 0    | 0.00    |
      | (less) Log Sales:                   |        |      | —       |
      | Net Purchased/Private Log Cost:     | 10     | 0    | 0.00    |
      | Total Company Logging Costs(Sch 1): | 10     | 10   | 1.00    |
      | Total Average Logging Costs:        | 20     | 10   | 0.50    |
    # BR-03 proper: the carried Purchased/Private volume is not enterable. Asserting the displayed "10"
    # above is not enough — that value could equally have come from an input — so pin the editable surface.
    And only the purchased-log cost and both log-sales fields are editable
    When I enter the following Schedule 2 values:
      | field                    | value                        |
      | Purchased Log Cost cost  | 50000                        |
      | Less Log Sales volume    | 4                            |
      | Less Log Sales cost      | 1000                         |
      | comments                 | E2E happy path — Schedule 2  |
    # DIV-1 RESOLVED (#291, 2026-08-21): legacy recomputed the derived figures on each field's own
    # `f:ajax event="change"`, so the totals moved as focus left a field. That behaviour is restored by a
    # display-only client mirror (spine AD-5 amended), so BEFORE the save the table already shows the
    # recalculated figures. The numbers below are deliberately IDENTICAL to the post-save block further
    # down: pre-save they come from the mirror, post-save from the server echo, so this scenario is now a
    # genuine mirror-vs-server agreement check — the AC5 "no jump on Save" guarantee, against a real
    # backend. The carried Wood Overhead and Total Company Logging rows must NOT move (they belong to
    # Schedules 3 and 1), which the unchanged rows in the post-save block still assert.
    Then the Schedule 2 document shows:
      | row                             | volume | cost   | perUnit  |
      | Subtotal:                       | 10     | 50,000 | 5,000.00 |
      | (less) Log Sales:               | 4      | 1,000  | 250.00   |
      | Net Purchased/Private Log Cost: | 6      | 49,000 | 8,166.67 |
      | Total Average Logging Costs:    | 16     | 49,010 | 3,063.13 |
    When I save Schedule 2
    Then I should see the message "Data saved successfully"
    # BR-06: subtotal, net purchased and total average are all recomputed server-side from the entered
    # values. Net Purchased = Subtotal − Log Sales; Total Average = Net Purchased + Total Company.
    And the Schedule 2 document shows:
      | row                                 | volume | cost   | perUnit  |
      | Purchased/Private Log Costs:        | 10     | 50,000 | 5,000.00 |
      | Purchased/Private Wood Overhead:    | 10     | 0      | 0.00     |
      | Subtotal:                           | 10     | 50,000 | 5,000.00 |
      | (less) Log Sales:                   | 4      | 1,000  | 250.00   |
      | Net Purchased/Private Log Cost:     | 6      | 49,000 | 8,166.67 |
      | Total Company Logging Costs(Sch 1): | 10     | 10     | 1.00     |
      | Total Average Logging Costs:        | 16     | 49,010 | 3,063.13 |
    # BR-02/BR-04: both line items are persisted — item 25 carries only a cost, item 26 a volume AND
    # a cost. Read back from the API, so this proves storage rather than client state.
    And the stored Schedule 2 record is:
      | field                   | value                       |
      | purchasedLogCostCost    | 50000                       |
      | lessLogSalesVolume      | 4                           |
      | lessLogSalesCost        | 1000                        |
      | comments                | E2E happy path — Schedule 2 |
    And the stored Schedule 2 derived figures are:
      | block        | volume | cost  | perUnit   |
      | subtotal     | 10     | 50000 | 5000      |
      | netPurchased | 6      | 49000 | 8166.6667 |
      | totalAverage | 16     | 49010 | 3063.125  |

  # Legacy rendered Save and Check Status in BOTH a top and a bottom action bar. The rewrite keeps
  # both, so the bottom one must genuinely save rather than being decorative.
  @p2 @S01
  Scenario: The bottom action bar saves too
    Given the Schedule 2 anchor "bottom-bar" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I enter "777" in the Schedule 2 "Purchased Log Cost cost" field
    And I save Schedule 2 from the bottom action bar
    Then I should see the message "Data saved successfully"
    And the stored Schedule 2 record is:
      | field                | value |
      | purchasedLogCostCost | 777   |
