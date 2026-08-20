# UC-SCH2-001-S07 / S08 — Check Status (BR-07)
#
# BR-07's two sides: Check Status reports all-requirements-met when the Purchased/Private Log Costs cost
# is present, and "Value Required" against that field's label when it is not. Nothing is mutated either
# way — Check Status is a read-only evaluation by contract.
#
# The ISSUES line is assembled server-side as "<label>: <message>" (Schedule2Controller prefixes the
# resolved missingRequiredFieldMsg with the legacy field label), so the whole string is pinned verbatim
# rather than either half being asserted alone.

@UC-SCH2-001 @sch2
Feature: Schedule 2 — Check Status

  As a Licensee
  I want to check whether Schedule 2 is complete
  So that I know what is outstanding before the schedules are submitted

  @p0 @S07
  Scenario: Check Status reports all requirements met when the purchased-log cost is present
    Given the Schedule 2 anchor "check-met" has a saved schedule
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I check Schedule 2 status
    Then I should see the message "All requirements for this schedule have been met"
    And I should not see the message "Purchased/Private Log Costs - Cost: Value Required"
    # A read-only evaluation: the token must not move, so nothing was written.
    And the stored Schedule 2 revision is unchanged

  @p0 @S08
  Scenario: Check Status flags the missing purchased-log cost against its own label
    Given the Schedule 2 anchor "check-missing" is an unsaved editable Draft
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I check Schedule 2 status
    Then I should see the warning "Purchased/Private Log Costs - Cost: Value Required"
    And I should not see the message "All requirements for this schedule have been met"
    # The entries are left exactly as they were — Check Status neither saves nor clears.
    And the Schedule 2 "Purchased Log Cost cost" field shows ""
    And no Schedule 2 record is stored

  # The follow-on of S03: a schedule can be SAVED and still incomplete, which is the whole reason
  # BR-07 exists as a separate check rather than a Save-time requirement.
  @p1 @S08
  Scenario: A saved schedule with no purchased-log cost still fails Check Status
    Given the Schedule 2 anchor "saved-incomplete" has a saved schedule with no purchased log cost
    And I have selected that mill and reporting year on the Home page
    When I open Schedule 2
    And I check Schedule 2 status
    Then I should see the warning "Purchased/Private Log Costs - Cost: Value Required"
    And the stored Schedule 2 revision is unchanged
