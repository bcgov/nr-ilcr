# Re-grounded from UC-SCH3-001-S20..S24.feature (EF7a/EF7b/EF8/EF9a/EF9b) — every entry rejection, on
# the main page and on both sub-pages, plus the recovery arm each legacy slice paired with it.
#
# WHAT RE-GROUNDING CHANGED
#  * Legacy rejected on the field's own AJAX round-trip. The rewrite validates on every keystroke,
#    renders the message as Carbon `invalidText` under the offending control, and additionally BLOCKS
#    Save with an advisory banner ("Please correct the highlighted fields before saving."). Both the
#    legacy FLD-001/FLD-002 wording and the new gate wording are asserted.
#  * FLD-003 was `[UNKNOWN — exact JSF required-field message text not confirmable from source]` in the
#    legacy Gherkin. The rewrite HAS a message — "Description: Value is required." — so S23/S24 are now
#    evidence-backed rather than carrying a placeholder.
#  * Every rejection also PROVES the negative: the mutation spy must see zero writes (over a settled
#    window, not one instant) and the stored schedule must still be empty. A rejected entry that
#    nonetheless reached the server would otherwise look identical from the banner alone.
#  * All of these scenarios share ONE read-only anchor, which is only safe because a client-rejected
#    entry writes nothing — that is exactly what the zero-write assertions establish.

@sch3 @UC-SCH3-001 @validation
Feature: Report Forest Management Administration Costs (Schedule 3) — rejected entry
  As a mill reporter
  I want out-of-range amounts and missing descriptions refused before they are saved
  So that Schedule 3 can only ever hold values the reporting rules allow

  @p1 @S20
  Scenario Outline: A Harvest or PO&P amount outside the allowed range is refused
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I note the Schedule 3 write count
    And I enter "<amount>" into the "Licenses, Fees, Insurance" <column> field
    Then I should see the error "<message>"
    When I save Schedule 3
    Then I should see the error "Please correct the highlighted fields before saving."
    And no Schedule 3 write was attempted
    And the stored Schedule 3 is still empty

    Examples:
      # The last row is a RE-GROUNDING GAIN: legacy had no confirmed message for non-numeric entry, so
      # its catalogue excluded the case. The rewrite has one, so it is now evidence-backed.
      | column  | amount     | message                                                  |
      | Harvest | 100000000  | Entered cost must be between -99,999,999 and 99,999,999. |
      | Harvest | -100000000 | Entered cost must be between -99,999,999 and 99,999,999. |
      | PO&P    | 100000000  | Entered cost must be between -99,999,999 and 99,999,999. |
      | Harvest | 12.5.6     | Entered cost is invalid.                                 |

  @p2 @S20
  Scenario: An in-range amount is accepted after an out-of-range one, and the Crown recalculates
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I enter "100000000" into the "Taxes, Leases, Rentals" Harvest field
    Then I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    # The inclusive upper bound is accepted, and the derived Crown follows it.
    When I enter "99999999" into the "Taxes, Leases, Rentals" Harvest field
    And I enter "1" into the "Taxes, Leases, Rentals" PO&P field
    Then I should not see the message "Entered cost must be between -99,999,999 and 99,999,999."
    And the "Taxes, Leases, Rentals" line shows Harvest "99999999", PO&P "1" and Crown "99999998"

  @p1 @S22
  Scenario Outline: A timber volume outside the allowed range is refused
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I note the Schedule 3 write count
    And I enter "<volume>" into the <field> volume
    Then I should see the error "<message>"
    When I save Schedule 3
    Then I should see the error "Please correct the highlighted fields before saving."
    And no Schedule 3 write was attempted
    And the stored Schedule 3 is still empty

    Examples:
      # Schedule 3 volumes are NON-negative — distinct from Schedule 1's signed range. The last row is a
      # re-grounding gain, as above: the rewrite has a distinct wording for a non-numeric volume.
      # (A MIS-GROUPED value is not that case: the page's parser strips every comma, so "9,9,9" is
      # accepted as 999 — a laxness the app documents against its own stricter `parseDecimalInput`.)
      | field        | volume   | message                                       |
      | PO&P Timber  | 10000000 | Entered volume must be between 0 and 9,999,999. |
      | PO&P Timber  | -1       | Entered volume must be between 0 and 9,999,999. |
      | Crown Timber | 10000000 | Entered volume must be between 0 and 9,999,999. |
      | Crown Timber | -1       | Entered volume must be between 0 and 9,999,999. |
      | Crown Timber | 12.5.6   | Entered volume entry is invalid.                |

  @p2 @S22
  Scenario: An in-range volume is accepted after an out-of-range one
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I enter "10000000" into the Crown Timber volume
    Then I should see the error "Entered volume must be between 0 and 9,999,999."
    When I enter "9999999" into the Crown Timber volume
    Then I should not see the message "Entered volume must be between 0 and 9,999,999."

  @p1 @S23
  Scenario: An other-acceptable row with no description is refused
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Other Costs sub-page
    And I note the sub-page write count
    And I add a sub-page row with no description and a total of "1000"
    Then I should see the error "Description: Value is required."
    And the sub-page row is not added
    And no sub-page write was attempted
    And no other-acceptable rows are stored

  @p1 @S24
  Scenario: An included-unacceptable row with no description is refused
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 Included Unacceptable Costs sub-page
    And I note the sub-page write count
    And I add a sub-page row with no description and a total of "1000"
    Then I should see the error "Description: Value is required."
    And the sub-page row is not added
    And no sub-page write was attempted
    And no included-unacceptable rows are stored

  @p2 @S21
  Scenario Outline: An out-of-range amount on a cost sub-page is refused
    Given the Schedule 3 anchor "validate"
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 3
    And I open the Schedule 3 <sub-page> sub-page
    And I note the sub-page write count
    And I add a sub-page row described "E2E range probe" with a total of "100000000"
    Then I should see the error "Entered cost must be between -99,999,999 and 99,999,999."
    And the sub-page row is not added
    And no sub-page write was attempted

    Examples:
      | sub-page                    |
      | Other Costs                 |
      | Included Unacceptable Costs |
