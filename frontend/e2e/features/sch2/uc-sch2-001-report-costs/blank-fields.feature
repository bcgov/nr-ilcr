# UC-SCH2-001-S03 / S04 — Save with fields left blank
#
# Both slices assert the SAME underlying rule from two sides: no editable field on Schedule 2 is
# mandatory at Save. Blank is a legitimate saved state, and Check Status (BR-07) — not Save — is what
# catches a missing purchased-log cost. The legacy slices reached this by JSF validator-skip-on-empty
# reasoning; the rewrite makes it explicit (validation.ts skips blank values outright).
#
# RE-GROUNDING NOTE — "computes using zero for the log-sales offset" (S04's wording) is realised as
# NULL PROPAGATION, not a literal zero: with no log-sales values, Net Purchased returns the minuend
# unchanged, and the volume/$-per-m³ cells stay absent (an em dash) rather than showing 0. Confirmed by
# probe on 2026-08-13. Same user-visible outcome the slice describes — the offset does not reduce the
# net — expressed the way this app expresses "no value".

@UC-SCH2-001 @sch2
Feature: Schedule 2 — blank fields are accepted at Save

  As a Licensee
  I want to save Schedule 2 before I have every figure to hand
  So that I can record what I know now and complete the rest later

  @p1 @S03
  Scenario: Save succeeds with the Purchased/Private Log Costs cost left blank
    Given the Schedule 2 anchor "blank-cost" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    # The cost is never touched — "left blank" means untouched, not cleared.
    And I enter the following Schedule 2 values:
      | field                 | value |
      | Less Log Sales volume | 5     |
      | Less Log Sales cost   | 500   |
    And I save Schedule 2
    # No field-level error: Save does not require the cost (only Check Status does — see S08).
    Then I should see the message "Data saved successfully"
    And the Schedule 2 "Purchased Log Cost cost" field has no inline error
    # BR-02: the record is persisted with NO cost value — a blank cost is a real saved state.
    And the stored Schedule 2 record is:
      | field                   | value |
      | purchasedLogCostCost    |       |
      | lessLogSalesVolume      | 5     |
      | lessLogSalesCost        | 500   |
    And the Schedule 2 schedule is stored

  @p1 @S04
  Scenario: Save succeeds with both (less) Log Sales fields left blank
    Given the Schedule 2 anchor "blank-sales" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I enter "8000" in the Schedule 2 "Purchased Log Cost cost" field
    And I save Schedule 2
    Then I should see the message "Data saved successfully"
    And the Schedule 2 "Less Log Sales volume" field has no inline error
    And the Schedule 2 "Less Log Sales cost" field has no inline error
    # BR-06 with no offset: the entered cost flows through Subtotal, Net Purchased and Total Average
    # unreduced, and every figure with no contributor stays absent rather than becoming zero.
    And the Schedule 2 document shows:
      | row                             | volume | cost  | perUnit |
      | Subtotal:                       | —      | 8,000 | —       |
      | Net Purchased/Private Log Cost: | —      | 8,000 | —       |
      | Total Average Logging Costs:    | —      | 8,000 | —       |
    And the stored Schedule 2 record is:
      | field                   | value |
      | purchasedLogCostCost    | 8000  |
      | lessLogSalesVolume      |       |
      | lessLogSalesCost        |       |
