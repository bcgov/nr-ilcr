# UC-SCH2-001-S13 / S14 / S15 / S16 — field range validation
#
# RE-GROUNDING NOTE — where validation happens and what it looks like:
#   - legacy rejected an out-of-range value on the field's own `f:ajax event="change"` and surfaced
#     FLD-001/002/003 in the `p:messages` panel. The rewrite validates on every keystroke and renders
#     the SAME verbatim text as Carbon inline `invalidText` under the offending field, then blocks Save
#     with an additional advisory banner. The message text is unchanged, so these scenarios still pin
#     the legacy contract strings.
#   - the range bounds are unchanged: item 25 cost [-99,999,999, 99,999,999]; item 26 volume
#     [0, 9,999,999] (unsigned); item 26 cost [-999,999,999, 999,999,999] (the wider costSize="9" range).
#
# Every scenario here runs on the validate-only anchor and PROVES no write was attempted — a rejection
# that merely failed to navigate would prove nothing.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — out-of-range values are rejected

  As a Licensee
  I want out-of-range costs and volumes refused as I enter them
  So that the cost report cannot carry impossible figures

  Background:
    Given the Schedule 2 anchor "validation" is an unsaved editable Draft
    And a spy is watching the Schedule 2 save requests
    And I have selected that mill and reporting year on the Home page

  @p1 @S13
  Scenario Outline: A Purchased/Private Log Costs cost outside its range is rejected
    When I open Schedule 2
    And I enter "<value>" in the Schedule 2 "Purchased Log Cost cost" field
    Then the Schedule 2 "Purchased Log Cost cost" field is invalid with "Entered cost must be between -99,999,999 and 99,999,999."
    When I save Schedule 2
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 2 save request should not have been sent
    And no Schedule 2 record is stored

    Examples:
      | value      |
      | 100000000  |
      | -100000000 |

  @p2 @S13
  Scenario Outline: A Purchased/Private Log Costs cost exactly on its bound is accepted
    When I open Schedule 2
    And I enter "<value>" in the Schedule 2 "Purchased Log Cost cost" field
    Then the Schedule 2 "Purchased Log Cost cost" field has no inline error

    Examples:
      | value     |
      | 99999999  |
      | -99999999 |

  @p1 @S14
  Scenario Outline: A (less) Log Sales volume outside its range is rejected
    When I open Schedule 2
    And I enter "<value>" in the Schedule 2 "Less Log Sales volume" field
    Then the Schedule 2 "Less Log Sales volume" field is invalid with "Entered volume must be between 0 and 9,999,999."
    When I save Schedule 2
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 2 save request should not have been sent
    And no Schedule 2 record is stored

    Examples:
      | value    |
      | 10000000 |
      | -1       |

  @p2 @S14
  Scenario Outline: A (less) Log Sales volume exactly on its bound is accepted
    When I open Schedule 2
    And I enter "<value>" in the Schedule 2 "Less Log Sales volume" field
    Then the Schedule 2 "Less Log Sales volume" field has no inline error

    Examples:
      | value   |
      | 0       |
      | 9999999 |

  @p1 @S15
  Scenario Outline: A (less) Log Sales cost outside its wider range is rejected
    When I open Schedule 2
    And I enter "<value>" in the Schedule 2 "Less Log Sales cost" field
    Then the Schedule 2 "Less Log Sales cost" field is invalid with "Entered cost must be between -999,999,999 and 999,999,999."
    When I save Schedule 2
    Then I should see the error "Please correct the highlighted fields before saving."
    And the Schedule 2 save request should not have been sent

    Examples:
      | value       |
      | 1000000000  |
      | -1000000000 |

  # The two cost fields carry DIFFERENT bounds (costSize="9" widens item 26). A value legal for item 26
  # and illegal for item 25 proves the wider range really is applied per field rather than globally.
  @p2 @S15
  Scenario: The wider range applies only to the (less) Log Sales cost
    When I open Schedule 2
    And I enter "500000000" in the Schedule 2 "Less Log Sales cost" field
    And I enter "500000000" in the Schedule 2 "Purchased Log Cost cost" field
    Then the Schedule 2 "Less Log Sales cost" field has no inline error
    And the Schedule 2 "Purchased Log Cost cost" field is invalid with "Entered cost must be between -99,999,999 and 99,999,999."

  @p1 @S16
  Scenario: Two fields out of range at once report both errors together
    When I open Schedule 2
    And I enter the following Schedule 2 values:
      | field                   | value     |
      | Purchased Log Cost cost | 100000000 |
      | Less Log Sales volume   | 10000000  |
    And I save Schedule 2
    # Both field validators report independently on the same attempt — not just the first one found.
    Then the Schedule 2 "Purchased Log Cost cost" field is invalid with "Entered cost must be between -99,999,999 and 99,999,999."
    And the Schedule 2 "Less Log Sales volume" field is invalid with "Entered volume must be between 0 and 9,999,999."
    And I should see the error "Please correct the highlighted fields before saving."
    # BR-02: no partial save.
    And the Schedule 2 save request should not have been sent
    And no Schedule 2 record is stored

  # A GAIN over the legacy slices, which deliberately excluded non-numeric entry for want of a confirmed
  # message. The rewrite HAS one: costs are whole dollars, so a fractional cost is refused with its own
  # verbatim text rather than being silently rounded into the record.
  @p2 @S13
  Scenario: A fractional cost is refused rather than silently rounded
    When I open Schedule 2
    And I enter "50.5" in the Schedule 2 "Purchased Log Cost cost" field
    Then the Schedule 2 "Purchased Log Cost cost" field is invalid with "Entered cost is invalid."
    When I save Schedule 2
    Then the Schedule 2 save request should not have been sent
    And no Schedule 2 record is stored

  # The volume field has its own invalid-format message, distinct from the cost one. Both are in the
  # rewrite's message set; covering only the cost message would leave one catalogue row unexercised.
  @p2 @S14
  Scenario: Non-numeric text in the volume field is refused with the volume wording
    When I open Schedule 2
    And I enter "abc" in the Schedule 2 "Less Log Sales volume" field
    Then the Schedule 2 "Less Log Sales volume" field is invalid with "Entered volume entry is invalid."
    When I save Schedule 2
    Then the Schedule 2 save request should not have been sent
    And no Schedule 2 record is stored

  # Legacy Check Status was validateClient="true" — it refused to run on invalid input rather than
  # firing an evaluation that ignored it. The rewrite keeps that gate, with its own wording.
  @p2 @S16
  Scenario: Check Status is also blocked while a field is invalid
    When I open Schedule 2
    And I enter "100000000" in the Schedule 2 "Purchased Log Cost cost" field
    And I check Schedule 2 status
    Then I should see the error "Please correct the highlighted fields before checking status."
    And I should not see the message "All requirements for this schedule have been met"
